package de.openbahn.api

import de.openbahn.api.debug.OpenBahnDebugLog
import de.openbahn.api.mapper.JourneyResponseParser
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import de.openbahn.model.isLongerTripRoute
import de.openbahn.model.tripRouteStops
import de.openbahn.model.withDelaysFrom
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Loads the full vehicle route for a journey leg (before/after the passenger segment).
 * Tries `/reiseloesung/fahrt` first, then station boards with `mitVias=true`.
 */
internal class TripRouteFetcher(
    private val client: DbVendoClient,
) {
    suspend fun fetchFullLegRoute(leg: Leg): List<StopEvent> {
        val segment = leg.tripRouteStops()
        val tripId = leg.tripId?.trim().orEmpty()
        if (tripId.isEmpty()) {
            OpenBahnDebugLog.d(TAG, "no tripId for ${leg.lineName} ${leg.lineDetail}; segment=${segment.size}")
            return segment
        }

        val fromFahrt = runCatching { client.fetchTripRoute(tripId) }
            .onFailure { e -> OpenBahnDebugLog.w(TAG, "fahrt failed tripId=${tripId.take(48)}: ${e.message}") }
            .getOrDefault(emptyList())

        if (isLongerTripRoute(fromFahrt, segment)) {
            OpenBahnDebugLog.d(TAG, "fahrt ok stops=${fromFahrt.size} segment=${segment.size}")
            return fromFahrt.withDelaysFrom(segment)
        }

        val fromBoard = runCatching { fetchRouteFromBoard(leg, tripId, segment) }
            .onFailure { e -> OpenBahnDebugLog.w(TAG, "board route failed: ${e.message}") }
            .getOrDefault(emptyList())

        if (isLongerTripRoute(fromBoard, segment)) {
            OpenBahnDebugLog.d(TAG, "board ok stops=${fromBoard.size} segment=${segment.size}")
            return fromBoard.withDelaysFrom(segment)
        }

        OpenBahnDebugLog.w(
            TAG,
            "no extended route (fahrt=${fromFahrt.size} board=${fromBoard.size} segment=${segment.size}) " +
                "tripId=${tripId.take(72)}",
        )
        return when {
            fromFahrt.size >= 2 -> fromFahrt.withDelaysFrom(segment)
            else -> segment
        }
    }

    private suspend fun fetchRouteFromBoard(
        leg: Leg,
        tripId: String,
        segment: List<StopEvent>,
    ): List<StopEvent> {
        val whenTime = parseBerlinLocal(leg.origin.scheduledTime) ?: return emptyList()
        val stationId = leg.origin.id?.takeIf { it.length >= 6 && it.all(Char::isDigit) }
            ?: return emptyList()
        val arrivalsText = client.fetchBoardRaw(
            stationExtId = stationId,
            whenTime = whenTime,
            departures = false,
            mitVias = true,
        )
        val departuresText = client.fetchBoardRaw(
            stationExtId = stationId,
            whenTime = whenTime,
            departures = true,
            mitVias = true,
        )
        return JourneyResponseParser.buildRouteFromBoard(
            arrivalsText = arrivalsText,
            departuresText = departuresText,
            tripId = tripId,
            leg = leg,
            segment = segment,
        )
    }

    private fun parseBerlinLocal(iso: String): LocalDateTime? = runCatching {
        LocalDateTime.parse(iso.take(19), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
    }.getOrNull()

    companion object {
        private const val TAG = "TripRoute"
    }
}
