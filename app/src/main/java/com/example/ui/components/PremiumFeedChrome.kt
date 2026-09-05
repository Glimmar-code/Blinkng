package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.FeedBackground
import com.example.ui.theme.FeedBlue
import com.example.ui.theme.FeedBorder
import com.example.ui.theme.FeedCardSurface
import com.example.ui.theme.FeedElevatedSurface
import com.example.ui.theme.FeedPurple
import com.example.ui.theme.FeedTextPrimary
import com.example.ui.theme.FeedTextSecondary
import com.example.ui.theme.feedAccentBrush

@Composable
fun FeedTopBar(
    userAvatar: String,
    hasUnreadNotifications: Boolean,
    onSearchClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onMenuClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(FeedBackground)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(FeedPurple.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.22f, 0f),
                        radius = size.width * 0.72f
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(FeedBlue.copy(alpha = 0.11f), Color.Transparent),
                        center = Offset(size.width * 0.84f, 0f),
                        radius = size.width * 0.58f
                    )
                )
            }
            .statusBarsPadding()
    ) {
        val wide = maxWidth >= 600.dp
        if (wide) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FeedBrandBlock()
                FeedSearchField(
                    onClick = onSearchClick,
                    modifier = Modifier.weight(1f).widthIn(max = 520.dp)
                )
                FeedHeaderActions(
                    userAvatar = userAvatar,
                    hasUnreadNotifications = hasUnreadNotifications,
                    onNotificationClick = onNotificationClick,
                    onMenuClick = onMenuClick,
                    onProfileClick = onProfileClick
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FeedBrandBlock(modifier = Modifier.weight(1f))
                    FeedHeaderActions(
                        userAvatar = userAvatar,
                        hasUnreadNotifications = hasUnreadNotifications,
                        onNotificationClick = onNotificationClick,
                        onMenuClick = onMenuClick,
                        onProfileClick = onProfileClick
                    )
                }
                Spacer(Modifier.height(8.dp))
                FeedSearchField(
                    onClick = onSearchClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun FeedBrandBlock(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "B",
            modifier = Modifier
                .width(38.dp)
                .semantics { contentDescription = "Blink" },
            style = TextStyle(
                brush = feedAccentBrush(),
                fontSize = 38.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-2).sp
            )
        )
        Column {
            Text(
                text = "Home",
                style = MaterialTheme.typography.headlineSmall,
                color = FeedTextPrimary,
                maxLines = 1
            )
            Text(
                text = "Your campus, in real time",
                style = MaterialTheme.typography.bodyMedium,
                color = FeedTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FeedSearchField(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Search people and posts" },
        shape = RoundedCornerShape(28.dp),
        color = FeedCardSurface.copy(alpha = 0.86f),
        border = BorderStroke(1.dp, FeedBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = FeedTextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Search people, posts…",
                color = FeedTextSecondary,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FeedHeaderActions(
    userAvatar: String,
    hasUnreadNotifications: Boolean,
    onNotificationClick: () -> Unit,
    onMenuClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            IconButton(onClick = onNotificationClick, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Default.NotificationsNone,
                    contentDescription = "Notifications",
                    tint = FeedTextPrimary,
                    modifier = Modifier.size(27.dp)
                )
            }
            if (hasUnreadNotifications) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-7).dp, y = 7.dp)
                        .size(9.dp)
                        .background(feedAccentBrush(), CircleShape)
                        .border(1.dp, FeedBackground, CircleShape)
                )
            }
        }
        IconButton(onClick = onMenuClick, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = FeedTextPrimary,
                modifier = Modifier.size(26.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(feedAccentBrush(), CircleShape)
                .padding(2.dp)
                .background(FeedBackground, CircleShape)
                .padding(2.dp)
                .clickable(role = Role.Button, onClick = onProfileClick),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = userAvatar,
                contentDescription = "Open profile",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(38.dp)
                    .background(FeedElevatedSurface, CircleShape)
                    .graphicsLayer { clip = true; shape = CircleShape }
            )
        }
    }
}

/**
 * Compact navigation for the swipe family. For You, Following and Game are the
 * only page tabs; Reels is intentionally a separate action so it never steals
 * horizontal space from the labels on narrow phones.
 */
@Composable
fun FeedTabs(
    selectedIndex: Int,
    onForYouClick: () -> Unit,
    onFollowingClick: () -> Unit,
    onGameClick: () -> Unit,
    onReelClick: () -> Unit,
    onFilterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(FeedBackground)
            .height(54.dp)
    ) {
        val availableWidth = maxWidth
        val actionsWidth = if (availableWidth < 360.dp) 88.dp else 106.dp
        val tabWidth = (availableWidth - actionsWidth) / 3
        val indicatorWidth = (tabWidth - 18.dp).coerceAtLeast(22.dp)
        val selected = selectedIndex.coerceIn(0, 2)
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selected + (tabWidth - indicatorWidth) / 2,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "feedTabIndicatorOffset"
        )

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FeedTabLabel("For You", selected == 0, Modifier.width(tabWidth), onForYouClick)
            FeedTabLabel("Following", selected == 1, Modifier.width(tabWidth), onFollowingClick)
            FeedTabLabel("Game", selected == 2, Modifier.width(tabWidth), onGameClick)

            Surface(
                modifier = Modifier
                    .width(if (availableWidth < 360.dp) 44.dp else 58.dp)
                    .height(38.dp)
                    .clickable(role = Role.Button, onClick = onReelClick)
                    .semantics { contentDescription = "Open Reels" },
                shape = RoundedCornerShape(19.dp),
                color = FeedPurple.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, FeedPurple.copy(alpha = 0.42f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = FeedTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    if (availableWidth >= 390.dp) {
                        Spacer(Modifier.width(2.dp))
                        Text(
                            "Reels",
                            color = FeedTextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .width(if (availableWidth < 360.dp) 44.dp else 48.dp)
                    .height(52.dp)
                    .clickable(role = Role.Button, onClick = onFilterClick)
                    .semantics { contentDescription = "Filter feed" },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = FeedTextSecondary,
                    modifier = Modifier.size(21.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = indicatorOffset)
                .width(indicatorWidth)
                .height(3.dp)
                .background(feedAccentBrush(), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
        )
    }
}

@Composable
private fun FeedTabLabel(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val color by animateColorAsState(
        targetValue = if (selected) FeedTextPrimary else FeedTextSecondary,
        animationSpec = tween(180),
        label = "feedTabLabelColor"
    )
    Box(
        modifier = modifier
            .height(52.dp)
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CreatePostFab(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(100),
        label = "createPostPressScale"
    )
    val horizontalPadding by animateDpAsState(
        targetValue = if (expanded) 18.dp else 15.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "createPostPadding"
    )
    val pill = RoundedCornerShape(28.dp)

    Row(
        modifier = modifier
            .shadow(10.dp, pill, clip = false)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .background(feedAccentBrush(), pill)
            .border(1.dp, Color.White.copy(alpha = 0.14f), pill)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .semantics {
                role = Role.Button
                contentDescription = "Create Post"
            }
            .testTag("create_post_fab")
            .heightIn(min = 54.dp)
            .padding(horizontal = horizontalPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(21.dp)
            )
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp).align(Alignment.BottomEnd)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(140)) + expandHorizontally(tween(180)),
            exit = fadeOut(tween(100)) + shrinkHorizontally(tween(160))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "Create Post",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}
