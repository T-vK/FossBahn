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
/**
 * Unique key per rail leg in the mobile v2 `trips` map.
 *
 * DB `journeyId` values repeat when the passenger stays on one train across sections;
 * reusing them makes later legs reference stopovers for the wrong segment → HTTP 500.
 */
fun ratingTripId(journeyId: String, legIndex: Int, @Suppress("UNUSED_PARAMETER") leg: Leg): String =
    BahnVorhersageClient.syntheticTripId(journeyId, legIndex)

fun buildTripRoutesForRating(
    journey: Journey,
    fetchedByDbTripId: Map<String, List<StopEvent>> = emptyMap(),
): Map<String, List<StopEvent>> {
    val result = linkedMapOf<String, List<StopEvent>>()
    journey.legs.forEachIndexed { legIndex, leg ->
        if (leg.isWalking) return@forEachIndexed
        val tripId = ratingTripId(journey.id, legIndex, leg)
        // Mobile v2 only needs exact leg endpoints in `trips`; intermediate stopovers can break
        // bahnvorhersage `streckennetz.route_length` and yield HTTP 500 (see BahnVorhersageMobileV2ProbeTest).
        val route = ensureLegEndpoints(listOf(leg.origin, leg.destination), leg)
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
