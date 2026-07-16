package de.openbahn.navigator.ui.search

import de.openbahn.model.Journey
import de.openbahn.navigator.ui.util.journeyArrivalDateTime
import java.time.LocalDateTime

/**
 * Reorders arrival-time ("arrive by") search results so the most useful connection is shown first.
 *
 * The **best match** is the connection whose arrival is closest to [targetArrival]. For an
 * "arrive by" search this is the latest connection that still arrives on or before the target;
 * if every connection arrives after the target the earliest one (closest from above) is used.
 * The prognosed arrival of the last leg is preferred, falling back to the scheduled arrival
 * (see [journeyArrivalDateTime]).
 *
 * By default the best match is placed first. As an exception, if another connection arrives
 * *before* the best match but still within [withinMinutes] of [targetArrival], that
 * immediately-preceding connection is shown first and the best match second, because it gives the
 * traveller a comfortable buffer while still arriving in time. The remaining connections follow
 * in chronological order by arrival.
 *
 * Only meant for arrival searches; departure searches should keep the order returned by the API.
 */
fun orderJourneysByArrival(
    journeys: List<Journey>,
    targetArrival: LocalDateTime,
    withinMinutes: Long = 10,
): List<Journey> {
    if (journeys.size < 2) return journeys

    val timed = journeys.mapNotNull { journey ->
        journeyArrivalDateTime(journey)?.let { arrival -> journey to arrival }
    }
    // Journeys without a parseable arrival keep their relative order and go last.
    val untimed = journeys.filter { journey -> journeyArrivalDateTime(journey) == null }
    if (timed.isEmpty()) return journeys

    val onOrBefore = timed.filter { (_, arrival) -> !arrival.isAfter(targetArrival) }
    val best = if (onOrBefore.isNotEmpty()) {
        onOrBefore.maxByOrNull { it.second }
    } else {
        timed.minByOrNull { it.second }
    } ?: return journeys

    val earliestAllowed = targetArrival.minusMinutes(withinMinutes)
    val preceding = timed
        .filter { (_, arrival) ->
            arrival.isBefore(best.second) && !arrival.isBefore(earliestAllowed)
        }
        .maxByOrNull { it.second }

    val ordered = ArrayList<Journey>(journeys.size)
    val usedIds = HashSet<String>()
    preceding?.let {
        ordered += it.first
        usedIds += it.first.id
    }
    ordered += best.first
    usedIds += best.first.id

    timed.asSequence()
        .filter { it.first.id !in usedIds }
        .sortedBy { it.second }
        .forEach { ordered += it.first }

    ordered += untimed
    return ordered
}
