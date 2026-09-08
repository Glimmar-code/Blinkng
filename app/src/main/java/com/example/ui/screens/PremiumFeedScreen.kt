package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.models.ConnectHubSnapshot
import com.example.data.models.FeedPost
import com.example.data.models.LeaderboardUser
import com.example.data.models.Story
import com.example.data.models.UserProfile
import com.example.data.network.NetworkMonitor
import com.example.data.repository.FollowStateStore
import com.example.ui.components.CreatePostFab
import com.example.ui.components.FeedTabs
import com.example.ui.components.FeedTopBar
import com.example.ui.components.PostCard
import com.example.ui.components.PremiumPullRefreshIndicator
import com.example.ui.components.shimmerBackground
import com.example.ui.theme.FeedBackground
import com.example.ui.theme.FeedBorder
import com.example.ui.theme.FeedCardSurface
import com.example.ui.theme.FeedElevatedSurface
import com.example.ui.theme.FeedPurple
import com.example.ui.theme.FeedTextPrimary
import com.example.ui.theme.FeedTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.random.Random

private enum class PremiumFeedFilter { ALL, PHOTOS, POLLS }

private sealed interface PremiumHomeRow {
    data class PostRow(val post: FeedPost, val sourceIndex: Int) : PremiumHomeRow
    data class ReelPreviewRow(val reel: FeedPost, val slot: Int) : PremiumHomeRow
}

private fun buildPremiumHomeRows(
    posts: List<FeedPost>,
    reels: List<FeedPost>,
    seed: Int
): List<PremiumHomeRow> {
    if (posts.isEmpty()) return emptyList()
    if (reels.isEmpty()) {
        return posts.mapIndexed { index, post -> PremiumHomeRow.PostRow(post, index) }
    }

    val random = Random(seed)
    val rows = ArrayList<PremiumHomeRow>(posts.size + (posts.size / 10) + 1)
    var postsSincePreview = 0
    var nextGap = random.nextInt(10, 21)
    var reelSlot = 0

    posts.forEachIndexed { index, post ->
        rows += PremiumHomeRow.PostRow(post, index)
        postsSincePreview += 1

        if (postsSincePreview >= nextGap) {
            // Keep the exact ranked reel order supplied by the existing feed algorithm.
            // Cycling only occurs if the post list is longer than the currently loaded
            // reel page. View events are still handled by the shared exposure tracker.
            rows += PremiumHomeRow.ReelPreviewRow(
                reel = reels[reelSlot % reels.size],
                slot = reelSlot
            )
            reelSlot += 1
            postsSincePreview = 0
            nextGap = random.nextInt(10, 21)
        }
    }
    return rows
}

/**
 * Keeps the ranked feed order stable for the current browsing session.
 *
 * The server ranking algorithm still decides the ranked payload. Background/realtime
 * updates may refresh the data of rows already on-screen/in-session, but they must not
 * insert or reorder unseen rows while the user is scrolling. Only pagination appends new
 * ranked rows; an explicit refresh is allowed to replace the whole ranked snapshot.
 */
private fun mergeStablePremiumFeed(
    current: List<FeedPost>,
    latest: List<FeedPost>,
    appendNew: Boolean
): List<FeedPost> {
    if (current.isEmpty()) return latest
    if (latest.isEmpty()) return current

    val latestById = latest.associateBy { it.id }
    val updatedInPlace = current.map { existing ->
        latestById[existing.id] ?: existing
    }
    if (!appendNew) return updatedInPlace

    val existingIds = current.asSequence().map { it.id }.toHashSet()
    return updatedInPlace + latest.filter { it.id !in existingIds }
}

