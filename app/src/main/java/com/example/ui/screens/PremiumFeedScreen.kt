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

private enum class PremiumFeedFilter { ALL, PHOTOS, POLLS }

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
    hasUnreadNotifications: Boolean = false
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
            isLoadingMorePosts = isLoadingMorePosts,
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
            onBottomBarVisibilityChange = onBottomBarVisibilityChange,
            onGameClick = { onSubTabChanged(3) },
            onReelClick = { onSubTabChanged(1) }
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
            onBottomBarVisibilityChange = onBottomBarVisibilityChange
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
            onReelClick = { onSubTabChanged(1) },
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
            onReel = { onSubTabChanged(1) }
        )

        else -> onSubTabChanged(0)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumHomeFeed(
    posts: List<FeedPost>,
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
    isLoadingMorePosts: Boolean,
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
    onBottomBarVisibilityChange: (Boolean) -> Unit,
    onGameClick: () -> Unit,
    onReelClick: () -> Unit
) {
    val context = LocalContext.current
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
    val chromeScrollThreshold = with(density) { 20.dp.toPx() }
    val scrollAccumulator = remember { floatArrayOf(0f) }
    var bottomChromeVisible by remember { mutableStateOf(true) }

    val networkMonitor = remember(context) { NetworkMonitor(context) }
    val isOnline by networkMonitor.isOnline.collectAsState(
        initial = networkMonitor.isCurrentlyOnline()
    )
    var offlineEmptyConfirmed by remember { mutableStateOf(false) }

    val filteredPosts = remember(posts, filter, laneIndex, followedAuthorKeys) {
        val rankedNormalPosts = posts.filterNot { it.isReel || !it.videoUrl.isNullOrBlank() }
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

    LaunchedEffect(isOnline, isLoading, posts.isEmpty(), filteredPosts.isEmpty(), filter, laneIndex) {
        offlineEmptyConfirmed = false
        if (
            !isOnline &&
            !isLoading &&
            posts.isEmpty() &&
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

    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            filteredPosts.isNotEmpty() && last >= filteredPosts.lastIndex - 3
        }
    }

    val scrollConnection = remember(onBottomBarVisibilityChange, chromeScrollThreshold) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                scrollAccumulator[0] = (scrollAccumulator[0] + available.y)
                    .coerceIn(-chromeScrollThreshold * 2f, chromeScrollThreshold * 2f)

                when {
                    scrollAccumulator[0] <= -chromeScrollThreshold && bottomChromeVisible -> {
                        bottomChromeVisible = false
                        fabExpanded = false
                        scrollAccumulator[0] = 0f
                        onBottomBarVisibilityChange(false)
                    }
                    scrollAccumulator[0] >= chromeScrollThreshold && !bottomChromeVisible -> {
                        bottomChromeVisible = true
                        fabExpanded = true
                        scrollAccumulator[0] = 0f
                        onBottomBarVisibilityChange(true)
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
        }
    }

    LaunchedEffect(homeReselectSignal) {
        if (homeReselectSignal > 0) {
            onLaneChanged(0)
            filter = PremiumFeedFilter.ALL
            if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
                // Avoid animating through hundreds of composed rows when the user is deep
                // in the feed. Jump near the top, then animate only the final short distance.
                if (listState.firstVisibleItemIndex > 8) listState.scrollToItem(8)
                listState.animateScrollToItem(0)
            } else {
                onRefresh()
            }
            scrollAccumulator[0] = 0f
            bottomChromeVisible = true
            fabExpanded = true
            onBottomBarVisibilityChange(true)
        }
    }

    LaunchedEffect(nearEnd, hasMorePosts, isLoadingMorePosts, laneIndex) {
        // Pagination remains the normal ranked feed pagination. Filtering happens after
        // each page arrives, preserving the server algorithm for followed authors.
        if (nearEnd && hasMorePosts && !isLoadingMorePosts) onLoadMorePosts()
    }

    LaunchedEffect(listState, filteredPosts) {
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
                FeedTopBar(
                    userAvatar = userAvatar,
                    hasUnreadNotifications = hasUnreadNotifications,
                    onSearchClick = onSearchClick,
                    onNotificationClick = onOpenActivity,
                    onMenuClick = onOpenMenu,
                    onProfileClick = { onProfileClick(currentUsername) }
                )

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
                        if (!errorMessage.isNullOrBlank() && posts.isNotEmpty()) {
                            item(key = "refresh_error") {
                                PremiumFeedRefreshNotice(errorMessage, onRetry)
                            }
                        }

                        when {
                            isLoading && posts.isEmpty() -> {
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

                                    !isOnline && posts.isEmpty() && !offlineEmptyConfirmed -> {
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
                                                offlineNoCache = !isOnline && posts.isEmpty() && offlineEmptyConfirmed,
                                                onCreatePost = onOpenCreatePost,
                                                onClearFilter = { filter = PremiumFeedFilter.ALL }
                                            )
                                        }
                                    }
                                }
                            }

                            else -> {
                                items(
                                    count = filteredPosts.size,
                                    key = { index -> "post:${filteredPosts[index].id}" },
                                    contentType = { index -> premiumPostContentType(filteredPosts[index]) }
                                ) { index ->
                                    val post = filteredPosts[index]
                                    PremiumPostEntrance(index = index) {
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
                                            isAuthor = post.author.equals(currentUsername, ignoreCase = true),
                                            onDelete = { onDeletePost(post.id) }
                                        )
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

            CreatePostFab(
                expanded = fabExpanded,
                onClick = onOpenCreatePost,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 18.dp, bottom = 94.dp)
            )
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
