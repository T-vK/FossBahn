package de.openbahn.api

import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import de.openbahn.model.tripRouteStops
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Loads full trip stop lists required by the Bahn-Vorhersage mobile rating API. */
suspend fun loadTripRoutesForJourneys(
    journeys: List<Journey>,
    fetchFullLegRoute: suspend (Leg) -> List<StopEvent>,
): Map<String, List<StopEvent>> = coroutineScope {
    val legsByTripId = linkedMapOf<String, Leg>()
    journeys.forEach { journey ->
        journey.legs
            .filter { !it.isWalking && !it.tripId.isNullOrBlank() }
            .forEach { leg ->
                val id = leg.tripId!!.trim()
                legsByTripId.putIfAbsent(id, leg)
            }
    }
    legsByTripId.map { (tripId, leg) ->
        async {
            tripId to runCatching { fetchFullLegRoute(leg) }
                .getOrElse { emptyList() }
                .takeIf { it.size >= 2 }
                ?: leg.tripRouteStops().takeIf { it.size >= 2 }
                ?: listOf(leg.origin, leg.destination)
        }
    }.awaitAll()
        .filter { (_, stops) -> stops.size >= 2 }
        .toMap()
}
