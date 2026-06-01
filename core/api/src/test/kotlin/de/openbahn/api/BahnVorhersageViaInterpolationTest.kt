package de.openbahn.api

import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class BahnVorhersageViaInterpolationTest {
    @Test
    fun parseRatedJourneys_interpolatesViaFromLegEndpointPmfs() {
        val journey = Journey(
            id = "via-leg",
            legs = listOf(
                Leg(
                    origin = StopEvent("Hamburg Hbf", scheduledTime = "2026-06-01T10:00:00"),
                    destination = StopEvent("Berlin Hbf", scheduledTime = "2026-06-01T12:00:00"),
                    intermediateStops = listOf(
                        StopEvent("Hannover Hbf", scheduledTime = "2026-06-01T11:00:00"),
                    ),
                    lineName = "ICE 701",
                ),
            ),
            durationMinutes = 120,
            transfers = 0,
            departure = "2026-06-01T10:00:00",
            arrival = "2026-06-01T12:00:00",
        )
        val response = """
            [{
              "legs": [{
                "type": "leg",
                "departureDelayPrediction": {
                  "offset": 2,
                  "predictions": [0.1, 0.1, 0.8, 0.0]
                },
                "arrivalDelayPrediction": {
                  "offset": 2,
                  "predictions": [0.2, 0.2, 0.5, 0.1]
                }
              }]
            }]
        """.trimIndent()
        val rated = BahnVorhersageFptfMapper.parseRatedJourneys(
            responseBody = response,
            journeys = listOf(journey),
            options = JourneyRatingOptions(
                onTimeTolerance = de.openbahn.model.OnTimeToleranceSettings.uniform(0),
            ),
        )!!
        val via = rated.first().stopTimeliness.first {
            it.intermediateIndex == 0 && it.isArrival
        }
        assertFalse(via.isEstimate)
        assertEquals(0.65, via.probability, 0.001)
    }
}
