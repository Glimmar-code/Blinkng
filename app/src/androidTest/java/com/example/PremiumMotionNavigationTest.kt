package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.components.FeedBottomBar
import com.example.viewmodel.MainTab
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on the emulator with animation scales enabled by android-runtime-smoke.yml.
 * Verifies that navigation stays interactive throughout successive animated changes.
 */
@RunWith(AndroidJUnit4::class)
class PremiumMotionNavigationTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun rapidTabChangesPreserveCorrectSelectedDestination() {
        composeRule.setContent {
            var tab by remember { mutableStateOf(MainTab.HOME) }
            var subTab by remember { mutableIntStateOf(0) }
            MaterialTheme {
                FeedBottomBar(
                    currentTab = tab,
                    feedSubTab = subTab,
                    isDark = true,
                    onHomeClick = { tab = MainTab.HOME; subTab = 0 },
                    onReelsClick = { tab = MainTab.HOME; subTab = 1 },
                    onMarketClick = { tab = MainTab.MARKET; subTab = 0 },
                    onConnectClick = { tab = MainTab.HOME; subTab = 2 },
                    onMessageClick = { tab = MainTab.MESSAGES; subTab = 0 },
                )
            }
        }

        composeRule.onNodeWithTag("feed_nav_home").assertIsSelected()
        composeRule.onNodeWithTag("feed_nav_reels").performClick()
        composeRule.onNodeWithTag("feed_nav_connect").performClick()
        composeRule.onNodeWithTag("feed_nav_market").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("feed_nav_market").assertIsSelected()
        composeRule.onNodeWithTag("feed_nav_home").assertIsNotSelected()

        composeRule.onNodeWithTag("feed_nav_message").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("feed_nav_message").assertIsSelected()
        composeRule.onNodeWithTag("feed_nav_market").assertIsNotSelected()
    }
}