/**
 * Premium feed shell.
 *
 * For You -> Following -> Game is one horizontal gesture family. Reels remains
 * an explicit independent action, while Connect keeps its own destination and
 * no longer shows the old four-way top navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumFeedScreen(
    posts: List<FeedPost>,
    reels: List<FeedPost>,
    stories: List<Story>,
    profiles: List<UserProfile>,
    leaderboardUsers: List<LeaderboardUser>,
    connectHub: ConnectHubSnapshot = ConnectHubSnapshot(),
    connectHubActions: ConnectHubActions = ConnectHubActions(),
    isConnectHubLoading: Boolean = false,
    currentUsername: String,
    userAvatar: String,
    currentSubTab: Int,
    onSubTabChanged: (Int) -> Unit,
    isDark: Boolean,
    onLikePost: (String) -> Unit,
    onCommentPost: (String) -> Unit,
    onBookmarkPost: (String) -> Unit,
    onRepostPost: (String) -> Unit,
    onSharePost: (String) -> Unit,
    onOptionsClick: (FeedPost) -> Unit,
    onDeletePost: (String) -> Unit = {},
    onProfileClick: (String) -> Unit,
    onAddStoryClick: () -> Unit,
    onStoryClick: (Story) -> Unit,
    onOpenCreatePost: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenMenu: () -> Unit,
    onToggleTheme: () -> Unit,
    isServerConnected: Boolean = true,
    isLoading: Boolean = false,
    isRefreshing: Boolean = false,
    errorMessage: String? = null,
    onRefresh: () -> Unit = {},
    onRetry: () -> Unit = {},
    onViewedPost: (String) -> Unit = {},
    onVotePoll: (postId: String, optionId: String) -> Unit = { _, _ -> },
    onDirectMessage: (partner: String, partnerName: String?, partnerAvatar: String?) -> Unit = { _, _, _ -> },
    onSearchClick: () -> Unit = {},
    onLeaderboardClick: () -> Unit = {},
    onMarketClick: () -> Unit = {},
    onMessageClick: () -> Unit = {},
    hasMorePosts: Boolean = false,
    hasMoreReels: Boolean = false,
    isLoadingMorePosts: Boolean = false,
    isLoadingMoreReels: Boolean = false,
    onLoadMorePosts: () -> Unit = {},
    onLoadMoreReels: () -> Unit = {},
    homeReselectSignal: Int = 0,
    onBottomBarVisibilityChange: (Boolean) -> Unit = {},
    hasUnreadNotifications: Boolean = false,
    routedReelId: String? = null
) {
    val context = LocalContext.current
    val resumePrefs = remember(context) {
        context.getSharedPreferences("blink_resume_positions", android.content.Context.MODE_PRIVATE)
    }
    val resumeUserKey = remember(currentUsername) {
        currentUsername.trim().removePrefix("@").lowercase().ifBlank { "anonymous" }
    }
    var feedLane by rememberSaveable(resumeUserKey) {
        mutableIntStateOf(
            resumePrefs.getInt("home_lane:$resumeUserKey", 0).coerceIn(0, 1)
        )
    }
    LaunchedEffect(feedLane, resumeUserKey) {
        resumePrefs.edit()
            .putInt("home_lane:$resumeUserKey", feedLane.coerceIn(0, 1))
            .apply()
    }
    val followingIds by FollowStateStore.followingIds.collectAsState()
    var launchReelId by rememberSaveable(resumeUserKey) { mutableStateOf<String?>(null) }
    var launchReelPositionMs by rememberSaveable(resumeUserKey) { mutableStateOf(0L) }

    LaunchedEffect(currentSubTab) {
        if (currentSubTab != 1 && launchReelId != null) {
            launchReelId = null
            launchReelPositionMs = 0L
        }
    }

    fun openReelsAt(reelId: String? = null, positionMs: Long = 0L) {
        launchReelId = reelId
        launchReelPositionMs = positionMs.coerceAtLeast(0L)
        onSubTabChanged(1)
    }

    LaunchedEffect(currentUsername) {
        if (currentUsername.isNotBlank()) FollowStateStore.refresh()
    }

    val followedAuthorKeys = remember(profiles, followingIds) {
        profiles.asSequence()
            .filter { it.id in followingIds }
            .flatMap { sequenceOf(it.username, it.fullName) }
            .map { it.trim().removePrefix("@").lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    when (currentSubTab) {
        0 -> PremiumHomeFeed(
            posts = posts,
            reels = reels,
            profiles = profiles,
            currentUsername = currentUsername,
            userAvatar = userAvatar,
            resumeUserKey = resumeUserKey,
            laneIndex = feedLane,
            followedAuthorKeys = followedAuthorKeys,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            isServerConnected = isServerConnected,
            errorMessage = errorMessage,
            hasMorePosts = hasMorePosts,
            hasMoreReels = hasMoreReels,
            isLoadingMorePosts = isLoadingMorePosts,
            isLoadingMoreReels = isLoadingMoreReels,
            homeReselectSignal = homeReselectSignal,
            hasUnreadNotifications = hasUnreadNotifications,
            onLaneChanged = { feedLane = it.coerceIn(0, 1) },
            onLikePost = onLikePost,
            onCommentPost = onCommentPost,
            onBookmarkPost = onBookmarkPost,
            onRepostPost = onRepostPost,
            onSharePost = onSharePost,
            onOptionsClick = onOptionsClick,
            onDeletePost = onDeletePost,
            onProfileClick = onProfileClick,
            onOpenCreatePost = onOpenCreatePost,
            onOpenActivity = onOpenActivity,
            onOpenMenu = onOpenMenu,
            onSearchClick = onSearchClick,
            onRefresh = onRefresh,
            onRetry = onRetry,
            onViewedPost = onViewedPost,
            onVotePoll = onVotePoll,
            onLoadMorePosts = onLoadMorePosts,
            onLoadMoreReels = onLoadMoreReels,
            onBottomBarVisibilityChange = onBottomBarVisibilityChange,
            onGameClick = { onSubTabChanged(3) },
            onReelClick = { openReelsAt() },
            onOpenInlineReel = { reelId, positionMs -> openReelsAt(reelId, positionMs) }
        )

        1 -> FeedScreen(
            posts = posts,
            reels = reels,
            stories = stories,
            profiles = profiles,
            leaderboardUsers = leaderboardUsers,
            connectHub = connectHub,
            connectHubActions = connectHubActions,
            isConnectHubLoading = isConnectHubLoading,
            currentUsername = currentUsername,
            userAvatar = userAvatar,
            currentSubTab = 1,
            onSubTabChanged = onSubTabChanged,
            isDark = isDark,
            onLikePost = onLikePost,
            onCommentPost = onCommentPost,
            onBookmarkPost = onBookmarkPost,
            onRepostPost = onRepostPost,
            onSharePost = onSharePost,
            onOptionsClick = onOptionsClick,
            onDeletePost = onDeletePost,
            onProfileClick = onProfileClick,
            onAddStoryClick = onAddStoryClick,
            onStoryClick = onStoryClick,
            onOpenCreatePost = onOpenCreatePost,
            onOpenActivity = onOpenActivity,
            onOpenMenu = onOpenMenu,
            onToggleTheme = onToggleTheme,
            isServerConnected = isServerConnected,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            errorMessage = errorMessage,
            onRefresh = onRefresh,
            onRetry = onRetry,
            onViewedPost = onViewedPost,
            onVotePoll = onVotePoll,
            onDirectMessage = onDirectMessage,
            onSearchClick = onSearchClick,
            onLeaderboardClick = onLeaderboardClick,
            onMarketClick = onMarketClick,
            onMessageClick = onMessageClick,
            hasMorePosts = hasMorePosts,
            hasMoreReels = hasMoreReels,
            isLoadingMorePosts = isLoadingMorePosts,
            isLoadingMoreReels = isLoadingMoreReels,
            onLoadMorePosts = onLoadMorePosts,
            onLoadMoreReels = onLoadMoreReels,
            homeReselectSignal = homeReselectSignal,
            onBottomBarVisibilityChange = onBottomBarVisibilityChange,
            initialReelId = routedReelId ?: launchReelId,
            initialReelPositionMs = if (routedReelId != null) 0L else launchReelPositionMs
        )

        2 -> PremiumConnectHost(
            profiles = profiles,
            currentUsername = currentUsername,
            userAvatar = userAvatar,
            isDark = isDark,
            onOpenMenu = onOpenMenu,
            onOpenActivity = onOpenActivity,
            onProfileClick = onProfileClick,
            onDirectMessage = onDirectMessage,
            connectHub = connectHub,
            connectHubActions = connectHubActions,
            isConnectHubLoading = isConnectHubLoading,
            onHomeClick = {
                feedLane = 0
                onSubTabChanged(0)
            },
            onReelClick = { openReelsAt() },
            onGameClick = { onSubTabChanged(3) }
        )

        3 -> PremiumGameHost(
            userAvatar = userAvatar,
            currentUsername = currentUsername,
            leaderboardUsers = leaderboardUsers,
            connectHub = connectHub,
            connectHubActions = connectHubActions,
            isDark = isDark,
            hasUnreadNotifications = hasUnreadNotifications,
            onOpenMenu = onOpenMenu,
            onOpenActivity = onOpenActivity,
            onProfileClick = onProfileClick,
            onSearchClick = onSearchClick,
            onForYou = {
                feedLane = 0
                onSubTabChanged(0)
            },
            onFollowing = {
                feedLane = 1
                onSubTabChanged(0)
            },
            onReel = { openReelsAt() }
        )

        else -> onSubTabChanged(0)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumHomeFeed(
    posts: List<FeedPost>,
    reels: List<FeedPost>,
    profiles: List<UserProfile>,
    currentUsername: String,
    userAvatar: String,
    resumeUserKey: String,
    laneIndex: Int,
    followedAuthorKeys: Set<String>,
    isLoading: Boolean,
    isRefreshing: Boolean,
    isServerConnected: Boolean,
    errorMessage: String?,
    hasMorePosts: Boolean,
    hasMoreReels: Boolean,
    isLoadingMorePosts: Boolean,
    isLoadingMoreReels: Boolean,
    homeReselectSignal: Int,
    hasUnreadNotifications: Boolean,
    onLaneChanged: (Int) -> Unit,
    onLikePost: (String) -> Unit,
    onCommentPost: (String) -> Unit,
    onBookmarkPost: (String) -> Unit,
    onRepostPost: (String) -> Unit,
    onSharePost: (String) -> Unit,
    onOptionsClick: (FeedPost) -> Unit,
    onDeletePost: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    onOpenCreatePost: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenMenu: () -> Unit,
    onSearchClick: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onViewedPost: (String) -> Unit,
    onVotePoll: (postId: String, optionId: String) -> Unit,
    onLoadMorePosts: () -> Unit,
    onLoadMoreReels: () -> Unit,
    onBottomBarVisibilityChange: (Boolean) -> Unit,
    onGameClick: () -> Unit,
    onReelClick: () -> Unit,
    onOpenInlineReel: (reelId: String, positionMs: Long) -> Unit
) {
    val context = LocalContext.current
    val authorPresenceByKey = remember(profiles) {
        buildMap<String, Boolean> {
            profiles.forEach { profile ->
                profile.username.trim().removePrefix("@").lowercase().takeIf(String::isNotBlank)?.let { put(it, profile.onlineNow) }
                profile.fullName.trim().lowercase().takeIf(String::isNotBlank)?.let { put(it, profile.onlineNow) }
            }
        }
    }
    val resumePrefs = remember(context) {
        context.getSharedPreferences("blink_resume_positions", android.content.Context.MODE_PRIVATE)
    }
    val laneResumeKey = "$resumeUserKey:$laneIndex"
    val listState = rememberLazyListState()
    var restoredLaneResumeKey by remember { mutableStateOf<String?>(null) }
    var restoringScroll by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()
    val density = LocalDensity.current
    val latestViewed by rememberUpdatedState(onViewedPost)
    val impressionTracker = remember { PostImpressionTracker() }
    var filter by remember(laneResumeKey) {
        mutableStateOf(
            runCatching {
                PremiumFeedFilter.valueOf(
                    resumePrefs.getString(
                        "home_filter:$laneResumeKey",
                        PremiumFeedFilter.ALL.name
                    ) ?: PremiumFeedFilter.ALL.name
                )
            }.getOrDefault(PremiumFeedFilter.ALL)
        )
    }
    LaunchedEffect(filter, laneResumeKey) {
        resumePrefs.edit()
            .putString("home_filter:$laneResumeKey", filter.name)
            .apply()
    }
    var filterMenuVisible by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(true) }
    var screenVisible by remember { mutableStateOf(false) }
    var horizontalDrag by remember { mutableStateOf(0f) }
    val swipeThreshold = with(density) { 64.dp.toPx() }
    val primaryCollapseThreshold = with(density) { 20.dp.toPx() }
    val immersiveCollapseThreshold = with(density) { 56.dp.toPx() }
    val scrollAccumulator = remember { floatArrayOf(0f) }
    var chromeStage by remember(laneResumeKey) { mutableIntStateOf(0) }
    var primaryHeaderVisible by remember(laneResumeKey) { mutableStateOf(true) }
    var secondaryChromeVisible by remember(laneResumeKey) { mutableStateOf(true) }
    var bottomChromeVisible by remember { mutableStateOf(true) }

    val networkMonitor = remember(context) { NetworkMonitor(context) }
    val isOnline by networkMonitor.isOnline.collectAsState(
        initial = networkMonitor.isCurrentlyOnline()
    )
    var offlineEmptyConfirmed by remember { mutableStateOf(false) }

    // Freeze positional order for this lane while keeping each existing post's live data
    // fresh. This prevents realtime/background re-ranking from inserting a post ahead of
    // the user's current scroll position. The ranking algorithm itself is untouched.
    var stableRankedPosts by remember(laneResumeKey) { mutableStateOf(posts) }
    var stableRankedReels by remember(laneResumeKey) { mutableStateOf(reels) }
    var postRefreshWasRunning by remember(laneResumeKey) { mutableStateOf(isRefreshing) }
    var reelRefreshWasRunning by remember(laneResumeKey) { mutableStateOf(isRefreshing) }
    var postPaginationWasRunning by remember(laneResumeKey) { mutableStateOf(isLoadingMorePosts) }
    var reelPaginationWasRunning by remember(laneResumeKey) { mutableStateOf(isLoadingMoreReels) }

    LaunchedEffect(posts, isRefreshing, isLoadingMorePosts, laneResumeKey) {
        val explicitRefreshCompleted = postRefreshWasRunning && !isRefreshing
        val paginationCompleted = postPaginationWasRunning && !isLoadingMorePosts

        stableRankedPosts = if (explicitRefreshCompleted) {
            posts
        } else {
            mergeStablePremiumFeed(
                current = stableRankedPosts,
                latest = posts,
                appendNew = paginationCompleted
            )
        }

        postRefreshWasRunning = isRefreshing
        postPaginationWasRunning = isLoadingMorePosts
    }

    LaunchedEffect(reels, isRefreshing, isLoadingMoreReels, laneResumeKey) {
        val explicitRefreshCompleted = reelRefreshWasRunning && !isRefreshing
        val paginationCompleted = reelPaginationWasRunning && !isLoadingMoreReels

        stableRankedReels = if (explicitRefreshCompleted) {
            reels
        } else {
            mergeStablePremiumFeed(
                current = stableRankedReels,
                latest = reels,
                appendNew = paginationCompleted
            )
        }

        reelRefreshWasRunning = isRefreshing
        reelPaginationWasRunning = isLoadingMoreReels
    }

    val filteredPosts = remember(stableRankedPosts, filter, laneIndex, followedAuthorKeys) {
        val rankedNormalPosts = stableRankedPosts.filterNot { it.isReel || !it.videoUrl.isNullOrBlank() }
        val lanePosts = if (laneIndex == 1) {
            // Preserve the exact ranking/order delivered by the normal feed algorithm;
            // Following is only an author-membership filter over that ranked list.
            rankedNormalPosts.filter { post ->
                post.author.trim().removePrefix("@").lowercase() in followedAuthorKeys
            }
        } else {
            rankedNormalPosts
        }
        lanePosts.filter { post ->
            when (filter) {
                PremiumFeedFilter.ALL -> true
                PremiumFeedFilter.PHOTOS -> post.images.any { it.isNotBlank() && !it.equals("null", true) }
                PremiumFeedFilter.POLLS -> post.poll != null
            }
        }
    }

    val rankedInlineReels = remember(stableRankedReels, laneIndex, followedAuthorKeys) {
        stableRankedReels.filter { reel ->
            val hasPlayableVideo = reel.isReel && !reel.videoUrl.isNullOrBlank()
            val authorKey = reel.authorUsername
                .ifBlank { reel.author }
                .trim()
                .removePrefix("@")
                .lowercase()
            hasPlayableVideo && (laneIndex == 0 || authorKey in followedAuthorKeys)
        }
    }
    val reelMixSeed = remember(laneResumeKey, filter, filteredPosts.firstOrNull()?.id) {
        "$laneResumeKey:${filter.name}:${filteredPosts.firstOrNull()?.id.orEmpty()}".hashCode()
    }
    val homeRows = remember(filteredPosts, rankedInlineReels, reelMixSeed) {
        buildPremiumHomeRows(filteredPosts, rankedInlineReels, reelMixSeed)
    }
    var activeInlineReelKey by remember(laneResumeKey) { mutableStateOf<String?>(null) }

    LaunchedEffect(isOnline, isLoading, stableRankedPosts.isEmpty(), filteredPosts.isEmpty(), filter, laneIndex) {
        offlineEmptyConfirmed = false
        if (
            !isOnline &&
            !isLoading &&
            stableRankedPosts.isEmpty() &&
            filteredPosts.isEmpty() &&
            filter == PremiumFeedFilter.ALL &&
            laneIndex == 0
        ) {
            // Give the durable local cache a moment to hydrate before declaring it empty.
            // If cached posts arrive, this effect is cancelled and the empty card never flashes.
            delay(500)
            offlineEmptyConfirmed = true
        }
    }

    val nearEnd by remember(homeRows) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            homeRows.isNotEmpty() && last >= homeRows.lastIndex - 3
        }
    }

    val scrollConnection = remember(
        onBottomBarVisibilityChange,
        primaryCollapseThreshold,
        immersiveCollapseThreshold
    ) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                when {
                    available.y < 0f -> {
                        // Moving deeper into the feed. Collapse the primary header first,
                        // then require a second deliberate scroll distance before entering
                        // immersive mode. This hysteresis avoids tiny-movement flicker.
                        scrollAccumulator[0] += -available.y
                        when {
                            chromeStage == 0 && scrollAccumulator[0] >= primaryCollapseThreshold -> {
                                chromeStage = 1
                                primaryHeaderVisible = false
                                secondaryChromeVisible = true
                                bottomChromeVisible = true
                                fabExpanded = true
                                scrollAccumulator[0] = 0f
                                onBottomBarVisibilityChange(true)
                            }
                            chromeStage == 1 && scrollAccumulator[0] >= immersiveCollapseThreshold -> {
                                chromeStage = 2
                                primaryHeaderVisible = false
                                secondaryChromeVisible = false
                                bottomChromeVisible = false
                                fabExpanded = false
                                scrollAccumulator[0] = 0f
                                onBottomBarVisibilityChange(false)
                            }
                        }
                    }

                    available.y > 0f -> {
                        // Any meaningful reverse scroll leaves immersive mode immediately.
                        // The primary header intentionally stays hidden until the list is
                        // genuinely back at the first post.
                        scrollAccumulator[0] = 0f
                        if (chromeStage == 2) {
                            chromeStage = 1
                            primaryHeaderVisible = false
                            secondaryChromeVisible = true
                            bottomChromeVisible = true
                            fabExpanded = true
                            onBottomBarVisibilityChange(true)
                        }
                    }
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    LaunchedEffect(Unit) { screenVisible = true }

    LaunchedEffect(laneResumeKey, filteredPosts.isNotEmpty()) {
        if (restoredLaneResumeKey != laneResumeKey) {
            val savedIndex = resumePrefs
                .getInt("home_scroll_index:$laneResumeKey", 0)
                .coerceAtLeast(0)
            val savedOffset = resumePrefs
                .getInt("home_scroll_offset:$laneResumeKey", 0)
                .coerceAtLeast(0)

            // Wait until the cached/ranked rows have had one frame to enter the LazyColumn.
            // A saved non-zero position is only restored once content exists.
            if (savedIndex == 0 || filteredPosts.isNotEmpty()) {
                restoringScroll = true
                delay(16)
                val maxIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                runCatching {
                    listState.scrollToItem(
                        savedIndex.coerceAtMost(maxIndex),
                        savedOffset
                    )
                }
                restoredLaneResumeKey = laneResumeKey
                restoringScroll = false
            }
        }

        scrollAccumulator[0] = 0f
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 1) {
            chromeStage = 0
            primaryHeaderVisible = true
        } else {
            chromeStage = 1
            primaryHeaderVisible = false
        }
        secondaryChromeVisible = true
        bottomChromeVisible = true
        fabExpanded = true
        onBottomBarVisibilityChange(true)
    }

    LaunchedEffect(listState, laneResumeKey) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collectLatest { (index, offset) ->
            if (!restoringScroll && restoredLaneResumeKey == laneResumeKey) {
                resumePrefs.edit()
                    .putInt("home_scroll_index:$laneResumeKey", index)
                    .putInt("home_scroll_offset:$laneResumeKey", offset)
                    .apply()
            }

            // The primary header is special: reverse scrolling never restores it.
            // Only the actual top of the feed (first item, zero offset) can do that.
            if (index == 0 && offset <= 1 && chromeStage != 0) {
                chromeStage = 0
                primaryHeaderVisible = true
                secondaryChromeVisible = true
                bottomChromeVisible = true
                fabExpanded = true
                scrollAccumulator[0] = 0f
                onBottomBarVisibilityChange(true)
            }
        }
    }

    LaunchedEffect(homeReselectSignal) {
        if (homeReselectSignal > 0) {
            // Home-on-Home is intentionally lightweight:
            // - about ten posts deep or farther: return to the first post instantly;
            // - still within the first ten posts: keep position and refresh in place.
            // Do not reset the user's For You/Following lane or active feed filter.
            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset > 1) {
                listState.scrollToItem(0)
            } else {
                onRefresh()
            }
            scrollAccumulator[0] = 0f
            chromeStage = 0
            primaryHeaderVisible = true
            secondaryChromeVisible = true
            bottomChromeVisible = true
            fabExpanded = true
            onBottomBarVisibilityChange(true)
        }
    }

    LaunchedEffect(
        nearEnd,
        hasMorePosts,
        hasMoreReels,
        isLoadingMorePosts,
        isLoadingMoreReels,
        laneIndex
    ) {
        // Pagination remains the normal ranked server pagination. We only request the next
        // reel page alongside a deep For You scroll so the teaser pool can grow naturally.
        if (nearEnd && hasMorePosts && !isLoadingMorePosts) onLoadMorePosts()
        if (nearEnd && laneIndex == 0 && hasMoreReels && !isLoadingMoreReels) onLoadMoreReels()
    }

    LaunchedEffect(listState, homeRows) {
        snapshotFlow { listState.layoutInfo }
            .collectLatest { layout ->
                val ids = layout.visibleItemsInfo.mapNotNull { item ->
                    val key = item.key as? String ?: return@mapNotNull null
                    if (!key.startsWith("post:")) return@mapNotNull null
                    if (
                        qualifiesForPostImpression(
                            itemOffset = item.offset,
                            itemSize = item.size,
                            viewportStart = layout.viewportStartOffset,
                            viewportEnd = layout.viewportEndOffset
                        )
                    ) key.removePrefix("post:") else null
                }.toSet()
                impressionTracker.update(ids).forEach(latestViewed)
            }
    }

    // This list-level visibility state only controls two-second muted autoplay. The
    // preview card itself uses the same qualified exposure tracker as full reels, so genuine
    // feed encounters follow the existing repeat-view and delayed-reflection algorithm.
    LaunchedEffect(listState, homeRows) {
        snapshotFlow { listState.layoutInfo }
            .collectLatest { layout ->
                activeInlineReelKey = layout.visibleItemsInfo.firstOrNull { item ->
                    val key = item.key as? String ?: return@firstOrNull false
                    key.startsWith("reel_preview:") && qualifiesForPostImpression(
                        itemOffset = item.offset,
                        itemSize = item.size,
                        viewportStart = layout.viewportStartOffset,
                        viewportEnd = layout.viewportEndOffset
                    )
                }?.key as? String
            }
    }

    AnimatedVisibility(
        visible = screenVisible,
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(100)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FeedBackground)
                .nestedScroll(scrollConnection)
                .pointerInput(laneIndex) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            horizontalDrag += amount
                        },
                        onDragEnd = {
                            when {
                                horizontalDrag <= -swipeThreshold && laneIndex == 0 -> onLaneChanged(1)
                                horizontalDrag <= -swipeThreshold && laneIndex == 1 -> onGameClick()
                                horizontalDrag >= swipeThreshold && laneIndex == 1 -> onLaneChanged(0)
                            }
                            horizontalDrag = 0f
                        },
                        onDragCancel = { horizontalDrag = 0f }
                    )
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = primaryHeaderVisible,
                    enter = fadeIn(tween(120)) + slideInVertically(tween(140)) { -it / 2 },
                    exit = fadeOut(tween(100)) + slideOutVertically(tween(120)) { -it / 2 }
                ) {
                    FeedTopBar(
                        userAvatar = userAvatar,
                        hasUnreadNotifications = hasUnreadNotifications,
                        onSearchClick = onSearchClick,
                        onNotificationClick = onOpenActivity,
                        onMenuClick = onOpenMenu,
                        onProfileClick = { onProfileClick(currentUsername) }
                    )
                }

                AnimatedVisibility(
                    visible = secondaryChromeVisible,
                    enter = fadeIn(tween(110)) + slideInVertically(tween(130)) { -it / 3 },
                    exit = fadeOut(tween(90)) + slideOutVertically(tween(110)) { -it / 3 }
                ) {
                    Column {
                        Box {
                            FeedTabs(
                                selectedIndex = laneIndex,
                                onForYouClick = { onLaneChanged(0) },
                                onFollowingClick = { onLaneChanged(1) },
                                onGameClick = onGameClick,
                                onReelClick = onReelClick,
                                onFilterClick = { filterMenuVisible = true }
                            )
                            DropdownMenu(
                                expanded = filterMenuVisible,
                                onDismissRequest = { filterMenuVisible = false },
                                modifier = Modifier.background(FeedElevatedSurface)
                            ) {
                                PremiumFilterItem("All posts", Icons.Default.Tune, filter == PremiumFeedFilter.ALL) {
                                    filter = PremiumFeedFilter.ALL
                                    filterMenuVisible = false
                                }
                                PremiumFilterItem("Photos", Icons.Default.Image, filter == PremiumFeedFilter.PHOTOS) {
                                    filter = PremiumFeedFilter.PHOTOS
                                    filterMenuVisible = false
                                }
                                PremiumFilterItem("Polls", Icons.Default.Poll, filter == PremiumFeedFilter.POLLS) {
                                    filter = PremiumFeedFilter.POLLS
                                    filterMenuVisible = false
                                }
                            }
                        }
                        HorizontalDivider(color = FeedBorder.copy(alpha = 0.72f))
                    }
                }

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = pullState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PremiumPullRefreshIndicator(
                            state = pullState,
                            isRefreshing = isRefreshing,
                            darkSurface = true,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 170.dp)
                    ) {
                        if (!errorMessage.isNullOrBlank() && stableRankedPosts.isNotEmpty()) {
                            item(key = "refresh_error") {
                                PremiumFeedRefreshNotice(errorMessage, onRetry)
                            }
                        }

                        when {
                            isLoading && stableRankedPosts.isEmpty() -> {
                                items(4, key = { "skeleton:$it" }) {
                                    PremiumFeedSkeleton()
                                }
                            }

                            filteredPosts.isEmpty() -> {
                                when {
                                    filter != PremiumFeedFilter.ALL || laneIndex == 1 -> {
                                        item(key = "empty_feed") {
                                            PremiumEmptyFeed(
                                                isFiltered = filter != PremiumFeedFilter.ALL,
                                                isFollowingLane = laneIndex == 1,
                                                offlineNoCache = false,
                                                onCreatePost = onOpenCreatePost,
                                                onClearFilter = { filter = PremiumFeedFilter.ALL }
                                            )
                                        }
                                    }

                                    !isOnline && stableRankedPosts.isEmpty() && !offlineEmptyConfirmed -> {
                                        // Never flash an empty-feed message while disk cache may still hydrate.
                                        items(2, key = { "cache_wait:$it" }) {
                                            PremiumFeedSkeleton()
                                        }
                                    }

                                    else -> {
                                        item(key = "empty_feed") {
                                            PremiumEmptyFeed(
                                                isFiltered = false,
                                                isFollowingLane = false,
                                                offlineNoCache = !isOnline && stableRankedPosts.isEmpty() && offlineEmptyConfirmed,
                                                onCreatePost = onOpenCreatePost,
                                                onClearFilter = { filter = PremiumFeedFilter.ALL }
                                            )
                                        }
                                    }
                                }
                            }

                            else -> {
                                items(
                                    count = homeRows.size,
                                    key = { index ->
                                        when (val row = homeRows[index]) {
                                            is PremiumHomeRow.PostRow -> "post:${row.post.id}"
                                            is PremiumHomeRow.ReelPreviewRow -> "reel_preview:${row.slot}:${row.reel.id}"
                                        }
                                    },
                                    contentType = { index ->
                                        when (val row = homeRows[index]) {
                                            is PremiumHomeRow.PostRow -> premiumPostContentType(row.post)
                                            is PremiumHomeRow.ReelPreviewRow -> 16
                                        }
                                    }
                                ) { index ->
                                    when (val row = homeRows[index]) {
                                        is PremiumHomeRow.PostRow -> {
                                            val post = row.post
                                            PremiumPostEntrance(index = row.sourceIndex) {
                                                PostCard(
                                                    post = post,
                                                    isDark = true,
                                                    onLike = { onLikePost(post.id) },
                                                    onComment = { onCommentPost(post.id) },
                                                    onBookmark = { onBookmarkPost(post.id) },
                                                    onRepost = { onRepostPost(post.id) },
                                                    onShare = { onSharePost(post.id) },
                                                    onOptionsClick = { onOptionsClick(post) },
                                                    onProfileClick = onProfileClick,
                                                    onVotePoll = onVotePoll,
                                                    authorOnline = authorPresenceByKey[
                                                        post.authorUsername.trim().removePrefix("@").lowercase()
                                                    ] ?: authorPresenceByKey[post.author.trim().removePrefix("@").lowercase()],
                                                    isAuthor = post.author.equals(currentUsername.removePrefix("@"), ignoreCase = true) ||
                                                            post.authorUsername.removePrefix("@").equals(currentUsername.removePrefix("@"), ignoreCase = true),
                                                    onDelete = { onDeletePost(post.id) }
                                                )
                                            }
                                        }

                                        is PremiumHomeRow.ReelPreviewRow -> {
                                            val previewKey = "reel_preview:${row.slot}:${row.reel.id}"
                                            InlineReelPreviewCard(
                                                reel = row.reel,
                                                isActive = activeInlineReelKey == previewKey,
                                                onContinue = { positionMs ->
                                                    onOpenInlineReel(row.reel.id, positionMs)
                                                },
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                                if (isLoadingMorePosts) {
                                    item(key = "loading_more") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 18.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                color = FeedPurple,
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = secondaryChromeVisible,
                enter = fadeIn(tween(110)) + slideInVertically(tween(130)) { it / 2 },
                exit = fadeOut(tween(90)) + slideOutVertically(tween(110)) { it / 2 },
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                CreatePostFab(
                    expanded = fabExpanded,
                    onClick = onOpenCreatePost,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(end = 18.dp, bottom = 72.dp)
                )
            }
        }
    }
}

@Composable
private fun PremiumGameHost(
    userAvatar: String,
    currentUsername: String,
    leaderboardUsers: List<LeaderboardUser>,
    connectHub: ConnectHubSnapshot,
    connectHubActions: ConnectHubActions,
    isDark: Boolean,
    hasUnreadNotifications: Boolean,
    onOpenMenu: () -> Unit,
    onOpenActivity: () -> Unit,
    onProfileClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onForYou: () -> Unit,
    onFollowing: () -> Unit,
    onReel: () -> Unit
) {
    val density = LocalDensity.current
    val threshold = with(density) { 64.dp.toPx() }
    var horizontalDrag by remember { mutableStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .background(FeedBackground)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        horizontalDrag += amount
                    },
                    onDragEnd = {
                        if (horizontalDrag >= threshold) onFollowing()
                        horizontalDrag = 0f
                    },
                    onDragCancel = { horizontalDrag = 0f }
                )
            }
    ) {
        GameSection(
            userAvatar = userAvatar,
            leaderboardUsers = leaderboardUsers,
            connectHub = connectHub,
            connectHubActions = connectHubActions,
            isDark = isDark,
            onOpenMenu = onOpenMenu,
            onOpenActivity = onOpenActivity,
            onProfileClick = onProfileClick,
            selectedTopTab = 3,
            onHomeClick = onForYou,
            onReelClick = onReel,
            onConnectClick = {},
            onGameClick = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun PremiumConnectHost(
    profiles: List<UserProfile>,
    currentUsername: String,
    userAvatar: String,
    isDark: Boolean,
    onOpenMenu: () -> Unit,
    onOpenActivity: () -> Unit,
    onProfileClick: (String) -> Unit,
    onDirectMessage: (partner: String, partnerName: String?, partnerAvatar: String?) -> Unit,
    connectHub: ConnectHubSnapshot,
    connectHubActions: ConnectHubActions,
    isConnectHubLoading: Boolean,
    onHomeClick: () -> Unit,
    onReelClick: () -> Unit,
    onGameClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(FeedBackground)
            .statusBarsPadding()
    ) {
        ConnectSection(
            profiles = profiles,
            currentUsername = currentUsername,
            userAvatar = userAvatar,
            isDark = isDark,
            onOpenMenu = onOpenMenu,
            onOpenActivity = onOpenActivity,
            onProfileClick = onProfileClick,
            onDirectMessage = onDirectMessage,
            connectHub = connectHub,
            connectHubActions = connectHubActions,
            isConnectHubLoading = isConnectHubLoading,
            selectedTopTab = 2,
            onHomeClick = onHomeClick,
            onReelClick = onReelClick,
            onConnectClick = {},
            onGameClick = onGameClick,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Lays the legacy section slightly taller than the viewport and places its old
 * header above the visible bounds. This keeps all existing Game/Connect logic
 * intact while removing duplicate navigation chrome.
 */
