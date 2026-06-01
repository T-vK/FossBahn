package de.openbahn.api

import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
        val syntheticTripId = BahnVorhersageClient.syntheticTripId("j1", 0)
        val body = BahnVorhersageFptfMapper.buildRateRequest(
            journeys = listOf(journey),
            tripRoutes = mapOf(
                syntheticTripId to listOf(
                    journey.legs.first().origin,
                    journey.legs.first().destination,
                ),
            ),
        )
        assertTrue(body.toString().contains(syntheticTripId))
        assertTrue(body.toString().contains("journeys"))
    }

    @Test
    fun parseRatedJourneys_usesExactOnTimeProbability_notUserTolerance() {
        val journey = Journey(
            id = "j1",
            legs = listOf(
                Leg(
                    origin = StopEvent("Berlin Hbf", scheduledTime = "2026-05-30T10:00:00"),
                    destination = StopEvent("Hamburg Hbf", scheduledTime = "2026-05-30T12:00:00"),
                    lineName = "ICE 701",
                ),
            ),
            durationMinutes = 120,
            transfers = 0,
            departure = "2026-05-30T10:00:00",
            arrival = "2026-05-30T12:00:00",
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
                onTimeTolerance = de.openbahn.model.OnTimeToleranceSettings.uniform(10),
            ),
        )!!
        val departure = rated.first().stopTimeliness.first {
            it.legIndex == 0 && !it.isArrival && it.intermediateIndex == null
        }
        val arrival = rated.first().stopTimeliness.first {
            it.legIndex == 0 && it.isArrival && it.intermediateIndex == null
        }
        assertFalse(departure.isEstimate)
        assertFalse(arrival.isEstimate)
        assertEquals(0.8, departure.probability, 0.001)
        assertEquals(0.5, arrival.probability, 0.001)
        assertTrue(rated.first().stopTimeliness.none { it.intermediateIndex != null })
    }

    @Test
    fun buildRateRequest_usesSyntheticTripIdWhenLegHasNoTripId() {
        val journey = Journey(
            id = "j-no-trip",
            legs = listOf(
                Leg(
                    origin = StopEvent("Berlin Hbf", scheduledTime = "2026-05-30T10:00:00"),
                    destination = StopEvent("Hamburg Hbf", scheduledTime = "2026-05-30T12:00:00"),
                    lineName = "ICE 701",
                    tripId = null,
                ),
            ),
            durationMinutes = 120,
            transfers = 0,
            departure = "2026-05-30T10:00:00",
            arrival = "2026-05-30T12:00:00",
        )
        val trips = buildTripRoutesForRating(listOf(journey))
        val body = BahnVorhersageFptfMapper.buildRateRequest(
            journeys = listOf(journey),
            tripRoutes = trips,
        )
        assertTrue(trips.containsKey("openbahn-j-no-trip-leg0"))
        assertTrue(body.toString().contains("openbahn-j-no-trip-leg0"))
    }

    @Test
    fun buildRateRequest_timesIncludeBerlinOffset() {
        val journey = Journey(
            id = "j1",
            legs = listOf(
                Leg(
                    origin = StopEvent("Berlin Hbf", scheduledTime = "2026-06-01T10:00:00"),
                    destination = StopEvent("Hamburg Hbf", scheduledTime = "2026-06-01T12:00:00"),
                    lineName = "ICE 701",
                ),
            ),
            durationMinutes = 120,
            transfers = 0,
            departure = "2026-06-01T10:00:00",
            arrival = "2026-06-01T12:00:00",
        )
        val trips = buildTripRoutesForRating(listOf(journey))
        val body = BahnVorhersageFptfMapper.buildRateRequest(listOf(journey), trips).toString()
        assertTrue(body.contains("+02:00") || body.contains("+01:00"), "times should carry Europe/Berlin offset")
    }
}
