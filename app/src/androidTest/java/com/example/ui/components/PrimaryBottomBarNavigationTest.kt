package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.viewmodel.MainTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Instrumentation regression: all five bottom destinations must remain tappable. */
class PrimaryBottomBarNavigationTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun eachPrimaryTabInvokesItsOwnDestination() {
        val tapped = mutableListOf<String>()
        rule.setContent {
            MaterialTheme {
                FeedBottomBar(
                    currentTab = MainTab.HOME,
                    feedSubTab = 0,
                    onHomeClick = { tapped += "home" },
                    onReelsClick = { tapped += "reels" },
                    onMarketClick = { tapped += "market" },
                    onConnectClick = { tapped += "connect" },
                    onMessageClick = { tapped += "message" },
                    isDark = true
                )
            }
        }

        listOf("home", "reels", "market", "connect", "message").forEach { destination ->
            rule.onNodeWithTag("feed_nav_$" + "{destination}").performClick()
        }
        rule.runOnIdle {
            assertEquals(listOf("home", "reels", "market", "connect", "message"), tapped)
        }
    }
}
