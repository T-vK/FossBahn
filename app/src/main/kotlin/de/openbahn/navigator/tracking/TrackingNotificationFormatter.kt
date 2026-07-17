package de.openbahn.navigator.tracking

import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import de.openbahn.model.railLegs
import de.openbahn.navigator.data.TrackedJourneyWithJourney
import de.openbahn.navigator.ui.util.formatJourneyClock
import de.openbahn.navigator.ui.util.parseJourneyDateTime
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Localized text pieces the foreground notification formatter needs. Kept behind an interface so the
 * formatter can be unit tested without an Android [android.content.Context].
 */
interface TrackingNotificationStrings {
    /** Platform segment such as `Pt. 14A-D` (localized prefix). */
    fun platformSegment(platform: String): String

    /** Multi-connection title, e.g. `3 tracked connections`. */
    fun multiTitle(count: Int): String
}

/** An inclusive-exclusive character range to render in bold within a [StyledNotificationText]. */
data class BoldRange(val start: Int, val end: Int)

/**
 * Plain notification text plus the character ranges that should be rendered bold. Kept free of any
 * Android types so the formatter stays unit testable; the service turns it into a `Spannable`.
 */
data class StyledNotificationText(
    val text: String,
    val boldRanges: List<BoldRange> = emptyList(),
)

/**
 * Joins several [StyledNotificationText] lines into one newline-separated block, shifting each line's
 * bold ranges by its offset so they still cover the correct characters. Used to feed the whole body
 * into a single `BigTextStyle` so long lines wrap instead of being truncated.
 */
fun combineStyledLines(lines: List<StyledNotificationText>): StyledNotificationText {
    val sb = StringBuilder()
    val ranges = mutableListOf<BoldRange>()
    lines.forEachIndexed { index, line ->
        if (index > 0) sb.append('\n')
        val offset = sb.length
        sb.append(line.text)
        line.boldRanges.forEach { ranges += BoldRange(it.start + offset, it.end + offset) }
    }
    return StyledNotificationText(sb.toString(), ranges)
}

/** Title + body text for the tracking foreground notification. */
data class TrackingNotificationContent(
    val title: String,
    val text: StyledNotificationText,
    /** Body lines (route + timeline per connection) joined into a single BigTextStyle block. */
    val lines: List<StyledNotificationText>,
)

/**
 * Builds the foreground tracking notification content from the currently tracked journeys.
 *
 * Single connection:
 *   Title `Kiel -> Lübeck -> Hamburg` (full station chain, shortened to fit if too long)
 *   Body  `12:35 (Pt. 1) -> 14:55 (Pt. 13) -> 16:02 (Pt. 2) | RE3 (12345)`
 *
 * Multiple connections:
 *   Title `N tracked connections`
 *   Body  two lines per connection: a route line with the full station chain followed by the
 *         timeline line `12:35 (Pt. 1) -> 14:55 (Pt. 13) | RE3`
 *
 * The stop chain lists the first rail leg's departure followed by every rail leg's destination
 * (transfer points and the final arrival). Prognosed times are preferred over scheduled ones.
 *
 * Bold styling marks the clock time and platform number of the stop closest to [now] and the first
 * rail leg's line name (never its parenthesised detail).
 */
class TrackingNotificationFormatter(private val strings: TrackingNotificationStrings) {

    fun format(
        tracked: List<TrackedJourneyWithJourney>,
        now: LocalDateTime = LocalDateTime.now(),
    ): TrackingNotificationContent? {
        if (tracked.isEmpty()) return null
        return if (tracked.size == 1) single(tracked.first(), now) else multi(tracked, now)
    }

    private fun single(item: TrackedJourneyWithJourney, now: LocalDateTime): TrackingNotificationContent {
        val journey = item.journey
        val stops = stopsOf(journey)
        val builder = StyledTextBuilder()
        appendStopChain(builder, stops, closestIndex(stops, now))
        appendLineLabel(builder, journey.railLegs().firstOrNull(), includeDetail = true)
        val body = builder.build()
        return TrackingNotificationContent(
            title = fitRouteTitle(routeChain(item)),
            text = body,
            lines = listOf(body),
        )
    }

