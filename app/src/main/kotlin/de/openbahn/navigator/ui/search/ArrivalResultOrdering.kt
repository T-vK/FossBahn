package de.openbahn.navigator.ui.search

import de.openbahn.model.Journey
import de.openbahn.navigator.ui.util.journeyArrivalDateTime
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

private const val DEFAULT_PREPEND_WINDOW_MINUTES = 10L
private const val LATE_FIRST_RESULT_THRESHOLD_MINUTES = 10L
private const val EXTENDED_PREPEND_WINDOW_MINUTES = 120L

/**
 * Picks the single earlier-page connection that should be prepended to an arrival-time
 * ("arrive by") search result list.
 *
 * The API order is never changed: results from bahn.de are kept in the chronological order they are
 * returned. To give the traveller a comfortable-but-still-in-time option, at most one connection
 * from the automatically fetched *earlier* page may be shown before the first result.
 *
 * A candidate qualifies when it:
 *  1. arrives **before** [firstJourney]'s arrival, and
 *  2. arrives no earlier than the allowed window before [targetArrival].
 *
 * The window is 10 minutes by default (e.g. 17:58 qualifies for an 18:00 target, 17:45 does not).
 * If the first result arrives more than 10 minutes after [targetArrival], the window widens to
 * 2 hours so a useful earlier option can still be surfaced.
 *
 * When several candidates qualify the latest arrival is chosen (closest to the target). If none
 * qualify, `null` is returned and nothing should be prepended.
 *
 * The prognosed arrival of the last leg is preferred, falling back to the scheduled arrival
 * (see [journeyArrivalDateTime]).
 */
fun selectArrivalPrependCandidate(
    earlierJourneys: List<Journey>,
    firstJourney: Journey,
    targetArrival: LocalDateTime,
): Journey? {
    val firstArrival = journeyArrivalDateTime(firstJourney) ?: return null
    val windowMinutes = arrivalPrependWindowMinutes(firstArrival, targetArrival)
    val earliestAllowed = targetArrival.minusMinutes(windowMinutes)
    return earlierJourneys
        .asSequence()
        .filter { it.id != firstJourney.id }
        .mapNotNull { journey -> journeyArrivalDateTime(journey)?.let { arrival -> journey to arrival } }
        .filter { (_, arrival) -> arrival.isBefore(firstArrival) && !arrival.isBefore(earliestAllowed) }
        .maxByOrNull { it.second }
        ?.first
}

internal fun arrivalPrependWindowMinutes(
    firstArrival: LocalDateTime,
    targetArrival: LocalDateTime,
): Long {
    if (!firstArrival.isAfter(targetArrival)) return DEFAULT_PREPEND_WINDOW_MINUTES
    val minutesAfterTarget = ChronoUnit.MINUTES.between(targetArrival, firstArrival)
    return if (minutesAfterTarget > LATE_FIRST_RESULT_THRESHOLD_MINUTES) {
        EXTENDED_PREPEND_WINDOW_MINUTES
    } else {
        DEFAULT_PREPEND_WINDOW_MINUTES
    }
}
