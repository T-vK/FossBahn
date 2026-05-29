package de.openbahn.api

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.openbahn.model.JourneySearchOptions
import de.openbahn.model.Location
import de.openbahn.model.TransportProduct
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/**
 * Live integration tests against the public bahn.de API (int.bahn.de).
 * Skipped when RUN_LIVE_API_TESTS is not set (CI sets it for the integration job).
 */
@RunWith(AndroidJUnit4::class)
class DbVendoApiIntegrationTest {
    private val client = DbVendoClient()

    @Test
    fun searchLocations_returnsBerlinHbf() = runBlocking {
        assumeLiveTestsEnabled()
        val results = client.searchLocations("Berlin Hbf", locale = "de")
        assertTrue(results.any { it.name.contains("Berlin", ignoreCase = true) })
    }

    @Test
    fun searchJourneys_returnsConnections() = runBlocking {
        assumeLiveTestsEnabled()
        val from = Location(id = "8011160", name = "Berlin Hbf")
        val to = Location(id = "8000261", name = "München Hbf")
        val journeys = client.searchJourneys(
            from,
            to,
            JourneySearchOptions(products = setOf(TransportProduct.ICE)),
            LocalDateTime.now().plusHours(2),
        )
        assertTrue("Expected at least one journey from Berlin to Munich", journeys.isNotEmpty())
    }

    @Test
    fun departures_returnsEntries() = runBlocking {
        assumeLiveTestsEnabled()
        val station = Location(id = "8011160", name = "Berlin Hbf")
        val deps = client.departures(station)
        assertTrue(deps.isNotEmpty())
    }

    private fun assumeLiveTestsEnabled() {
        assumeTrue(
            "Set RUN_LIVE_API_TESTS=true to run live API tests",
            System.getenv("RUN_LIVE_API_TESTS") == "true",
        )
    }
}
