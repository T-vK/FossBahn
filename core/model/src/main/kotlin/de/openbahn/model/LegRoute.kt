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
