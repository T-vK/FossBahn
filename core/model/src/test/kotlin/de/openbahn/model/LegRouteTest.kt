package de.openbahn.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LegRouteTest {
    @Test
    fun stationNamesMatch_ignoresPlatformSuffix() {
        assertTrue(stationNamesMatch("Uelzen", "Uelzen"))
        assertTrue(stationNamesMatch("Köln Hbf Gl.11-12", "Köln Hbf"))
    }

    @Test
    fun tripRouteStops_prefersFullRoute() {
        val leg = Leg(
            origin = StopEvent("B", scheduledTime = "2026-01-01T10:00:00"),
            destination = StopEvent("D", scheduledTime = "2026-01-01T12:00:00"),
            routeStops = listOf(
                StopEvent("A", scheduledTime = "2026-01-01T09:00:00"),
                StopEvent("B", scheduledTime = "2026-01-01T10:00:00"),
                StopEvent("C", scheduledTime = "2026-01-01T11:00:00"),
                StopEvent("D", scheduledTime = "2026-01-01T12:00:00"),
            ),
        )
        assertEquals(4, leg.tripRouteStops().size)
    }
}
