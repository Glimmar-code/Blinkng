package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BlinkBlack
import com.example.ui.theme.BlinkCream
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightBorder
import com.example.ui.theme.LightTextSecondary
import com.example.viewmodel.MainTab

data class NavTabItem(
    val tab: MainTab,
    val filledIcon: ImageVector,
    val outlineIcon: ImageVector,
    val label: String
)

val kNavTabItems = listOf(
    NavTabItem(MainTab.HOME, Icons.Filled.Home, Icons.Outlined.Home, "Home"),
    NavTabItem(MainTab.SEARCH, Icons.Filled.Search, Icons.Outlined.Search, "Search"),
    NavTabItem(MainTab.LEADERBOARD, Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents, "Leaderboard"),
    NavTabItem(MainTab.MARKET, Icons.Filled.Storefront, Icons.Outlined.Storefront, "Market"),
    NavTabItem(MainTab.MESSAGES, Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, "Messages")
)

@Composable
fun FloatingBottomBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val barBg = if (isDark) Color(0xF20D0D0D) else Color(0xF5FFFBF5)
    val borderColor = if (isDark) DarkBorder else LightBorder

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = barBg,
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, RoundedCornerShape(100.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                kNavTabItems.forEach { item ->
                    val isSelected = currentTab == item.tab
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.05f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f),
                        label = "tabScale"
                    )
                    val iconColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            if (isDark) BlinkBlack else BlinkCream
                        } else {
                            if (isDark) DarkTextSecondary else LightTextSecondary
                        },
                        label = "tabColor"
                    )

                    val selectedBg = if (isDark) BlinkCream else BlinkBlack

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .background(if (isSelected) selectedBg else Color.Transparent)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onTabSelected(item.tab)
                            }
                            .testTag("nav_tab_${item.tab.name.lowercase()}")
                    ) {
                        Icon(
                            imageVector = if (isSelected) item.filledIcon else item.outlineIcon,
                            contentDescription = item.label,
                            tint = iconColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
