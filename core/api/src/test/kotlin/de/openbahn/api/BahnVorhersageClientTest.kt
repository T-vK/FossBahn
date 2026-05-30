package de.openbahn.api

import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BahnVorhersageClientTest {
    @Test
    fun rateJourney_directConnection_returnsPunctuality() = runBlocking {
        val journey = Journey(
            id = "direct",
            legs = listOf(
                Leg(
                    origin = StopEvent("A", scheduledTime = "2026-05-30T10:00:00"),
                    destination = StopEvent("B", scheduledTime = "2026-05-30T12:00:00"),
                    lineName = "ICE 1",
                ),
            ),
            durationMinutes = 120,
            transfers = 0,
            departure = "2026-05-30T10:00:00",
            arrival = "2026-05-30T12:00:00",
        )
        val rated = BahnVorhersageClient().rateJourney(journey)
        assertTrue(rated.predictions.isEmpty())
        assertNotNull(rated.punctualityProbability)
        assertTrue(rated.punctualityIsEstimate)
    }
}
