package de.openbahn.navigator.tracking

import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.railLegs
import de.openbahn.model.railTransferCount
import de.openbahn.navigator.data.TrackedJourneyWithJourney
import de.openbahn.navigator.ui.util.formatJourneyClock

/**
 * Localized text pieces the foreground notification formatter needs. Kept behind an interface so the
 * formatter can be unit tested without an Android [android.content.Context].
 */
interface TrackingNotificationStrings {
    /** Platform segment such as `Pt. 14A-D` (localized prefix). */
    fun platformSegment(platform: String): String

    /** Transfer summary, e.g. `2 Transfers`, or the direct-connection wording for 0. */
    fun transfers(count: Int): String

    /** Multi-connection title, e.g. `3 tracked connections`. */
    fun multiTitle(count: Int): String
}

/** Title + body text for the tracking foreground notification. */
data class TrackingNotificationContent(
    val title: String,
    val text: String,
    /** One entry per tracked connection, for InboxStyle expansion. */
    val lines: List<String>,
)

/**
 * Builds the foreground tracking notification content from the currently tracked journeys.
 *
 * Single connection:
 *   Title `Departure -> Destination`
 *   Body  `Pt. {platform}: {departure} -> {arrival} | {line} [| {transfers}]`
 *
 * Multiple connections:
 *   Title `N tracked connections`
 *   Body  one line per connection: `From -> To | Pt. {platform}: {departure} -> {arrival} | {line}`
 */
class TrackingNotificationFormatter(private val strings: TrackingNotificationStrings) {

    fun format(tracked: List<TrackedJourneyWithJourney>): TrackingNotificationContent? {
        if (tracked.isEmpty()) return null
        return if (tracked.size == 1) single(tracked.first()) else multi(tracked)
    }

    private fun single(item: TrackedJourneyWithJourney): TrackingNotificationContent {
        val journey = item.journey
        val leg = journey.railLegs().firstOrNull()
        val body = buildString {
            appendPlatform(leg)
            append(departureClock(journey))
            append(" -> ")
            append(arrivalClock(journey))
            legLabel(leg, includeDetail = true)?.let {
                append(" | ")
                append(it)
            }
            val transferCount = journey.railTransferCount()
            if (transferCount > 0) {
                append(" | ")
                append(strings.transfers(transferCount))
            }
        }
        return TrackingNotificationContent(
            title = route(item),
            text = body,
            lines = listOf(body),
        )
    }

    private fun multi(tracked: List<TrackedJourneyWithJourney>): TrackingNotificationContent {
        val lines = tracked.map { item ->
            val journey = item.journey
            val leg = journey.railLegs().firstOrNull()
            buildString {
                append(route(item))
                append(" | ")
                appendPlatform(leg)
                append(departureClock(journey))
                append(" -> ")
                append(arrivalClock(journey))
                legLabel(leg, includeDetail = false)?.let {
                    append(" | ")
                    append(it)
                }
            }
        }
        return TrackingNotificationContent(
            title = strings.multiTitle(tracked.size),
            text = lines.joinToString("\n"),
            lines = lines,
        )
    }

    private fun StringBuilder.appendPlatform(leg: Leg?) {
        val platform = leg?.origin?.platform?.takeIf { it.isNotBlank() } ?: return
        append(strings.platformSegment(platform))
        append(": ")
    }

    private fun route(item: TrackedJourneyWithJourney): String =
        "${item.entity.fromName} -> ${item.entity.toName}"

    private fun departureClock(journey: Journey): String {
        val origin = journey.railLegs().firstOrNull()?.origin
        val iso = origin?.prognosedTime ?: origin?.scheduledTime ?: journey.departure
        return formatJourneyClock(iso)
    }

    private fun arrivalClock(journey: Journey): String {
        val destination = journey.railLegs().lastOrNull()?.destination
        val iso = destination?.prognosedTime ?: destination?.scheduledTime ?: journey.arrival
        return formatJourneyClock(iso)
    }

    private fun legLabel(leg: Leg?, includeDetail: Boolean): String? {
        val name = leg?.lineName?.takeIf { it.isNotBlank() } ?: return null
        val detail = leg.lineDetail?.takeIf { it.isNotBlank() && it != name }
        return if (includeDetail && detail != null) "$name ($detail)" else name
    }
}
