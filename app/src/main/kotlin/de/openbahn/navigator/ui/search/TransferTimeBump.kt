package de.openbahn.navigator.ui.search

import de.openbahn.model.Location
import de.openbahn.model.LocationType
import de.openbahn.model.StopEvent

/**
 * Stay-duration steps used by bahn.de's "Umstiegszeit verlängern" (extend transfer time).
 * Re-search goes through the same `/angebote/fahrplan` endpoint with
 * `zwischenhalte[].aufenthaltsdauer` for that transfer station only.
 */
val TRANSFER_TIME_PRESETS_MINUTES: List<Int> = listOf(5, 10, 15, 20, 30)

/**
 * Next stay duration above the scheduled gap (and any already requested stay).
 * Returns null when already at/above the largest preset.
 */
fun nextTransferStayMinutes(
    scheduledTransferMinutes: Int?,
    currentStayMinutes: Int? = null,
): Int? {
    val floor = maxOf(scheduledTransferMinutes ?: 0, currentStayMinutes ?: 0)
    return TRANSFER_TIME_PRESETS_MINUTES.firstOrNull { it > floor }
}

/** @deprecated Prefer [nextTransferStayMinutes] for the bahn.de-style single button. */
fun transferTimeBumpOptions(
    scheduledTransferMinutes: Int?,
    limit: Int = 3,
): List<Int> {
    val floor = scheduledTransferMinutes ?: 0
    return TRANSFER_TIME_PRESETS_MINUTES.filter { it > floor }.take(limit)
}

private val EVA_IN_HALT_ID = Regex("""@L=(\d+)@""")

/** Location used as a via stop when bumping stay time at a transfer. */
fun transferStationLocation(arrival: StopEvent, departure: StopEvent): Location? {
    listOf(arrival, departure).forEach { stop ->
        locationFromStopEvent(stop)?.let { return it }
    }
    val name = arrival.name.ifBlank { departure.name }
    if (name.isBlank()) return null
    return Location(id = name, name = name, type = LocationType.STATION)
}

internal fun locationFromStopEvent(stop: StopEvent): Location? {
    val rawId = stop.id?.takeIf { it.isNotBlank() }
    val eva = rawId?.let { id ->
        when {
            id.all(Char::isDigit) && id.length >= 6 -> id
            else -> EVA_IN_HALT_ID.find(id)?.groupValues?.getOrNull(1)
        }
    }
    if (eva != null) {
        return Location(
            id = eva,
            name = stop.name,
            type = LocationType.STATION,
            evaNumber = eva,
        )
    }
    if (rawId != null && rawId.startsWith("A=1@")) {
        return Location(id = rawId, name = stop.name, type = LocationType.STATION)
    }
    return null
}