@Composable
private fun LegacyChromeCrop(
    topCrop: Dp,
    content: @Composable (Modifier) -> Unit
) {
    val cropPx = with(LocalDensity.current) { topCrop.roundToPx() }
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = { content(Modifier.fillMaxSize()) }
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val childHeight = (height + cropPx).coerceAtLeast(height)
        val child = measurables.first().measure(
            constraints.copy(minHeight = childHeight, maxHeight = childHeight)
        )
        layout(width, height) {
            child.placeRelative(0, -cropPx)
        }
    }
}

@Composable
private fun PremiumPostEntrance(index: Int, content: @Composable () -> Unit) {
    // Only the first few rows get a very small initial fade. Rows composed during normal
    // scrolling render immediately, avoiding the delayed website-like card animation.
    if (index > 2) {
        content()
        return
    }

    var visible by remember(index) { mutableStateOf(false) }
    LaunchedEffect(index) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(90)),
        exit = fadeOut(tween(70)),
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}

private fun premiumPostContentType(post: FeedPost): Int {
    var type = 0
    if (post.text.isNotBlank()) type = type or 1
    if (post.images.any { it.isNotBlank() && !it.equals("null", true) }) type = type or 2
    if (post.poll != null) type = type or 4
    if (post.isSponsored) type = type or 8
    return type
}

