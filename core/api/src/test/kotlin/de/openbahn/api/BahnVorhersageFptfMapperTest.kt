package de.openbahn.api

import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BahnVorhersageFptfMapperTest {
    @Test
    fun buildRateRequest_includesJourneysAndTrips() {
        val journey = Journey(
            id = "j1",
            legs = listOf(
                Leg(
                    origin = StopEvent("Berlin Hbf", id = "A=1@O=Berlin Hbf@X=13369000@Y=52525000@L=8011160@", scheduledTime = "2026-05-30T10:00:00"),
                    destination = StopEvent("Hamburg Hbf", id = "A=1@O=Hamburg Hbf@X=10006000@Y=53553000@L=8002549@", scheduledTime = "2026-05-30T12:00:00"),
                    lineName = "ICE 701",
                    tripId = "trip-ice-701",
                ),
            ),
            durationMinutes = 120,
            transfers = 0,
            departure = "2026-05-30T10:00:00",
            arrival = "2026-05-30T12:00:00",
        )
        val body = BahnVorhersageFptfMapper.buildRateRequest(
            journeys = listOf(journey),
            tripRoutes = mapOf(
                "trip-ice-701" to listOf(
                    journey.legs.first().origin,
                    journey.legs.first().destination,
                ),
            ),
        )
        assertTrue(body.toString().contains("trip-ice-701"))
        assertTrue(body.toString().contains("journeys"))
    }
}
