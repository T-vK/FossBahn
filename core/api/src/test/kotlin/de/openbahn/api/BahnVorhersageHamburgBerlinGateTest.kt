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
 * Release gate: real Hamburg Hbf → Berlin Hbf search results must rate via mobile v2 (HTTP 200 + ML).
 *
 * CI: set `RUN_LIVE_API_TESTS=true` on a runner that can reach int.bahn.de and bahnvorhersage.de.
 */
@Tag("live-api")
@Tag("release-gate")
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_API_TESTS", matches = "true")
class BahnVorhersageHamburgBerlinGateTest {
    private val db = DbVendoClient()
    private val predictor = BahnVorhersageClient()

    @Test
    fun allParsedJourneys_receiveMlRatings_notHeuristicOnly() = runBlocking {
        val hamburg = apiCall {
            db.findStation("Hamburg Hbf", LiveApiTestSupport.HAMBURG_HBF_EVA)
        } ?: error("Hamburg Hbf not found")
        val berlin = apiCall {
            db.findStation("Berlin Hbf", LiveApiTestSupport.BERLIN_HBF_EVA)
        } ?: error("Berlin Hbf not found")
        requireFullBahnId(hamburg, "origin")
        requireFullBahnId(berlin, "destination")

        val whenTime = berlinDeparturePlusHours(2)
        val raw = apiCall { db.postFahrplan(hamburg, berlin, JourneySearchOptions(), whenTime) }
        assumeBlockedByResponseBody(raw)
        val journeys = parseFahrplanResponse(raw)
        assertJourneysNotEmpty(journeys, hamburg, berlin, raw)

        val failures = mutableListOf<String>()
        for (journey in journeys.take(8)) {
            val railLegs = journey.legs.count { !it.isWalking }
            if (railLegs == 0) continue
            val trips = buildTripRoutesForRating(journey)
            val rated = predictor.rateJourney(journey, tripRoutes = trips)
            if (rated.punctualityIsEstimate || rated.stopTimeliness.none { !it.isEstimate }) {
                failures += "${journey.id}: railLegs=$railLegs transfers=${journey.transfers} heuristic-only"
            }
        }
        assertTrue(
            failures.isEmpty(),
            "Expected ML ratings for every Hamburg→Berlin journey. Failures:\n${failures.joinToString("\n")}",
        )
    }
}
