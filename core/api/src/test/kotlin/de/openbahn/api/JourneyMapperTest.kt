package de.openbahn.api

import de.openbahn.api.dto.DbJourneyResponse
import de.openbahn.api.mapper.JourneyMapper
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JourneyMapperTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun mapsVerbindungsAbschnitteFromFixture() {
        val text = javaClass.getResource("/dbweb-journey-snippet.json")!!.readText()
        val response = json.decodeFromString<DbJourneyResponse>(text)
        val journeys = JourneyMapper.mapJourneys(response)
        assertEquals(1, journeys.size)
        val j = journeys.first()
        assertEquals(1, j.transfers)
        assertEquals(60, j.durationMinutes)
        assertEquals("Berlin Hbf", j.legs.first().origin.name)
        assertEquals("München Hbf", j.legs.first().destination.name)
        assertEquals("ICE 123", j.legs.first().lineName)
    }

    @Test
    fun mapsHamburgBerlinFixtureWithNumericPlatform() {
        val text = javaClass.getResource("/dbweb-journey-hamburg-berlin.json")!!.readText()
        val response = json.decodeFromString<de.openbahn.api.dto.DbJourneyResponse>(text)
        val journeys = JourneyMapper.mapJourneys(response)
        assertEquals(1, journeys.size)
        assertEquals("Hamburg Hbf", journeys.first().legs.first().origin.name)
        assertEquals("Berlin Hbf", journeys.first().legs.first().destination.name)
        assertEquals("7", journeys.first().legs.first().origin.platform)
    }

    @Test
    fun mapsRealDbWebJourneyFixtureWithMultipleLegs() {
        val text = javaClass.getResource("/dbweb-journey-full.json")!!.readText()
        val response = json.decodeFromString<DbJourneyResponse>(text)
        val journeys = JourneyMapper.mapJourneys(response)
        assertTrue(journeys.isNotEmpty(), "Expected at least one journey from full fixture")
        val j = journeys.first()
        assertTrue(j.legs.size >= 2, "Expected multiple legs, got ${j.legs.size}")
        assertEquals("Köln Hbf", j.legs.first().origin.name)
        assertEquals("Nürnberg Hbf", j.legs.last().destination.name)
        assertTrue(j.priceHint != null || j.durationMinutes > 0)
    }
}
