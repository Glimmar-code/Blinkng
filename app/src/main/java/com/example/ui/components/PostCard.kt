package com.example.ui.components

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Premium home-feed card. It renders only real post media: text-only posts never
 * allocate an empty image area and video content remains routed to Reels.
 */
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
    modifier: Modifier = Modifier
) {
    val displayImages = remember(post.images) {
        post.images
            .map(String::trim)
            .filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            .distinct()
    }
    var showImageFullscreen by remember(post.id) { mutableStateOf(false) }
    var imagePage by remember(post.id) { mutableIntStateOf(0) }
    var expandedText by remember(post.id) { mutableStateOf(false) }
    val likeScale = remember(post.id) { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val likedTint by animateColorAsState(
        targetValue = if (post.isLiked) FeedPurple else FeedTextSecondary,
        animationSpec = tween(160),
        label = "postLikeTint"
    )
    val repostTint by animateColorAsState(
        targetValue = if (post.isRepostedByMe) FeedPurple else FeedTextSecondary,
        animationSpec = tween(160),
        label = "postRepostTint"
    )
    val savedTint by animateColorAsState(
        targetValue = if (post.isBookmarked) FeedBlue else FeedTextSecondary,
        animationSpec = tween(160),
        label = "postSaveTint"
    )
    val railBrush = remember {
        Brush.verticalGradient(
            listOf(FeedGradientStart, FeedGradientMiddle, FeedGradientEnd)
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = FeedCardSurface),
        border = BorderStroke(1.dp, FeedBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        brush = railBrush,
                        size = Size(4.dp.toPx(), size.height)
                    )
                }
                .padding(start = 4.dp)
        ) {
            if (post.isSponsored) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 11.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = null,
                        tint = FeedPurple,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = post.adLabel ?: "Sponsored",
                        color = FeedPurple,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            post.repostedByUsername?.takeIf(String::isNotBlank)?.let { reposter ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 17.dp, end = 17.dp, top = 10.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = null,
                        tint = FeedTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "@$reposter reposted",
                        color = FeedTextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(FeedGradientStart, FeedGradientEnd)
                            ),
                            CircleShape
                        )
                        .padding(2.dp)
                        .background(FeedCardSurface, CircleShape)
                        .padding(2.dp)
                        .clickable(role = Role.Button) { onProfileClick(post.author) }
                ) {
                    AsyncImage(
                        model = post.authorAvatar,
                        contentDescription = "${post.author} profile picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = post.author,
                            color = FeedTextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { onProfileClick(post.author) }
                        )
                        if (post.isVerified || post.verificationBadge != VerificationBadge.NONE) {
                            Spacer(Modifier.width(5.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified",
                                tint = when (post.verificationBadge) {
                                    VerificationBadge.GOLD -> BlinkGold
                                    else -> FeedBlue
                                },
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    val meta = listOf(post.timeAgo, post.facultyTag)
                        .filter(String::isNotBlank)
                        .joinToString("  •  ")
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            color = FeedTextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = onOptionsClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Post options",
                        tint = FeedTextPrimary,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }

            if (post.text.isNotBlank()) {
                Text(
                    text = post.text,
                    color = FeedTextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    maxLines = if (expandedText) Int.MAX_VALUE else 7,
                    overflow = TextOverflow.Ellipsis
                )
                if (post.text.length > 320) {
                    Text(
                        text = if (expandedText) "Show less" else "See more",
                        color = FeedPurple,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .padding(start = 16.dp, top = 6.dp)
                            .clickable { expandedText = !expandedText }
                    )
                }
            }

            post.poll?.let { poll ->
                PremiumPollCard(poll = poll) { optionId ->
                    onVotePoll(post.id, optionId)
                }
            }

            if (displayImages.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 12.dp)
                        .clip(RoundedCornerShape(18.dp))
                ) {
                    if (displayImages.size == 1) {
                        AsyncImage(
                            model = displayImages.first(),
                            contentDescription = post.altText?.takeIf(String::isNotBlank) ?: "Post image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.45f)
                                .clickable {
                                    imagePage = 0
                                    showImageFullscreen = true
                                }
                        )
                    } else {
                        val mediaState = rememberLazyListState()
                        LazyRow(
                            state = mediaState,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(
                                items = displayImages,
                                key = { _, image -> image }
                            ) { index, image ->
                                AsyncImage(
                                    model = image,
                                    contentDescription = "Post image ${index + 1} of ${displayImages.size}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillParentMaxWidth()
                                        .aspectRatio(1.45f)
                                        .clickable {
                                            imagePage = index
                                            showImageFullscreen = true
                                        }
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.62f)
                        ) {
                            Text(
                                text = "${mediaState.firstVisibleItemIndex + 1}/${displayImages.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
                color = FeedBorder.copy(alpha = 0.82f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ReadOnlyMetricAction(
                    icon = Icons.Default.Visibility,
                    value = formatNumber(post.viewsCount),
                    description = "${post.viewsCount} views"
                )
                PremiumPostAction(
                    icon = if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    value = formatNumber(post.likes),
                    tint = likedTint,
                    description = if (post.isLiked) "Unlike" else "Like",
                    iconScale = likeScale.value,
                    onClick = {
                        scope.launch {
                            likeScale.snapTo(1f)
                            likeScale.animateTo(1.18f, tween(80))
                            likeScale.animateTo(
                                1f,
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        }
                        onLike()
                    }
                )
                PremiumPostAction(
                    icon = Icons.Default.ChatBubbleOutline,
                    value = formatNumber(post.commentsCount),
                    tint = FeedTextSecondary,
                    description = "Comment",
                    onClick = onComment
                )
                if (!isAuthor) {
                    PremiumPostAction(
                        icon = Icons.Default.Repeat,
                        value = formatNumber(post.repostsCount),
                        tint = repostTint,
                        description = if (post.isRepostedByMe) "Undo repost" else "Repost",
                        onClick = onRepost
                    )
                }
                PremiumPostAction(
                    icon = if (post.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    value = "Save",
                    tint = savedTint,
                    description = if (post.isBookmarked) "Remove saved post" else "Save post",
                    onClick = onBookmark
                )
                PremiumPostAction(
                    icon = Icons.Default.Share,
                    value = null,
                    tint = FeedTextSecondary,
                    description = "Share",
                    onClick = onShare
                )
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
    onVote: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = FeedElevatedSurface),
        border = BorderStroke(1.dp, FeedBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Poll,
                    contentDescription = null,
                    tint = FeedPurple,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = poll.question,
                    color = FeedTextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.size(8.dp))
            val total = poll.options.sumOf { it.votes }.coerceAtLeast(1)
            poll.options.forEach { option ->
                val progress = option.votes.toFloat() / total
                val selected = option.isVotedByMe
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .heightIn(min = 48.dp)
                        .clickable(enabled = !poll.hasVoted && !selected) { onVote(option.id) },
                    shape = RoundedCornerShape(14.dp),
                    color = FeedCardSurface,
                    border = BorderStroke(
                        1.dp,
                        if (selected) FeedPurple.copy(alpha = 0.72f) else FeedBorder
                    )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = option.text,
                                color = FeedTextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            if (poll.hasVoted || selected) {
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    color = FeedTextSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        if (poll.hasVoted || selected) {
                            Spacer(Modifier.size(6.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = FeedPurple,
                                trackColor = FeedBorder
                            )
                        }
                    }
                }
            }
            Text(
                text = "${poll.totalVotes} votes",
                color = FeedTextMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
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
                AnimatedContent(
                    targetState = value,
                    transitionSpec = {
                        fadeIn(tween(120)) togetherWith fadeOut(tween(90))
                    },
                    label = "postActionValue"
                ) { animatedValue ->
                    Text(
                        text = animatedValue,
                        color = tint,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Tap a feed image to expand it. Drag downward to dismiss. */
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
    var downwardDrag by remember { mutableFloatStateOf(0f) }
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
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                downwardDrag = (downwardDrag + dragAmount).coerceAtLeast(0f)
                                if (downwardDrag > 150f) dismissAnimated()
                            },
                            onDragEnd = { downwardDrag = 0f },
                            onDragCancel = { downwardDrag = 0f }
                        )
                    }
                    .graphicsLayer {
                        translationY = downwardDrag
                        alpha = (1f - downwardDrag / 900f).coerceIn(0.72f, 1f)
                    },
                color = Color.Black.copy(alpha = 0.97f)
            ) {
                LazyRow(
                    state = state,
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(images, key = { _, image -> image }) { index, image ->
                        AsyncImage(
                            model = image,
                            contentDescription = "Fullscreen image ${index + 1} of ${images.size}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}
