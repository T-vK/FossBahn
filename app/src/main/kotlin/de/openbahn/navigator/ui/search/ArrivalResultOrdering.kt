package de.openbahn.navigator.ui.search

import de.openbahn.model.Journey
import de.openbahn.navigator.ui.util.journeyArrivalDateTime
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

private const val DEFAULT_BUFFER_WINDOW_MINUTES = 10L
private const val LATE_BEST_MATCH_THRESHOLD_MINUTES = 10L
private const val EXTENDED_BUFFER_WINDOW_MINUTES = 120L

/**
 * Merges the initial arrival-search page with optional earlier/later pages into one chronological list.
 * API order within each page is preserved; pages are concatenated as earlier → initial → later and
 * deduplicated by journey id.
 */
fun mergeArrivalSearchPages(
    initial: List<Journey>,
    earlier: List<Journey> = emptyList(),
    later: List<Journey> = emptyList(),
): List<Journey> {
    return (earlier + initial + later)
        .distinctBy { it.id }
        .sortedWith(
            compareBy<Journey> { journeyArrivalDateTime(it) ?: LocalDateTime.MAX }
                .thenBy { it.id },
        )
}

/**
 * Result of trimming arrival-search results: [visible] connections shown in the UI and [hidden]
 * connections held back until the user taps "Earlier connections".
 */
data class ArrivalTrimResult(
    val visible: List<Journey>,
    val hidden: List<Journey>,
)

/**
 * Trims an arrival-search result list so the best match to [targetArrival] is shown first or second,
 * without reordering the remaining connections.
 *
 * The **best match** is the connection whose arrival is closest to [targetArrival] (ties prefer
 * arriving on or before the target).
 *
 * When a qualifying buffer connection exists — the latest arrival before the best match that is still
 * within the allowed window of [targetArrival] — it is kept as the first visible result and the best
 * match becomes second. Everything chronologically before that buffer is hidden until the user
 * requests earlier connections.
 *
 * The window is 10 minutes by default. If the best match arrives more than 10 minutes after
 * [targetArrival], the window widens to 2 hours.
 */
fun trimArrivalResultsForDisplay(
    journeys: List<Journey>,
    targetArrival: LocalDateTime,
): ArrivalTrimResult {
    if (journeys.isEmpty()) return ArrivalTrimResult(visible = journeys, hidden = emptyList())

    val chronological = journeys.sortedWith(
        compareBy<Journey> { journeyArrivalDateTime(it) ?: LocalDateTime.MAX }
            .thenBy { it.id },
    )

    val timed = chronological.mapIndexedNotNull { index, journey ->
        journeyArrivalDateTime(journey)?.let { arrival -> IndexedJourney(index, journey, arrival) }
    }
    if (timed.isEmpty()) return ArrivalTrimResult(visible = chronological, hidden = emptyList())

    val best = findArrivalBestMatch(timed, targetArrival)
    val windowMinutes = arrivalBufferWindowMinutes(best.arrival, targetArrival)
    val earliestAllowed = targetArrival.minusMinutes(windowMinutes)
    val preceding = timed
        .filter { it.arrival.isBefore(best.arrival) && !it.arrival.isBefore(earliestAllowed) }
        .maxByOrNull { it.arrival }

    val startIndex = preceding?.index ?: best.index
    return ArrivalTrimResult(
        visible = chronological.drop(startIndex),
        hidden = chronological.take(startIndex),
    )
}

internal fun arrivalBufferWindowMinutes(
    bestArrival: LocalDateTime,
    targetArrival: LocalDateTime,
): Long {
    if (!bestArrival.isAfter(targetArrival)) return DEFAULT_BUFFER_WINDOW_MINUTES
    val minutesAfterTarget = ChronoUnit.MINUTES.between(targetArrival, bestArrival)
    return if (minutesAfterTarget > LATE_BEST_MATCH_THRESHOLD_MINUTES) {
        EXTENDED_BUFFER_WINDOW_MINUTES
    } else {
        DEFAULT_BUFFER_WINDOW_MINUTES
    }
}

private data class IndexedJourney(
    val index: Int,
    val journey: Journey,
    val arrival: LocalDateTime,
)

private fun findArrivalBestMatch(
    timed: List<IndexedJourney>,
    targetArrival: LocalDateTime,
): IndexedJourney {
    return timed.minWith(
        compareBy<IndexedJourney> { kotlin.math.abs(ChronoUnit.MINUTES.between(targetArrival, it.arrival)) }
            .thenBy { if (it.arrival.isAfter(targetArrival)) 1 else 0 },
    )
}

/** @deprecated Use [arrivalBufferWindowMinutes]; kept for existing tests during rename. */
internal fun arrivalPrependWindowMinutes(
    bestArrival: LocalDateTime,
    targetArrival: LocalDateTime,
): Long = arrivalBufferWindowMinutes(bestArrival, targetArrival)
