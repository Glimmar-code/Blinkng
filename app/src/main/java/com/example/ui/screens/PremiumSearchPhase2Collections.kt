package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.FeedPost
import com.example.data.models.UserProfile
import com.example.ui.theme.BlinkThemeTokens
import java.util.Locale

/**
 * Phase 2A discovery collections layered on top of the stable Phase 1 search UI.
 *
 * Only collections backed by data available on both Android and Windows ship here.
 * Saved, Communities, Events and Marketplace remain staged until their cross-platform
 * search contracts are available; this prevents attractive controls that cannot work.
 */
private enum class PremiumSearchCollection(
    val label: String,
    val icon: ImageVector,
) {
    SEARCH("Search", Icons.Rounded.Search),
    TRENDING("Trending", Icons.Rounded.TrendingUp),
    PLACES("Places", Icons.Rounded.LocationOn),
}

private data class PremiumPlaceResult(
    val label: String,
    val mentions: Int,
    val posts: List<FeedPost>,
)

@Composable
internal fun PremiumSearchPhase2Host(
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
    var collection by rememberSaveable { mutableStateOf(PremiumSearchCollection.SEARCH) }
    val allProfiles = remember(profiles, serverProfiles) {
        (profiles + serverProfiles)
            .filter { it.username.isNotBlank() }
            .distinctBy { it.id.ifBlank { it.username.lowercase() } }
    }
    val allPosts = remember(posts, serverPosts) {
        (posts + serverPosts).distinctBy { it.id }
    }

    BackHandler(enabled = collection != PremiumSearchCollection.SEARCH) {
        collection = PremiumSearchCollection.SEARCH
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Crossfade(
            targetState = collection,
            animationSpec = tween(durationMillis = 240),
            label = "premiumSearchCollection",
        ) { selected ->
            when (selected) {
                PremiumSearchCollection.SEARCH -> PremiumSearchExperience(
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

                PremiumSearchCollection.TRENDING -> PremiumTrendingCollection(
                    profiles = allProfiles,
                    posts = allPosts,
                    onProfileClick = onProfileClick,
                    onPostClick = onPostClick,
                )

                PremiumSearchCollection.PLACES -> PremiumPlacesCollection(
                    profiles = allProfiles,
                    posts = allPosts,
                    onPostClick = onPostClick,
                )
            }
        }

        PremiumSearchCollectionDock(
            selected = collection,
            onSelect = { collection = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun PremiumSearchCollectionDock(
    selected: PremiumSearchCollection,
    onSelect: (PremiumSearchCollection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BlinkThemeTokens.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.borderSoft),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PremiumSearchCollection.entries.forEach { item ->
                val isSelected = item == selected
                Surface(
                    onClick = { onSelect(item) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSelected) colors.primaryBright.copy(alpha = 0.14f) else colors.surfaceElevated,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(17.dp),
                            tint = if (isSelected) colors.primaryBright else colors.textSecondary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = item.label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) colors.primaryBright else colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumTrendingCollection(
    profiles: List<UserProfile>,
    posts: List<FeedPost>,
    onProfileClick: (String) -> Unit,
    onPostClick: (FeedPost) -> Unit,
) {
    val trendingPosts = remember(posts) { posts.sortedByDescending(::phase2TrendScore).take(30) }
    val trendingCreators = remember(profiles) {
        profiles.sortedWith(
            compareByDescending<UserProfile> { it.followerCount }
                .thenByDescending { it.points }
                .thenByDescending { it.onlineNow }
        ).take(16)
    }
    val trendingTags = remember(posts) {
        posts.flatMap { post ->
            post.tags.map { tag -> tag.trim().removePrefix("#").lowercase() to phase2TrendScore(post) }
        }
            .filter { it.first.isNotBlank() }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, scores) -> scores.sum() }
            .entries
            .sortedByDescending { it.value }
            .take(12)
    }

    PremiumCollectionScaffold(
        title = "Trending now",
        subtitle = "High-momentum people, posts, reels and topics on Blink",
    ) {
        if (trendingTags.isNotEmpty()) {
            item { PremiumCollectionHeading("Trending searches", "Live momentum from current Blink content") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(trendingTags, key = { it.key }) { trend ->
                        PremiumTrendChip(tag = trend.key, score = trend.value)
                    }
                }
            }
        }

        if (trendingCreators.isNotEmpty()) {
            item { PremiumCollectionHeading("Trending creators", "Profiles with the strongest current social signal") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(trendingCreators, key = { it.id.ifBlank { it.username } }) { profile ->
                        PremiumCreatorMiniCard(profile = profile, onClick = { onProfileClick(profile.username) })
                    }
                }
            }
        }

        if (trendingPosts.isNotEmpty()) {
            item { PremiumCollectionHeading("Rising posts & reels", "Sorted by views, likes, comments and shares") }
            items(trendingPosts, key = { "trend-${it.id}" }) { post ->
                PremiumCollectionPostCard(post = post, onClick = { onPostClick(post) })
            }
        } else {
            item { PremiumCollectionEmpty("Nothing is trending yet", "As people interact with posts and reels, rising content will appear here.") }
        }
    }
}

@Composable
private fun PremiumPlacesCollection(
    profiles: List<UserProfile>,
    posts: List<FeedPost>,
    onPostClick: (FeedPost) -> Unit,
) {
    val places = remember(profiles, posts) {
        val postLocations = posts.mapNotNull { post ->
            post.location?.trim()?.takeIf { it.isNotBlank() }?.let { it to post }
        }
        val profileLocations = profiles.mapNotNull { profile ->
            profile.currentCityState.trim().takeIf { it.isNotBlank() }
        }
        val labels = (postLocations.map { it.first } + profileLocations)
            .distinctBy { it.lowercase() }

        labels.map { label ->
            val locationPosts = postLocations
                .filter { (location, _) -> location.equals(label, ignoreCase = true) }
                .map { it.second }
                .sortedByDescending(::phase2TrendScore)
            val profileMentions = profileLocations.count { it.equals(label, ignoreCase = true) }
            PremiumPlaceResult(
                label = label,
                mentions = locationPosts.size + profileMentions,
                posts = locationPosts,
            )
        }.sortedWith(compareByDescending<PremiumPlaceResult> { it.mentions }.thenBy { it.label.lowercase() })
    }

    PremiumCollectionScaffold(
        title = "Places",
        subtitle = "Discover campus and nearby places already attached to Blink profiles and posts",
    ) {
        if (places.isEmpty()) {
            item { PremiumCollectionEmpty("No places yet", "Places appear here when users add a location to their profile, post or reel.") }
        } else {
            item { PremiumCollectionHeading("Popular places", "${places.size} location${if (places.size == 1) "" else "s"} discovered") }
            items(places.take(40), key = { it.label.lowercase() }) { place ->
                PremiumPlaceCard(place = place, onPostClick = onPostClick)
            }
        }
    }
}

@Composable
private fun PremiumCollectionScaffold(
    title: String,
    subtitle: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val colors = BlinkThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(title, fontSize = 25.sp, fontWeight = FontWeight.Black, color = colors.textPrimary)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, fontSize = 11.sp, color = colors.textSecondary)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 106.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun PremiumCollectionHeading(title: String, subtitle: String) {
    val colors = BlinkThemeTokens.colors
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
        Text(subtitle, fontSize = 10.sp, color = colors.textSecondary)
    }
}

@Composable
private fun PremiumTrendChip(tag: String, score: Long) {
    val colors = BlinkThemeTokens.colors
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.borderSoft),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.TrendingUp, null, modifier = Modifier.size(16.dp), tint = colors.primaryBright)
            Spacer(Modifier.width(6.dp))
            Column {
                Text("#$tag", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text(phase2CompactNumber(score) + " signal", fontSize = 9.sp, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun PremiumCreatorMiniCard(profile: UserProfile, onClick: () -> Unit) {
    val colors = BlinkThemeTokens.colors
    Surface(
        modifier = Modifier.width(142.dp),
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.borderSoft),
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = profile.avatarUrl,
                contentDescription = profile.fullName.ifBlank { profile.username },
                modifier = Modifier.size(52.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                profile.fullName.ifBlank { profile.username },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            Text("@${profile.username.removePrefix("@")}", maxLines = 1, fontSize = 10.sp, color = colors.textSecondary)
            Spacer(Modifier.height(5.dp))
            Text("${phase2CompactNumber(profile.followerCount.toLong())} followers", fontSize = 9.sp, color = colors.primaryBright)
        }
    }
}

@Composable
private fun PremiumCollectionPostCard(post: FeedPost, onClick: () -> Unit) {
    val colors = BlinkThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.borderSoft),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = post.authorAvatar,
                    contentDescription = post.author,
                    modifier = Modifier.size(38.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(post.author, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text(post.timeAgo, fontSize = 9.sp, color = colors.textSecondary)
                }
                Icon(
                    imageVector = if (post.isReel) Icons.Rounded.VideoLibrary else Icons.Rounded.Photo,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.primaryBright,
                )
            }
            if (post.text.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(post.text, maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = colors.textPrimary)
            }
            val preview = post.images.firstOrNull()
            if (!preview.isNullOrBlank()) {
                Spacer(Modifier.height(9.dp))
                AsyncImage(
                    model = preview,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(156.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                PremiumMetric(Icons.Rounded.TrendingUp, phase2CompactNumber(post.viewsCount.toLong()))
                PremiumMetric(Icons.Rounded.Tag, phase2CompactNumber(post.likes.toLong()) + " likes")
                if (!post.location.isNullOrBlank()) PremiumMetric(Icons.Rounded.LocationOn, post.location.orEmpty())
            }
        }
    }
}

@Composable
private fun PremiumMetric(icon: ImageVector, value: String) {
    val colors = BlinkThemeTokens.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(13.dp), tint = colors.textSecondary)
        Spacer(Modifier.width(4.dp))
        Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 9.sp, color = colors.textSecondary)
    }
}

