package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.*
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.local.BlinkMediaCache
import com.example.data.models.FeedPost
import com.example.ui.components.PremiumPullRefreshIndicator
import com.example.ui.components.formatNumber
import com.example.ui.components.shimmerBackground
import com.example.ui.theme.BlinkPink
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * A polished, TikTok / Reels-style vertical video feed.
 *
 * Highlights:
 *  - Animated skeleton loading state, cross-faded against the empty and content states
 *  - Subtle scale + fade page transitions while swiping between reels
 *  - Double-tap-to-like with a floating multi-heart burst animation
 *  - Tap-to-mute with a fading volume hint overlay
 *  - Live buffering spinner and a bottom video progress bar
 *  - Animated action counts, an expandable caption, and an animated tab indicator
 *
 * The public signature is unchanged from the original screen, so this is a drop-in
 * replacement for existing call sites.
 */
@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun VideoReelsScreen(
    reels: List<FeedPost>,
    currentUsername: String,
    isDark: Boolean,
    onLike: (String) -> Unit,
    onComment: (String) -> Unit,
    onBookmark: (String) -> Unit,
    onShare: (String) -> Unit,
    onDelete: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    onBackToPosts: () -> Unit,
    isLoading: Boolean = false,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onHomeClick: () -> Unit = onBackToPosts,
    onConnectClick: () -> Unit = {},
    onGameClick: () -> Unit = {}
) {
    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        modifier = Modifier.fillMaxSize().background(Color.Black),
        indicator = {
            PremiumPullRefreshIndicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
                darkSurface = true
            )
        }
    ) {
        val uiState = when {
            reels.isEmpty() && isLoading -> ReelsUiState.Loading
            reels.isEmpty() -> ReelsUiState.Empty
            else -> ReelsUiState.Content
        }

        AnimatedContent(
            targetState = uiState,
            transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(200)) },
            label = "reelsUiState"
        ) { state ->
            when (state) {
                ReelsUiState.Loading -> ReelsLoadingSkeleton()
                ReelsUiState.Empty -> EmptyReelsState(onBackToPosts)
                ReelsUiState.Content -> ReelsContent(
                    reels = reels,
                    currentUsername = currentUsername,
                    onLike = onLike,
                    onComment = onComment,
                    onBookmark = onBookmark,
                    onShare = onShare,
                    onDelete = onDelete,
                    onProfileClick = onProfileClick,
                    onBackToPosts = onBackToPosts,
                    hasMore = hasMore,
                    isLoadingMore = isLoadingMore,
                    onLoadMore = onLoadMore
                )
            }
        }
    }
}

