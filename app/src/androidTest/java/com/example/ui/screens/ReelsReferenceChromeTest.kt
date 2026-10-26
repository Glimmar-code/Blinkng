package com.example.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.models.Story
import com.example.data.models.FeedPost
import com.example.data.supabase.BlinkFeaturedReel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Preview chrome keeps the real Story/Search/Menu routes and three reference tabs. */
@RunWith(AndroidJUnit4::class)
class ReelsReferenceChromeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun previewShowsReferenceNavigationAndRoutesRealActions() {
        var currentTab = "Reels"
        val actions = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                ReelsReferenceChrome(
                    stories = listOf(Story(id = "s1", username = "aisha", avatar = "", displayName = "Aisha")),
                    userAvatar = "",
                    currentUsername = "current",
                    isDark = false,
                    hasUnreadNotifications = true,
                    selectedTab = currentTab,
                    onSelectTab = { actions += "tab:$it"; currentTab = it },
                    onAddStory = { actions += "new-story" },
                    onStoryClick = { actions += "story:${it.id}" },
                    onProfileClick = { actions += "profile:$it" },
                    onSearch = { actions += "search" },
                    onGame = { actions += "games" },
                    onNotifications = { actions += "notifications" },
                    onMenu = { actions += "menu" },
                    featuredReels = listOf(BlinkFeaturedReel(
                        reel = FeedPost(
                            id = "reel-1", author = "Aisha", authorAvatar = "",
                            timeAgo = "Now", text = "Campus Vibes",
                            likes = 10, commentsCount = 3, sharesCount = 2,
                            isReel = true, videoUrl = "https://example.com/video.mp4"
                        ),
                        username = "aisha", displayName = "Aisha",
                        avatarUrl = "", coverUrl = "", caption = "Campus Vibes",
                        windowViews = 100, nextRefresh = "2026-10-27T00:00:00Z"
                    )),
                    onFeaturedReelClick = { actions += "featured:$it" },
                    autoSlideEnabled = false
                )
            }
        }
        composeRule.onNodeWithText("B L I N K").assertExists()
        composeRule.onNodeWithText("Reels").assertExists()
        composeRule.onNodeWithText("Following").assertExists()
        composeRule.onNodeWithText("For You").assertExists()
        composeRule.onNodeWithText("Aisha").assertExists()
        composeRule.onNodeWithText("Campus Vibes").performClick()
        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Following").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("featured:reel-1", "search", "menu", "tab:Following"), actions)
        }
    }
}
