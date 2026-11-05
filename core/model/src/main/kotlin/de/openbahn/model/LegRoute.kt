package de.openbahn.model

/** Segment of the full vehicle route relative to the passenger's booked leg. */
enum class RouteStopSegment {
    BEFORE,
    ON_TRIP,
    AFTER,
}

/** All stops on this leg in order (from API haltes), when available. */
fun Leg.tripRouteStops(): List<StopEvent> {
    if (routeStops.isNotEmpty()) return routeStops
    if (priorStops.isEmpty() && intermediateStops.isEmpty()) return emptyList()
    return buildList {
        addAll(priorStops)
        add(origin)
        addAll(intermediateStops)
        add(destination)
    }
}

fun stationNamesMatch(a: String, b: String): Boolean {
    val na = normalizeStationName(a)
    val nb = normalizeStationName(b)
    if (na.isEmpty() || nb.isEmpty()) return false
    return na == nb || na.startsWith(nb) || nb.startsWith(na)
}

fun normalizeStationName(name: String): String =
    name.trim()
        .substringBefore(" Gl.")
        .substringBefore(" gl.")
        .substringBefore(" Gleis")
        .trim()

fun boardIndexInRoute(
    stops: List<StopEvent>,
    boardAt: String,
    boardScheduled: String,
): Int {
    if (stops.isEmpty()) return -1
    val byName = stops.indexOfFirst { stationNamesMatch(it.name, boardAt) }
    if (byName >= 0) return byName
    if (boardScheduled.isNotBlank()) {
        val byTime = stops.indexOfFirst { it.scheduledTime == boardScheduled }
        if (byTime >= 0) return byTime
    }
    return -1
}

fun alightIndexInRoute(
    stops: List<StopEvent>,
    alightAt: String,
    alightScheduled: String,
    boardIndex: Int,
): Int {
    if (stops.isEmpty()) return -1
    val searchFrom = (boardIndex + 1).coerceAtLeast(0)
    val byName = stops.withIndex().indexOfFirst { (index, stop) ->
        index >= searchFrom && stationNamesMatch(stop.name, alightAt)
    }
    if (byName >= 0) return byName
    if (alightScheduled.isNotBlank()) {
        val byTime = stops.withIndex().indexOfFirst { (index, stop) ->
            index >= searchFrom && stop.scheduledTime == alightScheduled
        }
        if (byTime >= 0) return byTime
    }
    return stops.lastIndex
}

fun routeStopSegment(
    index: Int,
    boardIndex: Int,
    alightIndex: Int,
): RouteStopSegment = when {
    boardIndex < 0 || alightIndex < 0 -> RouteStopSegment.ON_TRIP
    index < boardIndex -> RouteStopSegment.BEFORE
    index > alightIndex -> RouteStopSegment.AFTER
    else -> RouteStopSegment.ON_TRIP
}

fun List<StopEvent>.withDelaysFrom(reference: List<StopEvent>): List<StopEvent> =
    map { stop ->
        val match = reference.firstOrNull { ref ->
            stationNamesMatch(ref.name, stop.name) &&
                (ref.scheduledTime == stop.scheduledTime || ref.id != null && ref.id == stop.id)
        }
        match?.let { stop.withRealtimeFrom(it) } ?: stop
    }

/** EVA-backed [Location] for station-board API calls when [StopEvent.id] is present. */
fun StopEvent.boardLocation(): Location? {
    val eva = id?.takeIf { it.isNotBlank() } ?: return null
    return Location(id = eva, name = name, evaNumber = eva)
}

/** Full stop list from refresh payloads used to merge realtime onto passenger segment stops. */
fun Leg.realtimeReferenceStops(): List<StopEvent> =
    routeStops.ifEmpty { tripRouteStops() }

/**
 * Name shown as the train's destination (part of the line identity, e.g. "to Stade").
 *
 * The journey `richtung` is often the line's usual headsign (S5 → Hamburg-Neugraben), not this
 * vehicle's last stop. Prefer the last stop of a known run that continues past the passenger
 * alighting station, then [Leg.direction], then the passenger stop.
 */
fun Leg.trainDestinationName(fullRoute: List<StopEvent>? = null): String {
    val candidates = listOfNotNull(
        fullRoute?.takeIf { it.size >= 2 },
        tripRouteStops().takeIf { it.size >= 2 },
    )
    for (route in candidates) {
        terminusBeyondAlight(route)?.let { return it }
    }
    direction?.takeIf { it.isNotBlank() }?.let { return it }
    return destination.name
}

/** Last stop of [route] when the vehicle continues after the passenger gets off. */
private fun Leg.terminusBeyondAlight(route: List<StopEvent>): String? {
    val board = boardIndexInRoute(route, origin.name, origin.scheduledTime)
    val alight = alightIndexInRoute(route, destination.name, destination.scheduledTime, board)
    if (alight < 0 || alight >= route.lastIndex) return null
    val last = route.last().name.takeIf { it.isNotBlank() } ?: return null
    if (stationNamesMatch(last, destination.name)) return null
    return last
}