private enum class ReelsUiState { Loading, Empty, Content }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ReelsContent(
    reels: List<FeedPost>,
    currentUsername: String,
    onLike: (String) -> Unit,
    onComment: (String) -> Unit,
    onBookmark: (String) -> Unit,
    onShare: (String) -> Unit,
    onDelete: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    onBackToPosts: () -> Unit,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit
) {
    val context = LocalContext.current
    val resumePrefs = remember(context) {
        context.getSharedPreferences("blink_resume_positions", android.content.Context.MODE_PRIVATE)
    }
    val resumeUserKey = remember(currentUsername) {
        currentUsername.trim().removePrefix("@").lowercase().ifBlank { "anonymous" }
    }
    val initialPage = remember(reels, resumeUserKey) {
        val savedId = resumePrefs.getString("reel_id:$resumeUserKey", null)
        val byId = savedId?.let { id -> reels.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
        val byIndex = resumePrefs.getInt("reel_index:$resumeUserKey", 0)
        (byId ?: byIndex).coerceIn(0, reels.lastIndex.coerceAtLeast(0))
    }
    val pager = rememberPagerState(
        initialPage = initialPage,
        pageCount = { reels.size }
    )
    var selectedTab by remember { mutableStateOf("For You") }

    LaunchedEffect(pager, reels, resumeUserKey) {
        snapshotFlow { pager.currentPage }.collectLatest { page ->
            reels.getOrNull(page)?.let { reel ->
                resumePrefs.edit()
                    .putInt("reel_index:$resumeUserKey", page)
                    .putString("reel_id:$resumeUserKey", reel.id)
                    .apply()
            }
        }
    }

    LaunchedEffect(pager.currentPage, reels.size, hasMore, isLoadingMore) {
        if (hasMore && !isLoadingMore && pager.currentPage >= (reels.size - 3).coerceAtLeast(0)) {
            onLoadMore()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pager,
            key = { index -> reels[index].id },
            beyondViewportPageCount = 0,
            modifier = Modifier.fillMaxSize()
        ) { index ->
            val reel = reels[index]
            val pageOffset = (pager.currentPage - index) + pager.currentPageOffsetFraction
            ReelPage(
                reel = reel,
                pageOffset = pageOffset,
                isActive = index == pager.currentPage,
                isAuthor = reel.author.equals(currentUsername, ignoreCase = true),
                onLike = onLike,
                onComment = onComment,
                onBookmark = onBookmark,
                onShare = onShare,
                onDelete = onDelete,
                onProfileClick = onProfileClick,
                onSwipeToHome = onBackToPosts,
                onSwipeToProfile = { onProfileClick(reel.author) }
            )
        }

        ReelsTopTabs(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 10.dp)
        )

        IconButton(
            onClick = onBackToPosts,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(4.dp)
        ) {
            Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
        }

        AnimatedVisibility(
            visible = isLoadingMore,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 18.dp)
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = .5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ReelsTopTabs(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("Following", "For You")
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = tab == selectedTab
            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else Color.White.copy(alpha = .55f),
                label = "tabColor"
            )
            val indicatorWidth by animateDpAsState(
                targetValue = if (isSelected) 24.dp else 0.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "tabIndicatorWidth"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onTabSelected(tab) }
            ) {
                Text(
                    text = tab,
                    color = textColor,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                    fontSize = if (isSelected) 15.sp else 14.sp
                )
                Spacer(Modifier.height(4.dp))
                Box(Modifier.width(indicatorWidth).height(2.dp).background(Color.White, CircleShape))
            }
            if (index == 0) Spacer(Modifier.width(20.dp))
        }
    }
}

@Composable
private fun ReelsLoadingSkeleton() {
    val base = Color(0xFF171717)
    val highlight = Color(0xFF343434)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Full-bleed video placeholder
        Box(
            Modifier
                .fillMaxSize()
                .shimmerBackground(RoundedCornerShape(0.dp), base, highlight)
        )

        // Top tabs skeleton
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(58.dp).height(13.dp).shimmerBackground(RoundedCornerShape(6.dp), base, highlight))
            Spacer(Modifier.width(20.dp))
            Box(Modifier.width(48.dp).height(15.dp).shimmerBackground(RoundedCornerShape(6.dp), base, highlight))
        }

        // Caption skeleton
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 100.dp, bottom = 30.dp)
        ) {
            Box(Modifier.width(112.dp).height(14.dp).shimmerBackground(RoundedCornerShape(8.dp), base, highlight))
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(.82f).height(11.dp).shimmerBackground(RoundedCornerShape(8.dp), base, highlight))
            Spacer(Modifier.height(7.dp))
            Box(Modifier.fillMaxWidth(.58f).height(11.dp).shimmerBackground(RoundedCornerShape(8.dp), base, highlight))
        }

        // Action rail skeleton
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 12.dp, bottom = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(52.dp).shimmerBackground(CircleShape, base, highlight))
            Spacer(Modifier.height(20.dp))
            repeat(4) {
                Box(Modifier.size(42.dp).shimmerBackground(CircleShape, base, highlight))
                Spacer(Modifier.height(18.dp))
            }
        }

        // Progress bar skeleton
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(2.dp)
                .background(base)
        )
    }
}

