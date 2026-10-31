package com.example.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.example.data.models.UserProfile
import com.example.ui.theme.BlinkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regression: the new BLINK profile must never be followed by the retired
 * legacy cover/identity/trust UI. No server or real user data is used here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35])
class ProfileSingleSurfaceRegressionTest {
    @get:Rule val rule = createComposeRule()

    private fun renderProfile(isMe: Boolean, initialTab: Int = 0) {
        rule.setContent {
            BlinkTheme(darkTheme = false) {
                ProfileScreen(
                    profile = UserProfile(id = "", fullName = "Test Profile", username = "test_profile"),
                    isMe = isMe,
                    initialTab = initialTab,
                    userPosts = emptyList(),
                    likedPosts = emptyList(),
                    savedPosts = emptyList(),
                    userMarketItems = emptyList(),
                    onBack = {},
                    onEditProfileClick = {},
                    onDirectMessage = {},
                    onEndorseSkill = {},
                    onLikePost = {},
                    onCommentPost = {},
                    onBookmarkPost = {},
                    onSharePost = {},
                    onOptionsClick = {},
                    onProfileClick = {},
                    onMarketItemClick = {},
                    isDark = false
                )
            }
        }
    }

    @Test fun ownerSeesOneReferenceDashboardWithoutLegacyProfile() {
        renderProfile(isMe = true)
        rule.onAllNodesWithTag("blink_premium_profile_dashboard", useUnmergedTree = true)
            .assertCountEquals(1)
        rule.onAllNodesWithText("Test Profile", useUnmergedTree = true)
            .assertCountEquals(1)
        rule.onAllNodesWithText("More profile details & actions", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test fun ownerCanDeepLinkDirectlyToSavedSection() {
        renderProfile(isMe = true, initialTab = 7)
        rule.onNodeWithTag("profile_content_list").performScrollToIndex(1)
        rule.onNodeWithTag("premium_profile_tab_saved", useUnmergedTree = true)
            .assertIsSelected()
    }

    @Test fun ownerDirectLikedSectionStillWorks() {
        renderProfile(isMe = true, initialTab = 6)
        rule.onNodeWithTag("profile_content_list").performScrollToIndex(1)
        rule.onNodeWithTag("premium_profile_tab_liked", useUnmergedTree = true)
            .assertIsSelected()
    }

    @Test fun visitorInvalidOwnerTabFallsBackToPosts() {
        renderProfile(isMe = false, initialTab = 7)
        // Profile tabs are lazy below the dashboard; reveal them before asserting.
        rule.onNodeWithTag("profile_content_list").performScrollToIndex(1)
        rule.waitForIdle()
        rule.onNodeWithTag("premium_profile_tab_posts", useUnmergedTree = true)
            .assertIsSelected()
    }

    @Test fun visitorSeesOneReferenceDashboardWithoutLegacyProfile() {
        renderProfile(isMe = false)
        rule.onAllNodesWithTag("blink_premium_profile_dashboard", useUnmergedTree = true)
            .assertCountEquals(1)
        rule.onAllNodesWithText("Test Profile", useUnmergedTree = true)
            .assertCountEquals(1)
        rule.onAllNodesWithText("More profile details & actions", useUnmergedTree = true)
            .assertCountEquals(0)
    }
}
