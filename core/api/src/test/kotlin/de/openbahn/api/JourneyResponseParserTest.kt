package de.openbahn.api

import de.openbahn.api.mapper.JourneyResponseParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JourneyResponseParserTest {
    @Test
    fun parsesFullFixture() {
        val text = javaClass.getResource("/dbweb-journey-full.json")!!.readText()
        val journeys = JourneyResponseParser.parse(text).journeys
        assertTrue(journeys.isNotEmpty())
    }

    @Test
    fun parsesHamburgBerlinWithNumericPlatform() {
        val text = javaClass.getResource("/dbweb-journey-hamburg-berlin.json")!!.readText()
        val journeys = JourneyResponseParser.parse(text).journeys
        assertEquals(1, journeys.size)
        assertEquals("7", journeys.first().legs.first().origin.platform)
    }

    @Test
    fun parsesNestedVerbindungWrapper() {
        val text = javaClass.getResource("/dbweb-probe-nested_verbindung.json")!!.readText()
        val journeys = JourneyResponseParser.parse(text).journeys
        assertTrue(journeys.isNotEmpty())
        assertTrue(journeys.first().legs.isNotEmpty())
    }

    @Test
    fun parsesNumericLinienNummer() {
        val text = javaClass.getResource("/dbweb-probe-numeric_linienNummer.json")!!.readText()
        val journeys = JourneyResponseParser.parse(text).journeys
        assertTrue(journeys.isNotEmpty())
    }

    @Test
    fun parsesConnectionsFromIntervalleWhenTopLevelEmpty() {
        val text = javaClass.getResource("/dbweb-journey-intervalle-only.json")!!.readText()
        val journeys = JourneyResponseParser.parse(text).journeys
        assertEquals(1, journeys.size)
        assertEquals("Hamburg Hbf", journeys.first().legs.first().origin.name)
    }

    @Test
    fun parsesDelaysAndPagingReferences() {
        val text = javaClass.getResource("/dbweb-journey-with-delay.json")!!.readText()
        val result = JourneyResponseParser.parse(text)
        assertEquals("earlier-token-test", result.pagingEarlier)
        assertEquals("later-token-test", result.pagingLater)
        val leg = result.journeys.single().legs.single()
        assertEquals(8, leg.origin.delayMinutes)
        assertEquals("2026-05-30T12:08:00", leg.origin.prognosedTime)
        assertEquals(15, leg.destination.delayMinutes)
    }
}
