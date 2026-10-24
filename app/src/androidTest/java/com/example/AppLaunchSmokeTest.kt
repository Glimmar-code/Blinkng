package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLaunchSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun signedOutColdLaunchShowsApprovedWelcomeSurface() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule
                .onAllNodesWithText("Welcome to BLINK")
                // A first-install app may still be attaching the Compose root.
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        // The welcome is deliberately scrollable on compact/large-font devices.
        // Test the actual Activity route and each accessible button, rather than
        // the previous dark screen's now-removed semantics and test tags.
        listOf(
            "onboarding_signin_btn",
            "onboarding_signup_btn",
            "onboarding_google_btn",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }
    }
}
