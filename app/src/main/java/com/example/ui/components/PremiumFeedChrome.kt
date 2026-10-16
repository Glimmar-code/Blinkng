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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.FeedBackground
import com.example.ui.theme.FeedBorder
import com.example.ui.theme.FeedPurple
import com.example.ui.theme.FeedTextPrimary
import com.example.ui.theme.feedAccentBrush

@Composable
fun FeedTopBar(
    userAvatar: String,
    hasUnreadNotifications: Boolean,
    unreadNotificationCount: Int = 0,
    onSearchClick: () -> Unit,
    onItemsClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onMenuClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    reserveStatusBar: Boolean = true,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            // Keep the status-bar safe inset so toolbar controls never sit under
            // the camera cutout, but match it to the exact Feed background.
            // The old radial purple/blue wash made this inset look like a
            // separate colored strip with empty space above Home.
            .background(MaterialTheme.colorScheme.surface)
            .then(if (reserveStatusBar) Modifier.statusBarsPadding() else Modifier)
    ) {
        val horizontalPadding = if (maxWidth >= 600.dp) 20.dp else 10.dp
        // Reserve proper 48dp minimum targets for all four toolbar buttons.
        // On narrow devices collapse the decorative title, not the actions.
        val showHomeTitle = maxWidth >= 350.dp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = 0.dp,
                    bottom = 0.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FeedBrandBlock(
                userAvatar = userAvatar,
                onProfileClick = onProfileClick,
                showTitle = showHomeTitle,
                modifier = Modifier.weight(1f).padding(end = 4.dp)
            )
            FeedHeaderActions(
                hasUnreadNotifications = hasUnreadNotifications,
                unreadNotificationCount = unreadNotificationCount,
                onSearchClick = onSearchClick,
                onItemsClick = onItemsClick,
                onNotificationClick = onNotificationClick,
                onMenuClick = onMenuClick
            )
        }
    }
}

