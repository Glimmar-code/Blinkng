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
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.Language
import java.text.NumberFormat
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
import androidx.compose.ui.platform.LocalDensity
import com.blinkng.shared.BlinkAdaptiveLayoutPolicy
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

/**
 * Reference-matched Home chrome. Use the Items destination behind the gamepad glyph:
 * Game remains one of the features INSIDE Items, not a new toolbar destination.
 */
@Composable
fun FeedTopBar(
    userAvatar: String,
    userIsOnline: Boolean = false,
    hasUnreadNotifications: Boolean,
    unreadNotificationCount: Int = 0,
    onSearchClick: () -> Unit,
    isAdmin: Boolean = false,
    onAdminClick: () -> Unit = {},
    onItemsClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onMenuClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    reserveStatusBar: Boolean = true,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .then(if (reserveStatusBar) Modifier.statusBarsPadding() else Modifier)
    ) {
        // Keep exactly four 44dp header actions, including on admin accounts.
        // Admin moves to Items so the header never hides BLINK or inserts a fifth icon.
        val compactToolbar = BlinkAdaptiveLayoutPolicy.compactToolbar(
            maxWidth.value.toInt(), LocalDensity.current.fontScale
        )
        // At large font scales the brand and four existing actions need
        // separate rails: never hide Search, Items, Notifications or Menu.
        if (compactToolbar) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    start = 12.dp, end = 8.dp, top = 8.dp, bottom = 6.dp
                )
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FeedBrandBlock(
                        userAvatar = userAvatar,
                        userIsOnline = userIsOnline,
                        onProfileClick = onProfileClick,
                        showTitle = true,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FeedHeaderActions(
                        compact = false,
                        hasUnreadNotifications = hasUnreadNotifications,
                        unreadNotificationCount = unreadNotificationCount,
                        onSearchClick = onSearchClick,
                        onItemsClick = onItemsClick,
                        onNotificationClick = onNotificationClick,
                        onMenuClick = onMenuClick
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 6.dp)
                    .height(50.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FeedBrandBlock(
                    userAvatar = userAvatar,
                    userIsOnline = userIsOnline,
                    onProfileClick = onProfileClick,
                    showTitle = true,
                    compact = false,
                    modifier = Modifier.weight(1f)
                )
                FeedHeaderActions(
                    compact = false,
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
}

@Composable
private fun FeedBrandBlock(
    userAvatar: String,
    userIsOnline: Boolean,
    onProfileClick: () -> Unit,
    showTitle: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        FeedProfileAvatar(userAvatar = userAvatar, userIsOnline = userIsOnline, onProfileClick = onProfileClick)
        if (showTitle) {
            Text(
                text = "BLINK",
                fontSize = if (compact) 22.sp else 27.sp,
                letterSpacing = if (compact) 3.sp else 4.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun FeedProfileAvatar(
    userAvatar: String,
    userIsOnline: Boolean,
    onProfileClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
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
                .size(36.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .graphicsLayer { clip = true; shape = CircleShape }
        )
        if (userIsOnline) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(10.dp)
                    .background(Color(0xFF22C55E), CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
private fun FeedHeaderActions(
    compact: Boolean,
    hasUnreadNotifications: Boolean,
    unreadNotificationCount: Int,
    onSearchClick: () -> Unit,
    onItemsClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        FeedRadialHeaderAction(
            compact = compact,
            imageVector = Icons.Outlined.Search,
            contentDescription = "Search people and posts",
            onClick = onSearchClick,
            modifier = Modifier.testTag("feed_search_action")
        )
        FeedRadialHeaderAction(
            compact = compact,
            imageVector = Icons.Default.GridView,
            contentDescription = "Open Items (includes Game)",
            onClick = onItemsClick,
            modifier = Modifier.testTag("feed_items_action")
        )
        Box(modifier = Modifier.size(if (compact) 38.dp else 44.dp)) {
            FeedRadialHeaderAction(
                compact = compact,
                imageVector = Icons.Outlined.NotificationsNone,
                contentDescription = if (hasUnreadNotifications || unreadNotificationCount > 0)
                    "Notifications, ${maxOf(unreadNotificationCount, 1)} unread" else "Notifications",
                onClick = onNotificationClick,
                modifier = Modifier.align(Alignment.Center).testTag("feed_notification_action")
            )
            if (hasUnreadNotifications || unreadNotificationCount > 0) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd)
                        .offset(x = (-7).dp, y = 5.dp)
                        .size(8.dp)
                        .background(Color(0xFFF12D45), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                )
            }
        }
        FeedRadialHeaderAction(
            compact = compact,
            imageVector = Icons.Outlined.Menu,
            contentDescription = "Menu",
            onClick = onMenuClick,
            modifier = Modifier.testTag("feed_menu_action")
        )
    }
}

@Composable
private fun FeedRadialHeaderAction(
    compact: Boolean = false,
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
            .size(if (compact) 38.dp else 44.dp)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .background(
                color = if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent,
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
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Above-feed summary values are server-sourced. Never display sample rank/coin
 * numbers from the reference artwork as though they belonged to a BLINK user.
 */
@Composable
fun FeedReferenceStatsRow(
    worldRank: Int,
    coins: Long,
    onRankClick: () -> Unit,
    onCoinsClick: () -> Unit,
    rankImprovement: Int? = null,
    modifier: Modifier = Modifier
) {
    val rankLabel = if (worldRank > 0) "#${NumberFormat.getIntegerInstance().format(worldRank)}" else "—"
    val coinLabel = NumberFormat.getIntegerInstance().format(coins.coerceAtLeast(0L))
    Row(
        modifier = modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.weight(1f).height(64.dp)
                .clickable(role = Role.Button, onClick = onRankClick)
                .testTag("feed_world_rank_card"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Icon(Icons.Outlined.Language, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(30.dp))
                Column {
                    Text("World Rank", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(rankLabel, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Black, maxLines = 1)
                        // Only show movement when it is backed by a previous recorded rank.
                        if (rankImprovement != null && rankImprovement > 0) {
                            Icon(
                                Icons.Default.ArrowDropUp,
                                contentDescription = "World rank improved",
                                tint = Color(0xFF29AB48), modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.weight(1f).height(64.dp).testTag("feed_coins_card"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(start = 9.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(Icons.Default.MonetizationOn, contentDescription = "BLINK Coins",
                    tint = Color(0xFFEAB308), modifier = Modifier.size(31.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Coins", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Text(coinLabel, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Black, maxLines = 1)
                }
                Box(
                    modifier = Modifier.size(32.dp)
                        .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClick = onCoinsClick)
                        .semantics { contentDescription = "Get BLINK Coins" }
                        .testTag("feed_add_coins"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(19.dp))
                }
            }
        }
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

/**
 * Home tabs mirror the Play reference: simple black/white text and underline,
 * while content refresh stays on pull-to-refresh rather than a visible icon.
 */
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
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 58.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FeedTabLabel("For You", selectedIndex == 0, Modifier.weight(1f), onForYouClick)
        FeedTabLabel("Following", selectedIndex == 1, Modifier.weight(1f), onFollowingClick)
    }
}

@Composable
private fun FeedTabLabel(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    val lightInk = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    Column(
        modifier = modifier.fillMaxHeight()
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .testTag(if (label == "For You") "feed_tab_for_you" else "feed_tab_following"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                label, color = if (selected) ink else lightInk,
                fontSize = 15.sp, lineHeight = 19.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier.fillMaxWidth()
                .padding(horizontal = 1.dp)
                .height(2.dp)
                .background(if (selected) ink else Color.Transparent, RoundedCornerShape(2.dp))
        )
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
                                .background(MaterialTheme.colorScheme.onSurface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "AI",
                                color = MaterialTheme.colorScheme.surface,
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
                            tint = MaterialTheme.colorScheme.surface,
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
                .background(MaterialTheme.colorScheme.onSurface, pill)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f), pill)
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
                tint = MaterialTheme.colorScheme.surface,
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
                        color = MaterialTheme.colorScheme.surface,
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
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f), shape)
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

