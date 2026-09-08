package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.data.models.ConnectHubSnapshot
import com.example.data.models.FeedPost
import com.example.data.models.LeaderboardUser
import com.example.data.models.Story
import com.example.data.models.UserProfile

/**
 * Legacy feed UI has been retired.
 *
 * PremiumFeedScreen is the only home/feed implementation. This compatibility entry point
 * remains temporarily because PremiumFeedScreen routes its Reels destination through the
 * existing call contract. It contains no post-feed UI and delegates only to VideoReelsScreen.
 */
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
    onBottomBarVisibilityChange: (Boolean) -> Unit = {},
    initialReelId: String? = null,
    initialReelPositionMs: Long = 0L
) {
    if (currentSubTab == 1) {
        VideoReelsScreen(
            reels = reels,
            currentUsername = currentUsername,
            isDark = isDark,
            onLike = onLikePost,
            onComment = onCommentPost,
            onBookmark = onBookmarkPost,
            onShare = onSharePost,
            onDelete = onDeletePost,
            onProfileClick = onProfileClick,
            onBackToPosts = { onSubTabChanged(0) },
            profiles = profiles,
            connectHub = connectHub,
            connectHubActions = connectHubActions,
            onDirectMessage = onDirectMessage,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            hasMore = hasMoreReels,
            isLoadingMore = isLoadingMoreReels,
            onLoadMore = onLoadMoreReels,
            onHomeClick = { onSubTabChanged(0) },
            onConnectClick = { onSubTabChanged(2) },
            onGameClick = { onSubTabChanged(3) },
            initialReelId = initialReelId,
            initialReelPositionMs = initialReelPositionMs
        )
    } else {
        LaunchedEffect(currentSubTab) {
            onSubTabChanged(0)
        }
    }
}
