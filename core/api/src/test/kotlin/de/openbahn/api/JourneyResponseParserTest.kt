package de.openbahn.api

import de.openbahn.api.mapper.JourneyResponseParser
import de.openbahn.model.tripRouteStops
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
    fun parsesWalkingLegAndSectionRemarks() {
        val text = javaClass.getResource("/dbweb-scenario-flx1247-cancelled.json")!!.readText()
        val journey = JourneyResponseParser.parse(text).journeys.single()
        assertTrue(journey.remarks.any { it.contains("entfällt", ignoreCase = true) })
        val leg = journey.legs.single()
        assertTrue(leg.remarks.any { it.contains("fällt aus", ignoreCase = true) })
    }

    @Test
    fun parsesWalkSectionFromRealFixture() {
        val text = javaClass.getResource("/dbweb-journey-db-vendo-real.json")!!.readText()
        val journey = JourneyResponseParser.parse(text).journeys.first()
        val walk = journey.legs.firstOrNull { it.isWalking }
        requireNotNull(walk) { "expected a walking leg in fixture" }
        assertEquals("Fußweg", walk.lineName)
        assertTrue((walk.durationMinutes ?: 0) > 0)
    }

    @Test
    fun prefersPublicLineLabelFromRealFixture() {
        val text = javaClass.getResource("/dbweb-journey-db-vendo-real.json")!!.readText()
        val railLegs = JourneyResponseParser.parse(text).journeys.first().legs.filter { !it.isWalking }
        val sBahn = railLegs.first { it.lineName?.startsWith("S") == true }
        assertEquals("S 12", sBahn.lineName)
        val ice = railLegs.first { it.lineName?.startsWith("ICE") == true }
        assertEquals("ICE 523", ice.lineName)
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

    @Test
    fun parsesRegionalLineAndPriorStopsWithDelays() {
        val text = javaClass.getResource("/dbweb-journey-regional-rb31.json")!!.readText()
        val leg = JourneyResponseParser.parse(text).journeys.single().legs.single()
        assertEquals("RB 31", leg.lineName)
        assertEquals("81633", leg.lineDetail)
        assertEquals(4, leg.routeStops.size)
        assertEquals(2, leg.priorStops.size)
        assertEquals("Hamburg Hbf", leg.priorStops.first().name)
        assertEquals(5, leg.priorStops.first().delayMinutes)
        assertEquals(3, leg.priorStops[1].delayMinutes)
        assertEquals("Uelzen", leg.origin.name)
    }

    @Test
    fun parseFahrtRoute_returnsFullVehicleRun() {
        val text = javaClass.getResource("/dbweb-fahrt-rb31.json")!!.readText()
        val stops = JourneyResponseParser.parseFahrtRoute(text)
        assertEquals(6, stops.size)
        assertEquals("Celle", stops.first().name)
        assertEquals("Salzwedel", stops.last().name)
        assertEquals(5, stops[1].delayMinutes)
        assertEquals(3, stops[2].delayMinutes)
    }

    @Test
    fun parseFahrtRoute_readsHalteFromNestedFahrtWrapper() {
        val wrapped = """{"fahrt": ${javaClass.getResource("/dbweb-fahrt-rb31.json")!!.readText()}}"""
        val stops = JourneyResponseParser.parseFahrtRoute(wrapped)
        assertEquals(6, stops.size)
    }

    @Test
    fun buildRouteFromBoard_extendsSegmentWithViaNames() {
        val leg = JourneyResponseParser.parse(
            javaClass.getResource("/dbweb-journey-regional-rb31.json")!!.readText(),
        ).journeys.single().legs.single()
        val segment = leg.tripRouteStops()
        val arrivals = """{"ankuenfte":[{"journeyId":"2|#ZB#RB    31#ZE#81633#","ueber":["Celle","Lüneburg"],"zeit":"2026-06-15T10:15:00"}]}"""
        val departures = """{"abfahrten":[{"journeyId":"2|#ZB#RB    31#ZE#81633#","ueber":["Wieren","Salzwedel"],"zeit":"2026-06-15T10:15:00"}]}"""
        val extended = JourneyResponseParser.buildRouteFromBoard(
            arrivalsText = arrivals,
            departuresText = departures,
            tripId = leg.tripId!!,
            leg = leg,
            segment = segment,
        )
        assertTrue(extended.size > segment.size)
        assertEquals("Celle", extended.first().name)
        assertEquals("Salzwedel", extended.last().name)
    }

    @Test
    fun parsesFlx1247CancellationFixture() {
        val text = javaClass.getResource("/dbweb-scenario-flx1247-cancelled.json")!!.readText()
        val leg = JourneyResponseParser.parse(text).journeys.single().legs.single()
        assertTrue(leg.origin.cancelled)
        assertEquals("FLX 1247", leg.lineName)
    }
}
