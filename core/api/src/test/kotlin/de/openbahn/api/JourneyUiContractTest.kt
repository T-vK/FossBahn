package de.openbahn.api

import de.openbahn.api.mapper.JourneyResponseParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Documents what the Search UI needs from [DbVendoClient.searchJourneys] / [JourneyResponseParser].
 *
 * If these tests pass but the app shows "No connections found", the bug is likely in
 * station resolution ([de.openbahn.navigator.ui.search.SearchViewModel.resolveLocation])
 * or the installed APK is older than the parser fix — not in the parser itself.
 */
class JourneyUiContractTest {
    @Test
    fun dbVendoRealFixture_yieldsJourneysForUi() {
        val text = javaClass.getResource("/dbweb-journey-db-vendo-real.json")!!.readText()
        val journeys = JourneyResponseParser.parse(text)
        assertUiContract(journeys, "db-vendo real journey fixture")
    }

    @Test
    fun hamburgBerlinFixture_yieldsJourneysForUi() {
        val text = javaClass.getResource("/dbweb-journey-hamburg-berlin.json")!!.readText()
        val journeys = JourneyResponseParser.parse(text)
        assertUiContract(journeys, "Hamburg–Berlin fixture")
    }

    @Test
    fun intervalleOnlyFixture_yieldsJourneysForUi() {
        val text = javaClass.getResource("/dbweb-journey-intervalle-only.json")!!.readText()
        val journeys = JourneyResponseParser.parse(text)
        assertUiContract(journeys, "intervalle-only fixture")
    }

    @Test
    fun emptyOuterAbschnitteWithNestedVerbindung_yieldsJourneysForUi() {
        val text = javaClass.getResource("/dbweb-journey-empty-outer-abschnitte.json")!!.readText()
        val journeys = JourneyResponseParser.parse(text)
        assertUiContract(journeys, "empty outer abschnitte + nested verbindung")
    }

    @Test
    fun nestedAbschnitteWrapperFixture_yieldsJourneysForUi() {
        val text = javaClass.getResource("/dbweb-journey-nested-abschnitte-wrapper.json")!!.readText()
        val journeys = JourneyResponseParser.parse(text)
        assertUiContract(journeys, "nested abschnitte wrapper")
        assertTrue(journeys.first().legs.size >= 2, "expected multiple legs from nested abschnitte")
    }

    @Test
    fun epochMillisAbschnittFixture_yieldsJourneysForUi() {
        val text = javaClass.getResource("/dbweb-journey-epoch-millis-abschnitt.json")!!.readText()
        val journeys = JourneyResponseParser.parse(text)
        assertUiContract(journeys, "epoch millis abschnitt")
    }

    @Test
    fun haltIdNamesFixture_yieldsJourneysForUi() {
        val text = javaClass.getResource("/dbweb-journey-halt-id-names.json")!!.readText()
        val journeys = JourneyResponseParser.parse(text)
        assertUiContract(journeys, "halt id names")
        assertTrue(journeys.first().legs.first().origin.name.contains("Hamburg"))
    }

    @Test
    fun sollzeitAbschnittFixture_yieldsJourneysForUi() {
        val text = javaClass.getResource("/dbweb-journey-sollzeit-abschnitt.json")!!.readText()
        val journeys = JourneyResponseParser.parse(text)
        assertUiContract(journeys, "abfahrt/ankunft sollzeit fixture")
        assertTrue(journeys.first().legs.first().origin.name.contains("Hamburg"))
    }

    @Test
    fun abfahrtNestedAbfahrtsZeitpunktFixture_yieldsJourneysForUi() {
        val text = javaClass.getResource("/dbweb-journey-abfahrt-nested-zeit.json")!!.readText()
        val journeys = JourneyResponseParser.parse(text)
        assertUiContract(journeys, "abfahrt/ankunft with nested abfahrtsZeitpunkt")
        assertTrue(journeys.first().legs.first().origin.name.contains("Hamburg"))
        assertTrue(journeys.first().legs.first().destination.name.contains("Berlin"))
    }

    @Test
    fun startHaltAbfahrtFixture_yieldsJourneysForUi() {
        val text = javaClass.getResource("/dbweb-journey-starthalt-abfahrt.json")!!.readText()
        val journeys = JourneyResponseParser.parse(text)
        assertUiContract(journeys, "startHalt/zielHalt + abfahrt/ankunft fixture")
        assertTrue(journeys.first().legs.first().origin.name.contains("Berlin"))
        assertTrue(journeys.first().legs.first().destination.name.contains("Hamburg"))
    }

    @Test
    fun nestedOrtAbschnittFixture_yieldsJourneysForUi() {
        val text = javaClass.getResource("/dbweb-journey-nested-ort-abschnitt.json")!!.readText()
        val journeys = JourneyResponseParser.parse(text)
        assertUiContract(journeys, "nested ort abschnitt")
    }

    @Test
    fun intermediateHalteFixture_mapsZwischenstationen() {
        val text = javaClass.getResource("/dbweb-journey-intermediate-halte.json")!!.readText()
        val journeys = JourneyResponseParser.parse(text)
        assertUiContract(journeys, "intermediate halte")
        val leg = journeys.first().legs.first()
        assertEquals(1, leg.intermediateStops.size)
        assertTrue(leg.intermediateStops.first().name.contains("Hannover"))
        assertEquals("7", leg.origin.platform)
        assertEquals("12", leg.destination.platform)
    }

    @Test
    fun halteOnlyAbschnittFixture_yieldsJourneysForUi() {
        val text = javaClass.getResource("/dbweb-journey-halte-only-abschnitt.json")!!.readText()
        val journeys = JourneyResponseParser.parse(text)
        assertUiContract(journeys, "halte-only abschnitt fixture")
        val names = journeys.first().legs.flatMap { listOf(it.origin.name, it.destination.name) }
        assertTrue(names.any { it.contains("Hamburg") })
        assertTrue(names.any { it.contains("Berlin") })
    }

    private fun assertUiContract(journeys: List<de.openbahn.model.Journey>, label: String) {
        assertFalse(
            journeys.isEmpty(),
            "$label: parser returned no journeys — UI would show info_no_connections",
        )
        val first = journeys.first()
        assertFalse(
            first.legs.isEmpty(),
            "$label: journey has no legs — JourneyCard would have nothing to show",
        )
        first.legs.forEach { leg ->
            assertFalse(leg.origin.name.isBlank(), "$label: leg missing origin name")
            assertFalse(leg.destination.name.isBlank(), "$label: leg missing destination name")
            assertFalse(leg.origin.scheduledTime.isBlank(), "$label: leg missing departure time")
        }
    }
}
