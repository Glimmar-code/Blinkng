package com.example.ui.screens

import androidx.compose.runtime.Composable
import com.example.data.models.FeedPost
import com.example.data.models.UserProfile

/**
 * One professional search surface.
 *
 * The previous Universal / Advanced / Explore selector exposed internal search phases
 * to users and reserved extra top chrome. Phase 3 remains the canonical server-backed
 * search experience; its existing people, post and discovery behavior is preserved.
 */
@Composable
internal fun UnifiedPremiumSearchHost(
    profiles: List<UserProfile>,
    posts: List<FeedPost>,
    currentUsername: String,
    serverProfiles: List<UserProfile>,
    serverPosts: List<FeedPost>,
    isSearching: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    onPostClick: (FeedPost) -> Unit,
    onLikePost: (String) -> Unit,
    onCommentPost: (String) -> Unit,
    onBookmarkPost: (String) -> Unit,
    onSharePost: (String) -> Unit,
    onOptionsClick: (FeedPost) -> Unit,
    onDeletePost: (String) -> Unit,
    onBackToHome: () -> Unit,
    isDark: Boolean,
) {
    PremiumSearchPhase3Host(
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
