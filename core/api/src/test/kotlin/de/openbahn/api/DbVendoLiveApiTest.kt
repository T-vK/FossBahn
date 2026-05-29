package de.openbahn.api

import de.openbahn.api.LiveApiTestSupport.apiCall
import de.openbahn.api.LiveApiTestSupport.assertJourneysNotEmpty
import de.openbahn.api.LiveApiTestSupport.findStation
import de.openbahn.model.JourneySearchOptions
import de.openbahn.model.TransportProduct
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * Live integration tests against int.bahn.de — JVM only (no emulator).
 *
 * Run: `.github/scripts/run-live-api-tests.sh`
 */
@Tag("live-api")
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_API_TESTS", matches = "true")
class DbVendoLiveApiTest {
    private val client = DbVendoClient()

    @Test
    fun searchLocations_returnsBerlinHbf() = runBlocking {
        val results = apiCall { client.searchLocations("Berlin Hbf", locale = "de") }
        assertTrue(
            results.any { it.name.contains("Berlin", ignoreCase = true) },
            "Expected Berlin Hbf in results, got: ${results.take(3).map { it.name }}",
        )
    }

    @Test
    fun searchJourneys_returnsConnections() = runBlocking {
        val origin = apiCall { client.findStation("Berlin Hbf", LiveApiTestSupport.BERLIN_HBF_EVA) }
            ?: return@runBlocking
        val destination = apiCall { client.findStation("München Hbf", "8000261") }
            ?: return@runBlocking
        val whenTime = LiveApiTestSupport.berlinDeparturePlusHours(3)
        val journeys = apiCall {
            client.searchJourneys(
                origin,
                destination,
                JourneySearchOptions(products = setOf(TransportProduct.ICE)),
                whenTime,
            )
        }
        assertJourneysNotEmpty(journeys, origin, destination)
    }

    @Test
    fun searchJourneys_hamburgToBerlin_returnsConnections() = runBlocking {
        val origin = apiCall {
            client.findStation("Hamburg Hbf", LiveApiTestSupport.HAMBURG_HBF_EVA)
        } ?: return@runBlocking
        val destination = apiCall {
            client.findStation("Berlin Hbf", LiveApiTestSupport.BERLIN_HBF_EVA)
        } ?: return@runBlocking
        val whenTime = LiveApiTestSupport.berlinDeparturePlusHours(3)
        val journeys = apiCall {
            client.searchJourneys(origin, destination, JourneySearchOptions(), whenTime)
        }
        assertJourneysNotEmpty(journeys, origin, destination)
    }

    @Test
    fun departures_returnsEntries() = runBlocking {
        val station = apiCall { client.findStation("Berlin Hbf", LiveApiTestSupport.BERLIN_HBF_EVA) }
            ?: return@runBlocking
        val deps = apiCall { client.departures(station) }
        assertTrue(deps.isNotEmpty(), "Expected departures at ${station.name}")
    }
}
