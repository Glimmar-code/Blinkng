package com.example

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.components.FeedReferenceStatsRow
import com.example.ui.components.FeedTabs
import com.example.ui.components.FeedTopBar
import com.example.ui.theme.BlinkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Regression test for the reference Home chrome on Android in both app themes. */
@RunWith(AndroidJUnit4::class)
class FeedReferenceThemeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun lightThemeFeedTabsRemainSelectableAndActionsWork() = verifyTabs(dark = false)
    @Test fun darkThemeFeedTabsRemainSelectableAndActionsWork() = verifyTabs(dark = true)

    private fun verifyTabs(dark: Boolean) {
        composeRule.setContent {
            var selected by remember { mutableIntStateOf(0) }
            BlinkTheme(darkTheme = dark) {
                FeedTabs(
                    selectedIndex = selected,
                    onForYouClick = { selected = 0 },
                    onFollowingClick = { selected = 1 },
                    onFilterClick = {},
                    onRefreshClick = {},
                    modifier = Modifier.width(360.dp)
                )
            }
        }

        composeRule.onNodeWithTag("feed_tab_for_you").assertIsSelected()
        composeRule.onNodeWithTag("feed_tab_following").assertIsNotSelected()
        composeRule.onNodeWithTag("feed_tab_following").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("feed_tab_following").assertIsSelected()
        composeRule.onNodeWithTag("feed_tab_for_you").assertIsNotSelected()
        // Refresh and filters are deliberately not visible in the supplied
        // reference. Pull-to-refresh remains available on the actual Home feed.
        composeRule.onNodeWithTag("feed_refresh_action").assertDoesNotExist()
        composeRule.onNodeWithTag("feed_filter_action").assertDoesNotExist()
    }

    @Test fun realRankAndCoinsCardsUseAccountValuesAndOpenActions() {
        var rankClicked = 0
        var coinsClicked = 0
        composeRule.setContent {
            BlinkTheme(darkTheme = false) {
                FeedReferenceStatsRow(
                    worldRank = 248,
                    coins = 1250,
                    onRankClick = { rankClicked++ },
                    onCoinsClick = { coinsClicked++ },
                    modifier = Modifier.width(360.dp)
                )
            }
        }
        composeRule.onNodeWithText("#248").assertIsDisplayed()
        composeRule.onNodeWithText("1,250").assertIsDisplayed()
        composeRule.onNodeWithTag("feed_world_rank_card").performClick()
        composeRule.onNodeWithTag("feed_add_coins").performClick()
        composeRule.runOnIdle {
            assertEquals(1, rankClicked)
            assertEquals(1, coinsClicked)
        }
    }

    @Test fun compactHomeHeaderKeepsTitleAndFourActionsInLightMode() {
        composeRule.setContent {
            BlinkTheme(darkTheme = false) {
                FeedTopBar(
                    userAvatar = "",
                    hasUnreadNotifications = true,
                    unreadNotificationCount = 27,
                    onSearchClick = {},
                    onItemsClick = {},
                    onNotificationClick = {},
                    onMenuClick = {},
                    onProfileClick = {},
                    reserveStatusBar = false,
                    modifier = Modifier.width(360.dp)
                )
            }
        }

        composeRule.onNodeWithText("BLINK").assertIsDisplayed()
        composeRule.onNodeWithTag("feed_search_action").assertIsDisplayed()
        composeRule.onNodeWithTag("feed_items_action").assertIsDisplayed()
        composeRule.onNodeWithTag("feed_notification_action").assertIsDisplayed()
        composeRule.onNodeWithTag("feed_menu_action").assertIsDisplayed()
    }
}
