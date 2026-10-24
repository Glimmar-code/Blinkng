package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.data.models.FeedPost
import com.example.data.models.MarketItem
import com.example.data.models.UserProfile

/**
 * Stable public Search entry point used by MainActivity.
 *
 * Phase 1, Phase 2 and Phase 3 are intentionally exposed together through
 * UnifiedPremiumSearchHost so newer discovery work never hides useful earlier tools.
 */
@Composable
fun SearchScreen(
    profiles: List<UserProfile>,
    posts: List<FeedPost>,
    currentUsername: String,
    serverProfiles: List<UserProfile> = emptyList(),
    serverPosts: List<FeedPost> = emptyList(),
    marketItems: List<MarketItem> = emptyList(),
    isSearching: Boolean = false,
    onSearchQueryChange: (String) -> Unit = {},
    onProfileClick: (String) -> Unit,
    onPostClick: (FeedPost) -> Unit,
    onMarketItemClick: (MarketItem) -> Unit = {},
    onGameClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    hasUnreadNotifications: Boolean = false,
    onLikePost: (String) -> Unit = {},
    onCommentPost: (String) -> Unit = {},
    onBookmarkPost: (String) -> Unit = {},
    onSharePost: (String) -> Unit = {},
    onOptionsClick: (FeedPost) -> Unit = {},
    onDeletePost: (String) -> Unit = {},
    onBackToHome: () -> Unit = {},
    isDark: Boolean
) {
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    if (!showAdvanced) {
        BlinkReferenceSearchScreen(
            profiles = profiles,
            posts = posts,
            serverProfiles = serverProfiles,
            serverPosts = serverPosts,
            marketItems = marketItems,
            currentUsername = currentUsername,
            onProfileClick = onProfileClick,
            onPostClick = onPostClick,
            onMarketItemClick = onMarketItemClick,
            onGameClick = onGameClick,
            onNotificationsClick = onNotificationsClick,
            hasUnreadNotifications = hasUnreadNotifications,
            onOpenAdvanced = { showAdvanced = true },
            onBackToHome = onBackToHome,
            isDark = isDark,
        )
    } else {
        UnifiedPremiumSearchHost(
        profiles = profiles,
        posts = posts,
        currentUsername = currentUsername,
        serverProfiles = serverProfiles,
        serverPosts = serverPosts,
        isSearching = isSearching,
        onSearchQueryChange = onSearchQueryChange,
        onProfileClick = onProfileClick,
        onPostClick = onPostClick,
        onLikePost = onLikePost,
        onCommentPost = onCommentPost,
        onBookmarkPost = onBookmarkPost,
        onSharePost = onSharePost,
        onOptionsClick = onOptionsClick,
        onDeletePost = onDeletePost,
        onBackToHome = { showAdvanced = false },
        isDark = isDark,
        )
    }
}
