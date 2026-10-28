package com.example.ui.components

import com.example.performance.rememberBlinkReduceMotion
import com.blinkng.shared.BlinkDesignTokens
import com.blinkng.shared.BlinkPostFormat
import com.blinkng.shared.BlinkElasticGesturePhysics
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs

import com.example.R
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.models.FeedPost
import com.example.data.models.PostPoll
import com.example.data.models.VerificationBadge
import com.example.ui.theme.BlinkGold
import com.example.ui.theme.FeedBlue
import com.example.ui.theme.FeedBorder
import com.example.ui.theme.FeedCardSurface
import com.example.ui.theme.FeedElevatedSurface
import com.example.ui.theme.FeedGradientEnd
import com.example.ui.theme.FeedGradientMiddle
import com.example.ui.theme.FeedGradientStart
import com.example.ui.theme.FeedPurple
import com.example.ui.theme.FeedTextMuted
import com.example.ui.theme.FeedTextPrimary
import com.example.ui.theme.FeedTextSecondary
import com.example.ui.theme.LightBorder
import com.example.ui.theme.LightSurface
import com.example.ui.theme.LightTextPrimary
import com.example.ui.theme.LightTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A second double-tap must never undo an existing Like. */
internal fun shouldApplyPostDoubleTapLike(alreadyLiked: Boolean): Boolean = !alreadyLiked

