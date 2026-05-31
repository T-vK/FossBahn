package de.openbahn.api

import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BahnVorhersageHeuristicTransferTest {
    @Test
    fun estimate_withMinTransferMinutes_lowersScoreForTightConnections() {
        val journey = Journey(
            id = "j1",
            legs = listOf(
                Leg(
                    origin = StopEvent("A", scheduledTime = "2026-05-30T10:00:00"),
                    destination = StopEvent("B", scheduledTime = "2026-05-30T12:00:00"),
                    lineName = "ICE 1",
                ),
                Leg(
                    origin = StopEvent("B", scheduledTime = "2026-05-30T12:08:00"),
                    destination = StopEvent("C", scheduledTime = "2026-05-30T14:00:00"),
                    lineName = "ICE 2",
                ),
            ),
            durationMinutes = 240,
            transfers = 1,
            departure = "2026-05-30T10:00:00",
            arrival = "2026-05-30T14:00:00",
        )
        val withoutBuffer = BahnVorhersageHeuristic.estimate(journey, minTransferMinutes = null)
            .single().successProbability!!
        val withTenMinuteBuffer = BahnVorhersageHeuristic.estimate(journey, minTransferMinutes = 10)
            .single().successProbability!!
        assertTrue(withTenMinuteBuffer < withoutBuffer)
    }
}