@Composable
private fun PremiumFilterItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                color = if (selected) FeedTextPrimary else FeedTextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) FeedPurple else FeedTextSecondary
            )
        },
        onClick = onClick
    )
}

@Composable
private fun PremiumFeedSkeleton() {
    val base = FeedElevatedSurface
    val highlight = Color.White.copy(alpha = 0.08f)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        color = FeedCardSurface,
        border = BorderStroke(1.dp, FeedBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).shimmerBackground(CircleShape, base, highlight))
                Spacer(Modifier.width(11.dp))
                Column {
                    Box(
                        Modifier
                            .width(132.dp)
                            .height(13.dp)
                            .shimmerBackground(RoundedCornerShape(8.dp), base, highlight)
                    )
                    Spacer(Modifier.height(7.dp))
                    Box(
                        Modifier
                            .width(86.dp)
                            .height(10.dp)
                            .shimmerBackground(RoundedCornerShape(8.dp), base, highlight)
                    )
                }
            }
            Spacer(Modifier.height(17.dp))
            Box(
                Modifier
                    .fillMaxWidth(.92f)
                    .height(13.dp)
                    .shimmerBackground(RoundedCornerShape(8.dp), base, highlight)
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth(.66f)
                    .height(13.dp)
                    .shimmerBackground(RoundedCornerShape(8.dp), base, highlight)
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .shimmerBackground(RoundedCornerShape(18.dp), base, highlight)
            )
        }
    }
}

