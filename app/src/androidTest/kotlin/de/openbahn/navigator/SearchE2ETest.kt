package de.openbahn.navigator

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI e2e with a **fake** [de.openbahn.navigator.ui.search.FakeJourneySearchRepository]
 * (see [OpenBahnTestApplication]). Does not call bahn.de — see [SearchLiveE2ETest] for live API.
 */
@RunWith(AndroidJUnit4::class)
class SearchE2ETest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun searchScreen_displaysFromAndToFields() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search_from").assertIsDisplayed()
        composeRule.onNodeWithTag("search_to").assertIsDisplayed()
        composeRule.onNodeWithTag("search_button").assertIsDisplayed()
    }

    @Test
    fun searchScreen_displaysTitle() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search_screen_title").assertIsDisplayed()
    }

    @Test
    fun searchConnections_showsJourneyResult() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search_from").performTextInput("Hamburg Hbf")
        waitForTag("location_suggestion_8002549")
        composeRule.onNodeWithTag("location_suggestion_8002549").performClick()
        composeRule.onNodeWithTag("search_to").performTextInput("Berlin Hbf")
        waitForTag("location_suggestion_8011160")
        composeRule.onNodeWithTag("location_suggestion_8011160").performClick()
        composeRule.onNodeWithTag("search_button").performClick()
        waitForTag("journey_card")
        composeRule.onNodeWithTag("journey_card").assertIsDisplayed()
        composeRule.onNodeWithTag("search_results").assertIsDisplayed()
    }

    @Test
    fun app_launchesWithCorrectPackage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assert(context.packageName.startsWith("de.openbahn.navigator"))
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
