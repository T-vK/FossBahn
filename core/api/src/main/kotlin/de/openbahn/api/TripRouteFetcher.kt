package de.openbahn.api

import de.openbahn.api.debug.OpenBahnDebugLog
import de.openbahn.api.mapper.JourneyResponseParser
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import de.openbahn.model.isLongerTripRoute
import de.openbahn.model.stationNamesMatch
import de.openbahn.model.tripRouteStops
import de.openbahn.model.withDelaysFrom
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Loads the full vehicle route for a journey leg (before/after the passenger segment).
 * Tries `/reiseloesung/fahrt` and station boards with `mitVias=true` in parallel.
 */
internal class TripRouteFetcher(
    private val client: DbVendoClient,
) {
    suspend fun fetchFullLegRoute(leg: Leg): List<StopEvent> {
        val segment = leg.tripRouteStops()
        val tripId = leg.tripId?.trim().orEmpty()
        if (tripId.isEmpty()) {
            OpenBahnDebugLog.d(TAG, "no tripId for ${leg.lineName} ${leg.lineDetail}; segment=${segment.size}")
            return mergeWithLegPriorStops(segment, leg)
        }

        val skipFahrt = isSegmentScopedTripId(tripId)
        if (skipFahrt) {
            OpenBahnDebugLog.d(TAG, "segment-scoped tripId; using board lookup tripId=${tripId.take(48)}")
        }
        val (fromFahrt, fromBoard) = coroutineScope {
            val fahrt = async {
                if (skipFahrt) {
                    emptyList()
                } else {
                    runCatching { client.fetchTripRoute(tripId) }
                        .onFailure { e ->
                            OpenBahnDebugLog.w(TAG, "fahrt failed tripId=${tripId.take(48)}: ${e.message}")
                        }
                        .getOrDefault(emptyList())
                }
            }
            val board = async {
                runCatching { fetchRouteFromBoard(leg, tripId, segment) }
                    .onFailure { e -> OpenBahnDebugLog.w(TAG, "board route failed: ${e.message}") }
                    .getOrDefault(emptyList())
            }
            fahrt.await() to board.await()
        }

        if (isLongerTripRoute(fromFahrt, segment)) {
            OpenBahnDebugLog.d(TAG, "fahrt ok stops=${fromFahrt.size} segment=${segment.size}")
            return mergeWithLegPriorStops(fromFahrt.withDelaysFrom(segment), leg)
        }

        if (isLongerTripRoute(fromBoard, segment)) {
            OpenBahnDebugLog.d(TAG, "board ok stops=${fromBoard.size} segment=${segment.size}")
            return mergeWithLegPriorStops(fromBoard.withDelaysFrom(segment), leg)
        }

        val merged = mergeRouteCandidates(segment, fromFahrt, fromBoard, leg)
        if (!isLongerTripRoute(merged, segment)) {
            OpenBahnDebugLog.w(
                TAG,
                "no extended route (fahrt=${fromFahrt.size} board=${fromBoard.size} segment=${segment.size}) " +
                    "tripId=${tripId.take(72)}",
            )
        }
        return merged
    }

    private suspend fun fetchRouteFromBoard(
        leg: Leg,
        tripId: String,
        segment: List<StopEvent>,
    ): List<StopEvent> {
        val whenTime = parseBerlinLocal(leg.origin.scheduledTime) ?: return emptyList()
        val stationId = leg.origin.id?.takeIf { it.length >= 6 && it.all(Char::isDigit) }
            ?: return emptyList()
        val (arrivalsText, departuresText) = coroutineScope {
            val arrivals = async {
                client.fetchBoardRaw(
                    stationExtId = stationId,
                    whenTime = whenTime,
                    departures = false,
                    mitVias = true,
                    durationMinutes = BOARD_LOOKUP_MINUTES,
                )
            }
            val departures = async {
                client.fetchBoardRaw(
                    stationExtId = stationId,
                    whenTime = whenTime,
                    departures = true,
                    mitVias = true,
                    durationMinutes = BOARD_LOOKUP_MINUTES,
                )
            }
            arrivals.await() to departures.await()
        }
        return JourneyResponseParser.buildRouteFromBoard(
            arrivalsText = arrivalsText,
            departuresText = departuresText,
            tripId = tripId,
            leg = leg,
            segment = segment,
        )
    }

    private fun mergeRouteCandidates(
        segment: List<StopEvent>,
        fromFahrt: List<StopEvent>,
        fromBoard: List<StopEvent>,
        leg: Leg,
    ): List<StopEvent> {
        val base = when {
            fromBoard.size >= 2 -> fromBoard
            fromFahrt.size >= 2 -> fromFahrt
            else -> segment
        }
        return mergeWithLegPriorStops(base.withDelaysFrom(segment), leg)
    }

    /** Keeps [priorStops] from journey search when the API route response is segment-scoped. */
    private fun mergeWithLegPriorStops(route: List<StopEvent>, leg: Leg): List<StopEvent> {
        if (leg.priorStops.isEmpty()) return route
        val missingPrior = leg.priorStops.filter { prior ->
            route.none { stationNamesMatch(it.name, prior.name) }
        }
        if (missingPrior.isEmpty()) return route
        return buildList {
            addAll(missingPrior)
            addAll(route)
        }
    }

    private fun parseBerlinLocal(iso: String): LocalDateTime? = runCatching {
        LocalDateTime.parse(iso.take(19), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
    }.getOrNull()

    companion object {
        private const val TAG = "TripRoute"
        /** Shorter window than general board enrichment — enough for one departure plus vias. */
        private const val BOARD_LOOKUP_MINUTES = 30

        /**
         * bahn.de embeds boarding stop/time in the trip id (`#1S#` / `#1T#`); `/fahrt` then returns only
         * the passenger segment, not the full vehicle run.
         */
        internal fun isSegmentScopedTripId(tripId: String): Boolean =
            tripId.contains("#1S#", ignoreCase = true) ||
                tripId.contains("#1T#", ignoreCase = true)
    }
}