/**
 * Premium home-feed card. It renders only real post media: text-only posts never
 * allocate an empty image area and video content remains routed to Reels.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PostCard(
    post: FeedPost,
    isDark: Boolean,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onBookmark: () -> Unit,
    onRepost: (() -> Unit)? = null,
    onShare: () -> Unit,
    onOptionsClick: () -> Unit,
    onProfileClick: (String) -> Unit,
    onVotePoll: (postId: String, optionId: String) -> Unit = { _, _ -> },
    isAuthor: Boolean = false,
    onDelete: () -> Unit = {},
    authorName: String = post.author,
    authorUsername: String = post.authorUsername.ifBlank { post.author },
    authorAvatar: String = post.authorAvatar,
    authorUniversity: String = "",
    authorVerificationBadge: VerificationBadge = when {
        post.verificationBadge != VerificationBadge.NONE -> post.verificationBadge
        post.isVerified -> VerificationBadge.BLUE
        else -> VerificationBadge.NONE
    },
    hasActiveStory: Boolean = false,
    authorProfileId: String = "",
    isFollowingAuthor: Boolean = false,
    isFollowMutationPending: Boolean = false,
    onFollowAuthor: (String) -> Unit = {},
    onUnfollowAuthor: (String) -> Unit = {},
    onMessageAuthor: () -> Unit = {},
    onGiftCoinsAuthor: (String, Int) -> Unit = { _, _ -> },
    coinGiftingEnabled: Boolean = false,
    onChallengeAuthor: (String) -> Unit = {},
    onMentorRequestAuthor: (String) -> Unit = {},
    onFriendRequestAuthor: (String) -> Unit = {},
    onRoommateRequestAuthor: (String) -> Unit = {},
    onStudyMateRequestAuthor: (String) -> Unit = {},
    onConnectHubAuthor: () -> Unit = {},
    authorOnline: Boolean? = null,
    trackExposure: Boolean = true,
    xFeedStyle: Boolean = false,
    isFeedScrolling: Boolean = false,
    onOpenPost: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val resolvedAuthorName = authorName.trim().ifBlank { post.author.trim() }
    val resolvedAuthorUsername = authorUsername.trim().removePrefix("@").ifBlank {
        post.authorUsername.trim().removePrefix("@").ifBlank { post.author.trim().removePrefix("@") }
    }
    val profileTarget = resolvedAuthorUsername.ifBlank { post.author }
    val displayedViewsCount = rememberDelayedContentViewCount(post.id, post.viewsCount)
    val surfaceColor = if (xFeedStyle) {
        if (isDark) FeedCardSurface else Color.White
    } else if (isDark) FeedCardSurface else LightSurface
    val primaryText = if (isDark) FeedTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) FeedTextSecondary else LightTextSecondary
    val dividerColor = if (isDark) FeedBorder else LightBorder
    val socialBlue = FeedBlue

    val displayImages = remember(post.images) {
        post.images
            .map(String::trim)
            .filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            .distinct()
    }
    var showImageFullscreen by remember(post.id) { mutableStateOf(false) }
    var imagePage by remember(post.id) { mutableIntStateOf(0) }
    var expandedText by remember(post.id, post.text) { mutableStateOf(false) }
    var textCanExpand by remember(post.id, post.text) { mutableStateOf(false) }
    var followingMenuExpanded by remember(post.id) { mutableStateOf(false) }
    var coinGiftDialogOpen by remember(post.id) { mutableStateOf(false) }
    var selectedGiftAmount by remember(post.id) { mutableIntStateOf(20) }
    val likeScale = remember(post.id) { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val likedTint by animateColorAsState(
        targetValue = if (post.isLiked && xFeedStyle) primaryText else if (post.isLiked) socialBlue else secondaryText,
        animationSpec = tween(150),
        label = "simplePostLikeTint"
    )
    val repostTint by animateColorAsState(
        targetValue = if (post.isRepostedByMe && xFeedStyle) primaryText else if (post.isRepostedByMe) socialBlue else secondaryText,
        animationSpec = tween(150),
        label = "simplePostRepostTint"
    )
    val savedTint by animateColorAsState(
        targetValue = if (post.isBookmarked && xFeedStyle) primaryText else if (post.isBookmarked) socialBlue else secondaryText,
        animationSpec = tween(150),
        label = "simplePostSavedTint"
    )

    val exposureModifier = if (trackExposure) {
        Modifier.trackContentExposure(post.id, displayedViewsCount)
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .then(exposureModifier)
            .fillMaxWidth()
            .padding(
                horizontal = if (xFeedStyle) 10.dp else 12.dp,
                vertical = if (xFeedStyle) 5.dp else 7.dp
            ),
        shape = RoundedCornerShape(if (xFeedStyle) 14.dp else 16.dp),
        color = surfaceColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(if (xFeedStyle) 0.6.dp else 1.dp, dividerColor.copy(alpha = if (xFeedStyle) 0.27f else 0.52f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (post.isSponsored) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = null,
                        tint = secondaryText,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = post.adLabel ?: "Sponsored",
                        color = secondaryText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            post.repostedByUsername?.takeIf(String::isNotBlank)?.let { reposter ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 15.dp, end = 15.dp, top = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = null,
                        tint = secondaryText,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "@$reposter reposted",
                        color = secondaryText,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Exact Home reference hierarchy: avatar | name and badge | metadata | follow | options.
            if (xFeedStyle) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 13.dp, top = 12.dp, end = 8.dp, bottom = 15.dp)
                        // Header whitespace opens the full post; child controls
                        // (avatar, name, Follow and options) retain their actions.
                        .clickable(enabled = onOpenPost != null, role = Role.Button) {
                            onOpenPost?.invoke()
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(52.dp)) {
                        AsyncImage(
                            model = authorAvatar,
                            error = painterResource(R.drawable.ic_default_profile),
                            fallback = painterResource(R.drawable.ic_default_profile),
                            contentDescription = "$resolvedAuthorName profile picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(51.dp).clip(CircleShape)
                                .clickable(role = Role.Button) { onProfileClick(profileTarget) }
                        )
                        if (authorOnline == true) {
                            Box(
                                Modifier.size(10.dp).align(Alignment.BottomEnd)
                                    .background(Color(BlinkDesignTokens.Semantic.Success), CircleShape)
                                    .border(2.dp, surfaceColor, CircleShape)
                            )
                        }
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                resolvedAuthorName, color = primaryText, fontSize = 15.sp,
                                fontWeight = FontWeight.Bold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                                    .clickable { onProfileClick(profileTarget) }
                            )
                            if (authorVerificationBadge != VerificationBadge.NONE) {
                                Spacer(Modifier.width(3.dp))
                                Icon(
                                    Icons.Default.Verified, contentDescription = "Verified",
                                    tint = if (authorVerificationBadge == VerificationBadge.GOLD) BlinkGold else FeedBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.clickable(enabled = onOpenPost != null, role = Role.Button) {
                                onOpenPost?.invoke()
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                authorUniversity.trim().ifBlank { "@$resolvedAuthorUsername" },
                                color = secondaryText, fontSize = 12.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (post.timeAgo.isNotBlank()) {
                                Text(
                                    " · ${BlinkPostFormat.age(post.timeAgo)}",
                                    color = secondaryText, fontSize = 12.sp, maxLines = 1
                                )
                            }
                        }
                    }
                    if (!isAuthor && authorProfileId.isNotBlank()) {
                        Spacer(Modifier.width(5.dp))
                        Box {
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = if (isFollowingAuthor) MaterialTheme.colorScheme.surfaceVariant
                                    else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clickable(enabled = !isFollowMutationPending, role = Role.Button) {
                                        if (isFollowingAuthor) followingMenuExpanded = true
                                        else onFollowAuthor(authorProfileId)
                                    }
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (isFollowMutationPending) "Updating…" else if (isFollowingAuthor) "Interact" else "Follow",
                                        color = if (isFollowingAuthor) primaryText else MaterialTheme.colorScheme.surface,
                                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = followingMenuExpanded && isFollowingAuthor,
                                onDismissRequest = { followingMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Message") },
                                    onClick = {
                                        followingMenuExpanded = false
                                        onMessageAuthor()
                                    }
                                )
                                if (coinGiftingEnabled) {
                                    DropdownMenuItem(
                                        text = { Text("Send BLINK Coins") },
                                        onClick = {
                                            followingMenuExpanded = false
                                            coinGiftDialogOpen = true
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("View profile") },
                                    onClick = {
                                        followingMenuExpanded = false
                                        onProfileClick(profileTarget)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("I follow · Unfollow") },
                                    onClick = {
                                        followingMenuExpanded = false
                                        onUnfollowAuthor(authorProfileId)
                                    }
                                )
                            }
                        }
                        if (coinGiftDialogOpen) {
                            AlertDialog(
                                onDismissRequest = { coinGiftDialogOpen = false },
                                title = { Text("Send BLINK Coins") },
                                text = {
                                    Column {
                                        Text("Choose how many coins to send.")
                                        listOf(20, 50, 100).forEach { amount ->
                                            TextButton(onClick = { selectedGiftAmount = amount }) {
                                                Text(if (selectedGiftAmount == amount) "✓ $amount coins" else "$amount coins")
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        coinGiftDialogOpen = false
                                        onGiftCoinsAuthor(authorProfileId, selectedGiftAmount)
                                    }) { Text("Send coins") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { coinGiftDialogOpen = false }) { Text("Cancel") }
                                }
                            )
                        }
                    }
                    IconButton(onClick = onOptionsClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Post options",
                            tint = primaryText, modifier = Modifier.size(22.dp))
                    }
                }
                if (post.audience.isNotBlank() && !post.audience.equals("Everyone", ignoreCase = true)) {
                    Text(
                        "Audience: ${post.audience}", color = secondaryText, fontSize = 12.sp,
                        modifier = Modifier.padding(start = 14.dp, bottom = 4.dp)
                    )
                }
            } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = if (xFeedStyle) 10.dp else 12.dp, end = 6.dp, bottom = if (xFeedStyle) 6.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val avatarModifier = if (hasActiveStory) {
                    Modifier
                        .size(46.dp)
                        .background(socialBlue, CircleShape)
                        .padding(2.dp)
                        .background(surfaceColor, CircleShape)
                        .padding(2.dp)
                } else {
                    Modifier.size(46.dp)
                }

                // Keep the presence badge outside the clipped avatar so it cannot be
                // cut off by the circular image/story ring on narrow feed layouts.
                Box(modifier = Modifier.size(50.dp)) {
                    Box(
                        modifier = avatarModifier
                            .align(Alignment.TopStart)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { onProfileClick(profileTarget) }
                    ) {
                        AsyncImage(
                            model = authorAvatar,
                            error = painterResource(R.drawable.ic_default_profile),
                            fallback = painterResource(R.drawable.ic_default_profile),
                            contentDescription = "$resolvedAuthorName profile picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    }

                    if (authorOnline == true) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .align(Alignment.BottomEnd)
                                .background(
                                    Color(BlinkDesignTokens.Semantic.Success),
                                    CircleShape
                                )
                                .border(2.dp, surfaceColor, CircleShape)
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // Keep identity and metadata predictable on every feed surface:
                    // display name first, handle beneath it, time aligned right.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = resolvedAuthorName,
                            color = primaryText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable { onProfileClick(profileTarget) }
                        )
                        if (authorVerificationBadge != VerificationBadge.NONE) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = when (authorVerificationBadge) {
                                    VerificationBadge.GOLD -> "Gold verified"
                                    VerificationBadge.BLUE -> "Blue verified"
                                    VerificationBadge.NONE -> null
                                },
                                tint = when (authorVerificationBadge) {
                                    VerificationBadge.GOLD -> BlinkGold
                                    VerificationBadge.BLUE -> socialBlue
                                    VerificationBadge.NONE -> socialBlue
                                },
                                modifier = Modifier.size(16.dp)
                            )
                        }

                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (resolvedAuthorUsername.isNotBlank()) {
                            Text(
                                text = "@$resolvedAuthorUsername",
                                color = secondaryText,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .clickable { onProfileClick(profileTarget) }
                            )
                        }
                        if (post.timeAgo.isNotBlank()) {
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = post.timeAgo,
                                color = secondaryText,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                        // Avoid showing redundant "Everyone", "Post" and "Live" labels.
                        // Restricted audiences stay clearly visible for privacy.
                        if (post.audience.isNotBlank() &&
                            !post.audience.equals("Everyone", ignoreCase = true)) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = "Audience: ${post.audience}",
                                tint = secondaryText,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = post.audience,
                                color = secondaryText,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Compact, always-visible follow control on X-style Home cards.
                    // The old gate hid following entirely on the main feed.
                    // One relationship control across Home, Reels and Profile.
                    // Keep the pending state visible and disable all mutation
                    // buttons while the follow/unfollow request is in flight.
                    if (!isAuthor && authorProfileId.isNotBlank()) {
                        Spacer(Modifier.size(6.dp))
                        if (isFollowMutationPending) {
                            Text(
                                "Updating…",
                                color = secondaryText,
                                style = MaterialTheme.typography.labelMedium
                            )
                        } else {
                            ProfileFollowInteractButton(
                                isFollowing = isFollowingAuthor,
                                onFollow = { onFollowAuthor(authorProfileId) },
                                onUnfollow = { onUnfollowAuthor(authorProfileId) },
                                onMessage = onMessageAuthor,
                                onGiftCoins = { amount -> onGiftCoinsAuthor(authorProfileId, amount) },
                                onGameChallenge = { onChallengeAuthor(authorProfileId) },
                                onMentorRequest = { onMentorRequestAuthor(authorProfileId) },
                                onFriendRequest = { onFriendRequestAuthor(authorProfileId) },
                                onRoommateRequest = { onRoommateRequestAuthor(authorProfileId) },
                                onStudyMateRequest = { onStudyMateRequestAuthor(authorProfileId) },
                                onViewProfile = { onProfileClick(profileTarget) },
                                onOpenConnectHub = onConnectHubAuthor,
                                darkSurface = isDark,
                                giftingEnabled = coinGiftingEnabled
                            )
                        }
                    }
                }

                if (!xFeedStyle) {
                    IconButton(
                        onClick = onBookmark,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (post.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (post.isBookmarked) "Remove saved post" else "Save post",
                            tint = savedTint,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onOptionsClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Post options",
                        tint = primaryText,
                        modifier = Modifier.size(23.dp)
                    )
                }
            }

            }

            if (post.text.isNotBlank()) {
                val hasSavedTextStyle = post.textStyle?.isNotBlank() == true &&
                    displayImages.isEmpty() && post.poll == null
                if (hasSavedTextStyle) {
                    val textStyle = resolveTextPostStyle(post.textStyle, post.id)
                    val textSize = when {
                        post.text.length > 320 -> 21.sp
                        post.text.length > 170 -> 24.sp
                        post.text.length > 80 -> 27.sp
                        else -> 29.sp
                    }
                    val textCardMinHeight = when {
                        post.text.length <= 80 -> 180.dp
                        post.text.length <= 180 -> 220.dp
                        else -> 250.dp
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = if (xFeedStyle) 14.dp else 0.dp, end = if (xFeedStyle) 14.dp else 0.dp)
                            .clip(RoundedCornerShape(if (xFeedStyle) 14.dp else 0.dp))
                            .heightIn(min = textCardMinHeight)
                            .background(textStyle.brush())
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SelectionContainer {
                            Text(
                                text = post.text,
                                modifier = Modifier.clickable(enabled = onOpenPost != null) { onOpenPost?.invoke() },
                                color = textStyle.textColor,
                                fontSize = textSize,
                                lineHeight = textSize * 1.18f,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = if (expandedText) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis,
                                onTextLayout = { result ->
                                    if (!expandedText) {
                                        textCanExpand = result.hasVisualOverflow
                                    }
                                }
                            )
                        }
                    }
                } else {
                    SelectionContainer {
                        Text(
                            text = post.text,
                            color = primaryText,
                            style = if (xFeedStyle) MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                lineHeight = 22.sp
                            ) else MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = if (xFeedStyle) 14.dp else 10.dp)
                                .clickable(enabled = onOpenPost != null) { onOpenPost?.invoke() },
                            maxLines = if (expandedText) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { result ->
                                if (!expandedText) {
                                    textCanExpand = result.hasVisualOverflow
                                }
                            }
                        )
                    }
                }
                if (textCanExpand || expandedText) {
                    Text(
                        text = if (expandedText) "Show less" else "See more",
                        color = socialBlue,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(start = 14.dp, top = 6.dp, bottom = 8.dp)
                            .heightIn(min = 40.dp)
                            .clickable(role = Role.Button) { expandedText = !expandedText }
                    )
                }
            }

            val postLinks = listOfNotNull(
                post.text.takeIf { it.isNotBlank() },
                post.linkUrl?.takeIf { it.isNotBlank() }
            ).joinToString(" ")
            if (postLinks.isNotBlank()) {
                BlinkRichLinkPreview(
                    text = postLinks,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }

            post.poll?.let { poll ->
                PremiumPollCard(
                    poll = poll,
                    modifier = Modifier.padding(start = if (xFeedStyle) 14.dp else 0.dp)
                ) { optionId ->
                    onVotePoll(post.id, optionId)
                }
            }

            if (displayImages.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (xFeedStyle) 14.dp else 0.dp,
                            end = if (xFeedStyle) 14.dp else 0.dp,
                            bottom = if (xFeedStyle) 6.dp else 0.dp
                        )
                ) {
                    if (displayImages.size == 1) {
                        NaturalAspectPostImage(
                            imageUrl = displayImages.first(),
                            contentDescription = post.altText?.takeIf(String::isNotBlank) ?: "Post image",
                            cropToFrame = false,
                            deferAspectChanges = isFeedScrolling,
                            onDoubleTapLike = if (xFeedStyle) ({ if (shouldApplyPostDoubleTapLike(post.isLiked)) onLike() }) else null,
                            onClick = {
                                imagePage = 0
                                showImageFullscreen = true
                            }
                        )
                    } else {
                        val mediaState = rememberLazyListState()
                        val mediaFlingBehavior = rememberSnapFlingBehavior(mediaState)
                        Column {
                        LazyRow(
                            state = mediaState,
                            modifier = Modifier.fillMaxWidth(),
                            flingBehavior = mediaFlingBehavior
                        ) {
                            itemsIndexed(
                                items = displayImages,
                                key = { index, image -> "image:$index:$image" }
                            ) { index, image ->
                                NaturalAspectPostImage(
                                    imageUrl = image,
                                    contentDescription = "Post image ${index + 1} of ${displayImages.size}",
                                    modifier = Modifier.fillParentMaxWidth(),
                                    cropToFrame = false,
                                    deferAspectChanges = isFeedScrolling,
                                    onDoubleTapLike = if (xFeedStyle) ({ if (shouldApplyPostDoubleTapLike(post.isLiked)) onLike() }) else null,
                                    onClick = {
                                        imagePage = index
                                        showImageFullscreen = true
                                    }
                                )
                            }
                        }
                        // Place the counter *below* the photograph instead of over
                        // its corner, so no part of the uploaded image is covered.
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "${mediaState.firstVisibleItemIndex + 1}/${displayImages.size}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        }
                    }
                }
            }

            if (xFeedStyle) {
                // Like remains visible for every post, including posts with images.
                // Views are read-only metadata below the action row (no eye icon).
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PremiumPostAction(
                        icon = if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        value = if (post.hideLikes) null else formatNumber(post.likes),
                        tint = likedTint,
                        description = if (post.isLiked) "Unlike post" else "Like post",
                        onClick = onLike
                    )
                    PremiumPostAction(
                        icon = Icons.Default.ChatBubbleOutline,
                        value = formatNumber(post.commentsCount), tint = primaryText,
                        description = "Comments", enabled = post.allowComments,
                        onClick = onComment
                    )
                    PremiumPostAction(
                        icon = Icons.Default.Repeat,
                        value = formatNumber(post.repostsCount), tint = if (post.isRepostedByMe) repostTint else primaryText,
                        description = if (post.isRepostedByMe) "Undo repost" else "Repost",
                        enabled = !isAuthor && onRepost != null,
                        onClick = { onRepost?.invoke() }
                    )
                    PremiumPostAction(
                        icon = if (post.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        // Render the backend aggregate when known, never a placeholder.
                        value = post.savesCount?.let(::formatNumber), tint = if (post.isBookmarked) savedTint else primaryText,
                        description = if (post.isBookmarked) "Remove saved post" else "Save post",
                        onClick = onBookmark
                    )
                    PremiumPostAction(
                        icon = Icons.Default.Share, value = null,
                        tint = primaryText, description = "Share post", onClick = onShare
                    )
                }
                Text(
                    text = "${formatNumber(displayedViewsCount)} views",
                    color = secondaryText.copy(alpha = 0.82f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 15.dp, bottom = 9.dp)
                        .clickable(enabled = onOpenPost != null) { onOpenPost?.invoke() }
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val primaryMetrics = buildList {
                        add("${formatNumber(displayedViewsCount)} views")
                        if (!post.hideLikes && post.likes > 0) {
                            add("${formatNumber(post.likes)} likes")
                        }
                    }
                    val secondaryMetrics = buildList {
                        if (post.commentsCount > 0) {
                            add("${formatNumber(post.commentsCount)} comments")
                        }
                        if (post.sharesCount > 0) {
                            add("${formatNumber(post.sharesCount)} shares")
                        }
                    }
                    Text(
                        text = primaryMetrics.joinToString("  ·  "),
                        color = secondaryText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                    Spacer(Modifier.weight(1f))
                    if (secondaryMetrics.isNotEmpty()) {
                        Text(
                            text = secondaryMetrics.joinToString("  ·  "),
                            color = secondaryText,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }
                }
    
                HorizontalDivider(color = dividerColor)
    
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val compactActions = maxWidth < 360.dp
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PremiumPostAction(
                            icon = if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            value = if (compactActions) null else "Like",
                            tint = likedTint,
                            description = if (post.isLiked) "Unlike" else "Like",
                            iconScale = likeScale.value,
                            onClick = {
                                scope.launch {
                                    likeScale.snapTo(1f)
                                    likeScale.animateTo(1.10f, tween(90))
                                    likeScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                                }
                                onLike()
                            }
                        )
                        PremiumPostAction(
                            icon = Icons.Default.ChatBubbleOutline,
                            value = if (compactActions) null else "Comment",
                            tint = secondaryText,
                            description = if (post.allowComments) "Comment" else "Comments disabled",
                            enabled = post.allowComments,
                            onClick = onComment
                        )
                        if (!isAuthor) {
                            PremiumPostAction(
                                icon = Icons.Default.Repeat,
                                value = if (compactActions) null else "Repost",
                                tint = repostTint,
                                description = if (onRepost == null) "Repost unavailable here" else if (post.isRepostedByMe) "Undo repost" else "Repost",
                                enabled = onRepost != null,
                                onClick = { onRepost?.invoke() }
                            )
                        }
                        PremiumPostAction(
                            icon = Icons.Default.Share,
                            value = if (compactActions) null else "Share",
                            tint = secondaryText,
                            description = "Share",
                            onClick = onShare
                        )
                    }
                }
    
                HorizontalDivider(color = dividerColor)
            }
        }
    }

    if (showImageFullscreen && displayImages.isNotEmpty()) {
        ImageFullscreenDialog(
            images = displayImages,
            initialPage = imagePage,
            onDismiss = { showImageFullscreen = false }
        )
    }
}

internal fun pollVotingClosed(expiresAtMillis: Long?, nowMillis: Long): Boolean =
    expiresAtMillis != null && nowMillis >= expiresAtMillis

@Composable
private fun PremiumPollCard(
    poll: PostPoll,
    modifier: Modifier = Modifier,
    onVote: (String) -> Unit
) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val selectedColor = MaterialTheme.colorScheme.primary
    // Debounce taps until the server has had time to accept the vote; a
    // failed request can still be retried after the cooldown.
    val lastVoteAtMs = remember(poll.question) { longArrayOf(0L) }
    var wallClock by remember(poll.expiresAtMillis) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(poll.expiresAtMillis) {
        val deadline = poll.expiresAtMillis ?: return@LaunchedEffect
        while (wallClock < deadline) {
            delay(minOf(60_000L, (deadline - wallClock).coerceAtLeast(1_000L)))
            wallClock = System.currentTimeMillis()
        }
    }
    val pollClosed = pollVotingClosed(poll.expiresAtMillis, wallClock)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Poll,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = poll.question,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.size(7.dp))
        val total = poll.options.sumOf { it.votes }.coerceAtLeast(1)
        poll.options.forEach { option ->
            val progress = option.votes.toFloat() / total
            val selected = option.isVotedByMe
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .heightIn(min = 46.dp)
                    .clickable(enabled = !pollClosed && !poll.hasVoted && !selected) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastVoteAtMs[0] >= 1500L) {
                        lastVoteAtMs[0] = now
                        onVote(option.id)
                    }
                },
                shape = RoundedCornerShape(10.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, if (selected) selectedColor else outline)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = option.text,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (pollClosed || poll.hasVoted || selected) {
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    if (pollClosed || poll.hasVoted || selected) {
                        Spacer(Modifier.size(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = selectedColor,
                            trackColor = outline
                        )
                    }
                }
            }
        }
        Text(
            text = if (pollClosed) "${poll.totalVotes} votes · Poll closed"
            else "${poll.totalVotes} votes",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun RowScope.ReadOnlyMetricAction(
    icon: ImageVector,
    value: String,
    description: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = value,
                color = tint,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RowScope.PremiumPostAction(
    icon: ImageVector,
    value: String?,
    tint: Color,
    description: String,
    iconScale: Float = 1f,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .blinkPressFeedback(
                interactionSource = interactionSource,
                enabled = enabled,
                pressedScale = 0.97f
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                enabled = enabled,
                onClick = onClick
            )
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
            if (!value.isNullOrBlank()) {
                Spacer(Modifier.width(2.dp))
                Text(
                    text = value,
                    color = tint,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Renders feed media at the uploaded image's own aspect ratio instead of forcing
 * every post into one crop. The full feed width is preserved while portrait,
 * square, and landscape images are allowed to use the vertical space they need.
 */
