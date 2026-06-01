package de.openbahn.api

import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BahnVorhersageLegExpansionTest {
    @Test
    fun expandLegsForRating_insertsWalkBetweenConsecutiveRailLegs() {
        val legs = listOf(
            Leg(
                origin = StopEvent("Hamburg Hbf", id = "8002549", scheduledTime = "2026-06-01T18:00:00"),
                destination = StopEvent("Hannover Hbf", id = "8000152", scheduledTime = "2026-06-01T19:00:00"),
                lineName = "ICE 703",
            ),
            Leg(
                origin = StopEvent("Hannover Hbf", id = "8000152", scheduledTime = "2026-06-01T19:15:00"),
                destination = StopEvent("Berlin Hbf", id = "8011160", scheduledTime = "2026-06-01T20:30:00"),
                lineName = "ICE 1001",
            ),
        )
        val expanded = expandLegsForRating(legs)
        assertEquals(3, expanded.size)
        assertTrue(expanded[1].syntheticWalk)
        assertTrue(expanded[1].leg.isWalking)
        assertEquals("Hannover Hbf", expanded[1].leg.origin.name)
        assertEquals("Hannover Hbf", expanded[1].leg.destination.name)
    }

    @Test
    fun buildRateRequest_insertsWalkingSegmentForTwoRailLegs() {
        val journey = Journey(
            id = "hh-be",
            legs = listOf(
                Leg(
                    origin = StopEvent("Hamburg Hbf", id = "8002549", scheduledTime = "2026-06-01T18:00:00"),
                    destination = StopEvent("Hannover Hbf", id = "8000152", scheduledTime = "2026-06-01T19:00:00"),
                    lineName = "ICE 703",
                ),
                Leg(
                    origin = StopEvent("Hannover Hbf", id = "8000152", scheduledTime = "2026-06-01T19:15:00"),
                    destination = StopEvent("Berlin Hbf", id = "8011160", scheduledTime = "2026-06-01T20:30:00"),
                    lineName = "ICE 1001",
                ),
            ),
            durationMinutes = 150,
            transfers = 1,
            departure = "2026-06-01T18:00:00",
            arrival = "2026-06-01T20:30:00",
        )
        val trips = buildTripRoutesForRating(journey)
        val serialized = BahnVorhersageFptfMapper.buildRateRequest(listOf(journey), trips).toString()
        assertTrue(serialized.contains("\"walking\":true"))
    }
}
