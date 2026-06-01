package de.openbahn.api

import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BahnVorhersageRouteTrimTest {
    @Test
    fun routeStopsForRating_trimsFullVehicleRunToPassengerSegment() {
        val leg = Leg(
            origin = StopEvent("Hamburg Hbf", scheduledTime = "2026-06-01T10:00:00"),
            destination = StopEvent("Berlin Hbf", scheduledTime = "2026-06-01T12:00:00"),
            lineName = "ICE 100",
            intermediateStops = listOf(
                StopEvent("Büchen", scheduledTime = "2026-06-01T10:30:00"),
            ),
        )
        val fullRun = listOf(
            StopEvent("Köln Hbf", scheduledTime = "2026-06-01T08:00:00"),
            StopEvent("Hannover Hbf", scheduledTime = "2026-06-01T09:00:00"),
            leg.origin,
            leg.intermediateStops.single(),
            leg.destination,
            StopEvent("Dresden Hbf", scheduledTime = "2026-06-01T14:00:00"),
        )
        val trimmed = routeStopsForRating(leg, fullRun)
        assertEquals(3, trimmed.size)
        assertEquals("Hamburg Hbf", trimmed.first().name)
        assertEquals("Berlin Hbf", trimmed.last().name)
    }
}