@Composable
private fun NaturalAspectPostImage(
    imageUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    cropToFrame: Boolean = false,
    deferAspectChanges: Boolean = false,
    onDoubleTapLike: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    // Show the original image ratio without changing card height mid-scroll.
    // Keep a small ratio-only cache; never keep decoded bitmaps in Compose state.
    var imageAspectRatio by remember(imageUrl, cropToFrame) {
        mutableFloatStateOf(
            if (cropToFrame) 4f / 5f
            else PostImageAspectRatioCache.get(imageUrl) ?: 4f / 5f
        )
    }
    var pendingImageAspectRatio by remember(imageUrl) { mutableStateOf<Float?>(null) }
    LaunchedEffect(deferAspectChanges, cropToFrame, pendingImageAspectRatio) {
        if (!deferAspectChanges && !cropToFrame) {
            pendingImageAspectRatio?.let { ratio ->
                imageAspectRatio = ratio
                pendingImageAspectRatio = null
            }
        }
    }
    var isLoading by remember(imageUrl) { mutableStateOf(true) }
    var loadFailed by remember(imageUrl) { mutableStateOf(false) }
    var retryNonce by remember(imageUrl) { mutableIntStateOf(0) }
    val request = remember(imageUrl, retryNonce, context) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            // Fading multiple decoded images during a fast fling adds GPU work.
            .crossfade(false)
            .build()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(imageAspectRatio.coerceIn(0.50f, 1.91f))
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
            .then(
                if (onDoubleTapLike == null) Modifier.clickable(enabled = !loadFailed, onClick = onClick)
                else Modifier.pointerInput(imageUrl, loadFailed, onDoubleTapLike) {
                    detectTapGestures(
                        onTap = { if (!loadFailed) onClick() },
                        onDoubleTap = { if (!loadFailed) onDoubleTapLike() }
                    )
                }.semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Like post") { onDoubleTapLike(); true },
                        CustomAccessibilityAction("Open image") { onClick(); true }
                    )
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = if (cropToFrame) ContentScale.Crop else ContentScale.Fit,
            onLoading = {
                isLoading = true
                loadFailed = false
            },
            onSuccess = { state ->
                isLoading = false
                loadFailed = false
                val drawable = state.result.drawable
                val width = drawable.intrinsicWidth
                val height = drawable.intrinsicHeight
                if (width > 0 && height > 0) {
                    val ratio = (width.toFloat() / height.toFloat()).coerceIn(0.50f, 1.91f)
                    PostImageAspectRatioCache.put(imageUrl, ratio)
                    if (!cropToFrame && ratio != imageAspectRatio) {
                        pendingImageAspectRatio = ratio
                    }
                }
            },
            onError = {
                isLoading = false
                loadFailed = true
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isLoading) {
            // Static placeholder avoids a spinner animation on every loading card.
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )
        }

        if (loadFailed) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = "Image unavailable · Tap to retry",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .clickable {
                            loadFailed = false
                            isLoading = true
                            retryNonce += 1
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}

