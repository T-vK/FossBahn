package de.openbahn.navigator

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
    fun app_launchesWithCorrectPackage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assert(context.packageName.startsWith("de.openbahn.navigator"))
    }
}
