package de.openbahn.model

/** True when [candidate] covers more of the vehicle run than [segment] (extra stops before/after). */
fun isLongerTripRoute(
    candidate: List<StopEvent>,
    segment: List<StopEvent>,
): Boolean {
    if (candidate.size < 2) return false
    if (segment.size < 2) return candidate.size >= 2
    if (candidate.size > segment.size) return true
    val firstDiffers = !stationNamesMatch(candidate.first().name, segment.first().name)
    val lastDiffers = !stationNamesMatch(candidate.last().name, segment.last().name)
    return firstDiffers || lastDiffers
}
