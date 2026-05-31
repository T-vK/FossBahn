package de.openbahn.model

/** All stops on this train leg in order (from API haltes), when available. */
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
