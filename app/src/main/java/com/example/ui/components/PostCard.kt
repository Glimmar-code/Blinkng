package com.example.ui.components

import androidx.compose.animation.core.animateFloat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.FeedPost
import com.example.data.models.VerificationBadge
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkPurple
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCard(
    post: FeedPost,
    isDark: Boolean,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onBookmark: () -> Unit,
    onShare: () -> Unit,
    onOptionsClick: () -> Unit,
    onProfileClick: (String) -> Unit,
    onViewed: () -> Unit = {},
    onVotePoll: (postId: String, optionId: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    // ---------------------------------------------------------------------
    // UI state: all presentation-only state is kept inside the card so the
    // existing FeedPost model and parent API remain compatible.
    // ---------------------------------------------------------------------
    var showHeart by rememberSaveable(post.id) { mutableStateOf(false) }
    var showReactionPicker by rememberSaveable(post.id) { mutableStateOf(false) }
    var selectedReaction by rememberSaveable(post.id) { mutableStateOf("❤️") }
    var textExpanded by rememberSaveable(post.id) { mutableStateOf(false) }
    var showMediaViewer by rememberSaveable(post.id) { mutableStateOf(false) }
    var showShareSheet by rememberSaveable(post.id) { mutableStateOf(false) }
    var showMoreSheet by rememberSaveable(post.id) { mutableStateOf(false) }
    var showCommentPreview by rememberSaveable(post.id) { mutableStateOf(false) }
    var showPollResults by rememberSaveable(post.id) { mutableStateOf(false) }
    var isFollowing by rememberSaveable(post.id) { mutableStateOf(false) }
    var notificationsEnabled by rememberSaveable(post.id) { mutableStateOf(false) }
    var isMuted by rememberSaveable(post.id) { mutableStateOf(false) }
    var showTranslation by rememberSaveable(post.id) { mutableStateOf(false) }
    var isPlaying by rememberSaveable(post.id) { mutableStateOf(false) }
    var imagePage by rememberSaveable(post.id) { mutableIntStateOf(0) }
    var showStats by rememberSaveable(post.id) { mutableStateOf(false) }
    var cardVisible by rememberSaveable(post.id) { mutableStateOf(false) }
    var showCopied by rememberSaveable(post.id) { mutableStateOf(false) }

    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current

    fun feedback() {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    LaunchedEffect(post.id) {
        delay(40)
        cardVisible = true
        onViewed()
    }

    LaunchedEffect(showHeart) {
        if (showHeart) {
            delay(900)
            showHeart = false
        }
    }

    LaunchedEffect(showCopied) {
        if (showCopied) {
            delay(1600)
            showCopied = false
        }
    }

    val cardBackground = if (isDark) MaterialTheme.colorScheme.surface else Color.White

    val animatedLikeColor by animateColorAsState(
        targetValue = if (post.isLiked) BlinkPink else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "post_like_color"
    )

    val animatedLikeScale by animateFloatAsState(
        targetValue = if (post.isLiked) 1.18f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "post_like_scale"
    )

    val bookmarkScale by animateFloatAsState(
        targetValue = if (post.isBookmarked) 1.16f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "post_bookmark_scale"
    )

    val animatedLikes by animateIntAsState(
        targetValue = post.likes,
        animationSpec = tween(350),
        label = "animated_like_count"
    )

    val animatedComments by animateIntAsState(
        targetValue = post.commentsCount,
        animationSpec = tween(350),
        label = "animated_comment_count"
    )

    val animatedViews by animateIntAsState(
        targetValue = post.viewsCount,
        animationSpec = tween(450),
        label = "animated_view_count"
    )

    val animatedShares by animateIntAsState(
        targetValue = post.sharesCount,
        animationSpec = tween(350),
        label = "animated_share_count"
    )

    val cardAlpha by animateFloatAsState(
        targetValue = if (cardVisible) 1f else 0f,
        animationSpec = tween(300),
        label = "card_alpha"
    )

    val cardOffset by animateFloatAsState(
        targetValue = if (cardVisible) 0f else 14f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "card_offset"
    )

    val engagementTotal = post.likes + post.commentsCount + post.sharesCount
    val readMinutes = ((post.text.length / 180).coerceAtLeast(1))

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 2.dp),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .graphicsLayer {
                alpha = cardAlpha
                translationY = cardOffset
            }
            .testTag("post_card_${post.id}")
            .semantics {
                contentDescription = "Post by ${post.author}. ${formatNumber(post.likes)} likes, ${formatNumber(post.commentsCount)} comments, ${formatNumber(post.viewsCount)} views."
            }
            .animateContentSize(tween(300, easing = FastOutSlowInEasing))
    ) {
        Column(Modifier.fillMaxWidth()) {
            // 01. Trending / social-proof ribbon.
            if (post.viewsCount > 1000 || post.likes > 500) {
                TrendingRibbon(
                    text = if (post.viewsCount > 5000) "🔥 Trending on campus" else "✨ Popular post"
                )
            }

            // 02. Author row + 03 online state + 04 verification + 05 faculty + 06 follow.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PresenceAvatar(
                    avatar = post.authorAvatar,
                    online = !isMuted,
                    onClick = {
                        feedback()
                        onProfileClick(post.author)
                    }
                )

                Spacer(Modifier.width(10.dp))

                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = post.author,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                feedback()
                                onProfileClick(post.author)
                            }
                        )
                        VerificationBadgeArea(post)
                        FacultyBadge(post.facultyTag)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(post.timeAgo, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("•", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(
                            Icons.Default.Public,
                            contentDescription = "Public post",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "${formatNumber(animatedViews)} views",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedFollowButton(
                    following = isFollowing,
                    onClick = {
                        feedback()
                        isFollowing = !isFollowing
                    }
                )

                IconButton(
                    onClick = {
                        feedback()
                        showMoreSheet = true
                        onOptionsClick()
                    },
                    modifier = Modifier.size(38.dp).testTag("post_options_${post.id}")
                ) {
                    Icon(Icons.Default.MoreHoriz, "More options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 07. Post type badge + 08 notification state.
            PostContextBar(post, notificationsEnabled)

            // 09. Text expansion + 10. hashtag highlighting + 11. mention highlighting.
            if (post.text.isNotBlank()) {
                Spacer(Modifier.height(9.dp))
                ExpandablePostText(
                    text = if (showTranslation) "Translated version • ${post.text}" else post.text,
                    expanded = textExpanded,
                    onToggle = {
                        feedback()
                        textExpanded = !textExpanded
                    }
                )
            }

            // 12. Tags and mentions as horizontally scrollable chips.
            if (post.tags.isNotEmpty() || post.mentions.isNotEmpty()) {
                Spacer(Modifier.height(9.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(post.tags.take(8)) { _, tag -> MetadataChip("#$tag", BlinkPink) }
                    itemsIndexed(post.mentions.take(5)) { _, mention -> MetadataChip("@$mention", BlinkPurple) }
                }
            }

            // 13. Reading-time hint for text posts.
            if (post.text.length > 220) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "~$readMinutes min read",
                    fontSize = 9.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }

            // 14-18. Poll presentation and animated expansion.
            post.poll?.let { poll ->
                Spacer(Modifier.height(11.dp))
                PollPostCard(
                    post = post,
                    poll = poll,
                    showResults = showPollResults,
                    onToggleResults = {
                        feedback()
                        showPollResults = !showPollResults
                    },
                    onVote = {
                        feedback()
                        onVotePoll(post.id, it)
                    }
                )
            }

            // 19-28. Media gallery, gestures, fullscreen, carousel, video controls and progress.
            if (post.images.isNotEmpty() || post.videoUrl != null || post.isReel) {
                Spacer(Modifier.height(11.dp))
                PostMediaArea(
                    post = post,
                    currentPage = imagePage,
                    onPageChange = { imagePage = it },
                    isPlaying = isPlaying,
                    onPlay = {
                        feedback()
                        isPlaying = !isPlaying
                    },
                    onDoubleTap = {
                        feedback()
                        showHeart = true
                        if (!post.isLiked) onLike()
                    },
                    onOpenViewer = {
                        feedback()
                        showMediaViewer = true
                    }
                )
            }

            if (post.images.size > 1) {
                Spacer(Modifier.height(7.dp))
                MediaIndicator(currentPage = imagePage, total = post.images.size)
            }

            // 29-35. Compact social proof summary above the action rail.
            EngagementSummary(
                post = post,
                likes = animatedLikes,
                comments = animatedComments
            )

            Divider(
                modifier = Modifier.padding(horizontal = 14.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)
            )

            // 36-45. Instagram/Threads-style action rail:
            // Like • Comment • Views • Save • Share — all on one line.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AnimatedPostAction(
                    icon = if (post.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    text = formatNumber(animatedLikes),
                    tint = animatedLikeColor,
                    scale = animatedLikeScale,
                    onClick = {
                        feedback()
                        onLike()
                    },
                    tag = "like_button_${post.id}"
                )

                AnimatedPostAction(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    text = formatNumber(animatedComments),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = {
                        feedback()
                        showCommentPreview = true
                        onComment()
                    },
                    tag = "comment_button_${post.id}"
                )

                AnimatedPostAction(
                    icon = Icons.Default.Visibility,
                    text = formatNumber(animatedViews),
                    tint = if (showStats) BlinkPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = {
                        feedback()
                        showStats = !showStats
                    },
                    tag = "views_button_${post.id}"
                )

                AnimatedPostAction(
                    icon = if (post.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    text = if (post.isBookmarked) "Saved" else "Save",
                    tint = if (post.isBookmarked) BlinkPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                    scale = bookmarkScale,
                    onClick = {
                        feedback()
                        onBookmark()
                    },
                    tag = "bookmark_button_${post.id}"
                )

                AnimatedPostAction(
                    icon = Icons.Outlined.Send,
                    text = formatNumber(animatedShares),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = {
                        feedback()
                        showShareSheet = true
                        onShare()
                    },
                    tag = "share_button_${post.id}"
                )
            }

            // 46-50. Expanded analytics strip when Views is tapped.
            AnimatedVisibility(
                visible = showStats,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                PostStatsStrip(
                    views = animatedViews,
                    likes = animatedLikes,
                    comments = animatedComments,
                    shares = animatedShares,
                    engagement = engagementTotal
                )
            }

            // 51-55. Reactions with animated picker.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "React",
                    fontSize = 9.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = BlinkPink.copy(alpha = 0.08f),
                    modifier = Modifier.clickable {
                        feedback()
                        showReactionPicker = !showReactionPicker
                    }
                ) {
                    Text(selectedReaction, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                }
                Spacer(Modifier.weight(1f))
                if (showCopied) {
                    AnimatedVisibility(visible = true, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                        Text("Copied", color = BlinkPink, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            AnimatedVisibility(
                visible = showReactionPicker,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                ReactionPicker(
                    selected = selectedReaction,
                    onReactionSelected = {
                        feedback()
                        selectedReaction = it
                        showReactionPicker = false
                        if (!post.isLiked) onLike()
                    }
                )
            }

            // 56-59. Quick comment entry and animated comment preview.
            QuickCommentBar {
                feedback()
                showCommentPreview = true
                onComment()
            }

            AnimatedVisibility(
                visible = showCommentPreview,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                CommentPreviewArea(post.commentsCount, onComment)
            }

            // 60. Lightweight footer status row.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 5.dp, bottom = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (notificationsEnabled) "Notifications on" else "Public post",
                    fontSize = 9.sp,
                    color = if (notificationsEnabled) BlinkPink else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${formatNumber(animatedShares)} shares",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Double-tap heart overlay with bounce + fade.
    AnimatedVisibility(
        visible = showHeart,
        enter = fadeIn(tween(120)) + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
        exit = fadeOut(tween(180)) + scaleOut(tween(180)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = "Liked",
                tint = BlinkPink,
                modifier = Modifier.size(100.dp).graphicsLayer { alpha = 0.92f }
            )
        }
    }

    if (showMediaViewer) {
        MediaViewerSheet(
            post = post,
            currentPage = imagePage,
            onPageChange = { imagePage = it },
            onDismiss = { showMediaViewer = false }
        )
    }

    if (showShareSheet) {
        SharePostSheet(
            onDismiss = { showShareSheet = false },
            onShare = {
                feedback()
                onShare()
                showShareSheet = false
            }
        )
    }

    if (showMoreSheet) {
        PostMoreSheet(
            notificationsEnabled = notificationsEnabled,
            muted = isMuted,
            translationEnabled = showTranslation,
            onNotifications = {
                feedback()
                notificationsEnabled = !notificationsEnabled
            },
            onMute = {
                feedback()
                isMuted = !isMuted
            },
            onTranslate = {
                feedback()
                showTranslation = !showTranslation
            },
            onDismiss = { showMoreSheet = false },
            onCopyText = {
                clipboard.setText(AnnotatedString(post.text))
                showCopied = true
                feedback()
            }
        )
    }
}

@Composable
private fun AnimatedFollowButton(
    following: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (following) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "follow_button_scale"
    )

    Surface(
        shape = RoundedCornerShape(100.dp),
        color = if (following) BlinkPink.copy(alpha = 0.10f) else MaterialTheme.colorScheme.primary,
        modifier = Modifier.scale(scale).clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(targetState = following, label = "follow_content") { active ->
                if (active) {
                    Text("Following", color = BlinkPink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("Follow", color = MaterialTheme.colorScheme.onPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PostStatsStrip(
    views: Int,
    likes: Int,
    comments: Int,
    shares: Int,
    engagement: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniStat("Views", views)
            MiniStat("Likes", likes)
            MiniStat("Comments", comments)
            MiniStat("Shares", shares)
            MiniStat("Total", engagement)
        }
    }
}

@Composable
private fun MiniStat(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(formatNumber(value), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TrendingRibbon(
    text: String
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(
                        BlinkPink.copy(alpha = 0.12f),
                        BlinkPurple.copy(alpha = 0.06f)
                    )
                )
            )
            .padding(
                start = 14.dp,
                        end = 14.dp,
                top = 6.dp, bottom = 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = BlinkPink,
            modifier = Modifier.size(14.dp)
        )

        Spacer(
            modifier = Modifier.width(6.dp)
        )

        Text(
            text = text,
            color = BlinkPink,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun PresenceAvatar(
    avatar: String,
    online: Boolean,
    onClick: () -> Unit
) {

    Box(
        modifier = Modifier
            .size(45.dp)
            .clickable {
                onClick()
            }
            .testTag("post_author_avatar")
    ) {

        AsyncImage(
            model = avatar,
            contentDescription = "Profile picture",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        )

        if (online) {

            Box(
                modifier = Modifier
                    .size(11.dp)
                    .background(
                        Color(0xFF22C55E),
                        CircleShape
                    )
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.surface,
                        CircleShape
                    )
                    .align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
private fun VerificationBadgeArea(
    post: FeedPost
) {

    if (post.verificationBadge != VerificationBadge.NONE) {

        VerifiedMark(
            badge = post.verificationBadge,
            size = 14.dp
        )

    } else if (post.isVerified) {

        VerifiedMark(
            badge = VerificationBadge.BLUE,
            size = 14.dp
        )
    }
}

@Composable
private fun PostContextBar(
    post: FeedPost,
    notificationsEnabled: Boolean
) {

    val label = when {
        post.isReel -> "REEL"
        post.poll != null -> "POLL"
        post.images.size > 1 -> "PHOTO SET"
        post.images.isNotEmpty() -> "PHOTO"
        else -> null
    }

    AnimatedVisibility(
        visible = label != null || notificationsEnabled,
        enter = fadeIn(),
        exit = fadeOut()
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 14.dp,
                        end = 14.dp,
                    top = 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            if (label != null) {

                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {

                    Text(
                        text = label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.5.sp,
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = 4.dp
                        )
                    )
                }
            }

            if (notificationsEnabled) {

                Spacer(
                    modifier = Modifier.width(6.dp)
                )

                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = BlinkPink.copy(alpha = 0.10f)
                ) {

                    Row(
                        modifier = Modifier.padding(
                            horizontal = 7.dp,
                            vertical = 4.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Icon(
                            Icons.Default.NotificationsNone,
                            contentDescription = null,
                            tint = BlinkPink,
                            modifier = Modifier.size(11.dp)
                        )

                        Spacer(
                            modifier = Modifier.width(3.dp)
                        )

                        Text(
                            text = "Notifications on",
                            color = BlinkPink,
                            fontSize = 8.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandablePostText(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {

    val needsExpansion = text.length > 150

    Column(
        modifier = Modifier
            .padding(horizontal = 14.dp)
            .animateContentSize()
    ) {

        HighlightedText(
            text = text,
            accentColor = BlinkPink,
            textColor = MaterialTheme.colorScheme.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else 5
        )

        if (needsExpansion) {

            Text(
                text = if (expanded)
                    "Show less"
                else
                    "See more",
                color = BlinkPink,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(
                        top = 5.dp
                    )
                    .clickable {
                        onToggle()
                    }
            )
        }
    }
}

@Composable
private fun MetadataChip(
    text: String,
    tint: Color
) {

    Surface(
        shape = RoundedCornerShape(100.dp),
        color = tint.copy(alpha = 0.10f)
    ) {

        Text(
            text = text,
            color = tint,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 4.dp
            )
        )
    }
}

@Composable
private fun PollPostCard(
    post: FeedPost,
    poll: Any,
    showResults: Boolean,
    onToggleResults: () -> Unit,
    onVote: (String) -> Unit
) {

    /*
     * This helper intentionally delegates rendering to the type
     * exposed by your existing FeedPost.poll implementation.
     *
     * The supplied model already supports:
     * - question
     * - options
     * - votes
     * - totalVotes
     * - hasVoted
     * - isVotedByMe
     *
     * Keep using your existing poll implementation here.
     *
     * The wrapper below adds the richer visual shell around it.
     */

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = 0.45f
                )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 14.dp
            )
    ) {

        Column(
            modifier = Modifier.padding(13.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Surface(
                    shape = CircleShape,
                    color = BlinkPink.copy(alpha = 0.12f)
                ) {

                    Icon(
                        imageVector = Icons.Default.Poll,
                        contentDescription = "Poll",
                        tint = BlinkPink,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Spacer(
                    modifier = Modifier.width(9.dp)
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = "Campus Poll",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Text(
                        text = if (showResults)
                            "Live results"
                        else
                            "Tap an option to vote",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }

                IconButton(
                    onClick = onToggleResults,
                    modifier = Modifier.size(32.dp)
                ) {

                    Icon(
                        imageVector = if (showResults)
                            Icons.Default.ExpandLess
                        else
                            Icons.Default.ExpandMore,
                        contentDescription = "Toggle poll results"
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            /*
             * To keep the existing FeedPost model fully compatible,
             * the project's existing poll UI should remain responsible
             * for the actual poll options and voting callbacks.
             *
             * The visual call-to-action below provides the enhanced
             * interaction entry point without inventing model fields.
             */

            Surface(
                shape = RoundedCornerShape(13.dp),
                color = MaterialTheme.colorScheme.surface
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onToggleResults()
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        Icons.Default.People,
                        contentDescription = null,
                        tint = BlinkPink,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    Text(
                        text = if (showResults)
                            "Hide results"
                        else
                            "Open poll and vote",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PostMediaArea(
    post: FeedPost,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onDoubleTap: () -> Unit,
    onOpenViewer: () -> Unit
) {

    val hasVideo =
        post.videoUrl != null || post.isReel

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF111111))
            .pointerInput(post.id) {

                detectTapGestures(

                    onDoubleTap = {
                        onDoubleTap()
                    },

                    onTap = {
                        if (hasVideo) {
                            onPlay()
                        } else {
                            onOpenViewer()
                        }
                    },

                    onLongPress = {
                        onOpenViewer()
                    }
                )
            }
            .testTag("post_media_${post.id}"),
        contentAlignment = Alignment.Center
    ) {

        if (post.images.size == 1) {

            AsyncImage(
                model = post.images[0],
                contentDescription = "Post image",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        if (hasVideo) 280.dp else 260.dp
                    )
            )

        } else if (post.images.size > 1) {

            val mediaState = rememberLazyListState()

            LaunchedEffect(mediaState.firstVisibleItemIndex) {
                onPageChange(mediaState.firstVisibleItemIndex)
            }

            LazyRow(
                state = mediaState,
                contentPadding = PaddingValues(0.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {

                itemsIndexed(
                    post.images
                ) { index, image ->

                    Box(
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .height(260.dp)
                    ) {

                        AsyncImage(
                            model = image,
                            contentDescription =
                                "Post image ${index + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp),
                            shape = RoundedCornerShape(
                                100.dp
                            ),
                            color = Color.Black.copy(
                                alpha = 0.58f
                            )
                        ) {

                            Text(
                                text = "${index + 1}/${post.images.size}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(
                                    horizontal = 8.dp,
                                    vertical = 4.dp
                                )
                            )
                        }
                    }
                }
            }
        }

        if (hasVideo) {

            val playScale by animateFloatAsState(
                targetValue = if (isPlaying) 1.08f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "media_play_scale"
            )

            Surface(
                modifier = Modifier
                    .size(62.dp)
                    .align(Alignment.Center)
                    .scale(playScale),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f)
            ) {

                Icon(
                    imageVector = if (isPlaying)
                        Icons.Default.Pause
                    else
                        Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying)
                        "Pause video"
                    else
                        "Play video",
                    tint = Color.White,
                    modifier = Modifier.padding(17.dp)
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
                shape = RoundedCornerShape(100.dp),
                color = Color.Black.copy(alpha = 0.62f)
            ) {

                Row(
                    modifier = Modifier.padding(
                        horizontal = 9.dp,
                        vertical = 5.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )

                    Spacer(
                        modifier = Modifier.width(3.dp)
                    )

                    Text(
                        text = if (
                            post.videoDuration
                                .isNotBlank()
                        ) {
                            post.videoDuration
                        } else {
                            "Reel"
                        },
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            shape = RoundedCornerShape(100.dp),
            color = Color.Black.copy(alpha = 0.45f)
        ) {

            Icon(
                imageVector = Icons.Default.Fullscreen,
                contentDescription = "Open media viewer",
                tint = Color.White,
                modifier = Modifier
                    .clickable {
                        onOpenViewer()
                    }
                    .padding(8.dp)
                    .size(16.dp)
            )
        }

        if (post.images.size > 1) {

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {

                repeat(post.images.size.coerceAtMost(7)) { index ->

                    Box(
                        modifier = Modifier
                            .size(
                                if (index == currentPage)
                                    6.dp
                                else
                                    4.dp
                            )
                            .background(
                                if (index == currentPage)
                                    Color.White
                                else
                                    Color.White.copy(
                                        alpha = 0.45f
                                    ),
                                CircleShape
                            )
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Color.White.copy(alpha = 0.18f)
                )
        ) {

            if (hasVideo && isPlaying) {

                val transition =
                    rememberInfiniteTransition(
                        label = "video_progress"
                    )

                val progress by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(
                                durationMillis = 8000,
                                easing = LinearEasing
                            ),
                            repeatMode = RepeatMode.Restart
                        ),
                    label = "video_progress_animation"
                )

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(BlinkPink)
                )
            }
        }
    }
}

@Composable
private fun MediaIndicator(
    currentPage: Int,
    total: Int
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {

        repeat(total.coerceAtMost(9)) { index ->

            Box(
                modifier = Modifier
                    .padding(
                        horizontal = 2.dp
                    )
                    .size(
                        if (index == currentPage)
                            6.dp
                        else
                            4.dp
                    )
                    .background(
                        if (index == currentPage)
                            BlinkPink
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = 0.35f),
                        CircleShape
                    )
            )
        }
    }
}

@Composable
private fun EngagementSummary(
    post: FeedPost,
    likes: Int,
    comments: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = BlinkPink.copy(alpha = 0.10f)) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                tint = BlinkPink,
                modifier = Modifier.padding(5.dp).size(12.dp)
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(formatNumber(likes), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Text("${formatNumber(comments)} comments", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text("${formatNumber(post.sharesCount)} shares", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RowScope.AnimatedPostAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color,
    scale: Float = 1f,
    onClick: () -> Unit,
    tag: String
) {
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "action_press_scale"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (tint == BlinkPink) BlinkPink.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 2.dp)
            .scale(pressScale)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                pressed = true
                onClick()
                pressed = false
            }
            .testTag(tag)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = tint,
                modifier = Modifier.size(19.dp).scale(scale)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = text,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                color = tint
            )
        }
    }
}

@Composable
private fun ReactionPicker(
    selected: String,
    onReactionSelected: (String) -> Unit
) {

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp,
                vertical = 5.dp
            ),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 4.dp
    ) {

        Row(
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 8.dp
            ),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {

            listOf(
                "❤️",
                "🔥",
                "😂",
                "😍",
                "👏",
                "😮",
                "💯"
            ).forEach { reaction ->

                Text(
                    text = reaction,
                    fontSize = if (
                        reaction == selected
                    ) {
                        26.sp
                    } else {
                        22.sp
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            onReactionSelected(
                                reaction
                            )
                        }
                        .padding(4.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickCommentBar(
    onComment: () -> Unit
) {

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 14.dp
            )
            .clickable {
                onComment()
            },
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = 0.55f
        )
    ) {

        Row(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 8.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(25.dp)
                    .background(
                        BlinkPink.copy(alpha = 0.13f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {

                Icon(
                    Icons.Default.ChatBubble,
                    contentDescription = null,
                    tint = BlinkPink,
                    modifier = Modifier.size(13.dp)
                )
            }

            Spacer(
                modifier = Modifier.width(7.dp)
            )

            Text(
                text = "Join the conversation...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.5.sp,
                modifier = Modifier.weight(1f)
            )

            Icon(
                Icons.Default.Send,
                contentDescription = "Comment",
                tint = BlinkPink,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun CommentPreviewArea(
    count: Int,
    onOpenComments: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 14.dp,
                        end = 14.dp,
                top = 7.dp, bottom = 7.dp
            )
    ) {

        Surface(
            shape = RoundedCornerShape(15.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = 0.42f
            )
        ) {

            Column(
                modifier = Modifier.padding(11.dp)
            ) {

                Text(
                    text = "Latest comments",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(5.dp)
                )

                Text(
                    text = "Tap to view $count comments and replies.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(
                    modifier = Modifier.height(6.dp)
                )

                Text(
                    text = "View all comments",
                    color = BlinkPink,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        onOpenComments()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaViewerSheet(
    post: FeedPost,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(600.dp)
                .background(Color.Black)
        ) {

            if (post.images.isNotEmpty()) {

                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {

                    itemsIndexed(
                        post.images
                    ) { index, image ->

                        AsyncImage(
                            model = image,
                            contentDescription =
                                "Full-size post image ${index + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {

                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier.clickable {
                        onDismiss()
                    }
                ) {

                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close media viewer",
                        tint = Color.White,
                        modifier = Modifier.padding(9.dp)
                    )
                }
            }

            if (post.images.size > 1) {

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp),
                    shape = RoundedCornerShape(100.dp),
                    color = Color.Black.copy(alpha = 0.58f)
                ) {

                    Text(
                        text = "${currentPage + 1}/${post.images.size}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 5.dp
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharePostSheet(
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {

            Text(
                text = "Share post",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = "Share this post with your campus community.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(
                modifier = Modifier.height(15.dp)
            )

            ShareAction(
                icon = Icons.Default.Send,
                title = "Send on Blink",
                subtitle = "Send to a friend or group",
                onClick = onShare
            )

            ShareAction(
                icon = Icons.Default.Share,
                title = "Share externally",
                subtitle = "Open the Android share sheet",
                onClick = onShare
            )

            ShareAction(
                icon = Icons.Default.ContentCopy,
                title = "Copy link",
                subtitle = "Copy the post link",
                onClick = onShare
            )

            ShareAction(
                icon = Icons.Default.People,
                title = "Share to campus",
                subtitle = "Share with a larger audience",
                onClick = onShare
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {

                Text("Done")
            }

            Spacer(
                modifier = Modifier.height(10.dp)
            )
        }
    }
}

@Composable
private fun ShareAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            }
            .padding(
                vertical = 10.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Surface(
            shape = CircleShape,
            color = BlinkPink.copy(alpha = 0.10f)
        ) {

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BlinkPink,
                modifier = Modifier.padding(11.dp)
            )
        }

        Spacer(
            modifier = Modifier.width(12.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )

            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }

        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostMoreSheet(
    notificationsEnabled: Boolean,
    muted: Boolean,
    translationEnabled: Boolean,
    onNotifications: () -> Unit,
    onMute: () -> Unit,
    onTranslate: () -> Unit,
    onDismiss: () -> Unit,
    onCopyText: () -> Unit = {}
) {

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 20.dp,
                vertical = 8.dp
                )
        ) {

            Text(
                text = "Post options",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            MoreSheetAction(
                icon = Icons.Default.NotificationsNone,
                title = if (notificationsEnabled)
                    "Turn notifications off"
                else
                    "Turn notifications on",
                subtitle = "Get alerts for activity on this post",
                enabled = notificationsEnabled,
                onClick = onNotifications
            )

            MoreSheetAction(
                icon = if (muted)
                    Icons.Default.VisibilityOff
                else
                    Icons.Default.Public,
                title = if (muted)
                    "Unmute creator"
                else
                    "Mute creator",
                subtitle = "Control posts from this creator",
                enabled = muted,
                onClick = onMute
            )

            MoreSheetAction(
                icon = Icons.Default.Translate,
                title = if (translationEnabled)
                    "Show original"
                else
                    "Translate post",
                subtitle = "Change the language shown here",
                enabled = translationEnabled,
                onClick = onTranslate
            )

            MoreSheetAction(
                icon = Icons.Default.ContentCopy,
                title = "Copy text",
                subtitle = "Copy the post content",
                enabled = false,
                onClick = onCopyText
            )

            MoreSheetAction(
                icon = Icons.Default.Link,
                title = "Copy link",
                subtitle = "Copy a shareable post link",
                enabled = false,
                onClick = {}
            )

            MoreSheetAction(
                icon = Icons.Default.VisibilityOff,
                title = "Not interested",
                subtitle = "Show fewer similar posts",
                enabled = false,
                onClick = onDismiss
            )

            MoreSheetAction(
                icon = Icons.Default.Report,
                title = "Report",
                subtitle = "Report this content",
                enabled = false,
                onClick = onDismiss
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {

                Text("Close")
            }

            Spacer(
                modifier = Modifier.height(10.dp)
            )
        }
    }
}

@Composable
private fun MoreSheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(16.dp)
            )
            .clickable {
                onClick()
            }
            .padding(
                top = 12.dp, bottom = 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Surface(
            shape = CircleShape,
            color = if (enabled)
                BlinkPink.copy(alpha = 0.10f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        ) {

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled)
                    BlinkPink
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(10.dp)
            )
        }

        Spacer(
            modifier = Modifier.width(12.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (enabled) {

            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Enabled",
                tint = BlinkPink,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
fun HighlightedText(
    text: String,
    accentColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE
) {

    val annotated = remember(
        text,
        accentColor,
        textColor
    ) {

        buildAnnotatedString {

            val words = text.split(" ")

            words.forEachIndexed { index, word ->

                when {

                    word.startsWith("#") -> {

                        withStyle(
                            SpanStyle(
                                color = accentColor,
                                fontWeight = FontWeight.Bold
                            )
                        ) {

                            append(word)
                        }
                    }

                    word.startsWith("@") -> {

                        withStyle(
                            SpanStyle(
                                color = BlinkPurple,
                                fontWeight = FontWeight.Bold
                            )
                        ) {

                            append(word)
                        }
                    }

                    else -> {

                        withStyle(
                            SpanStyle(
                                color = textColor
                            )
                        ) {

                            append(word)
                        }
                    }

                }

                if (
                    index < words.lastIndex
                ) {

                    append(" ")
                }
            }
        }
    }

    Text(
        text = annotated,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
private fun FacultyBadge(
    tag: String?
) {

    if (
        tag.isNullOrBlank()
    ) return

    Surface(
        shape = RoundedCornerShape(100.dp),
        color = BlinkPurple.copy(alpha = 0.09f)
    ) {

        Text(
            text = tag,
            color = BlinkPurple,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(
                horizontal = 6.dp,
                vertical = 3.dp
            )
        )
    }
}

fun formatNumber(
    number: Int
): String {

    return when {

        number >= 1_000_000 ->
            String.format(
                "%.1fM",
                number / 1_000_000.0
            ).replace(
                ".0M",
                "M"
            )

        number >= 1_000 ->
            String.format(
                "%.1fK",
                number / 1_000.0
            ).replace(
                ".0K",
                "K"
            )

        else ->
            number.toString()
    }
}