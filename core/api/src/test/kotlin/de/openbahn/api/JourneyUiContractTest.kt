package de.openbahn.api

import de.openbahn.api.mapper.JourneyResponseParser
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