@Composable
private fun EmptyReelsState(onBackToPosts: () -> Unit) {
    val visibleState = remember { MutableTransitionState(false) }.apply { targetState = true }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(tween(400)) + scaleIn(initialScale = .9f, animationSpec = tween(400))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = .65f),
                    modifier = Modifier.size(54.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text("No live reels yet", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "Drag down to check for new reels.",
                    color = Color.White.copy(alpha = .65f),
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onBackToPosts) { Text("Back to Home") }
            }
        }
    }
}

@Composable
private fun ReelPage(
    reel: FeedPost,
    pageOffset: Float,
    isActive: Boolean,
    isAuthor: Boolean,
    onLike: (String) -> Unit,
    onComment: (String) -> Unit,
    onBookmark: (String) -> Unit,
    onShare: (String) -> Unit,
    onDelete: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    onSwipeToHome: () -> Unit,
    onSwipeToProfile: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    var burstTrigger by remember(reel.id) { mutableStateOf(0) }
    var isMuted by remember(reel.id) { mutableStateOf(false) }
    var showMuteHint by remember(reel.id) { mutableStateOf(false) }
    var isBuffering by remember(reel.id) { mutableStateOf(false) }
    var progress by remember(reel.id) { mutableStateOf(0f) }
    var horizontalDrag by remember(reel.id) { mutableFloatStateOf(0f) }

    LaunchedEffect(showMuteHint) {
        if (showMuteHint) {
            delay(650)
            showMuteHint = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val distance = abs(pageOffset.coerceIn(-1f, 1f))
                scaleX = lerp(1f, 0.94f, distance)
                scaleY = lerp(1f, 0.94f, distance)
                alpha = lerp(1f, 0.55f, distance)
            }
            .pointerInput(reel.id) {
                detectTapGestures(
                    onDoubleTap = {
                        if (!reel.isLiked) onLike(reel.id)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        burstTrigger++
                    },
                    onTap = {
                        isMuted = !isMuted
                        showMuteHint = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
            }
            .pointerInput(reel.id, isActive) {
                if (!isActive) return@pointerInput
                val swipeThreshold = 84.dp.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { horizontalDrag = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        horizontalDrag += dragAmount
                    },
                    onDragEnd = {
                        when {
                            horizontalDrag >= swipeThreshold -> onSwipeToHome()
                            horizontalDrag <= -swipeThreshold -> onSwipeToProfile()
                        }
                        horizontalDrag = 0f
                    },
                    onDragCancel = { horizontalDrag = 0f }
                )
            }
    ) {
        val url = reel.videoUrl?.trim()
        if (!url.isNullOrBlank() && isActive) {
            ReelVideo(
                url = url,
                isActive = isActive,
                isMuted = isMuted,
                onProgressChange = { progress = it },
                onBufferingChange = { isBuffering = it }
            )
        } else if (!url.isNullOrBlank()) {
            ReelPreview(reel)
        } else {
            Box(
                Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text("Video unavailable", color = Color.White)
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(.15f), Color.Transparent, Color.Black.copy(.82f))
                    )
                )
        )

        AnimatedVisibility(
            visible = isBuffering && isActive,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = .85f),
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(38.dp)
            )
        }

        DoubleTapHeartBurst(trigger = burstTrigger)

        MuteHintOverlay(
            visible = showMuteHint,
            isMuted = isMuted,
            modifier = Modifier.align(Alignment.Center)
        )

        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 10.dp, bottom = 34.dp)
                .entranceEffect(delayMillis = 60),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = reel.authorAvatar,
                contentDescription = reel.author,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .clickable { onProfileClick(reel.author) }
            )
            Spacer(Modifier.height(16.dp))
            ReelAction(
                icon = Icons.Default.Visibility,
                text = formatNumber(reel.viewsCount),
                tint = Color.White.copy(alpha = .9f),
                contentDescription = "Views"
            ) {}
            ReelAction(
                icon = if (reel.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                text = formatNumber(reel.likes),
                tint = if (reel.isLiked) BlinkPink else Color.White,
                contentDescription = if (reel.isLiked) "Unlike" else "Like"
            ) { onLike(reel.id) }
            ReelAction(
                icon = Icons.Default.ChatBubble,
                text = formatNumber(reel.commentsCount),
                tint = Color.White,
                contentDescription = "Comments"
            ) { onComment(reel.id) }
            ReelAction(
                icon = if (reel.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                text = "Save",
                tint = Color.White,
                contentDescription = if (reel.isBookmarked) "Remove from saved" else "Save"
            ) { onBookmark(reel.id) }
            ReelAction(
                icon = Icons.Default.Share,
                text = formatNumber(reel.sharesCount),
                tint = Color.White,
                contentDescription = "Share"
            ) { onShare(reel.id) }
            if (isAuthor) {
                ReelAction(
                    icon = Icons.Default.DeleteOutline,
                    text = "Delete",
                    tint = Color(0xFFFF6B6B),
                    contentDescription = "Delete"
                ) { onDelete(reel.id) }
            }
            Spacer(Modifier.height(10.dp))
            StaticDisc(reel.authorAvatar)
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 15.dp, end = 88.dp, bottom = 22.dp)
                .entranceEffect(delayMillis = 120)
        ) {
            Text(
                "@${reel.author}",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                modifier = Modifier.clickable { onProfileClick(reel.author) }
            )
            if (reel.text.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                ExpandableCaption(reel.text)
            }
            reel.audioTitle?.takeIf { it.isNotBlank() }?.let { audio ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(audio, color = Color.White, fontSize = 10.5.sp, maxLines = 1)
                }
            }
        }

        VideoProgressBar(
            progress = progress,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** Fades and slides content in once, the first time it enters composition. */
private fun Modifier.entranceEffect(delayMillis: Int = 0): Modifier = composed {
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(24f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        launch { alpha.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
        launch {
            offsetY.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
            )
        }
    }
    this.graphicsLayer {
        this.alpha = alpha.value
        translationY = offsetY.value
    }
}

private data class HeartSpec(
    val xOffset: Float,
    val rotation: Float,
    val delayMillis: Long,
    val scaleTarget: Float
)

/** A big center heart plus a handful of small hearts flying outward, TikTok-style. */
@Composable
private fun DoubleTapHeartBurst(trigger: Int) {
    if (trigger == 0) return
    key(trigger) {
        val hearts = remember {
            List(6) {
                HeartSpec(
                    xOffset = Random.nextInt(-90, 90).toFloat(),
                    rotation = Random.nextInt(-35, 35).toFloat(),
                    delayMillis = Random.nextLong(0L, 150L),
                    scaleTarget = Random.nextInt(70, 100) / 100f
                )
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MainBurstHeart()
            hearts.forEach { spec -> SmallBurstHeart(spec) }
        }
    }
}

@Composable
private fun MainBurstHeart() {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1.3f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        )
        scale.animateTo(1f, tween(150))
        delay(300)
        alpha.animateTo(0f, tween(250))
    }

    Icon(
        imageVector = Icons.Default.Favorite,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier
            .size(96.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
    )
}

@Composable
private fun SmallBurstHeart(spec: HeartSpec) {
    val offsetY = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.3f) }

    LaunchedEffect(Unit) {
        delay(spec.delayMillis)
        launch { alpha.animateTo(1f, tween(150)) }
        launch {
            scale.animateTo(
                targetValue = spec.scaleTarget,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            )
        }
        launch { offsetY.animateTo(-260f, tween(900, easing = FastOutSlowInEasing)) }
        delay(500)
        alpha.animateTo(0f, tween(300))
    }

    Icon(
        imageVector = Icons.Default.Favorite,
        contentDescription = null,
        tint = BlinkPink,
        modifier = Modifier
            .size(26.dp)
            .graphicsLayer {
                translationX = spec.xOffset
                translationY = offsetY.value
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                rotationZ = spec.rotation
            }
    )
}

