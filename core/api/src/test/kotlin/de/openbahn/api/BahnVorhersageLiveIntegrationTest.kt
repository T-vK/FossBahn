package de.openbahn.api

import de.openbahn.api.LiveApiTestSupport.apiCall
import de.openbahn.api.LiveApiTestSupport.assertJourneysNotEmpty
import de.openbahn.api.LiveApiTestSupport.assumeBlockedByResponseBody
import de.openbahn.api.LiveApiTestSupport.berlinDeparturePlusHours
import de.openbahn.api.LiveApiTestSupport.findStation
import de.openbahn.api.LiveApiTestSupport.parseFahrplanResponse
import de.openbahn.api.LiveApiTestSupport.requireFullBahnId
import de.openbahn.model.JourneySearchOptions
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * Live check: OpenBahn-shaped FPTF payload against bahnvorhersage mobile v2.
 *
 * Run: `RUN_LIVE_API_TESTS=true ./gradlew :core:api:test --tests "*.BahnVorhersageLiveIntegrationTest"`
 */
@Tag("live-api")
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_API_TESTS", matches = "true")
class BahnVorhersageLiveIntegrationTest {
    private val db = DbVendoClient()
    private val predictor = BahnVorhersageClient()

    @Test
    fun mobileV2_hamburgBerlin_returnsMlPredictions() = runBlocking {
        val hamburg = apiCall {
            db.findStation("Hamburg Hbf", LiveApiTestSupport.HAMBURG_HBF_EVA)
        } ?: error("Hamburg Hbf not found")
        val berlin = apiCall {
            db.findStation("Berlin Hbf", LiveApiTestSupport.BERLIN_HBF_EVA)
        } ?: error("Berlin Hbf not found")
        requireFullBahnId(hamburg, "origin")
        requireFullBahnId(berlin, "destination")

        val whenTime = berlinDeparturePlusHours(3)
        val raw = apiCall {
            db.postFahrplan(hamburg, berlin, JourneySearchOptions(), whenTime)
        }
        assumeBlockedByResponseBody(raw)
        val journeys = parseFahrplanResponse(raw)
        assertJourneysNotEmpty(journeys, hamburg, berlin, raw)

        val journey = journeys.first { j -> j.legs.any { !it.isWalking } }
        val trips = buildTripRoutesForRating(journey)
        assertTrue(trips.isNotEmpty(), "trip stopovers required for rating")

        val rated = predictor.rateJourney(journey, tripRoutes = trips)
        assertFalse(
            rated.punctualityIsEstimate,
            "expected ML rating from bahnvorhersage.de, got heuristic-only",
        )
        assertTrue(rated.stopTimeliness.any { !it.isEstimate })
    }
}
