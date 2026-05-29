package de.openbahn.navigator

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Live journey search against bahn.de (requires network, may be skipped if OPS_BLOCKED).
 *
 * Default CI uses [OpenBahnTestApplication] with a fake API. Run this test manually:
 *
 * ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunner=de.openbahn.navigator.OpenBahnLiveTestRunner \
 *   -Pandroid.testInstrumentationRunnerArguments.runLiveSearchE2e=true \
 *   -Pandroid.testInstrumentationRunnerArguments.class=de.openbahn.navigator.SearchLiveE2ETest
 */
@RunWith(AndroidJUnit4::class)
class SearchLiveE2ETest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun assumeLiveSearchEnabled() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Set -Pandroid.testInstrumentationRunnerArguments.runLiveSearchE2e=true",
            args.getString("runLiveSearchE2e") == "true",
        )
    }

    @Test
    fun searchConnections_hamburgToBerlin_showsJourneyFromApi() {
        composeRule.waitForIdle()
        pickStation("search_from", "Hamburg Hbf")
        pickStation("search_to", "Berlin Hbf")
        composeRule.onNodeWithTag("search_button").performClick()
        composeRule.waitUntil(90_000) {
            runCatching {
                composeRule.onNodeWithTag("journey_card").assertExists()
            }.isSuccess
        }
        composeRule.onNodeWithTag("journey_card").assertIsDisplayed()
        composeRule.onNodeWithTag("search_results").assertIsDisplayed()
    }

    private fun pickStation(fieldTag: String, stationName: String) {
        composeRule.onNodeWithTag(fieldTag).performTextInput(stationName)
        composeRule.waitUntil(30_000) {
            runCatching {
                composeRule.onNodeWithText(stationName, substring = true).assertExists()
            }.isSuccess
        }
        composeRule.onNodeWithText(stationName, substring = true).performClick()
    }
}
