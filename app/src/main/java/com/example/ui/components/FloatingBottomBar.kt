package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.FeedBlue
import com.example.ui.theme.FeedBorder
import com.example.ui.theme.FeedElevatedSurface
import com.example.ui.theme.FeedPurple
import com.example.ui.theme.FeedTextPrimary
import com.example.ui.theme.FeedTextSecondary
import com.example.viewmodel.MainTab

private enum class FeedBottomDestination {
    HOME, CONNECT, LEADERBOARD, MARKET, MESSAGE, MENU
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
    FeedBottomItem(FeedBottomDestination.MENU, Icons.Filled.Menu, Icons.Filled.Menu, "Menu")
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
    val selectedDestination = when {
        isMenuOpen -> FeedBottomDestination.MENU
        currentTab == MainTab.HOME && feedSubTab == 2 -> FeedBottomDestination.CONNECT
        currentTab == MainTab.HOME -> FeedBottomDestination.HOME
        currentTab == MainTab.LEADERBOARD -> FeedBottomDestination.LEADERBOARD
        currentTab == MainTab.MARKET -> FeedBottomDestination.MARKET
        currentTab == MainTab.MESSAGES -> FeedBottomDestination.MESSAGE
        else -> null
    }
    val navigationSurface = if (isDark) Color(0xFF0E0F10) else Color.White
    val navigationBorder = if (isDark) Color(0xFF2D3035) else Color(0xFFE1E4E8)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = navigationSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, navigationBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
    onClick: () -> Unit
) {
    val tint = if (selected) FeedBlue else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 62.dp)
            .clickable(role = Role.Tab, onClick = onClick)
            .testTag("feed_nav_${item.destination.name.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(.66f)
                .heightIn(min = 3.dp, max = 3.dp)
                .background(if (selected) FeedBlue else Color.Transparent)
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (selected) item.filledIcon else item.outlinedIcon,
                contentDescription = item.label,
                tint = tint,
                modifier = Modifier.size(26.dp)
            )
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
