package de.openbahn.api

import de.openbahn.api.LiveApiTestSupport.assertJourneysNotEmpty
import de.openbahn.api.LiveApiTestSupport.apiCall
import de.openbahn.api.LiveApiTestSupport.findStation
import de.openbahn.api.LiveApiTestSupport.requireFullBahnId
import de.openbahn.model.JourneySearchOptions
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.time.LocalDateTime

/**
 * End-to-end journey search against live int.bahn.de (JVM only — no Android emulator).
 *
 * Mirrors the app flow: resolve stations via /orte → POST /angebote/fahrplan → parse results.
 *
 * Run locally (home/mobile IP usually works; GitHub Actions often gets OPS_BLOCKED):
 * ```
 * ./gradlew :core:api:testDebugUnitTest -PliveApi
 * ```
 */
@Tag("live-api")
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_API_TESTS", matches = "true")
class DbVendoLiveJourneyIntegrationTest {
    private val client = DbVendoClient()

    @Test
    fun hamburgHbfToBerlinHbf_fullFlowWithBahnLocationIds() = runBlocking {
        val hamburg = apiCall {
            client.findStation("Hamburg Hbf", LiveApiTestSupport.HAMBURG_HBF_EVA)
        } ?: error("Hamburg Hbf not found via /orte")
        val berlin = apiCall {
            client.findStation("Berlin Hbf", LiveApiTestSupport.BERLIN_HBF_EVA)
        } ?: error("Berlin Hbf not found via /orte")

        requireFullBahnId(hamburg, "origin")
        requireFullBahnId(berlin, "destination")

        val whenTime = LocalDateTime.now().plusHours(2)
        val journeys = apiCall {
            client.searchJourneys(hamburg, berlin, JourneySearchOptions(), whenTime)
        }
        assertJourneysNotEmpty(journeys, hamburg, berlin)

        val first = journeys.first()
        assertTrue(first.legs.isNotEmpty(), "Journey should contain at least one leg")
        val stopNames = first.legs.flatMap { leg ->
            listOf(leg.origin.name, leg.destination.name)
        }
        assertTrue(
            stopNames.any { it.contains("Hamburg", ignoreCase = true) } &&
                stopNames.any { it.contains("Berlin", ignoreCase = true) },
            "Expected Hamburg and Berlin in leg names, got: $stopNames",
        )
    }

    @Test
    fun hamburgHbfToBerlinHbf_requestUsesHaltIdsFromLocations() = runBlocking {
        val hamburg = apiCall {
            client.findStation("Hamburg Hbf", LiveApiTestSupport.HAMBURG_HBF_EVA)
        } ?: error("Hamburg Hbf not found")
        val berlin = apiCall {
            client.findStation("Berlin Hbf", LiveApiTestSupport.BERLIN_HBF_EVA)
        } ?: error("Berlin Hbf not found")

        val body = JourneyRequestBuilder.build(
            hamburg,
            berlin,
            JourneySearchOptions(),
            LocalDateTime.now().plusHours(2),
        )
        val ab = body["abfahrtsHalt"].toString().trim('"')
        val an = body["ankunftsHalt"].toString().trim('"')
        assertTrue(ab.startsWith("A=1@"), "abfahrtsHalt should use bahn lid, got: $ab")
        assertTrue(an.startsWith("A=1@"), "ankunftsHalt should use bahn lid, got: $an")
        assertTrue(ab.contains(LiveApiTestSupport.HAMBURG_HBF_EVA))
        assertTrue(an.contains(LiveApiTestSupport.BERLIN_HBF_EVA))
    }
}
