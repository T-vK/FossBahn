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
    fun boardAndAlightIndexInRoute() {
        val stops = listOf(
            StopEvent("A", scheduledTime = "2026-01-01T09:00:00"),
            StopEvent("B", scheduledTime = "2026-01-01T10:00:00"),
            StopEvent("C", scheduledTime = "2026-01-01T11:00:00"),
            StopEvent("D", scheduledTime = "2026-01-01T12:00:00"),
        )
        val board = boardIndexInRoute(stops, "B", "2026-01-01T10:00:00")
        val alight = alightIndexInRoute(stops, "C", "2026-01-01T11:00:00", board)
        assertEquals(1, board)
        assertEquals(2, alight)
        assertEquals(RouteStopSegment.BEFORE, routeStopSegment(0, board, alight))
        assertEquals(RouteStopSegment.ON_TRIP, routeStopSegment(1, board, alight))
        assertEquals(RouteStopSegment.AFTER, routeStopSegment(3, board, alight))
    }

    @Test
    fun withDelaysFrom_mergesRealtimeFromSegment() {
        val full = listOf(
            StopEvent("A", scheduledTime = "2026-01-01T09:00:00"),
            StopEvent("B", scheduledTime = "2026-01-01T10:00:00", delayMinutes = 5),
        )
        val merged = full.withDelaysFrom(
            listOf(StopEvent("B", scheduledTime = "2026-01-01T10:00:00", delayMinutes = 5)),
        )
        assertEquals(5, merged[1].delayMinutes)
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
