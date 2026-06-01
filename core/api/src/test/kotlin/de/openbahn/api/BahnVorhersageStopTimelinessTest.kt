package de.openbahn.api

import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BahnVorhersageStopTimelinessTest {
    @Test
    fun buildStopTimeliness_variesByDelayAndProduct() {
        val onTimeIce = journey(
            origin = StopEvent("Berlin Hbf", scheduledTime = "2026-05-30T10:00:00"),
            destination = StopEvent("Hamburg Hbf", scheduledTime = "2026-05-30T12:00:00", delayMinutes = 0),
            lineName = "ICE 701",
        )
        val delayedRe = journey(
            origin = StopEvent("A", scheduledTime = "2026-05-30T10:00:00"),
            destination = StopEvent("B", scheduledTime = "2026-05-30T11:00:00", delayMinutes = 18),
            lineName = "RE 5",
        )
        val tolerance = de.openbahn.model.OnTimeToleranceSettings.uniform(10)
        val iceArrival = BahnVorhersageHeuristic.buildStopTimeliness(onTimeIce, tolerance)
            .single { it.isArrival }
        val reArrival = BahnVorhersageHeuristic.buildStopTimeliness(delayedRe, tolerance)
            .single { it.isArrival }
        assertTrue(iceArrival.probability > reArrival.probability)
        assertNotEquals(0.8, iceArrival.probability, 0.001)
    }

    @Test
    fun buildStopTimeliness_tightTransferLowersDownstreamDeparture() {
        val journey = Journey(
            id = "transfer",
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
        val stops = BahnVorhersageHeuristic.buildStopTimeliness(
            journey,
            de.openbahn.model.OnTimeToleranceSettings.uniform(10),
            minTransferMinutes = 10,
        )
        val firstDep = stops.first { it.legIndex == 0 && !it.isArrival }
        val secondDep = stops.first { it.legIndex == 1 && !it.isArrival }
        assertTrue(secondDep.probability < firstDep.probability)
    }

    private fun journey(origin: StopEvent, destination: StopEvent, lineName: String) = Journey(
        id = "j",
        legs = listOf(
            Leg(
                origin = origin,
                destination = destination,
                lineName = lineName,
            ),
        ),
        durationMinutes = 120,
        transfers = 0,
        departure = origin.scheduledTime,
        arrival = destination.scheduledTime,
    )
}
