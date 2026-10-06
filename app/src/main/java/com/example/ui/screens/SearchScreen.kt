package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.data.models.FeedPost
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
    isSearching: Boolean = false,
    onSearchQueryChange: (String) -> Unit = {},
    onProfileClick: (String) -> Unit,
    onPostClick: (FeedPost) -> Unit,
    onLikePost: (String) -> Unit = {},
    onCommentPost: (String) -> Unit = {},
    onBookmarkPost: (String) -> Unit = {},
    onSharePost: (String) -> Unit = {},
    onOptionsClick: (FeedPost) -> Unit = {},
    onDeletePost: (String) -> Unit = {},
    onBackToHome: () -> Unit = {},
    isDark: Boolean
) {
    Column(modifier = Modifier.fillMaxSize()) {
        BlinkPromotedDiscoveryRail(
            surface = "SEARCH",
            onProfileClick = onProfileClick,
            onPostClick = onPostClick,
        )
        Box(modifier = Modifier.weight(1f)) {
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
                onBackToHome = onBackToHome,
                isDark = isDark,
            )
        }
    }
}
