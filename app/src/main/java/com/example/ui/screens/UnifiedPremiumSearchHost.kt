package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.FeedPost
import com.example.data.models.UserProfile
import com.example.ui.theme.BlinkThemeTokens

/**
 * Unified Search entry point that keeps Phase 1, Phase 2 and Phase 3 available together.
 *
 * Universal = Phase 3 server-authoritative discovery.
 * Advanced = Phase 1 power search, filters, hashtags, campus and autocomplete.
 * Explore = Phase 2 Search / Trending / Places collections.
 */
private enum class UnifiedSearchMode(
    val label: String,
    val icon: ImageVector,
) {
    UNIVERSAL("Universal", Icons.Rounded.AutoAwesome),
    ADVANCED("Advanced", Icons.Rounded.Tune),
    EXPLORE("Explore", Icons.Rounded.Explore),
}

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
    var mode by rememberSaveable { mutableStateOf(UnifiedSearchMode.UNIVERSAL) }

    BackHandler(enabled = mode != UnifiedSearchMode.UNIVERSAL) {
        mode = UnifiedSearchMode.UNIVERSAL
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 54.dp),
        ) {
            Crossfade(
                targetState = mode,
                animationSpec = tween(durationMillis = 220),
                label = "unifiedSearchPhase",
            ) { selected ->
                when (selected) {
                    UnifiedSearchMode.UNIVERSAL -> PremiumSearchPhase3Host(
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

                    UnifiedSearchMode.ADVANCED -> PremiumSearchExperience(
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

                    UnifiedSearchMode.EXPLORE -> PremiumSearchPhase2Host(
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

        UnifiedSearchModeDock(
            selected = mode,
            onSelect = { mode = it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun UnifiedSearchModeDock(
    selected: UnifiedSearchMode,
    onSelect: (UnifiedSearchMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BlinkThemeTokens.colors

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.borderSoft),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp),
        ) {
            UnifiedSearchMode.entries.forEach { item ->
                val active = item == selected
                Surface(
                    onClick = { onSelect(item) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(15.dp),
                    color = if (active) colors.primaryBright.copy(alpha = 0.14f) else colors.surfaceElevated,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (active) colors.primaryBright else colors.textMuted,
                        )
                        Text(
                            text = item.label,
                            modifier = Modifier.padding(start = 5.dp),
                            fontSize = 10.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) colors.primaryBright else colors.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
