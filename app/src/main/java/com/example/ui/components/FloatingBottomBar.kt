package com.example.ui.components

import android.app.Activity
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.BlinkStoreActivity
import com.example.R
import com.example.ui.theme.BlinkElevation
import com.example.ui.theme.BlinkMotion
import com.example.ui.theme.BlinkThemeTokens
import com.example.viewmodel.MainTab

private enum class FeedBottomDestination {
    HOME, CONNECT, LEADERBOARD, MARKET, MESSAGE, STORE
}

private data class FeedBottomItem(
    val destination: FeedBottomDestination,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector,
    val label: String
)

private val feedBottomItems = listOf(
    FeedBottomItem(FeedBottomDestination.HOME, Icons.Filled.Home, Icons.Outlined.Home, "Home"),
    FeedBottomItem(FeedBottomDestination.CONNECT, Icons.Filled.People, Icons.Outlined.People, "Connect"),
    FeedBottomItem(FeedBottomDestination.LEADERBOARD, Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents, "Leaderboard"),
    FeedBottomItem(FeedBottomDestination.MARKET, Icons.Filled.Storefront, Icons.Outlined.Storefront, "Market"),
    FeedBottomItem(FeedBottomDestination.MESSAGE, Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, "Message"),
    FeedBottomItem(FeedBottomDestination.STORE, Icons.Filled.Apps, Icons.Filled.Apps, "Blink Store")
)

@Composable
fun FeedBottomBar(
    currentTab: MainTab,
    feedSubTab: Int,
    onHomeClick: () -> Unit,
    onConnectClick: () -> Unit,
    onLeaderboardClick: () -> Unit,
    onMarketClick: () -> Unit,
    onMessageClick: () -> Unit,
    isDark: Boolean,
    onMenuClick: () -> Unit = {},
    isMenuOpen: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = BlinkThemeTokens.colors
    val selectedDestination = when {
        currentTab == MainTab.HOME && feedSubTab == 2 -> FeedBottomDestination.CONNECT
        currentTab == MainTab.HOME -> FeedBottomDestination.HOME
        currentTab == MainTab.LEADERBOARD -> FeedBottomDestination.LEADERBOARD
        currentTab == MainTab.MARKET -> FeedBottomDestination.MARKET
        currentTab == MainTab.MESSAGES -> FeedBottomDestination.MESSAGE
        else -> null
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        shape = RoundedCornerShape(22.dp),
        color = colors.surface.copy(alpha = if (isDark) 0.97f else 0.99f),
        tonalElevation = BlinkElevation.raised,
        shadowElevation = BlinkElevation.floating,
        border = BorderStroke(1.dp, colors.border.copy(alpha = 0.9f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            feedBottomItems.forEach { item ->
                val selected = item.destination == selectedDestination
                FeedBottomBarItem(
                    item = item,
                    selected = selected,
                    onClick = {
                        when (item.destination) {
                            FeedBottomDestination.HOME -> onHomeClick()
                            FeedBottomDestination.CONNECT -> onConnectClick()
                            FeedBottomDestination.LEADERBOARD -> onLeaderboardClick()
                            FeedBottomDestination.MARKET -> onMarketClick()
                            FeedBottomDestination.MESSAGE -> onMessageClick()
                            FeedBottomDestination.STORE -> {
                                context.startActivity(Intent(context, BlinkStoreActivity::class.java))
                                (context as? Activity)?.overridePendingTransition(
                                    R.anim.blink_slide_in_right,
                                    R.anim.blink_stay
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.FeedBottomBarItem(
    item: FeedBottomItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = BlinkThemeTokens.colors
    val haptics = LocalHapticFeedback.current
    val tint by animateColorAsState(
        targetValue = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = BlinkMotion.fastTween(),
        label = "feedNavTint"
    )
    val indicator by animateColorAsState(
        targetValue = if (selected) colors.primary else Color.Transparent,
        animationSpec = BlinkMotion.standardTween(),
        label = "feedNavIndicator"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = spring(),
        label = "feedNavScale"
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .height(64.dp)
            .clickable(
                role = Role.Tab,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            .testTag("feed_nav_${item.destination.name.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Box(
            modifier = Modifier
                .height(3.dp)
                .fillMaxWidth(0.28f)
                .background(indicator, RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
        )
        Spacer(Modifier.height(7.dp))
        Surface(
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            shape = RoundedCornerShape(15.dp),
            color = if (selected) colors.primary.copy(alpha = 0.18f) else Color.Transparent,
            border = if (selected) BorderStroke(1.dp, colors.primary.copy(alpha = 0.28f)) else null,
            tonalElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (selected) item.filledIcon else item.outlinedIcon,
                    contentDescription = item.label,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/** Compatibility wrapper for older callers; new main navigation uses [FeedBottomBar]. */
@Composable
fun FloatingBottomBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    isDark: Boolean,
    onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    FeedBottomBar(
        currentTab = currentTab,
        feedSubTab = 0,
        onHomeClick = { onTabSelected(MainTab.HOME) },
        onConnectClick = { onTabSelected(MainTab.SEARCH) },
        onLeaderboardClick = { onTabSelected(MainTab.LEADERBOARD) },
        onMarketClick = { onTabSelected(MainTab.MARKET) },
        onMessageClick = { onTabSelected(MainTab.MESSAGES) },
        isDark = isDark,
        onMenuClick = onMenuClick,
        modifier = modifier
    )
}
