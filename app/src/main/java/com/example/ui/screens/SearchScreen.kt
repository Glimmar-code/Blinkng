package com.example.ui.screens

import androidx.compose.runtime.Composable
import com.example.data.models.FeedPost
import com.example.data.models.UserProfile

/**
 * Stable public entry point used by MainActivity.
 *
 * Search remains routed through a stable wrapper so navigation and ViewModel
 * contracts do not churn while premium discovery collections evolve in Testlab.
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
    PremiumSearchPhase2Host(
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
