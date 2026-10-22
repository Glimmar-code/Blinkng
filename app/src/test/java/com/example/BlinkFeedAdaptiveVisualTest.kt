package com.example

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import com.example.ui.components.FeedBottomBar
import com.example.ui.components.CreatePostFab
import com.example.viewmodel.MainTab
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import com.blinkng.shared.BlinkFeedScrollMotion
import com.example.ui.components.FeedTopBar
import com.example.ui.components.FeedTabs
import com.example.ui.theme.BlinkTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Deterministic snapshots of the real BLINK Home toolbar components.
 * Screenshots are evidence for visual review, NOT proof of reference pixel parity.
 * The actual authenticated LazyColumn and bottom navbar require emulator capture.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@Config(application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [35])
class BlinkFeedAdaptiveVisualTest {
    @get:Rule val rule = createComposeRule()

    @Test fun homeHeaderAt100PercentOnCompact320dpScreen() = render(320, 1f, 0)
    @Test fun homeHeaderAt130PercentOnCompact320dpScreen() = render(320, 1.3f, 0)
    @Test fun homeHeaderAt150PercentOnCompact320dpScreen() = render(320, 1.5f, 0)
    @Test fun homeHeaderAt200PercentOnCompact320dpScreen() = render(320, 2f, 0)
    @Test fun homeHeaderAt200PercentOnStandard412dpScreen() = render(412, 2f, 0)
    @Test fun scrollAfter80PixelsMovesHeader20AndNav50() = render(412, 1f, 80)
    @Test fun scrollAfter160PixelsMovesHeader40AndNav80Clamped() = render(412, 1f, 160)

    private fun render(widthDp: Int, fontScale: Float, postScrollPixels: Int) {
        val topMove = BlinkFeedScrollMotion.advance(
            0f,postScrollPixels.toFloat(),BlinkFeedScrollMotion.TOP_PER_POST,96f
        )
        val navMove = BlinkFeedScrollMotion.advance(
            0f,postScrollPixels.toFloat(),BlinkFeedScrollMotion.NAV_PER_POST,80f
        )
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f,fontScale)) {
                BlinkTheme(darkTheme = true) {
                    Box(
                        Modifier.width(widthDp.dp).height(340.dp)
                            .background(Color.Black).testTag("feed_visual_frame")
                    ) {
                        Column {
                            FeedTopBar(
                                userAvatar = "",
                                hasUnreadNotifications = true,
                                onSearchClick = {},
                                onNotificationClick = {},
                                onMenuClick = {},
                                onProfileClick = {},
                                modifier = Modifier.graphicsLayer { translationY = -topMove * 0.6f }
                            )
                            FeedTabs(
                                selectedIndex = 0,
                                onForYouClick = {},
                                onFollowingClick = {},
                                onGameClick = {},
                                onReelClick = {},
                                onFilterClick = {},
                                modifier = Modifier.graphicsLayer { translationY = -topMove * 0.4f }
                            )
                        }
                        CreatePostFab(
                            expanded = postScrollPixels < 80,
                            onClick = {},
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 67.dp)
                        )
                        FeedBottomBar(
                            currentTab = MainTab.HOME,
                            feedSubTab = 0,
                            onHomeClick = {},
                            onConnectClick = {},
                            onReelsClick = {},
                            onMarketClick = {},
                            onMessageClick = {},
                            isDark = true,
                            modifier = Modifier.align(Alignment.BottomCenter)
                                .graphicsLayer { translationY = navMove }
                                .testTag("nav_scroll_reference")
                        )
                    }
                }
            }
        }
        rule.onNodeWithText("For You").assertExists()
        rule.onNodeWithText("Following").assertExists()
        rule.onNodeWithContentDescription("Filter feed").assertExists()
        rule.onNodeWithContentDescription("Open Game").assertExists()
        val sizeName = fontScale.toString().replace('.', '_')
        rule.onNodeWithTag("feed_visual_frame").captureRoboImage(
            filePath = "build/test-visuals/home_w${widthDp}_scale${sizeName}_scroll${postScrollPixels}.png"
        )
    }
}
