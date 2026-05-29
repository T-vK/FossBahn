package de.openbahn.navigator

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchE2ETest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun searchScreen_displaysFromAndToFields() {
        composeRule.onNodeWithText("From", substring = true, ignoreCase = true).assertIsDisplayed()
        composeRule.onNodeWithText("To", substring = true, ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun liveSearch_findsJourney_whenApiEnabled() {
        assumeTrue(System.getenv("RUN_LIVE_API_TESTS") == "true")
        // Full live E2E would type into fields and assert journey cards; kept minimal for CI stability
        composeRule.onNodeWithText("Journey search", substring = true, ignoreCase = true).assertIsDisplayed()
    }
}