@Composable
private fun FeedBrandBlock(
    userAvatar: String,
    onProfileClick: () -> Unit,
    showTitle: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FeedProfileAvatar(userAvatar = userAvatar, onProfileClick = onProfileClick)
        if (showTitle) {
            Text(
                text = "Home",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FeedProfileAvatar(
    userAvatar: String,
    onProfileClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background((if (MaterialTheme.colorScheme.background == Color.Black) MaterialTheme.colorScheme.primary else Color(0xFF3266D5)), CircleShape)
            .padding(2.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
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
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .graphicsLayer { clip = true; shape = CircleShape }
        )
    }
}

@Composable
private fun FeedHeaderActions(
    hasUnreadNotifications: Boolean,
    unreadNotificationCount: Int,
    onSearchClick: () -> Unit,
    onItemsClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        FeedRadialHeaderAction(
            imageVector = Icons.Default.Search,
            contentDescription = "Search people and posts",
            onClick = onSearchClick,
            modifier = Modifier.testTag("feed_search_action")
        )
        FeedRadialHeaderAction(
            imageVector = Icons.Default.Widgets,
            contentDescription = "Open Items",
            onClick = onItemsClick,
            modifier = Modifier.testTag("feed_items_action")
        )
        Box(modifier = Modifier.size(48.dp)) {
            FeedRadialHeaderAction(
                imageVector = Icons.Default.NotificationsNone,
                contentDescription = "Notifications",
                onClick = onNotificationClick,
                modifier = Modifier.align(Alignment.Center).testTag("feed_notification_action")
            )
            val visibleUnreadCount = maxOf(unreadNotificationCount, if (hasUnreadNotifications) 1 else 0)
            if (visibleUnreadCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .size(if (visibleUnreadCount < 10) 18.dp else 22.dp)
                        .background(feedAccentBrush(), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (visibleUnreadCount > 99) "99+" else visibleUnreadCount.toString(),
                        color = Color.White,
                        fontSize = if (visibleUnreadCount > 99) 7.sp else 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
        FeedRadialHeaderAction(
            imageVector = Icons.Default.Menu,
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
            .size(48.dp)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .background(
                color = if (pressed) FeedPurple.copy(alpha = 0.16f) else Color.Transparent,
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
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(25.dp)
        )
    }
}

@Composable
fun FeedUtilityRow(
    onLeaderboardClick: () -> Unit,
    onGameClick: () -> Unit,
    onStoreClick: () -> Unit,
    onBoostClick: () -> Unit,
    onDropsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .height(64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FeedUtilityAction(
            icon = Icons.Default.EmojiEvents,
            label = "Rank",
            contentDescription = "Open Leaderboard",
            onClick = onLeaderboardClick,
            modifier = Modifier.weight(1f).testTag("feed_rank_action")
        )
        FeedUtilityAction(
            icon = Icons.Default.SportsEsports,
            label = "Game",
            contentDescription = "Open Game",
            onClick = onGameClick,
            modifier = Modifier.weight(1f).testTag("feed_game_action")
        )
        FeedUtilityAction(
            icon = Icons.Default.Campaign,
            label = "Boost",
            contentDescription = "Open Boost and Earn Rank Points",
            onClick = onBoostClick,
            modifier = Modifier.weight(1f).testTag("feed_boost_action")
        )
        FeedUtilityAction(
            icon = Icons.Default.CardGiftcard,
            label = "Drops",
            contentDescription = "Open BLINK Drops",
            onClick = onDropsClick,
            modifier = Modifier.weight(1f).testTag("feed_drops_action")
        )
        FeedUtilityAction(
            icon = Icons.Default.Storefront,
            label = "Store",
            contentDescription = "BLINK Store coming soon",
            onClick = onStoreClick,
            modifier = Modifier.weight(1f).testTag("feed_store_action"),
            muted = true
        )
    }
}

@Composable
private fun FeedUtilityAction(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    muted: Boolean = false
) {
    Column(
        modifier = modifier
            .height(64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (muted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else if (MaterialTheme.colorScheme.background == Color.Black) MaterialTheme.colorScheme.primary else Color(0xFF3266B4),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun FeedTabs(
    selectedIndex: Int,
    onForYouClick: () -> Unit,
    onFollowingClick: () -> Unit,
    onFilterClick: () -> Unit,
    onRefreshClick: () -> Unit = {},
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Reference blue pill selector, with theme-aware background and 48dp targets.
    Row(
        modifier = modifier.fillMaxWidth().height(56.dp)
            .background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BoxWithConstraints(
            modifier = Modifier.weight(1f).height(44.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                .padding(3.dp)
        ) {
            val segmentWidth = maxWidth / 2
            val selection = selectedIndex.coerceIn(0, 1)
            val selectedOffset by animateDpAsState(
                targetValue = segmentWidth * selection,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "feedSegmentOffset"
            )
            Box(Modifier.offset(x = selectedOffset).width(segmentWidth).fillMaxHeight()
                .background(if (MaterialTheme.colorScheme.background == Color.Black) MaterialTheme.colorScheme.primary else Color(0xFF2563EB), RoundedCornerShape(22.dp)))
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                FeedTabLabel("For You", selection == 0, Modifier.weight(1f), onForYouClick)
                FeedTabLabel("Following", selection == 1, Modifier.weight(1f), onFollowingClick)
            }
        }
        Box(
            Modifier.size(48.dp)
                .clickable(enabled = !isRefreshing, role = Role.Button, onClick = onRefreshClick)
                .semantics { contentDescription = "Refresh feed" }
                .testTag("feed_refresh_action"),
            contentAlignment = Alignment.Center
        ) {
            if (isRefreshing) CircularProgressIndicator(
                modifier = Modifier.size(19.dp), color = if (MaterialTheme.colorScheme.background == Color.Black) MaterialTheme.colorScheme.primary else Color(0xFF2563EB), strokeWidth = 2.dp
            ) else Icon(Icons.Default.Refresh, null,
                tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
        }
        Box(
            Modifier.size(48.dp).clickable(role = Role.Button, onClick = onFilterClick)
                .semantics { contentDescription = "Filter feed" }
                .testTag("feed_filter_action"),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Tune, null,
                tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun FeedTabLabel(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val labelColor by animateColorAsState(
        targetValue = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "feedSegmentLabelColor"
    )
    Box(
        modifier.fillMaxHeight().clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .testTag(if (label == "For You") "feed_tab_for_you" else "feed_tab_following"),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = labelColor, fontSize = 14.sp, lineHeight = 17.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * One stable create speed-dial for the dedicated action rail. The collapsed state is one
 * button; expanding reveals Blink AI and New post horizontally so the control never grows
 * upward over post engagement content.
 */
@Composable
fun CreatePostFab(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showBlinkAi by remember { mutableStateOf(false) }
    var actionsExpanded by remember { mutableStateOf(false) }
    val mainInteractionSource = remember { MutableInteractionSource() }
    val mainPressed by mainInteractionSource.collectIsPressedAsState()
    val mainScale by animateFloatAsState(
        targetValue = if (mainPressed) 0.94f else 1f,
        animationSpec = tween(90),
        label = "createMenuPressScale"
    )
    val pill = RoundedCornerShape(28.dp)

    if (showBlinkAi) {
        BlinkAiSheet(onDismiss = { showBlinkAi = false })
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedVisibility(
            visible = actionsExpanded,
            enter = fadeIn(tween(130)) + expandHorizontally(tween(160)),
            exit = fadeOut(tween(100)) + shrinkHorizontally(tween(140))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FeedCreateAction(
                    label = "Blink AI",
                    contentDescription = "Open Blink AI",
                    onClick = {
                        actionsExpanded = false
                        showBlinkAi = true
                    },
                    leading = {
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
                    },
                    modifier = Modifier.testTag("blink_ai_fab")
                )
                FeedCreateAction(
                    label = "New post",
                    contentDescription = "Create Post",
                    onClick = {
                        actionsExpanded = false
                        onClick()
                    },
                    leading = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    },
                    modifier = Modifier.testTag("create_post_action")
                )
            }
        }

        Row(
            modifier = Modifier
                .shadow(10.dp, pill, clip = false)
                .graphicsLayer { scaleX = mainScale; scaleY = mainScale }
                .background(feedAccentBrush(), pill)
                .border(1.dp, Color.White.copy(alpha = 0.14f), pill)
                .clickable(
                    interactionSource = mainInteractionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = { actionsExpanded = !actionsExpanded }
                )
                .semantics {
                    role = Role.Button
                    contentDescription = if (actionsExpanded) "Close create menu" else "Open create menu"
                }
                .testTag("create_post_fab")
                .heightIn(min = 54.dp)
                .padding(horizontal = if (expanded) 17.dp else 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (actionsExpanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(23.dp)
            )
            AnimatedVisibility(
                visible = expanded && !actionsExpanded,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(90))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Create",
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

@Composable
private fun FeedCreateAction(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .shadow(7.dp, shape, clip = false)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(1.dp, FeedPurple.copy(alpha = 0.52f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            }
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(Modifier.width(9.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

