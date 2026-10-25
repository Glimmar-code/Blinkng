package com.example.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.models.Story
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
                    selectedTab = currentTab,
                    onSelectTab = { actions += "tab:$it"; currentTab = it },
                    onAddStory = { actions += "new-story" },
                    onStoryClick = { actions += "story:${it.id}" },
                    onProfileClick = { actions += "profile:$it" },
                    onSearch = { actions += "search" },
                    onGame = { actions += "games" },
                    onNotifications = { actions += "notifications" },
                    onMenu = { actions += "menu" }
                )
            }
        }
        composeRule.onNodeWithText("B L I N K").assertExists()
        composeRule.onNodeWithText("Reels").assertExists()
        composeRule.onNodeWithText("Following").assertExists()
        composeRule.onNodeWithText("For You").assertExists()
        composeRule.onNodeWithText("Aisha").assertExists()
        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Following").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("search", "menu", "tab:Following"), actions)
        }
    }
}
