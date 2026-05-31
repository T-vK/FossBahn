package de.openbahn.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JourneyRightsExtensionsTest {
    @Test
    fun arrivalDelayUsesDestinationStop() {
        val journey = Journey(
            id = "j1",
            legs = listOf(
                Leg(
                    origin = StopEvent("A", scheduledTime = "2026-01-01T10:00:00"),
                    destination = StopEvent(
                        "B",
                        scheduledTime = "2026-01-01T12:00:00",
                        delayMinutes = 90,
                    ),
                ),
            ),
            durationMinutes = 120,
            transfers = 0,
            departure = "2026-01-01T10:00:00",
            arrival = "2026-01-01T12:00:00",
        )
        assertEquals(90, journey.arrivalDelayMinutes())
    }
}
