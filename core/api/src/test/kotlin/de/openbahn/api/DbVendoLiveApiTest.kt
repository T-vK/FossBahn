package de.openbahn.api

import de.openbahn.model.JourneySearchOptions
import de.openbahn.model.TransportProduct
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.time.LocalDateTime

/**
 * Live integration tests against int.bahn.de — run on the CI host JVM (no emulator).
 * Enable with RUN_LIVE_API_TESTS=true.
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
        val from = apiCall { client.searchLocations("Berlin Hbf", "de").firstOrNull() }
        val to = apiCall { client.searchLocations("München Hbf", "de").firstOrNull() }
        val origin = from ?: return@runBlocking
        val destination = to ?: return@runBlocking
        val journeys = apiCall {
            client.searchJourneys(
                origin,
                destination,
                JourneySearchOptions(products = setOf(TransportProduct.ICE)),
                LocalDateTime.now().plusHours(2),
            )
        }
        assertTrue(
            journeys.isNotEmpty(),
            "Expected journeys ${origin.name}→${destination.name}, got none",
        )
    }

    @Test
    fun searchJourneys_hamburgToBerlin_returnsConnections() = runBlocking {
        val from = apiCall { client.searchLocations("Hamburg Hbf", "de").firstOrNull() }
        val to = apiCall { client.searchLocations("Berlin Hbf", "de").firstOrNull() }
        val origin = from ?: return@runBlocking
        val destination = to ?: return@runBlocking
        val journeys = apiCall {
            client.searchJourneys(
                origin,
                destination,
                JourneySearchOptions(),
                LocalDateTime.now().plusHours(2),
            )
        }
        assertTrue(
            journeys.isNotEmpty(),
            "Expected journeys ${origin.name}→${destination.name}, got none",
        )
    }

    @Test
    fun departures_returnsEntries() = runBlocking {
        val station = apiCall { client.searchLocations("Berlin Hbf", "de").firstOrNull() }
            ?: return@runBlocking
        val deps = apiCall { client.departures(station) }
        assertTrue(deps.isNotEmpty(), "Expected departures at ${station.name}")
    }

    private suspend fun <T> apiCall(block: suspend () -> T): T = try {
        block()
    } catch (e: DbApiBlockedException) {
        assumeTrue(false, "Deutsche Bahn blocked this IP (OPS_BLOCKED): ${e.message}")
        error("unreachable")
    }
}
