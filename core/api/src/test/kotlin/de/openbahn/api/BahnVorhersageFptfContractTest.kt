package de.openbahn.api

import de.openbahn.api.mapper.JourneyResponseParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Validates mobile v2 request shape against bahnvorhersage `api/fptf.py` + `webserver/mobile_v2.py`.
 */
class BahnVorhersageFptfContractTest {
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