@Composable
private fun PremiumPlaceCard(place: PremiumPlaceResult, onPostClick: (FeedPost) -> Unit) {
    val colors = BlinkThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.borderSoft),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = colors.primaryBright.copy(alpha = .14f)) {
                    Icon(Icons.Rounded.LocationOn, null, modifier = Modifier.padding(9.dp).size(19.dp), tint = colors.primaryBright)
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(place.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text("${place.mentions} Blink mention${if (place.mentions == 1) "" else "s"}", fontSize = 10.sp, color = colors.textSecondary)
                }
            }
            if (place.posts.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(place.posts.take(6), key = { it.id }) { post ->
                        Surface(
                            modifier = Modifier.width(178.dp).clickable { onPostClick(post) },
                            shape = RoundedCornerShape(14.dp),
                            color = colors.background,
                            border = BorderStroke(1.dp, colors.borderSoft),
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(post.author, maxLines = 1, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    post.text.ifBlank { if (post.isReel) "Reel at ${place.label}" else "Post at ${place.label}" },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 10.sp,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumCollectionEmpty(title: String, message: String) {
    val colors = BlinkThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        shape = RoundedCornerShape(22.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.borderSoft),
    ) {
        Column(modifier = Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Search, null, modifier = Modifier.size(28.dp), tint = colors.primaryBright)
            Spacer(Modifier.height(10.dp))
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(message, fontSize = 11.sp, color = colors.textSecondary)
        }
    }
}

private fun phase2TrendScore(post: FeedPost): Long =
    post.viewsCount.toLong() +
        post.likes.toLong() * 4L +
        post.commentsCount.toLong() * 6L +
        post.sharesCount.toLong() * 8L +
        post.repostsCount.toLong() * 7L

private fun phase2CompactNumber(value: Long): String = when {
    value >= 1_000_000L -> compactWithSuffix(value / 1_000_000.0, "M")
    value >= 1_000L -> compactWithSuffix(value / 1_000.0, "K")
    else -> value.toString()
}

private fun compactWithSuffix(value: Double, suffix: String): String {
    val formatted = String.format(Locale.US, "%.1f", value).removeSuffix(".0")
    return "$formatted$suffix"
}
