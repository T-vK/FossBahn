package de.openbahn.api

import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent

/**
 * Builds the `trips` map for `POST /api/mobile/v2/journeys`.
 *
 * Every rail leg's [ratingTripId] must appear in this map with stopovers that include
 * that leg's origin and destination station names (see bahnvorhersage `journeys_to_df`).
 */
fun ratingTripId(journeyId: String, legIndex: Int, leg: Leg): String =
    leg.tripId?.trim()?.takeIf { it.isNotEmpty() }
        ?: BahnVorhersageClient.syntheticTripId(journeyId, legIndex)

fun buildTripRoutesForRating(
    journey: Journey,
    fetchedByDbTripId: Map<String, List<StopEvent>> = emptyMap(),
): Map<String, List<StopEvent>> {
    val result = linkedMapOf<String, List<StopEvent>>()
    journey.legs.forEachIndexed { legIndex, leg ->
        if (leg.isWalking) return@forEachIndexed
        val tripId = ratingTripId(journey.id, legIndex, leg)
        if (tripId in result) return@forEachIndexed
        val fetched = leg.tripId?.trim()?.let { fetchedByDbTripId[it] }.orEmpty()
        val route = routeStopsForRating(leg, fetched)
        if (route.size >= 2) {
            result[tripId] = route
        }
    }
    return result
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
