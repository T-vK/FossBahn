package de.openbahn.navigator.ui.util

/**
 * Formats journey duration for display: minutes below 60, otherwise `H:MM` (e.g. 1:05).
 */
fun formatDurationMinutes(minutes: Int): String {
    if (minutes < 60) return "$minutes min"
    val hours = minutes / 60
    val mins = minutes % 60
    return "$hours:${mins.toString().padStart(2, '0')}"
}

/** Extracts clock time (HH:mm) from bahn.de ISO timestamps. */
fun formatJourneyClock(isoTime: String): String {
    val t = isoTime.indexOf('T')
    if (t >= 0 && isoTime.length >= t + 6) {
        return isoTime.substring(t + 1, t + 6)
    }
    return isoTime.takeLast(5).takeIf { it.contains(':') } ?: isoTime
}
