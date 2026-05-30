package de.openbahn.api.mapper

import de.openbahn.model.BoardEntry
import de.openbahn.model.Leg
import de.openbahn.model.delayMinutesFromTimes
import de.openbahn.model.isoTimeToEpochMillis

internal object JourneyBoardMatcher {
    fun findDepartureMatch(entries: List<BoardEntry>, leg: Leg): BoardEntry? =
        entries.firstOrNull { matchesDeparture(it, leg) }

    fun findArrivalMatch(entries: List<BoardEntry>, leg: Leg): BoardEntry? =
        entries.firstOrNull { matchesArrival(it, leg) }

    private fun matchesDeparture(entry: BoardEntry, leg: Leg): Boolean {
        if (tripIdsMatch(entry.tripId, leg.tripId)) return true
        if (!timesRoughlyEqual(entry.scheduledTime, leg.origin.scheduledTime)) return false
        return linesRoughlyEqual(entry.line, leg.lineName)
    }

    private fun matchesArrival(entry: BoardEntry, leg: Leg): Boolean {
        if (tripIdsMatch(entry.tripId, leg.tripId)) return true
        if (!timesRoughlyEqual(entry.scheduledTime, leg.destination.scheduledTime)) return false
        return linesRoughlyEqual(entry.line, leg.lineName)
    }

    fun boardDelayMinutes(entry: BoardEntry): Int? =
        entry.delayMinutes?.takeIf { it > 0 }
            ?: delayMinutesFromTimes(entry.scheduledTime, entry.prognosedTime)

    private fun tripIdsMatch(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        if (a == b) return true
        val normA = a.substringBefore('$').takeLast(48)
        val normB = b.substringBefore('$').takeLast(48)
        return normA == normB || a.contains(b.take(24)) || b.contains(a.take(24))
    }

    private fun timesRoughlyEqual(a: String, b: String): Boolean {
        if (a.take(16) == b.take(16)) return true
        val ma = isoTimeToEpochMillis(a)
        val mb = isoTimeToEpochMillis(b)
        if (ma != null && mb != null) return kotlin.math.abs(ma - mb) <= 120_000
        return false
    }

    private fun linesRoughlyEqual(boardLine: String, legLine: String?): Boolean {
        if (legLine.isNullOrBlank()) return true
        val a = boardLine.uppercase().filter { it.isLetterOrDigit() }
        val b = legLine.uppercase().filter { it.isLetterOrDigit() }
        if (a.isEmpty() || b.isEmpty()) return true
        return a.contains(b.take(4)) || b.contains(a.take(4)) || a == b
    }
}
