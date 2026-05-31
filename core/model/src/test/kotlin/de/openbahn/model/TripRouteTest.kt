package de.openbahn.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TripRouteTest {
    @Test
    fun isLongerTripRoute_detectsExtraStops() {
        val segment = listOf(
            StopEvent("B", scheduledTime = "2026-01-01T10:00:00"),
            StopEvent("D", scheduledTime = "2026-01-01T12:00:00"),
        )
        val full = listOf(
            StopEvent("A", scheduledTime = "2026-01-01T09:00:00"),
            StopEvent("B", scheduledTime = "2026-01-01T10:00:00"),
            StopEvent("C", scheduledTime = "2026-01-01T11:00:00"),
            StopEvent("D", scheduledTime = "2026-01-01T12:00:00"),
            StopEvent("E", scheduledTime = "2026-01-01T13:00:00"),
        )
        assertTrue(isLongerTripRoute(full, segment))
        assertFalse(isLongerTripRoute(segment, segment))
    }
}