@Composable
private fun MuteHintOverlay(visible: Boolean, isMuted: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)) + scaleIn(initialScale = .7f, animationSpec = tween(200)),
        exit = fadeOut(tween(200)),
        modifier = modifier
    ) {
        Box(
            Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = .45f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = if (isMuted) "Muted" else "Unmuted",
                tint = Color.White,
                modifier = Modifier.size(38.dp)
            )
        }
    }
}

@Composable
private fun ExpandableCaption(text: String) {
    val collapsedThreshold = 90
    if (text.length <= collapsedThreshold) {
        Text(text, color = Color.White, fontSize = 13.sp)
        return
    }

    var expanded by remember(text) { mutableStateOf(false) }

    Column(Modifier.animateContentSize()) {
        Text(
            text = if (expanded) text else text.take(collapsedThreshold).trimEnd() + "…",
            color = Color.White,
            fontSize = 13.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 4
        )
        Text(
            text = if (expanded) "Show less" else "more",
            color = Color.White.copy(alpha = .65f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(top = 2.dp)
                .clickable { expanded = !expanded }
        )
    }
}

@Composable
private fun VideoProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(180, easing = LinearEasing),
        label = "videoProgress"
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(Color.White.copy(alpha = .25f))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .background(Color.White)
        )
    }
}

