package com.example.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
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

    private fun renderProfile(isMe: Boolean) {
        rule.setContent {
            BlinkTheme(darkTheme = false) {
                ProfileScreen(
                    profile = UserProfile(id = "", fullName = "Test Profile", username = "test_profile"),
                    isMe = isMe,
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
