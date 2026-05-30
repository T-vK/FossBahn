package de.openbahn.api

import de.openbahn.api.LiveApiTestSupport.apiCall
import de.openbahn.api.LiveApiTestSupport.findStation
import de.openbahn.api.mapper.JourneyResponseParser
import de.openbahn.model.JourneySearchOptions
import de.openbahn.model.maxDelayMinutes
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.time.LocalDateTime

/**
 * Verifies delay + cancellation handling for two documented scenarios on 2026-05-30:
 *
 * 1. ICE 603  Hamburg 13:49 → Berlin 16:20 (delayed dep 13:56, arr 16:42)
 * 2. FLX 1247 Hamburg 17:11 → Berlin 19:47 (cancelled)
 *
 * Run fixtures (offline): `./scripts/verify-delay-scenarios.sh`
 * Run live API too:        `./scripts/verify-delay-scenarios.sh --live`
 */
@Tag("delay-scenario")
class DelayScenarioMay302026VerificationTest {
    private val client = DbVendoClient()

    @Test
    fun fixture_ice603_parsesDepartureAndArrivalDelays() {
        DelayScenarioDebug.banner("FIXTURE: ICE 603 (delayed)")
        val text = readFixture("/dbweb-scenario-ice603-delay.json")
        val journey = JourneyResponseParser.parse(text).journeys.single()
        DelayScenarioDebug.printJourney("after parse", journey)

        val leg = journey.legs.single()
        assertEquals("ICE 603", leg.lineName)
        assertEquals("13:49", DelayScenarioDebug.clock(leg.origin.scheduledTime))
        assertEquals("13:56", DelayScenarioDebug.clock(leg.origin.prognosedTime!!))
        assertEquals(7, leg.origin.delayMinutes)
        assertEquals("16:20", DelayScenarioDebug.clock(leg.destination.scheduledTime))
        assertEquals("16:42", DelayScenarioDebug.clock(leg.destination.prognosedTime!!))
        assertEquals(22, leg.destination.delayMinutes)
        println("✓ ICE 603 fixture: delays match expected +7 / +22 min")
    }

    @Test
    fun fixture_flx1247_parsesCancellationSignals() {
        DelayScenarioDebug.banner("FIXTURE: FLX 1247 (cancelled)")
        val text = readFixture("/dbweb-scenario-flx1247-cancelled.json")
        val journey = JourneyResponseParser.parse(text).journeys.single()
        DelayScenarioDebug.printJourney("after parse", journey)

        val leg = journey.legs.single()
        assertEquals("FLX 1247", leg.lineName)
        assertEquals("17:11", DelayScenarioDebug.clock(leg.origin.scheduledTime))
        assertTrue(
            leg.origin.cancelled || leg.destination.cancelled ||
                journey.remarks.any { it.contains("fällt", ignoreCase = true) },
            "Expected cancellation on FLX 1247 (stop flag or journey remark)",
        )
        println("✓ FLX 1247 fixture: cancellation detected")
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_DELAY_SCENARIO_VERIFY", matches = "true")
    fun live_may302026_hamburgBerlin_ice603_and_flx1247() = runBlocking {
        DelayScenarioDebug.banner("LIVE API: Hamburg Hbf → Berlin Hbf (2026-05-30)")
        val hamburg = apiCall {
            client.findStation("Hamburg Hbf", LiveApiTestSupport.HAMBURG_HBF_EVA)
        } ?: error("Hamburg Hbf not found")
        val berlin = apiCall {
            client.findStation("Berlin Hbf", LiveApiTestSupport.BERLIN_HBF_EVA)
        } ?: error("Berlin Hbf not found")

        val whenTime = LocalDateTime.of(2026, 5, 30, 12, 0)
        println("Stations: ${hamburg.name} (${hamburg.id.take(20)}…) → ${berlin.name}")
        println("Search time: $whenTime (finds afternoon connections)")

        val searchResult = apiCall {
            client.searchJourneys(hamburg, berlin, JourneySearchOptions(), whenTime)
        }
        println("Search returned ${searchResult.journeys.size} connection(s)")
        searchResult.journeys.take(8).forEach { j ->
            val leg = j.legs.firstOrNull() ?: return@forEach
            println(
                "  • ${leg.lineName} dep ${DelayScenarioDebug.clock(leg.origin.scheduledTime)}" +
                    " delay=${leg.origin.delayMinutes ?: 0} token=${j.refreshToken != null}",
            )
        }

        val enriched = apiCall {
            client.enrichJourneysWithRealtime(searchResult.journeys, hamburg, berlin)
        }

        verifyIce603(enriched)
        verifyFlx1247(enriched)
    }

    private fun verifyIce603(journeys: List<de.openbahn.model.Journey>) {
        DelayScenarioDebug.banner("CHECK: ICE 603 @ 13:49")
        val ice = DelayScenarioDebug.findByLineAndDeparture(journeys, "ICE 603", "13:49")
            ?: journeys.firstOrNull { it.legs.any { l -> l.lineName?.contains("ICE 603") == true } }
        if (ice == null) {
            println("⚠ ICE 603 not in search results (API may no longer list this historical connection)")
            return
        }
        DelayScenarioDebug.printJourney("ICE 603 enriched", ice)
        val leg = ice.legs.first { it.lineName?.contains("ICE 603") == true }
        val depDelay = leg.origin.delayMinutes ?: 0
        val arrDelay = leg.destination.delayMinutes ?: 0
        assertTrue(
            depDelay >= 5 || DelayScenarioDebug.clock(leg.origin.prognosedTime ?: "") == "13:56",
            "Expected ~7 min departure delay (got $depDelay min, prognosed ${leg.origin.prognosedTime})",
        )
        assertTrue(
            arrDelay >= 15 || DelayScenarioDebug.clock(leg.destination.prognosedTime ?: "") >= "16:42",
            "Expected ~22 min arrival delay (got $arrDelay min)",
        )
        println("✓ ICE 603 live: delays visible (dep=$depDelay min, arr=$arrDelay min)")
    }

    private fun verifyFlx1247(journeys: List<de.openbahn.model.Journey>) {
        DelayScenarioDebug.banner("CHECK: FLX 1247 @ 17:11")
        val flx = DelayScenarioDebug.findByLineAndDeparture(journeys, "FLX 1247", "17:11")
            ?: journeys.firstOrNull { it.legs.any { l -> l.lineName?.contains("FLX 1247") == true } }
        if (flx == null) {
            println("⚠ FLX 1247 not in search results")
            return
        }
        DelayScenarioDebug.printJourney("FLX 1247 enriched", flx)
        val leg = flx.legs.first { it.lineName?.contains("FLX 1247") == true }
        val cancelled = leg.origin.cancelled || leg.destination.cancelled
        val remarkCancel = flx.remarks.any {
            it.contains("fällt", ignoreCase = true) || it.contains("ausfall", ignoreCase = true)
        }
        assertTrue(
            cancelled || remarkCancel,
            "Expected FLX 1247 cancellation (stop.cancelled=$cancelled remarks=$remarkCancel)",
        )
        println("✓ FLX 1247 live: cancellation detected")
    }

    private fun readFixture(path: String): String =
        checkNotNull(javaClass.getResource(path)) { "Missing fixture $path" }.readText()
}
