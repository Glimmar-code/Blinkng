package com.example.ui.components

import com.example.R
import androidx.compose.ui.res.painterResource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.FeedBackground
import com.example.ui.theme.FeedBlue
import com.example.ui.theme.FeedBorder
import com.example.ui.theme.FeedElevatedSurface
import com.example.ui.theme.FeedPurple
import com.example.ui.theme.FeedTextPrimary
import com.example.ui.theme.FeedTextSecondary
import com.example.ui.theme.feedAccentBrush

@Composable
fun FeedTopBar(
    userAvatar: String,
    worldRank: Int,
    coinBalance: Long,
    hasUnreadNotifications: Boolean,
    onSearchClick: () -> Unit,
    onGameClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onMenuClick: () -> Unit,
    onLeaderboardClick: () -> Unit,
    onStoreClick: () -> Unit,
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
                        colors = listOf(FeedPurple.copy(alpha = 0.13f), Color.Transparent),
                        center = Offset(size.width * 0.24f, 0f),
                        radius = size.width * 0.68f
                    )
                )
            }
            .statusBarsPadding()
    ) {
        val horizontalPadding = if (maxWidth >= 600.dp) 20.dp else 14.dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = 8.dp,
                    bottom = 6.dp
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FeedBrandBlock(
                    userAvatar = userAvatar,
                    onProfileClick = onProfileClick,
                    modifier = Modifier.weight(1f)
                )
                FeedStatusChip(
                    icon = Icons.Outlined.EmojiEvents,
                    label = if (worldRank > 0) "#$worldRank" else "#—",
                    contentDescription = if (worldRank > 0) "Open leaderboard. World rank $worldRank" else "Open leaderboard. Not ranked yet",
                    onClick = onLeaderboardClick
                )
                Spacer(Modifier.width(8.dp))
                FeedStatusChip(
                    icon = Icons.Outlined.MonetizationOn,
                    label = formatFeedCoinBalance(coinBalance),
                    contentDescription = "Open Blink Store. $coinBalance Blink Coins",
                    onClick = onStoreClick
                )
            }

            Spacer(Modifier.height(4.dp))
            FeedHeaderActions(
                hasUnreadNotifications = hasUnreadNotifications,
                onSearchClick = onSearchClick,
                onGameClick = onGameClick,
                onNotificationClick = onNotificationClick,
                onMenuClick = onMenuClick,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun FeedBrandBlock(
    userAvatar: String,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        FeedProfileAvatar(userAvatar = userAvatar, onProfileClick = onProfileClick)
        BlinkMark(
            size = 26.dp,
            showText = true
        )
    }
}

@Composable
private fun FeedProfileAvatar(
    userAvatar: String,
    onProfileClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(feedAccentBrush(), CircleShape)
            .padding(2.dp)
            .background(FeedBackground, CircleShape)
            .padding(2.dp)
            .clickable(role = Role.Button, onClick = onProfileClick)
            .semantics { contentDescription = "Open profile" }
            .testTag("feed_profile_action"),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = userAvatar,
            error = painterResource(R.drawable.ic_default_profile),
            fallback = painterResource(R.drawable.ic_default_profile),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(36.dp)
                .background(FeedElevatedSurface, CircleShape)
                .graphicsLayer { clip = true; shape = CircleShape }
        )
    }
}

@Composable
private fun FeedStatusChip(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(90),
        label = "feedStatusChipScale"
    )
    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(36.dp)
            .background(FeedElevatedSurface, RoundedCornerShape(18.dp))
            .border(1.dp, FeedBorder.copy(alpha = 0.64f), RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = FeedTextSecondary,
            modifier = Modifier.size(17.dp)
        )
        Text(
            text = label,
            color = FeedTextPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

private fun formatFeedCoinBalance(value: Long): String =
    java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(value.coerceAtLeast(0L))

@Composable
private fun FeedHeaderActions(
    hasUnreadNotifications: Boolean,
    onSearchClick: () -> Unit,
    onGameClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        FeedRadialHeaderAction(
            imageVector = Icons.Outlined.Search,
            contentDescription = "Search people and posts",
            onClick = onSearchClick,
            modifier = Modifier.testTag("feed_search_action")
        )
        FeedRadialHeaderAction(
            imageVector = Icons.Outlined.SportsEsports,
            contentDescription = "Open Game",
            onClick = onGameClick,
            modifier = Modifier.testTag("feed_game_action")
        )
        Box(modifier = Modifier.size(40.dp)) {
            FeedRadialHeaderAction(
                imageVector = Icons.Outlined.NotificationsNone,
                contentDescription = "Notifications",
                onClick = onNotificationClick,
                modifier = Modifier.align(Alignment.Center).testTag("feed_notification_action")
            )
            if (hasUnreadNotifications) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-1).dp, y = 1.dp)
                        .size(8.dp)
                        .background(feedAccentBrush(), CircleShape)
                        .border(1.dp, FeedBackground, CircleShape)
                )
            }
        }
        FeedRadialHeaderAction(
            imageVector = Icons.Outlined.Menu,
            contentDescription = "Menu",
            onClick = onMenuClick,
            modifier = Modifier.testTag("feed_menu_action")
        )
    }
}

