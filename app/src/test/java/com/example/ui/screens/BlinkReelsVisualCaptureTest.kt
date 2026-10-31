package com.example.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.models.FeedPost
import com.example.data.supabase.BlinkFeaturedReel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** True Compose frames, isolated from Supabase and video decoder timing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [35])
class BlinkReelsVisualCaptureTest {
    @get:Rule val rule = createComposeRule()

    private val highlights = listOf(
        BlinkFeaturedReel(
            reel = FeedPost(
                id = "staging-reel-1", author = "Campus Creator", authorAvatar = "",
                timeAgo = "Now", text = "Campus moment",
                likes = 10, commentsCount = 2, sharesCount = 1,
                isReel = true, videoUrl = "https://example.invalid/staging-only.mp4"
            ),
            username = "campus_creator", displayName = "Campus Creator",
            avatarUrl = "", coverUrl = "", caption = "A highlight from campus",
            windowViews = 240, nextRefresh = "2026-11-01T00:00:00Z"
        ),
        BlinkFeaturedReel(
            reel = FeedPost(
                id = "staging-reel-2", author = "Second Creator", authorAvatar = "",
                timeAgo = "Now", text = "Campus performance",
                likes = 16, commentsCount = 3, sharesCount = 2,
                isReel = true, videoUrl = "https://example.invalid/staging-only-2.mp4"
            ),
            username = "second_creator", displayName = "Second Creator",
            avatarUrl = "", coverUrl = "", caption = "Second highlight",
            windowViews = 90, nextRefresh = "2026-11-01T00:00:00Z"
        )
    )

    private fun render(isDark: Boolean, empty: Boolean) {
        rule.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().background(if (isDark) Color.Black else Color.White)) {
                    ReelsReferenceChrome(
                        stories = emptyList(), userAvatar = "", currentUsername = "staging",
                        isDark = isDark, hasUnreadNotifications = true,
                        selectedTab = "Reels", onSelectTab = {},
                        onAddStory = {}, onStoryClick = {}, onProfileClick = {},
                        onSearch = {}, onGame = {}, onNotifications = {}, onMenu = {},
                        featuredReels = if (empty) emptyList() else highlights,
                        onFeaturedReelClick = {}, onCreateReel = {},
                        autoSlideEnabled = false
                    )
                }
            }
        }
    }

    @Test fun populatedDark() {
        render(isDark = true, empty = false)
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/reels-reference-populated-dark.png")
    }

    @Test fun emptyDark() {
        render(isDark = true, empty = true)
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/reels-reference-empty-dark.png")
    }

    @Test fun populatedLight() {
        render(isDark = false, empty = false)
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/reels-reference-populated-light.png")
    }
}
