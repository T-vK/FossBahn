package de.openbahn.api

import de.openbahn.api.mapper.JourneyResponseParser
import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Validates mobile v2 request shape against bahnvorhersage `api/fptf.py` + `webserver/mobile_v2.py`.
 */
class BahnVorhersageFptfContractTest {
    @Test
    fun buildRateRequest_walkingUsesArrivalBeforeDeparture() {
        val journey = Journey(
            id = "walk-times",
            legs = listOf(
                Leg(
                    origin = StopEvent("Hamburg Hbf", id = "8002549", scheduledTime = "2026-06-01T18:00:00"),
                    destination = StopEvent("Hannover Hbf", id = "8000152", scheduledTime = "2026-06-01T19:00:00"),
                    lineName = "ICE 1",
                ),
                Leg(
                    origin = StopEvent("Hannover Hbf", id = "8000152", scheduledTime = "2026-06-01T19:15:00"),
                    destination = StopEvent("Berlin Hbf", id = "8011160", scheduledTime = "2026-06-01T20:00:00"),
                    lineName = "ICE 2",
                ),
            ),
            durationMinutes = 120,
            transfers = 1,
            departure = "2026-06-01T18:00:00",
            arrival = "2026-06-01T20:00:00",
        )
        val serialized = BahnVorhersageFptfMapper.buildRateRequest(
            listOf(journey),
            buildTripRoutesForRating(journey),
        ).toString()
        assertTrue(serialized.contains("\"walking\":true"))
        val walkIdx = serialized.indexOf("\"walking\":true")
        val walkSlice = serialized.substring(walkIdx, (walkIdx + 400).coerceAtMost(serialized.length))
        val arrivalPos = walkSlice.indexOf("\"arrival\"")
        val departurePos = walkSlice.indexOf("\"departure\"")
        assertTrue(arrivalPos >= 0 && departurePos > arrivalPos, "walk leg must set arrival before departure")
    }

    @Test
    fun buildRateRequest_walkingLegUsesWalkingFlag() {
        val text = javaClass.getResource("/dbweb-journey-db-vendo-real.json")!!.readText()
        val journey = JourneyResponseParser.parse(text).journeys.first()
        val walk = journey.legs.first { it.isWalking }
        val trips = buildTripRoutesForRating(journey)
        val body = BahnVorhersageFptfMapper.buildRateRequest(listOf(journey), trips)
        val serialized = body.toString()
        assertTrue(serialized.contains("\"walking\":true"), "walking legs must use walking:true")
        assertTrue(!serialized.contains("\"type\":\"transfer\""), "do not send type=transfer for walks")
        val railTripIds = journey.legs.withIndex()
            .filter { !it.value.isWalking }
            .map { (index, leg) -> ratingTripId(journey.id, index, leg) }
        railTripIds.forEach { tripId ->
            assertTrue(tripId in trips.keys, "trips must contain $tripId")
            assertTrue(serialized.contains(tripId), "request must reference $tripId")
        }
        val walkTripId = ratingTripId(journey.id, journey.legs.indexOf(walk), walk)
        assertTrue(walkTripId !in trips.keys, "walking legs must not require trip stopovers")
    }

    @Test
    fun buildRateRequest_stopoversAlwaysIncludeArrivalAndDepartureKeys() {
        val text = javaClass.getResource("/dbweb-journey-db-vendo-real.json")!!.readText()
        val journey = JourneyResponseParser.parse(text).journeys.first()
        val trips = buildTripRoutesForRating(journey)
        val serialized = BahnVorhersageFptfMapper.buildRateRequest(listOf(journey), trips).toString()
        assertTrue(
            serialized.contains("\"arrival\":null") || serialized.contains("\"arrival\": null"),
            "first stopover must send arrival:null (bahnvorhersage indexes the key)",
        )
        assertTrue(
            serialized.contains("\"departure\":null") || serialized.contains("\"departure\": null"),
            "last stopover must send departure:null",
        )
    }

    @Test
    fun buildTripRoutesForRating_includesEveryRailLeg() {
        val text = javaClass.getResource("/dbweb-journey-db-vendo-real.json")!!.readText()
        val journey = JourneyResponseParser.parse(text).journeys.first()
        val railCount = journey.legs.count { !it.isWalking }
        val trips = buildTripRoutesForRating(journey)
        assertEquals(railCount, trips.size)
        trips.values.forEach { stops ->
            assertTrue(stops.size >= 2)
        }
    }
}