/**
 * Tap a feed image to open it full-screen. Pinch with two fingers to zoom up to
 * 5x and pan around the enlarged image. Back or the close button exits safely.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ImageFullscreenDialog(
    images: List<String>,
    initialPage: Int,
    onDismiss: () -> Unit
) {
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = initialPage.coerceIn(0, (images.size - 1).coerceAtLeast(0))
    )
    var entered by remember { mutableStateOf(false) }
    var dragDown by remember { mutableFloatStateOf(0f) }
    var gestureActive by remember { mutableStateOf(false) }
    val dismissActivation = with(LocalDensity.current) { 110.dp.toPx() }
    val flickMinimum = with(LocalDensity.current) { 28.dp.toPx() }
    val flickVelocity = with(LocalDensity.current) { 1100.dp.toPx() }
    val reducedMotion = rememberBlinkReduceMotion()
    val visualDown by animateFloatAsState(
        targetValue = if (reducedMotion) 0f else BlinkElasticGesturePhysics.visualDistance(dragDown, dismissActivation),
        animationSpec = if (gestureActive || reducedMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ), label = "feedImageElasticDismiss"
    )
    val scope = rememberCoroutineScope()

    fun dismissAnimated() {
        if (!entered) return
        entered = false
        if (reducedMotion) {
            onDismiss()
        } else {
            scope.launch {
                delay(170)
                onDismiss()
            }
        }
    }

    LaunchedEffect(Unit) { entered = true }

    Dialog(
        onDismissRequest = ::dismissAnimated,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedVisibility(
            visible = entered,
            enter = if (reducedMotion) EnterTransition.None else fadeIn(tween(280)) + scaleIn(
                initialScale = 0.94f,
                animationSpec = tween(280)
            ),
            exit = if (reducedMotion) ExitTransition.None else fadeOut(tween(160)) + scaleOut(
                targetScale = 0.97f,
                animationSpec = tween(160)
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = visualDown }
                    .pointerInput(images, dismissActivation) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val velocity = VelocityTracker()
                            velocity.addPosition(down.uptimeMillis, down.position)
                            var handledByChild = false
                            var committed = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Final)
                                if (event.changes.size > 1) handledByChild = true
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.pressed && change.isConsumed) handledByChild = true
                                velocity.addPosition(change.uptimeMillis, change.position)
                                val deltaX = change.position.x - down.position.x
                                val deltaY = change.position.y - down.position.y
                                val downward = deltaY > 0f && deltaY > abs(deltaX) * 1.35f
                                if (change.pressed) {
                                    gestureActive = !handledByChild && downward
                                    dragDown = if (gestureActive) deltaY else 0f
                                } else {
                                    committed = !handledByChild && downward &&
                                        BlinkElasticGesturePhysics.shouldCommit(
                                            deltaY, BlinkElasticGesturePhysics.resolvedFlickVelocity(
                                                deltaY, velocity.calculateVelocity().y,
                                                change.uptimeMillis - down.uptimeMillis
                                            ), dismissActivation, flickMinimum, flickVelocity
                                        )
                                    break
                                }
                            }
                            gestureActive = false
                            dragDown = 0f
                            if (committed) dismissAnimated()
                        }
                    },
                color = Color.Black.copy(alpha = 0.97f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyRow(
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                        flingBehavior = rememberSnapFlingBehavior(state)
                    ) {
                        itemsIndexed(images, key = { index, image -> "image:$index:$image" }) { index, image ->
                            ZoomableFullscreenImage(
                                imageUrl = image,
                                contentDescription = "Fullscreen image ${index + 1} of ${images.size}",
                                modifier = Modifier.fillParentMaxWidth()
                            )
                        }
                    }

                    if (images.size > 1) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 28.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.58f)
                        ) {
                            Text(
                                text = "${state.firstVisibleItemIndex + 1}/${images.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = ::dismissAnimated,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 18.dp, end = 14.dp)
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.48f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close image",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableFullscreenImage(
    imageUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    var scale by remember(imageUrl) { mutableFloatStateOf(1f) }
    var offset by remember(imageUrl) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(imageUrl) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                                offset = Offset.Zero
                            }
                        }
                    )
                }
                .pointerInput(imageUrl) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        val maxX = (size.width * (newScale - 1f)) / 2f
                        val maxY = (size.height * (newScale - 1f)) / 2f

                        scale = newScale
                        offset = if (newScale <= 1.01f) {
                            Offset.Zero
                        } else {
                            Offset(
                                x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                            )
                        }
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}
