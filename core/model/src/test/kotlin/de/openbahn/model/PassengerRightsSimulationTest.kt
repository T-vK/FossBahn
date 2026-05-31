package de.openbahn.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PassengerRightsSimulationTest {
    @Test
    fun withSimulatedArrivalDelay_setsDestinationDelay() {
        val journey = Journey(
            id = "j1",
            legs = listOf(
                Leg(
                    origin = StopEvent("A", scheduledTime = "2026-05-31T10:00:00"),
                    destination = StopEvent("B", scheduledTime = "2026-05-31T12:00:00"),
                ),
            ),
            durationMinutes = 120,
            transfers = 0,
            departure = "2026-05-31T10:00:00",
            arrival = "2026-05-31T12:00:00",
        )
        val simulated = journey.withSimulatedArrivalDelay(65)
        assertEquals(65, simulated.arrivalDelayMinutes())
        assertEquals("2026-05-31T13:05:00", simulated.legs.single().destination.prognosedTime)
    }

    @Test
    fun applyConfig_missedTransfer_onTwoLegJourney() {
        val journey = Journey(
            id = "j2",
            legs = listOf(
                Leg(
                    origin = StopEvent("A", scheduledTime = "2026-05-31T10:00:00"),
                    destination = StopEvent("B", scheduledTime = "2026-05-31T11:00:00"),
                ),
                Leg(
                    origin = StopEvent("B", scheduledTime = "2026-05-31T11:05:00"),
                    destination = StopEvent("C", scheduledTime = "2026-05-31T12:00:00"),
                ),
            ),
            durationMinutes = 120,
            transfers = 1,
            departure = "2026-05-31T10:00:00",
            arrival = "2026-05-31T12:00:00",
        )
        val simulated = journey.applyPassengerRightsSimulation(
            PassengerRightsSimulationConfig(
                enabled = true,
                simulateMissedTransfer = true,
            ),
        )
        assertTrue(simulated.missedTransferCount(minTransferMinutes = 10) >= 1)
    }
}
