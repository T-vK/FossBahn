package de.openbahn.api

import de.openbahn.api.debug.OpenBahnDebugLog
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import de.openbahn.model.alightIndexInRoute
import de.openbahn.model.boardIndexInRoute
import de.openbahn.model.tripRouteStops
import de.openbahn.model.withDelaysFrom

/**
 * Bahn-Vorhersage only needs the passenger segment of a trip, not the full vehicle run.
 * Sending 30+ board stopovers per trip blows up batch requests and triggers HTTP 500.
 */
internal fun routeStopsForRating(leg: Leg, fetchedRoute: List<StopEvent>): List<StopEvent> {
    val segment = leg.tripRouteStops()
    val base = when {
        fetchedRoute.size >= 2 -> fetchedRoute
        segment.size >= 2 -> segment
        else -> listOf(leg.origin, leg.destination)
    }

    val boardIdx = boardIndexInRoute(base, leg.origin.name, leg.origin.scheduledTime)
    val alightIdx = alightIndexInRoute(base, leg.destination.name, leg.destination.scheduledTime, boardIdx)
    if (boardIdx >= 0 && alightIdx >= boardIdx) {
        val trimmed = base.subList(boardIdx, alightIdx + 1)
        if (trimmed.size >= 2 && trimmed.size < base.size) {
            if (base.size - trimmed.size >= 2) {
                OpenBahnDebugLog.d(
                    "BahnVorhersage",
                    "trimmed trip route ${base.size}→${trimmed.size} stops for ${leg.lineName} " +
                        "${leg.origin.name}→${leg.destination.name}",
                )
            }
            return trimmed.withDelaysFrom(segment)
        }
    }
    return when {
        segment.size >= 2 -> segment
        base.size >= 2 -> base
        else -> listOf(leg.origin, leg.destination)
    }
}