@Composable
private fun ReelAction(
    icon: ImageVector,
    text: String,
    tint: Color,
    contentDescription: String = text,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) .8f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "reelActionScale"
    )

    LaunchedEffect(pressed) {
        if (pressed) {
            delay(120)
            pressed = false
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        IconButton(
            onClick = { pressed = true; onClick() },
            modifier = Modifier
                .size(48.dp)
                .scale(scale)
        ) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(29.dp))
        }
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                (slideInVertically(tween(200)) { it } + fadeIn(tween(200))) togetherWith
                    (slideOutVertically(tween(200)) { -it } + fadeOut(tween(150)))
            },
            label = "reelActionCount"
        ) { animatedText ->
            Text(animatedText, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StaticDisc(avatar: String) {
    Surface(
        shape = CircleShape,
        color = Color(0xFF202020),
        modifier = Modifier.size(42.dp)
    ) {
        Box(Modifier.padding(7.dp), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        }
    }
}

@Composable
private fun ReelPreview(reel: FeedPost) {
    val preview = remember(reel.id, reel.images) {
        reel.images.firstOrNull { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }
    if (preview == null) {
        Box(Modifier.fillMaxSize().background(Color.Black))
    } else {
        AsyncImage(
            model = preview,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ReelVideo(
    url: String,
    isActive: Boolean,
    isMuted: Boolean,
    onProgressChange: (Float) -> Unit,
    onBufferingChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var error by remember(url) { mutableStateOf<String?>(null) }

    val currentOnProgressChange by rememberUpdatedState(onProgressChange)
    val currentOnBufferingChange by rememberUpdatedState(onBufferingChange)

    val player = remember(url) {
        val httpDataSource = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(30000)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(BlinkMediaCache.dataSourceFactory(context, httpDataSource))
            )
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true
                )
                addListener(object : Player.Listener {
                    override fun onPlayerError(e: PlaybackException) {
                        error = e.errorCodeName
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        currentOnBufferingChange(state == Player.STATE_BUFFERING)
                    }
                })
                setMediaItem(MediaItem.fromUri(url))
                prepare()
            }
    }

    LaunchedEffect(isActive, player) {
        player.playWhenReady = isActive
        if (isActive) player.play() else player.pause()
    }

    LaunchedEffect(isMuted, player) {
        player.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(player, isActive) {
        while (isActive) {
            val duration = player.duration
            if (duration > 0) {
                currentOnProgressChange((player.currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f))
            }
            delay(200)
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )
        if (error != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Unable to play this reel", color = Color.White, fontWeight = FontWeight.Bold)
                TextButton(onClick = {
                    error = null
                    player.prepare()
                    if (isActive) player.play()
                }) {
                    Text("Retry")
                }
            }
        }
    }
}
