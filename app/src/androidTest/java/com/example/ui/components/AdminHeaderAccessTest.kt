package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AdminHeaderAccessTest {
    @get:Rule val rule = createComposeRule()

    @Test fun nonAdminCannotSeeDashboardShortcut() {
        rule.setContent {
            MaterialTheme {
                FeedTopBar(
                    userAvatar = "",
                    hasUnreadNotifications = false,
                    onSearchClick = {},
                    isAdmin = false,
                    onAdminClick = {},
                    onItemsClick = {},
                    onNotificationClick = {},
                    onMenuClick = {},
                    onProfileClick = {},
                    reserveStatusBar = false
                )
            }
        }
        rule.onNodeWithTag("feed_admin_action").assertDoesNotExist()
    }

    @Test fun accessibilityFontScaleKeepsAdminControlUsableOnNarrowPhone() {
        var opened = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                MaterialTheme {
                    Box(Modifier.width(320.dp)) {
                        FeedTopBar(
                            userAvatar = "",
                            hasUnreadNotifications = false,
                            onSearchClick = {},
                            isAdmin = true,
                            onAdminClick = { opened = true },
                            onItemsClick = {},
                            onNotificationClick = {},
                            onMenuClick = {},
                            onProfileClick = {},
                            reserveStatusBar = false
                        )
                    }
                }
            }
        }
        rule.onNodeWithTag("feed_admin_action").performClick()
        rule.runOnIdle { assertTrue(opened) }
    }

    @Test fun adminSeesDashboardShortcutNextToSearch() {
        var opened = false
        rule.setContent {
            MaterialTheme {
                FeedTopBar(
                    userAvatar = "",
                    hasUnreadNotifications = false,
                    onSearchClick = {},
                    isAdmin = true,
                    onAdminClick = { opened = true },
                    onItemsClick = {},
                    onNotificationClick = {},
                    onMenuClick = {},
                    onProfileClick = {},
                    reserveStatusBar = false
                )
            }
        }
        rule.onNodeWithTag("feed_search_action").assertExists()
        rule.onNodeWithTag("feed_admin_action").performClick()
        rule.runOnIdle { assertTrue(opened) }
    }
}
