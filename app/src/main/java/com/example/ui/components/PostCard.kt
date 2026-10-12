package com.example.ui.components

import com.example.R
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
    onRepost: () -> Unit = {},
    onShare: () -> Unit,
    onOptionsClick: () -> Unit,
    onProfileClick: (String) -> Unit,
    onVotePoll: (postId: String, optionId: String) -> Unit = { _, _ -> },
    isAuthor: Boolean = false,
    onDelete: () -> Unit = {},
    authorName: String = post.author,
    authorUsername: String = post.authorUsername.ifBlank { post.author },
    authorVerificationBadge: VerificationBadge = when {
        post.verificationBadge != VerificationBadge.NONE -> post.verificationBadge
        post.isVerified -> VerificationBadge.BLUE
        else -> VerificationBadge.NONE
    },
    hasActiveStory: Boolean = false,
    authorProfileId: String = "",
    isFollowingAuthor: Boolean = false,
    onFollowAuthor: (String) -> Unit = {},
    onUnfollowAuthor: (String) -> Unit = {},
    onMessageAuthor: () -> Unit = {},
    onGiftCoinsAuthor: (String) -> Unit = {},
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
    modifier: Modifier = Modifier
) {
    val resolvedAuthorName = authorName.trim().ifBlank { post.author.trim() }
    val resolvedAuthorUsername = authorUsername.trim().removePrefix("@").ifBlank {
        post.authorUsername.trim().removePrefix("@").ifBlank { post.author.trim().removePrefix("@") }
    }
    val profileTarget = resolvedAuthorUsername.ifBlank { post.author }
    val displayedViewsCount = rememberDelayedContentViewCount(post.id, post.viewsCount)
    val surfaceColor = if (xFeedStyle && isDark) Color.Black else if (isDark) FeedCardSurface else LightSurface
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
    val likeScale = remember(post.id) { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val likedTint by animateColorAsState(
        targetValue = if (post.isLiked) socialBlue else secondaryText,
        animationSpec = tween(150),
        label = "simplePostLikeTint"
    )
    val repostTint by animateColorAsState(
        targetValue = if (post.isRepostedByMe) socialBlue else secondaryText,
        animationSpec = tween(150),
        label = "simplePostRepostTint"
    )
    val savedTint by animateColorAsState(
        targetValue = if (post.isBookmarked) socialBlue else secondaryText,
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
                horizontal = if (xFeedStyle) 0.dp else 12.dp,
                vertical = if (xFeedStyle) 0.dp else 6.dp
            ),
        shape = RoundedCornerShape(if (xFeedStyle) 0.dp else 16.dp),
        color = surfaceColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = if (xFeedStyle) null else BorderStroke(1.dp, dividerColor.copy(alpha = 0.72f))
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
                            model = post.authorAvatar,
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
                                    Color(0xFF22C55E),
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
                            maxLines = 1,
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
                        BlinkVipMarkForUsername(
                            username = resolvedAuthorUsername,
                            knownVip = if (post.authorIsVip) true else null,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        if (post.timeAgo.isNotBlank()) {
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = post.timeAgo,
                                color = secondaryText,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (resolvedAuthorUsername.isNotBlank()) {
                            Text(
                                text = "@$resolvedAuthorUsername",
                                color = secondaryText,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { onProfileClick(profileTarget) }
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

                    if (!xFeedStyle && !isAuthor && authorProfileId.isNotBlank()) {
                        Spacer(Modifier.size(6.dp))
                        ProfileFollowInteractButton(
                            isFollowing = isFollowingAuthor,
                            onFollow = { onFollowAuthor(authorProfileId) },
                            onUnfollow = { onUnfollowAuthor(authorProfileId) },
                            onMessage = onMessageAuthor,
                            onGiftCoins = { onGiftCoinsAuthor(authorProfileId) },
                            onGameChallenge = { onChallengeAuthor(authorProfileId) },
                            onMentorRequest = { onMentorRequestAuthor(authorProfileId) },
                            onFriendRequest = { onFriendRequestAuthor(authorProfileId) },
                            onRoommateRequest = { onRoommateRequestAuthor(authorProfileId) },
                            onStudyMateRequest = { onStudyMateRequestAuthor(authorProfileId) },
                            onViewProfile = { onProfileClick(profileTarget) },
                            onOpenConnectHub = onConnectHubAuthor
                        )
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
                            .padding(start = if (xFeedStyle) 74.dp else 0.dp, end = if (xFeedStyle) 12.dp else 0.dp)
                            .clip(RoundedCornerShape(if (xFeedStyle) 14.dp else 0.dp))
                            .heightIn(min = textCardMinHeight)
                            .background(textStyle.brush())
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SelectionContainer {
                            Text(
                                text = post.text,
                                color = textStyle.textColor,
                                fontSize = textSize,
                                lineHeight = textSize * 1.18f,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = if (expandedText) Int.MAX_VALUE else 14,
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
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = if (xFeedStyle) 74.dp else 14.dp, end = 14.dp, bottom = 10.dp),
                            maxLines = if (expandedText) Int.MAX_VALUE else 7,
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
                            .padding(start = if (xFeedStyle) 74.dp else 14.dp, top = 6.dp, bottom = 8.dp)
                            .heightIn(min = 40.dp)
                            .clickable(role = Role.Button) { expandedText = !expandedText }
                    )
                }
            }

            post.poll?.let { poll ->
                PremiumPollCard(
                    poll = poll,
                    modifier = Modifier.padding(start = if (xFeedStyle) 60.dp else 0.dp)
                ) { optionId ->
                    onVotePoll(post.id, optionId)
                }
            }

            if (displayImages.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (xFeedStyle) 74.dp else 0.dp,
                            end = if (xFeedStyle) 12.dp else 0.dp,
                            bottom = if (xFeedStyle) 6.dp else 0.dp
                        )
                ) {
                    if (displayImages.size == 1) {
                        NaturalAspectPostImage(
                            imageUrl = displayImages.first(),
                            contentDescription = post.altText?.takeIf(String::isNotBlank) ?: "Post image",
                            cropToFrame = xFeedStyle,
                            deferAspectChanges = isFeedScrolling,
                            onClick = {
                                imagePage = 0
                                showImageFullscreen = true
                            }
                        )
                    } else {
                        val mediaState = rememberLazyListState()
                        val mediaFlingBehavior = rememberSnapFlingBehavior(mediaState)
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
                                    cropToFrame = xFeedStyle,
                                    deferAspectChanges = isFeedScrolling,
                                    onClick = {
                                        imagePage = index
                                        showImageFullscreen = true
                                    }
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Black.copy(alpha = 0.62f)
                        ) {
                            Text(
                                text = "${mediaState.firstVisibleItemIndex + 1}/${displayImages.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (xFeedStyle) {
                // Stable, compact priority: views -> likes -> comments -> reposts
                // -> save -> share. Counter updates never re-order the row.
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    // Redmi-class phones have six actions competing for a narrow
                    // metric rail. Always prioritize visible views and likes; show
                    // extra counts only where each touch target has enough width.
                    val showCoreCounts = maxWidth >= 340.dp
                    val showSecondaryCounts = maxWidth >= 450.dp
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 64.dp, end = 6.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ReadOnlyMetricAction(
                            icon = Icons.Default.Visibility,
                            value = if (showCoreCounts) formatNumber(displayedViewsCount) else "",
                            description = "${displayedViewsCount} views"
                        )
                        PremiumPostAction(
                            icon = if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            value = if (showCoreCounts && !post.hideLikes && post.likes > 0) formatNumber(post.likes) else null,
                            tint = likedTint,
                            description = if (post.isLiked) "Unlike post · ${post.likes} likes" else "Like post · ${post.likes} likes",
                            iconScale = likeScale.value,
                            onClick = {
                                scope.launch {
                                    likeScale.snapTo(1f)
                                    likeScale.animateTo(1.10f, tween(90))
                                    likeScale.animateTo(1f, spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ))
                                }
                                onLike()
                            }
                        )
                        PremiumPostAction(
                            icon = Icons.Default.ChatBubbleOutline,
                            value = if (showSecondaryCounts && post.commentsCount > 0) formatNumber(post.commentsCount) else null,
                            tint = secondaryText,
                            description = if (post.allowComments) "${post.commentsCount} comments" else "Comments disabled",
                            enabled = post.allowComments,
                            onClick = onComment
                        )
                        if (!isAuthor) {
                            PremiumPostAction(
                                icon = Icons.Default.Repeat,
                                value = if (showSecondaryCounts && post.repostsCount > 0) formatNumber(post.repostsCount) else null,
                                tint = repostTint,
                                description = if (post.isRepostedByMe) "Undo repost · ${post.repostsCount} reposts" else "Repost · ${post.repostsCount} reposts",
                                onClick = onRepost
                            )
                        }
                        PremiumPostAction(
                            icon = if (post.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            value = null,
                            tint = savedTint,
                            description = if (post.isBookmarked) "Remove saved post" else "Save post",
                            onClick = onBookmark
                        )
                        PremiumPostAction(
                            icon = Icons.Default.Share,
                            value = null,
                            tint = secondaryText,
                            description = "Share post",
                            onClick = onShare
                        )
                    }
                }
                HorizontalDivider(color = dividerColor.copy(alpha = 0.65f))
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
                                description = if (post.isRepostedByMe) "Undo repost" else "Repost",
                                onClick = onRepost
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
                    .clickable(enabled = !poll.hasVoted && !selected) {
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
                        if (poll.hasVoted || selected) {
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    if (poll.hasVoted || selected) {
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
            text = "${poll.totalVotes} votes",
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
    description: String
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
                tint = FeedTextSecondary,
                modifier = Modifier.size(21.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = value,
                color = FeedTextSecondary,
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
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(100),
        label = "postActionPressScale"
    )
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
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
                    .size(21.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
            if (!value.isNullOrBlank()) {
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
    onClick: () -> Unit
) {
    val context = LocalContext.current
    // Reuse measured ratios across recycled feed cards. While the finger is
    // moving, defer first-load ratio changes so the viewport never shifts.
    // Use a stable frame throughout this card's composition lifetime.
    // Image completion updates the bounded cache for future card compositions,
    // never the currently visible row height (which would jump the viewport).
    // Fixed 4:5 previews on the X-style feed cannot jump in height when
    // their decoded dimensions change. Fullscreen still displays the original.
    val imageAspectRatio = remember(imageUrl, cropToFrame) {
        if (cropToFrame) 4f / 5f
        else PostImageAspectRatioCache.get(imageUrl) ?: 4f / 5f
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
            .aspectRatio(imageAspectRatio.coerceIn(0.72f, 1.91f))
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
            .clickable(enabled = !loadFailed, onClick = onClick),
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
                    val ratio = (width.toFloat() / height.toFloat()).coerceIn(0.72f, 1.91f)
                    PostImageAspectRatioCache.put(imageUrl, ratio)
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
    val scope = rememberCoroutineScope()

    fun dismissAnimated() {
        if (!entered) return
        entered = false
        scope.launch {
            delay(170)
            onDismiss()
        }
    }

    LaunchedEffect(Unit) { entered = true }

    Dialog(
        onDismissRequest = ::dismissAnimated,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedVisibility(
            visible = entered,
            enter = fadeIn(tween(280)) + scaleIn(
                initialScale = 0.94f,
                animationSpec = tween(280)
            ),
            exit = fadeOut(tween(160)) + scaleOut(
                targetScale = 0.97f,
                animationSpec = tween(160)
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
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