@Composable
private fun FeedRadialHeaderAction(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(90),
        label = "feedHeaderActionScale"
    )
    Box(
        modifier = modifier
            .size(40.dp)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .background(
                color = if (pressed) FeedPurple.copy(alpha = 0.14f) else Color.Transparent,
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = FeedTextPrimary,
            modifier = Modifier.size(23.dp)
        )
    }
}

@Composable
fun FeedTabs(
    selectedIndex: Int,
    onForYouClick: () -> Unit,
    onFollowingClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(FeedBackground)
            .height(48.dp)
    ) {
        val tabWidth = maxWidth / 2f
        val indicatorWidth = (tabWidth - 72.dp).coerceAtLeast(56.dp)
        val labelSelection = selectedIndex.coerceIn(0, 1)
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * labelSelection + (tabWidth - indicatorWidth) / 2,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "feedTabIndicatorOffset"
        )

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FeedTabLabel("For You", selectedIndex == 0, Modifier.width(tabWidth), onForYouClick)
            FeedTabLabel("Following", selectedIndex == 1, Modifier.width(tabWidth), onFollowingClick)
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
        animationSpec = tween(160),
        label = "feedTabLabelColor"
    )
    Box(
        modifier = modifier.height(48.dp).clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Premium floating action stack. Blink AI is deliberately positioned above Create Post
 * so the AI entry point stays visible on the current premium feed instead of living in the
 * retired legacy FeedScreen.
 */
@Composable
fun CreatePostFab(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showBlinkAi by remember { mutableStateOf(false) }
    val createInteractionSource = remember { MutableInteractionSource() }
    val createPressed by createInteractionSource.collectIsPressedAsState()
    val createScale by animateFloatAsState(
        targetValue = if (createPressed) 0.97f else 1f,
        animationSpec = tween(100),
        label = "createPostPressScale"
    )
    val aiInteractionSource = remember { MutableInteractionSource() }
    val aiPressed by aiInteractionSource.collectIsPressedAsState()
    val aiScale by animateFloatAsState(
        targetValue = if (aiPressed) 0.96f else 1f,
        animationSpec = tween(100),
        label = "blinkAiPressScale"
    )
    val horizontalPadding by animateDpAsState(
        targetValue = if (expanded) 18.dp else 15.dp,
        animationSpec = tween(durationMillis = 180),
        label = "createPostPadding"
    )
    val pill = RoundedCornerShape(28.dp)

    if (showBlinkAi) {
        BlinkAiSheet(onDismiss = { showBlinkAi = false })
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .shadow(8.dp, pill, clip = false)
                .graphicsLayer { scaleX = aiScale; scaleY = aiScale }
                .background(FeedElevatedSurface, pill)
                .border(1.dp, FeedPurple.copy(alpha = 0.72f), pill)
                .clickable(
                    interactionSource = aiInteractionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = { showBlinkAi = true }
                )
                .semantics {
                    role = Role.Button
                    contentDescription = "Open Blink AI"
                }
                .testTag("blink_ai_fab")
                .heightIn(min = 50.dp)
                .padding(horizontal = if (expanded) 16.dp else 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(27.dp)
                    .background(feedAccentBrush(), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AI",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
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
                        text = "Blink AI",
                        color = FeedTextPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .shadow(10.dp, pill, clip = false)
                .graphicsLayer { scaleX = createScale; scaleY = createScale }
                .background(feedAccentBrush(), pill)
                .border(1.dp, Color.White.copy(alpha = 0.14f), pill)
                .clickable(
                    interactionSource = createInteractionSource,
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
}
