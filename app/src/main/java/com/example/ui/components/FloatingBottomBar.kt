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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Storefront
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.FeedBlue
import com.example.viewmodel.MainTab

private enum class FeedBottomDestination {
    HOME, REELS, MARKET, CONNECT, MESSAGE
}

private data class FeedBottomItem(
    val destination: FeedBottomDestination,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector,
    val label: String
)

private val feedBottomItems = listOf(
    FeedBottomItem(FeedBottomDestination.HOME, Icons.Filled.Home, Icons.Outlined.Home, "Home"),
    FeedBottomItem(FeedBottomDestination.REELS, Icons.Filled.PlayArrow, Icons.Filled.PlayArrow, "Reels"),
    FeedBottomItem(FeedBottomDestination.MARKET, Icons.Filled.Storefront, Icons.Outlined.Storefront, "Market"),
    FeedBottomItem(FeedBottomDestination.CONNECT, Icons.Filled.People, Icons.Outlined.People, "Connect"),
    FeedBottomItem(FeedBottomDestination.MESSAGE, Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, "Message")
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
    modifier: Modifier = Modifier
) {
    val selectedDestination = when {
        currentTab == MainTab.HOME && feedSubTab == 1 -> FeedBottomDestination.REELS
        currentTab == MainTab.HOME && feedSubTab == 2 -> FeedBottomDestination.CONNECT
        currentTab == MainTab.HOME -> FeedBottomDestination.HOME
        currentTab == MainTab.MARKET -> FeedBottomDestination.MARKET
        currentTab == MainTab.MESSAGES -> FeedBottomDestination.MESSAGE
        else -> null
    }

    val reduceMotion = rememberBlinkReduceMotion()
    val navigationSurface = if (isDark) Color.Black else Color.White
    val navigationBorder = if (isDark) Color(0xFF242424) else Color(0xFFE1E4E8)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = navigationSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, navigationBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            feedBottomItems.forEach { item ->
                val selected = item.destination == selectedDestination
                FeedBottomBarItem(
                    item = item,
                    selected = selected,
                    reduceMotion = reduceMotion,
                    onClick = {
                        when (item.destination) {
                            FeedBottomDestination.HOME -> onHomeClick()
                            FeedBottomDestination.REELS -> onReelsClick()
                            FeedBottomDestination.MARKET -> onMarketClick()
                            FeedBottomDestination.CONNECT -> onConnectClick()
                            FeedBottomDestination.MESSAGE -> onMessageClick()
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
    reduceMotion: Boolean,
    onClick: () -> Unit
) {
    val tint by animateColorAsState(
        targetValue = if (selected) FeedBlue else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(if (reduceMotion) 0 else BlinkMotion.revealMillis),
        label = "bottomNavTint"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) BlinkMotion.selectedScale else 1f,
        animationSpec = tween(if (reduceMotion) 0 else BlinkMotion.revealMillis),
        label = "bottomNavIconScale"
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) FeedBlue else Color.Transparent,
        animationSpec = tween(if (reduceMotion) 0 else BlinkMotion.revealMillis),
        label = "bottomNavIndicator"
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .height(56.dp)
            .clickable(role = Role.Tab, onClick = onClick)
            .testTag("feed_nav_${item.destination.name.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .height(3.dp)
                .background(indicatorColor)
        )
        Spacer(Modifier.height(5.dp))
        Icon(
            imageVector = if (selected) item.filledIcon else item.outlinedIcon,
            contentDescription = item.label,
            tint = tint,
            modifier = Modifier.size(23.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale }
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = item.label,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
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
