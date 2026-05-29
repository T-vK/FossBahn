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
import java.time.LocalDateTime

/**
 * Live integration tests against int.bahn.de — run on the CI host JVM (no emulator).
 *
 * ```
 * ./gradlew :core:api:testDebugUnitTest -PliveApi
 * ```
 *
 * Skips (does not fail) when DB blocks datacenter IPs (OPS_BLOCKED).
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
        val journeys = apiCall {
            client.searchJourneys(
                origin,
                destination,
                JourneySearchOptions(products = setOf(TransportProduct.ICE)),
                LocalDateTime.now().plusHours(2),
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
        val journeys = apiCall {
            client.searchJourneys(
                origin,
                destination,
                JourneySearchOptions(),
                LocalDateTime.now().plusHours(2),
            )
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
