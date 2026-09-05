package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WifiOff
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.data.models.ConnectHubSnapshot
import com.example.data.models.FeedPost
import com.example.data.models.LeaderboardUser
import com.example.data.models.Story
import com.example.data.models.UserProfile
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

private enum class PremiumFeedLane { FOR_YOU, FOLLOWING }
private enum class PremiumFeedFilter { ALL, PHOTOS, POLLS }

/**
 * Reference-driven home feed shell. Non-home feed families delegate to the established
 * FeedScreen so Reels, Connect and Game retain their existing ViewModels, repositories,
 * navigation and backend behavior while the home feed gets the new premium chrome.
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
    // Preserve all existing interactive families exactly as they already work.
    if (currentSubTab != 0) {
        FeedScreen(
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
            currentSubTab = currentSubTab,
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
        return
    }

    PremiumHomeFeed(
        posts = posts,
        currentUsername = currentUsername,
        userAvatar = userAvatar,
        isLoading = isLoading,
        isRefreshing = isRefreshing,
        isServerConnected = isServerConnected,
        errorMessage = errorMessage,
        hasMorePosts = hasMorePosts,
        isLoadingMorePosts = isLoadingMorePosts,
        homeReselectSignal = homeReselectSignal,
        hasUnreadNotifications = hasUnreadNotifications,
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumHomeFeed(
    posts: List<FeedPost>,
    currentUsername: String,
    userAvatar: String,
    isLoading: Boolean,
    isRefreshing: Boolean,
    isServerConnected: Boolean,
    errorMessage: String?,
    hasMorePosts: Boolean,
    isLoadingMorePosts: Boolean,
    homeReselectSignal: Int,
    hasUnreadNotifications: Boolean,
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
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    val density = LocalDensity.current
    val latestViewed by rememberUpdatedState(onViewedPost)
    val impressionTracker = remember { PostImpressionTracker() }
    var lane by remember { mutableStateOf(PremiumFeedLane.FOR_YOU) }
    var filter by remember { mutableStateOf(PremiumFeedFilter.ALL) }
    var filterMenuVisible by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(true) }
    var screenVisible by remember { mutableStateOf(false) }

    val filteredPosts = remember(posts, filter) {
        posts.filterNot { it.isReel || !it.videoUrl.isNullOrBlank() }.filter { post ->
            when (filter) {
                PremiumFeedFilter.ALL -> true
                PremiumFeedFilter.PHOTOS -> post.images.any { it.isNotBlank() && !it.equals("null", true) }
                PremiumFeedFilter.POLLS -> post.poll != null
            }
        }
    }

    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            filteredPosts.isNotEmpty() && last >= filteredPosts.lastIndex - 3
        }
    }

    val scrollConnection = remember(onBottomBarVisibilityChange) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (available.y < -2f) {
                    fabExpanded = false
                    onBottomBarVisibilityChange(false)
                } else if (available.y > 2f) {
                    fabExpanded = true
                    onBottomBarVisibilityChange(true)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    LaunchedEffect(Unit) { screenVisible = true }

    LaunchedEffect(homeReselectSignal) {
        if (homeReselectSignal > 0) {
            lane = PremiumFeedLane.FOR_YOU
            filter = PremiumFeedFilter.ALL
            if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
                listState.animateScrollToItem(0)
            } else {
                onRefresh()
            }
            fabExpanded = true
            onBottomBarVisibilityChange(true)
        }
    }

    LaunchedEffect(nearEnd, hasMorePosts, isLoadingMorePosts) {
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

    val enterOffsetPx = with(density) { 8.dp.roundToPx() }

    AnimatedVisibility(
        visible = screenVisible,
        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { enterOffsetPx },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { enterOffsetPx },
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FeedBackground)
                .nestedScroll(scrollConnection)
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
                        selectedIndex = if (lane == PremiumFeedLane.FOR_YOU) 0 else 1,
                        onForYouClick = { lane = PremiumFeedLane.FOR_YOU },
                        onFollowingClick = { lane = PremiumFeedLane.FOLLOWING },
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
                        if (!isServerConnected) {
                            item(key = "connection_notice") {
                                PremiumFeedConnectionNotice(onRetry)
                            }
                        }
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
                                item(key = "empty_feed") {
                                    PremiumEmptyFeed(
                                        isFiltered = filter != PremiumFeedFilter.ALL,
                                        onCreatePost = onOpenCreatePost,
                                        onClearFilter = { filter = PremiumFeedFilter.ALL }
                                    )
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
private fun PremiumPostEntrance(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(10) * 35L))
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { 24 },
        exit = fadeOut(tween(120)),
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
private fun PremiumFeedConnectionNotice(onRetry: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 7.dp),
        shape = RoundedCornerShape(18.dp),
        color = FeedElevatedSurface,
        border = BorderStroke(1.dp, FeedBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = null,
                tint = FeedTextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Connection interrupted", color = FeedTextPrimary, style = MaterialTheme.typography.labelMedium)
                Text(
                    "Showing available cached posts while Blink reconnects.",
                    color = FeedTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            TextButton(onClick = onRetry) { Text("Retry", color = FeedPurple) }
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
            text = if (isFiltered) "No posts match this filter" else "Your feed is ready for something new",
            color = FeedTextPrimary,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = if (isFiltered) "Choose another feed filter to keep browsing." else "Share an update, photo or poll with your campus.",
            color = FeedTextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(14.dp))
        TextButton(onClick = if (isFiltered) onClearFilter else onCreatePost) {
            Text(if (isFiltered) "Show all posts" else "Create Post", color = FeedPurple)
        }
    }
}
