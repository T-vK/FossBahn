package de.openbahn.navigator.ui.search

import de.openbahn.model.Journey
import de.openbahn.navigator.ui.util.journeyArrivalDateTime
import java.time.LocalDateTime

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
 *  2. arrives no earlier than [withinMinutes] before [targetArrival] (e.g. 17:58 qualifies for an
 *     18:00 target, 17:45 does not).
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
    withinMinutes: Long = 10,
): Journey? {
    val firstArrival = journeyArrivalDateTime(firstJourney) ?: return null
    val earliestAllowed = targetArrival.minusMinutes(withinMinutes)
    return earlierJourneys
        .asSequence()
        .filter { it.id != firstJourney.id }
        .mapNotNull { journey -> journeyArrivalDateTime(journey)?.let { arrival -> journey to arrival } }
        .filter { (_, arrival) -> arrival.isBefore(firstArrival) && !arrival.isBefore(earliestAllowed) }
        .maxByOrNull { it.second }
        ?.first
}