@Composable
private fun PremiumFeedRefreshNotice(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            color = FeedTextSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
            maxLines = 2
        )
        TextButton(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Refresh")
        }
    }
}

@Composable
private fun PremiumEmptyFeed(
    isFiltered: Boolean,
    isFollowingLane: Boolean,
    offlineNoCache: Boolean = false,
    onCreatePost: () -> Unit,
    onClearFilter: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp, vertical = 58.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = CircleShape, color = FeedPurple.copy(alpha = 0.13f)) {
            Icon(
                imageVector = if (isFiltered) Icons.Default.Tune else Icons.Default.Image,
                contentDescription = null,
                tint = FeedPurple,
                modifier = Modifier.padding(16.dp).size(30.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = when {
                isFiltered -> "No posts match this filter"
                isFollowingLane -> "No posts from people you follow yet"
                else -> if (offlineNoCache) "Your feed is ready for something new" else "No posts to show right now"
            },
            color = FeedTextPrimary,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = when {
                isFiltered -> "Choose another feed filter to keep browsing."
                isFollowingLane -> "Follow people from Discover or profiles; their ranked posts will appear here."
                else -> if (offlineNoCache) "You're offline and there are no saved posts on this device." else "Pull to refresh for the latest posts."
            },
            color = FeedTextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(14.dp))
        if (!isFollowingLane || isFiltered) {
            TextButton(onClick = if (isFiltered) onClearFilter else onCreatePost) {
                Text(if (isFiltered) "Show all posts" else "Create Post", color = FeedPurple)
            }
        }
    }
}