    private fun multi(tracked: List<TrackedJourneyWithJourney>, now: LocalDateTime): TrackingNotificationContent {
        val lines = tracked.flatMap { item ->
            val journey = item.journey
            val stops = stopsOf(journey)
            val routeLine = StyledNotificationText(routeChain(item).joinToString(" -> "))
            val detailBuilder = StyledTextBuilder()
            appendStopChain(detailBuilder, stops, closestIndex(stops, now))
            appendLineLabel(detailBuilder, journey.railLegs().firstOrNull(), includeDetail = false)
            listOf(routeLine, detailBuilder.build())
        }
        return TrackingNotificationContent(
            title = strings.multiTitle(tracked.size),
            text = StyledNotificationText(lines.take(2).joinToString("\n") { it.text }),
            lines = lines,
        )
    }

    /** First rail leg departure followed by each rail leg destination (transfers + final arrival). */
    private fun stopsOf(journey: Journey): List<TrackedStop> {
        val rails = journey.railLegs()
        if (rails.isEmpty()) return emptyList()
        val stops = mutableListOf(stopOf(rails.first().origin))
        rails.forEach { stops += stopOf(it.destination) }
        return stops
    }

    private fun stopOf(event: StopEvent): TrackedStop {
        val iso = event.prognosedTime?.takeIf { it.isNotBlank() } ?: event.scheduledTime
        return TrackedStop(
            clock = formatJourneyClock(iso),
            platform = event.platform?.takeIf { it.isNotBlank() },
            time = parseJourneyDateTime(iso),
        )
    }

    private fun closestIndex(stops: List<TrackedStop>, now: LocalDateTime): Int? =
        stops.indices
            .filter { stops[it].time != null }
            .minByOrNull { abs(ChronoUnit.SECONDS.between(stops[it].time, now)) }

    private fun appendStopChain(builder: StyledTextBuilder, stops: List<TrackedStop>, closest: Int?) {
        stops.forEachIndexed { index, stop ->
            if (index > 0) builder.append(" -> ")
            val bold = index == closest
            builder.append(stop.clock, bold)
            val platform = stop.platform ?: return@forEachIndexed
            builder.append(" (")
            appendPlatform(builder, strings.platformSegment(platform), platform, bold)
            builder.append(")")
        }
    }

    /** Appends the localized platform segment, bolding only the platform value (not the prefix). */
    private fun appendPlatform(
        builder: StyledTextBuilder,
        segment: String,
        platform: String,
        bold: Boolean,
    ) {
        val at = segment.lastIndexOf(platform)
        if (!bold || at < 0) {
            builder.append(segment)
            return
        }
        builder.append(segment.substring(0, at))
        builder.append(platform, bold = true)
        builder.append(segment.substring(at + platform.length))
    }

    private fun appendLineLabel(builder: StyledTextBuilder, leg: Leg?, includeDetail: Boolean) {
        val name = leg?.lineName?.takeIf { it.isNotBlank() } ?: return
        builder.append(" | ")
        builder.append(name, bold = true)
        val detail = leg.lineDetail?.takeIf { it.isNotBlank() && it != name }
        if (includeDetail && detail != null) builder.append(" ($detail)")
    }

    /**
     * Full station chain for the connection, falling back to the stored from/to names when the
     * journey exposes no rail legs.
     */
    private fun routeChain(item: TrackedJourneyWithJourney): List<String> =
        routeStationNames(item.journey)
            .ifEmpty { listOf(item.entity.fromName, item.entity.toName) }

    private data class TrackedStop(
        val clock: String,
        val platform: String?,
        val time: LocalDateTime?,
    )

    private class StyledTextBuilder {
        private val sb = StringBuilder()
        private val ranges = mutableListOf<BoldRange>()

        fun append(text: String, bold: Boolean = false) {
            val start = sb.length
            sb.append(text)
            if (bold && text.isNotEmpty()) ranges += BoldRange(start, sb.length)
        }

        fun build(): StyledNotificationText = StyledNotificationText(sb.toString(), ranges.toList())
    }
}
