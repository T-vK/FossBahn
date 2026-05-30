package de.openbahn.api

import de.openbahn.api.mapper.JourneyResponseParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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
    fun parseRefresh_parsesWrappedVerbindungWithDelays() {
        val text = javaClass.getResource("/dbweb-journey-refresh.json")!!.readText()
        val root = Json.parseToJsonElement(text).jsonObject
        val journey = JourneyResponseParser.parseRefresh(root)
        requireNotNull(journey)
        val leg = journey.legs.single()
        assertEquals(12, leg.origin.delayMinutes)
        assertEquals(20, leg.destination.delayMinutes)
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

    @Test
    fun parsesDelaysOnAbfahrtWhenHalteListPresent() {
        val text = javaClass.getResource("/dbweb-journey-halte-abfahrt-delay.json")!!.readText()
        val leg = JourneyResponseParser.parse(text).journeys.single().legs.single()
        assertEquals(10, leg.origin.delayMinutes)
        assertEquals("2026-05-30T12:10:00", leg.origin.prognosedTime)
        assertEquals(12, leg.destination.delayMinutes)
    }

    @Test
    fun parsesDelaysFromEzHaltTimes() {
        val text = javaClass.getResource("/dbweb-journey-halt-ez-delay.json")!!.readText()
        val leg = JourneyResponseParser.parse(text).journeys.single().legs.single()
        assertEquals(7, leg.origin.delayMinutes)
        assertEquals(11, leg.destination.delayMinutes)
    }
}
