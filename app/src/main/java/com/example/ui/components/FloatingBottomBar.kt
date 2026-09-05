package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateColorAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.FeedBackground
import com.example.ui.theme.FeedBlue
import com.example.ui.theme.FeedBorder
import com.example.ui.theme.FeedElevatedSurface
import com.example.ui.theme.FeedPurple
import com.example.ui.theme.FeedTextPrimary
import com.example.ui.theme.FeedTextSecondary
import com.example.viewmodel.MainTab

private enum class FeedBottomDestination {
    HOME, CONNECT, LEADERBOARD, MARKET, MESSAGE
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
    FeedBottomItem(FeedBottomDestination.MESSAGE, Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, "Message")
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
    modifier: Modifier = Modifier
) {
    val selectedDestination = when {
        currentTab == MainTab.HOME && feedSubTab == 2 -> FeedBottomDestination.CONNECT
        currentTab == MainTab.HOME -> FeedBottomDestination.HOME
        currentTab == MainTab.LEADERBOARD -> FeedBottomDestination.LEADERBOARD
        currentTab == MainTab.MARKET -> FeedBottomDestination.MARKET
        currentTab == MainTab.MESSAGES -> FeedBottomDestination.MESSAGE
        else -> null
    }
    val shape = RoundedCornerShape(34.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(FeedPurple.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.22f, size.height),
                        radius = size.width * 0.46f
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(FeedBlue.copy(alpha = 0.13f), Color.Transparent),
                        center = Offset(size.width * 0.82f, size.height),
                        radius = size.width * 0.42f
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = if (isDark) FeedElevatedSurface.copy(alpha = 0.95f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, if (isDark) FeedBorder else MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 7.dp),
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
                            }
                        }
                    )
                }
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
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bottomNavScale"
    )
    val tint by animateColorAsState(
        targetValue = if (selected) FeedTextPrimary else FeedTextSecondary,
        label = "bottomNavTint"
    )
    val labelTint by animateColorAsState(
        targetValue = if (selected) FeedTextPrimary else FeedTextSecondary,
        label = "bottomNavLabelTint"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 64.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .testTag("feed_nav_${item.destination.name.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(
                    color = if (selected) FeedPurple.copy(alpha = 0.18f) else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (selected) item.filledIcon else item.outlinedIcon,
                contentDescription = item.label,
                tint = tint,
                modifier = Modifier.size(23.dp)
            )
        }
        Spacer(Modifier.size(2.dp))
        Text(
            text = item.label,
            color = labelTint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + scaleIn(initialScale = 0.5f),
            exit = fadeOut() + scaleOut(targetScale = 0.5f)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .size(5.dp)
                    .background(
                        brush = Brush.linearGradient(listOf(FeedPurple, FeedBlue)),
                        shape = CircleShape
                    )
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
        modifier = modifier
    )
}
