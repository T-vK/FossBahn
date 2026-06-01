package de.openbahn.api

import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent

/**
 * Unique key per rail leg in the mobile v2 `trips` map.
 *
 * DB `journeyId` values repeat when the passenger stays on one train across sections;
 * reusing them makes later legs reference stopovers for the wrong segment → HTTP 500.
 */
fun ratingTripId(journeyId: String, legIndex: Int, @Suppress("UNUSED_PARAMETER") leg: Leg): String =
    BahnVorhersageClient.syntheticTripId(journeyId, legIndex)

/** Max stopovers per trip in mobile v2 (full vehicle runs with 30+ stops cause HTTP 500). */
internal const val MAX_TRIP_STOPOVERS_FOR_RATING = 16

fun passengerSegmentStopsForRating(leg: Leg): List<StopEvent> {
    val segment = if (leg.intermediateStops.isEmpty()) {
        listOf(leg.origin, leg.destination)
    } else {
        listOf(leg.origin) + leg.intermediateStops + listOf(leg.destination)
    }
    return ensureLegEndpoints(segment, leg).take(MAX_TRIP_STOPOVERS_FOR_RATING)
}

fun buildTripRoutesForRating(
    journey: Journey,
    fetchedByDbTripId: Map<String, List<StopEvent>> = emptyMap(),
): Map<String, List<StopEvent>> {
    val result = linkedMapOf<String, List<StopEvent>>()
    journey.legs.forEachIndexed { legIndex, leg ->
        if (leg.isWalking) return@forEachIndexed
        val tripId = ratingTripId(journey.id, legIndex, leg)
        val fetched = leg.tripId?.let { fetchedByDbTripId[it] }
        val route = when {
            fetched != null && fetched.size >= 2 ->
                routeStopsForRating(leg, fetched).take(MAX_TRIP_STOPOVERS_FOR_RATING)
            else -> passengerSegmentStopsForRating(leg)
        }
        result[tripId] = route
    }
    return result
}

/** bahnvorhersage `journeys_to_df` indexes stopovers by exact `leg.origin` / `leg.destination` names. */
internal fun ensureLegEndpoints(stops: List<StopEvent>, leg: Leg): List<StopEvent> {
    if (stops.size < 2) return listOf(leg.origin, leg.destination)
    val out = stops.toMutableList()
    if (out.first().name != leg.origin.name) {
        out[0] = leg.origin.copy(
            scheduledTime = leg.origin.scheduledTime.ifBlank { out.first().scheduledTime },
            prognosedTime = leg.origin.prognosedTime ?: out.first().prognosedTime,
            platform = leg.origin.platform ?: out.first().platform,
            id = leg.origin.id ?: out.first().id,
        )
    }
    if (out.last().name != leg.destination.name) {
        out[out.lastIndex] = leg.destination.copy(
            scheduledTime = leg.destination.scheduledTime.ifBlank { out.last().scheduledTime },
            prognosedTime = leg.destination.prognosedTime ?: out.last().prognosedTime,
            platform = leg.destination.platform ?: out.last().platform,
            id = leg.destination.id ?: out.last().id,
        )
    }
    return out
}

fun buildTripRoutesForRating(
    journeys: List<Journey>,
    fetchedByDbTripId: Map<String, List<StopEvent>> = emptyMap(),
): Map<String, List<StopEvent>> {
    val result = linkedMapOf<String, List<StopEvent>>()
    journeys.forEach { journey ->
        result.putAll(buildTripRoutesForRating(journey, fetchedByDbTripId))
    }
    return result
}
