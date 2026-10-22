package com.example

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import com.example.data.models.ActivityItem
import com.example.ui.components.FeedTopBar
import com.example.ui.components.FeedTabs
import com.example.ui.components.FeedBottomBar
import com.example.ui.components.FeedReferenceStatsRow
import com.example.ui.screens.ActivityScreen
import com.example.ui.theme.BlinkTheme
import com.example.viewmodel.MainTab
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real current-main composables with deterministic offline data; requires visual review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [35])
class BlinkMainAdaptiveVisualTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun homeNormal320() = renderHome(320, 1f)
    @Test fun home130Compact() = renderHome(320, 1.3f)
    @Test fun home150Compact() = renderHome(320, 1.5f)
    @Test fun home200Compact() = renderHome(320, 2f)
    @Test fun home200Standard() = renderHome(412, 2f)

    @Test fun notificationsNormal320() = renderNotifications(320, 1f)
    @Test fun notifications130Compact() = renderNotifications(320, 1.3f)
    @Test fun notifications150Compact() = renderNotifications(320, 1.5f)
    @Test fun notifications200Compact() = renderNotifications(320, 2f)
    @Test fun notifications200Standard() = renderNotifications(412, 2f)

    private fun renderHome(widthDp: Int,scale: Float) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f,scale)) {
                BlinkTheme(darkTheme=true) {
                    Box(Modifier.width(widthDp.dp).height(630.dp).background(Color.Black)
                        .testTag("main_home_capture")) {
                        Column {
                            FeedTopBar(
                                userAvatar = "",
                                hasUnreadNotifications = true,
                                unreadNotificationCount = 3,
                                onSearchClick = {},
                                onItemsClick = {},
                                onNotificationClick = {},
                                onMenuClick = {},
                                onProfileClick = {},
                                reserveStatusBar = false
                            )
                            FeedReferenceStatsRow(
                                worldRank = 18, coins = 1240,
                                onRankClick = {}, onCoinsClick = {}
                            )
                            FeedTabs(
                                selectedIndex = 0,
                                onForYouClick = {},
                                onFollowingClick = {},
                                onFilterClick = {}
                            )
                        }
                        FeedBottomBar(
                            currentTab = MainTab.HOME,
                            feedSubTab = 0,
                            onHomeClick = {}, onReelsClick = {},
                            onMarketClick = {}, onConnectClick = {},
                            onMessageClick = {}, isDark = true,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
        rule.onNodeWithText("BLINK").assertExists()
        rule.onNodeWithContentDescription("Open Items (includes Game)").assertExists()
        rule.onNodeWithTag("feed_tab_for_you").assertExists()
        rule.onNodeWithTag("feed_add_coins").assertExists()
        rule.activity.window.decorView.captureRoboImage(
            filePath = "src/test/screenshots/main_home_w${widthDp}_font${scale.toString().replace('.', '_')}.png"
        )
    }
    private fun renderNotifications(widthDp: Int,scale: Float) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f,scale)) {
                BlinkTheme(darkTheme=true) {
                    Box(Modifier.width(widthDp.dp).height(950.dp)
                        .background(Color.Black).testTag("main_notifications_capture")) {
                        ActivityScreen(
                            activities = listOf(
                                ActivityItem(
                                    id = "local-notice-1",
                                    user = "", avatar = "", action = "started following you",
                                    time = "1 hour ago", isUnread = true, previewText = "null"
                                )
                            ),
                            onBack = {}, onProfileClick = {}, onNotificationClick = {},
                            isDark = true, isConnected = false
                        )
                    }
                }
            }
        }
        rule.onNodeWithText("Notifications").assertExists()
        rule.activity.window.decorView.captureRoboImage(
            filePath = "src/test/screenshots/main_notifications_w${widthDp}_font${scale.toString().replace('.', '_')}.png"
        )
    }
}
