package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLaunchSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun signedOutColdLaunchShowsWelcomeSurface() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule
                .onAllNodesWithText("Connect. Share. Discover.")
                // The first Compose root may attach after the Activity resumes.
                // Keep waiting until the deadline while still requiring the welcome UI.
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        composeRule.onNodeWithTag("welcome_login").assertIsDisplayed()
        composeRule.onNodeWithTag("welcome_create_account").assertIsDisplayed()
        composeRule.onNodeWithTag("welcome_google").assertIsDisplayed()
    }
}
