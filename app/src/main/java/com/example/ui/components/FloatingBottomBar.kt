package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import com.example.performance.rememberBlinkReduceMotion
import com.example.ui.theme.BlinkMotion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.FeedBlue
import com.example.viewmodel.MainTab

private enum class FeedBottomDestination {
    HOME, REELS, MARKET, CONNECT, MENU
}

private data class FeedBottomItem(
    val destination: FeedBottomDestination,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector,
    val label: String
)

private val feedBottomItems = listOf(
    FeedBottomItem(FeedBottomDestination.HOME, Icons.Filled.Home, Icons.Outlined.Home, "Home"),
    FeedBottomItem(FeedBottomDestination.REELS, Icons.Outlined.SmartDisplay, Icons.Outlined.SmartDisplay, "Reels"),
    FeedBottomItem(FeedBottomDestination.MARKET, Icons.Filled.Storefront, Icons.Outlined.Storefront, "Market"),
    FeedBottomItem(FeedBottomDestination.CONNECT, Icons.Filled.People, Icons.Outlined.People, "Connect"),
    FeedBottomItem(FeedBottomDestination.MENU, Icons.Filled.Menu, Icons.Outlined.Menu, "Menu")
)

@Composable
fun FeedBottomBar(
    currentTab: MainTab,
    feedSubTab: Int,
    onHomeClick: () -> Unit,
    onReelsClick: () -> Unit,
    onMarketClick: () -> Unit,
    onConnectClick: () -> Unit,
    onMessageClick: () -> Unit,
    isDark: Boolean,
    onMenuClick: () -> Unit = {},
    isMenuOpen: Boolean = false,
    hasUnreadMessages: Boolean = false,
    modifier: Modifier = Modifier
) {
    val selectedDestination = when {
        isMenuOpen -> FeedBottomDestination.MENU
        currentTab == MainTab.HOME && feedSubTab == 1 -> FeedBottomDestination.REELS
        currentTab == MainTab.HOME && feedSubTab == 2 -> FeedBottomDestination.CONNECT
        currentTab == MainTab.HOME -> FeedBottomDestination.HOME
        currentTab == MainTab.MARKET -> FeedBottomDestination.MARKET
        else -> null
    }

    val reduceMotion = rememberBlinkReduceMotion()
    val navigationSurface = if (isDark) Color.Black else Color.White
    // Mirror Auth's restrained translucent-white borders, not gray blocks.
    val navigationBorder = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE1E4E8)

    Surface(
        // Paint behind gesture navigation and keep destinations above the
        // system bar on edge-to-edge Android 15+ devices.
        modifier = modifier
            .fillMaxWidth()
            .background(navigationSurface)
            .navigationBarsPadding(),
        shape = RoundedCornerShape(0.dp),
        color = navigationSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, navigationBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            feedBottomItems.forEach { item ->
                val selected = item.destination == selectedDestination
                FeedBottomBarItem(
                    item = item,
                    selected = selected,
                    isDark = isDark,
                    reduceMotion = reduceMotion,
                    showUnreadDot = hasUnreadMessages && item.destination == FeedBottomDestination.MENU,
                    onClick = {
                        when (item.destination) {
                            FeedBottomDestination.HOME -> onHomeClick()
                            FeedBottomDestination.REELS -> onReelsClick()
                            FeedBottomDestination.MARKET -> onMarketClick()
                            FeedBottomDestination.CONNECT -> onConnectClick()
                            FeedBottomDestination.MENU -> onMenuClick()
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
    isDark: Boolean,
    reduceMotion: Boolean,
    showUnreadDot: Boolean,
    onClick: () -> Unit
) {
    val activeAccent = MaterialTheme.colorScheme.onSurface
    val tint by animateColorAsState(
        targetValue = if (selected) activeAccent else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(if (reduceMotion) 0 else BlinkMotion.revealMillis),
        label = "bottomNavTint"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) BlinkMotion.selectedScale else 1f,
        animationSpec = tween(if (reduceMotion) 0 else BlinkMotion.revealMillis),
        label = "bottomNavIconScale"
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) activeAccent else Color.Transparent,
        animationSpec = tween(if (reduceMotion) 0 else BlinkMotion.revealMillis),
        label = "bottomNavIndicator"
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .height(64.dp)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .testTag("feed_nav_${item.destination.name.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(6.dp))
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                imageVector = if (selected) item.filledIcon else item.outlinedIcon,
                contentDescription = item.label,
                tint = tint,
                modifier = Modifier.size(22.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale }
            )
            if (showUnreadDot) {
                Box(Modifier.size(7.dp).background(Color(0xFFF12D45), CircleShape))
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = item.label, color = tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier.width(45.dp).height(2.dp)
                .background(indicatorColor, RoundedCornerShape(2.dp))
        )
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
        onReelsClick = { onTabSelected(MainTab.HOME) },
        onMarketClick = { onTabSelected(MainTab.MARKET) },
        onConnectClick = { onTabSelected(MainTab.SEARCH) },
        onMessageClick = { onTabSelected(MainTab.MESSAGES) },
        isDark = isDark,
        onMenuClick = onMenuClick,
        modifier = modifier
    )
}
