package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.models.ConnectHubSnapshot
import com.example.data.models.FeedPost
import com.example.data.models.Story
import com.example.data.models.UserProfile
import com.example.data.models.LeaderboardUser
import com.example.ui.components.PostCard
import com.example.ui.components.PremiumPullRefreshIndicator
import com.example.ui.components.StoryBar
import com.example.ui.components.shimmerBackground
import com.example.ui.theme.BlinkBlack
import com.example.ui.theme.BlinkCream
import com.example.ui.theme.BlinkPink
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun FeedScreen(
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
    onBottomBarVisibilityChange: (Boolean) -> Unit = {}
) {
    val selectedTopTab = currentSubTab
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    val bottomBarVisibility by rememberUpdatedState(onBottomBarVisibilityChange)
    val recordVisiblePost by rememberUpdatedState(onViewedPost)
    val refreshFeed by rememberUpdatedState(onRefresh)
    val postIds = remember(posts) { posts.mapTo(linkedSetOf()) { it.id } }
    // 0 = full header + tabs, 1 = compact utility header, 2 = hidden while scrolling.
    var chromeStage by remember { mutableIntStateOf(0) }
    var fabVisible by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collectLatest { scrolling ->
            if (!scrolling) {
                delay(800)
                if (!listState.isScrollInProgress) {
                    // After 0.8s idle, restore the compact menu/home/notification/profile row.
                    if (chromeStage == 2) chromeStage = 1
                    fabVisible = true
                }
            }
        }
    }

    val nestedScrollConnection = remember(selectedTopTab) {
        object : NestedScrollConnection {
            private var lastVisible = true
            private var accumulatedScroll = 0f
            private var chromeScroll = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero

                if (available.y < 0f) {
                    // Upward swipe: fold Home/Reel/Connect/Game first, then fade utilities.
                    chromeScroll += -available.y
                    fabVisible = false
                    when {
                        chromeStage == 0 && chromeScroll >= 44f -> {
                            chromeStage = 1
                            chromeScroll = 0f
                        }
                        chromeStage == 1 && chromeScroll >= 72f -> {
                            chromeStage = 2
                            chromeScroll = 0f
                        }
                    }
                } else {
                    // Downward navigation restores the full chrome immediately.
                    chromeScroll = 0f
                    chromeStage = 0
                    fabVisible = true
                }

                // Do not animate the bottom bar for every tiny finger movement. Accumulate
                // intentional movement and only change visibility after a meaningful swipe.
                if ((accumulatedScroll > 0f && available.y < 0f) ||
                    (accumulatedScroll < 0f && available.y > 0f)
                ) {
                    accumulatedScroll = 0f
                }

                accumulatedScroll = (accumulatedScroll + available.y).coerceIn(-160f, 160f)
                val shouldBeVisible = when {
                    accumulatedScroll <= -56f -> false
                    accumulatedScroll >= 56f -> true
                    else -> null
                }

                if (shouldBeVisible != null) {
                    accumulatedScroll = 0f
                    if (shouldBeVisible != lastVisible) {
                        lastVisible = shouldBeVisible
                        bottomBarVisibility(shouldBeVisible)
                    }
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(selectedTopTab) {
        bottomBarVisibility(true)
        chromeStage = 0
        fabVisible = true
    }

    LaunchedEffect(homeReselectSignal, selectedTopTab) {
        if (homeReselectSignal <= 0 || selectedTopTab != 0) return@LaunchedEffect

        bottomBarVisibility(true)
        val isAlreadyAtTop = listState.firstVisibleItemIndex == 0 &&
            listState.firstVisibleItemScrollOffset == 0

        if (!isAlreadyAtTop) {
            // For long feeds, jump near the top first so the visible smooth animation is quick
            // instead of trying to animate through hundreds of composed rows.
            if (listState.firstVisibleItemIndex > 8) {
                listState.scrollToItem(8)
            }
            listState.animateScrollToItem(0)
        }
        refreshFeed()
    }

    LaunchedEffect(listState, postIds, selectedTopTab) {
        if (selectedTopTab != 0 || postIds.isEmpty()) return@LaunchedEffect

        val tracker = PostImpressionTracker()
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            layoutInfo.visibleItemsInfo.mapNotNullTo(linkedSetOf()) { item ->
                val postId = item.key as? String
                postId?.takeIf {
                    it in postIds && qualifiesForPostImpression(
                        itemOffset = item.offset,
                        itemSize = item.size,
                        viewportStart = layoutInfo.viewportStartOffset,
                        viewportEnd = layoutInfo.viewportEndOffset
                    )
                }
            }
        }.collect { qualifiedPostIds ->
            tracker.update(qualifiedPostIds).forEach(recordVisiblePost)
        }
    }

    fun navigate(tab: Int) {
        if (currentSubTab != tab) onSubTabChanged(tab)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (selectedTopTab) {
            1 -> VideoReelsScreen(
                reels = reels,
                currentUsername = currentUsername,
                isDark = isDark,
                onLike = onLikePost,
                onComment = onCommentPost,
                onBookmark = onBookmarkPost,
                onShare = onSharePost,
                onDelete = onDeletePost,
                onProfileClick = onProfileClick,
                onBackToPosts = { navigate(0) },
                isLoading = isLoading,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                hasMore = hasMoreReels,
                isLoadingMore = isLoadingMoreReels,
                onLoadMore = onLoadMoreReels,
                onHomeClick = { navigate(0) },
                onConnectClick = { navigate(2) },
                onGameClick = { navigate(3) }
            )

            2 -> ConnectSection(
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
                onHomeClick = { navigate(0) },
                onReelClick = { navigate(1) },
                onConnectClick = { navigate(2) },
                onGameClick = { navigate(3) }
            )

            3 -> GameSection(
                userAvatar = userAvatar,
                leaderboardUsers = leaderboardUsers,
                connectHub = connectHub,
                connectHubActions = connectHubActions,
                isDark = isDark,
                onOpenMenu = onOpenMenu,
                onOpenActivity = onOpenActivity,
                onProfileClick = onProfileClick,
                selectedTopTab = 3,
                onHomeClick = { navigate(0) },
                onReelClick = { navigate(1) },
                onConnectClick = { navigate(2) },
                onGameClick = { navigate(3) }
            )

            else -> {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = pullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PremiumPullRefreshIndicator(
                            state = pullToRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(nestedScrollConnection)
                            .pointerInput(selectedTopTab) {
                                var horizontalDrag = 0f
                                val openMenuThreshold = 76.dp.toPx()
                                detectHorizontalDragGestures(
                                    onDragStart = { horizontalDrag = 0f },
                                    onHorizontalDrag = { _, dragAmount ->
                                        horizontalDrag += dragAmount
                                    },
                                    onDragEnd = {
                                        if (horizontalDrag >= openMenuThreshold) {
                                            onOpenMenu()
                                        }
                                        horizontalDrag = 0f
                                    },
                                    onDragCancel = { horizontalDrag = 0f }
                                )
                            },
                        contentPadding = PaddingValues(bottom = 92.dp)
                    ) {
                        stickyHeader(key = "home_feed_chrome") {
                            Surface(
                                color = MaterialTheme.colorScheme.background,
                                tonalElevation = if (chromeStage < 2) 2.dp else 0.dp
                            ) {
                                Column {
                                    AnimatedVisibility(
                                        visible = chromeStage < 2,
                                        enter = fadeIn(tween(180)) + expandVertically(),
                                        exit = fadeOut(tween(140)) + shrinkVertically()
                                    ) {
                                        HomeHeader(
                                            userAvatar = userAvatar,
                                            onMenuClick = onOpenMenu,
                                            onNotificationClick = onOpenActivity,
                                            onProfileClick = { onProfileClick("you") }
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = chromeStage == 0,
                                        enter = fadeIn(tween(180)) + expandVertically(),
                                        exit = fadeOut(tween(140)) + shrinkVertically()
                                    ) {
                                        TopNavigation(
                                            selected = selectedTopTab,
                                            onHome = { navigate(0) },
                                            onReel = { navigate(1) },
                                            onConnect = { navigate(2) },
                                            onGame = { navigate(3) }
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = isRefreshing,
                                        enter = fadeIn(tween(150)) + expandVertically(),
                                        exit = fadeOut(tween(150)) + shrinkVertically()
                                    ) {
                                        FeedRefreshingBanner()
                                    }
                                }
                            }
                        }

                        if (!errorMessage.isNullOrBlank() && posts.isNotEmpty()) {
                            item(key = "stale_feed_banner") {
                                FeedRefreshNotice(onRetry = onRetry)
                            }
                        }

                    item {
                        Spacer(Modifier.height(6.dp))
                        StoryBar(
                            stories = stories,
                            userAvatar = userAvatar,
                            onAddStory = onAddStoryClick,
                            onStoryClick = onStoryClick
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    when {
                        isLoading && posts.isEmpty() -> {
                            items(3, key = { "feed_loading_$it" }) {
                                FeedLoadingCard()
                            }
                        }

                        posts.isEmpty() && !errorMessage.isNullOrBlank() -> {
                            item(key = "feed_error") {
                                FeedErrorState(
                                    message = errorMessage,
                                    onRetry = onRetry
                                )
                            }
                        }

                        posts.isEmpty() -> {
                            item(key = "feed_empty") {
                                EmptyHomeFeed(onOpenCreatePost)
                            }
                        }

                        else -> {
                            items(
                                items = posts,
                                key = { it.id },
                                contentType = { feedPostContentType(it) }
                            ) { post ->
                                Column {
                                    PostCard(
                                        post = post,
                                        isDark = isDark,
                                        onLike = { onLikePost(post.id) },
                                        onComment = { onCommentPost(post.id) },
                                        onBookmark = { onBookmarkPost(post.id) },
                                        onRepost = { onRepostPost(post.id) },
                                        onShare = { onSharePost(post.id) },
                                        onOptionsClick = { onOptionsClick(post) },
                                        onProfileClick = onProfileClick,
                                        isAuthor = post.author.equals(currentUsername, true),
                                        onDelete = { onDeletePost(post.id) },
                                        onVotePoll = onVotePoll
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                            if (hasMorePosts) {
                                item(key = "feed_load_more") {
                                    LaunchedEffect(posts.lastOrNull()?.id) {
                                        if (!isLoadingMorePosts) onLoadMorePosts()
                                    }
                                    Box(
                                        Modifier.fillMaxWidth().padding(20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isLoadingMorePosts) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                        } else {
                                            Text("Loading more…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }

                AnimatedVisibility(
                    visible = fabVisible,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(140)),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 20.dp, bottom = 92.dp)
                ) {
                    FloatingActionButton(
                        onClick = onOpenCreatePost,
                        containerColor = if (isDark) BlinkCream else BlinkBlack,
                        contentColor = if (isDark) BlinkBlack else BlinkCream,
                        modifier = Modifier.testTag("create_post_fab")
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Create post",
                            modifier = Modifier.size(27.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun feedPostContentType(post: FeedPost): Int {
    var type = 0
    if (post.text.isNotBlank()) type = type or 1
    if (post.images.any { it.isNotBlank() && !it.equals("null", ignoreCase = true) }) type = type or 2
    if (post.poll != null) type = type or 4
    if (post.isSponsored) type = type or 8
    return type
}

@Composable
private fun HomeHeader(
    userAvatar: String,
    onMenuClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                Icons.Default.MoreHoriz,
                contentDescription = "Menu",
                modifier = Modifier.size(27.dp)
            )
        }

        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Home",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
            Text(
                "Your campus, in real time",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(
            onClick = onNotificationClick,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                Icons.Default.NotificationsNone,
                contentDescription = "Notifications",
                modifier = Modifier.size(25.dp)
            )
        }

        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onProfileClick)
                .semantics { },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = userAvatar,
                contentDescription = "Profile",
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
            )
        }

        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun TopNavigation(
    selected: Int,
    onHome: () -> Unit,
    onReel: () -> Unit,
    onConnect: () -> Unit,
    onGame: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TopTab("Home", selected == 0, onHome)
        TopTab("Reel", selected == 1, onReel)
        TopTab("Connect", selected == 2, onConnect)
        TopTab("Game", selected == 3, onGame)
    }
}

@Composable
private fun TopTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick),
        shape = RoundedCornerShape(100.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    ) {
        Text(
            text,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun FeedRefreshingBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Refreshing feed…",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FeedConnectionNotice(onRetry: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Icon(
                    Icons.Default.WifiOff,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(11.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    "Connection interrupted",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Showing what is available. New posts and messages may take a moment to sync.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun FeedRefreshNotice(onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Showing your last refreshed feed.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onRetry) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("Refresh")
        }
    }
}

@Composable
private fun FeedLoadingCard() {
    val shimmerBase = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)
    val shimmerHighlight = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .shimmerBackground(CircleShape, shimmerBase, shimmerHighlight)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Box(
                        Modifier
                            .width(120.dp)
                            .height(12.dp)
                            .shimmerBackground(RoundedCornerShape(8.dp), shimmerBase, shimmerHighlight)
                    )
                    Spacer(Modifier.height(7.dp))
                    Box(
                        Modifier
                            .width(78.dp)
                            .height(9.dp)
                            .shimmerBackground(RoundedCornerShape(8.dp), shimmerBase, shimmerHighlight)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Box(
                Modifier
                    .fillMaxWidth(.9f)
                    .height(12.dp)
                    .shimmerBackground(RoundedCornerShape(8.dp), shimmerBase, shimmerHighlight)
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth(.68f)
                    .height(12.dp)
                    .shimmerBackground(RoundedCornerShape(8.dp), shimmerBase, shimmerHighlight)
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .shimmerBackground(RoundedCornerShape(14.dp), shimmerBase, shimmerHighlight)
            )
        }
    }
}

@Composable
private fun FeedErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp, vertical = 54.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Icon(
                Icons.Default.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .size(60.dp)
                    .padding(16.dp)
            )
        }

        Spacer(Modifier.height(15.dp))

        Text(
            "Feed unavailable",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(6.dp))

        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text("Try again")
        }
    }
}

@Composable
private fun EmptyHomeFeed(onCreatePost: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = BlinkPink.copy(alpha = 0.12f)
        ) {
            Icon(
                Icons.Default.Home,
                contentDescription = null,
                tint = BlinkPink,
                modifier = Modifier
                    .size(58.dp)
                    .padding(14.dp)
            )
        }

        Spacer(Modifier.height(15.dp))

        Text(
            "Your feed is ready for something new",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(5.dp))

        Text(
            "Share an update, photo, reel, or poll with your campus.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        Button(onClick = onCreatePost) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text("Create post")
        }
    }
}
