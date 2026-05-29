package de.openbahn.api

import de.openbahn.api.mapper.JourneyResponseParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JourneyResponseParserTest {
    @Test
    fun parsesFullFixture() {
        val text = javaClass.getResource("/dbweb-journey-full.json")!!.readText()
        val journeys = JourneyResponseParser.parse(text)
        assertTrue(journeys.isNotEmpty())
    }

    @Test
    fun parsesHamburgBerlinWithNumericPlatform() {
        val text = javaClass.getResource("/dbweb-journey-hamburg-berlin.json")!!.readText()
        val journeys = JourneyResponseParser.parse(text)
        assertEquals(1, journeys.size)
        assertEquals("7", journeys.first().legs.first().origin.platform)
    }

    @Test
    fun parsesNestedVerbindungWrapper() {
        val text = javaClass.getResource("/dbweb-probe-nested_verbindung.json")!!.readText()
        val journeys = JourneyResponseParser.parse(text)
        assertTrue(journeys.isNotEmpty())
        assertTrue(journeys.first().legs.isNotEmpty())
    }

    @Test
    fun parsesNumericLinienNummer() {
        val text = javaClass.getResource("/dbweb-probe-numeric_linienNummer.json")!!.readText()
        val journeys = JourneyResponseParser.parse(text)
        assertTrue(journeys.isNotEmpty())
    }
}
