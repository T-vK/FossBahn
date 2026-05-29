package de.openbahn.api

import de.openbahn.api.LiveApiTestSupport.apiCall
import de.openbahn.api.LiveApiTestSupport.assertJourneysNotEmpty
import de.openbahn.api.LiveApiTestSupport.findStation
import de.openbahn.api.LiveApiTestSupport.requireFullBahnId
import de.openbahn.api.mapper.JourneyResponseParser
import de.openbahn.model.JourneySearchOptions
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * Live Hamburg Hbf → Berlin Hbf at a concrete departure time (Europe/Berlin).
 *
 * This is the closest automated check to what the Search screen does — same client,
 * same parser, same halt ids — but **not** the Compose UI layer.
 *
 * Run: `.github/scripts/run-live-api-tests.sh`
 */
@Tag("live-api")
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_API_TESTS", matches = "true")
class DbVendoLiveJourneyIntegrationTest {
    private val client = DbVendoClient()

    @Test
    fun hamburgHbfToBerlinHbf_atBerlinTime_matchesUiPipeline() = runBlocking {
        val hamburg = apiCall {
            client.findStation("Hamburg Hbf", LiveApiTestSupport.HAMBURG_HBF_EVA)
        } ?: error("Hamburg Hbf not found via /orte")
        val berlin = apiCall {
            client.findStation("Berlin Hbf", LiveApiTestSupport.BERLIN_HBF_EVA)
        } ?: error("Berlin Hbf not found via /orte")

        requireFullBahnId(hamburg, "origin")
        requireFullBahnId(berlin, "destination")

        val whenTime = LiveApiTestSupport.berlinDeparturePlusHours(3)
        val raw = apiCall {
            client.postFahrplan(hamburg, berlin, JourneySearchOptions(), whenTime)
        }
        val journeys = JourneyResponseParser.parse(raw)
        assertJourneysNotEmpty(journeys, hamburg, berlin, raw)

        val stopNames = journeys.first().legs.flatMap { listOf(it.origin.name, it.destination.name) }
        assertTrue(
            stopNames.any { it.contains("Hamburg", ignoreCase = true) },
            "Expected Hamburg in legs, got: $stopNames",
        )
        assertTrue(
            stopNames.any { it.contains("Berlin", ignoreCase = true) },
            "Expected Berlin in legs, got: $stopNames",
        )
    }
}
