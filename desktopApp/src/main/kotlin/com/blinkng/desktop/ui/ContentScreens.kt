package com.blinkng.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.desktop.DesktopAppState
import com.blinkng.desktop.data.DesktopComment
import com.blinkng.desktop.data.DesktopConnectInbox
import com.blinkng.desktop.data.DesktopConnectListing
import com.blinkng.desktop.data.DesktopFeedPost
import com.blinkng.desktop.data.DesktopInventoryItem
import com.blinkng.desktop.data.DesktopLeaderboardEntry
import com.blinkng.desktop.data.DesktopMarketItem
import com.blinkng.desktop.data.DesktopProfile
import com.blinkng.desktop.data.DesktopRpcActions
import com.blinkng.desktop.data.DesktopNotification
import com.blinkng.desktop.data.DesktopSearchResults
import com.blinkng.desktop.data.DesktopStoreItem
import com.blinkng.desktop.data.DesktopUserSettings
import com.blinkng.desktop.sharing.DesktopShareLinkManager
import com.blinkng.shared.BlinkActivityPulseDefaults
import com.blinkng.shared.BlinkActivityPulsePolicy
import com.blinkng.shared.BlinkPulseSessionStore
import com.blinkng.shared.BlinkPulseTrend
import com.blinkng.shared.campusActivityLabel
import com.blinkng.shared.communityActivityRange
import com.blinkng.shared.nextPulseValue
import com.blinkng.shared.pulseTrend
import com.blinkng.shared.rankPulseRange
import com.blinkng.shared.BlinkCoinPack
import com.blinkng.shared.BlinkDailyMission
import com.blinkng.shared.BlinkEconomyDefaults
import com.blinkng.shared.BlinkEconomyPolicy
import com.blinkng.shared.BlinkRewardMilestone
import com.blinkng.shared.xpProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.random.Random
import java.awt.Desktop
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.json.JSONObject
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

@Composable
fun HomeScreen(
    state: DesktopAppState,
    onFeedCompactChange: (Boolean) -> Unit = {},
) {
    var posts by remember { mutableStateOf<List<DesktopFeedPost>>(emptyList()) }
    var composer by remember { mutableStateOf("") }
    var composerAudience by remember { mutableStateOf("Everyone") }
    var composerCategory by remember { mutableStateOf("Campus Life") }
    var composerLocation by remember { mutableStateOf("") }
    var composerLink by remember { mutableStateOf("") }
    var composerAllowComments by remember { mutableStateOf(true) }
    var composerHideLikes by remember { mutableStateOf(false) }
    var composerTemporary by remember { mutableStateOf(false) }
    var composerMoreSettings by remember { mutableStateOf(false) }
    var composerRequestId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var commentsFor by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 52
        }.collect { compact ->
            onFeedCompactChange(compact)
        }
    }

    suspend fun reload() {
        loading = true
        runCatching { state.client.fetchFeed() }
            .onSuccess { posts = it; error = null }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    fun normalizedComposerLink(): String? {
        val raw = composerLink.trim()
        if (raw.isBlank()) return null
        val hasExplicitScheme = raw.contains("://")
        val isHttpScheme = raw.startsWith("https://", true) || raw.startsWith("http://", true)
        if (hasExplicitScheme && !isHttpScheme) return null
        val candidate = if (isHttpScheme) raw else "https://$raw"
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        return candidate.takeIf {
            (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
                !uri.host.isNullOrBlank()
        }
    }

    val composerLinkValid = composerLink.isBlank() || normalizedComposerLink() != null

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Box(Modifier.padding(bottom = 14.dp)) {
                ScreenHeader("Home", "Your live Blinkng feed")
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = { state.selectedRoute = "leaderboard" }) {
                    Icon(Icons.Rounded.Leaderboard, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Rank")
                }
                OutlinedButton(onClick = { state.selectedRoute = "games" }) {
                    Icon(Icons.Rounded.SportsEsports, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Game")
                }
                OutlinedButton(onClick = { state.selectedRoute = "store" }) {
                    Icon(Icons.Rounded.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Store")
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.padding(bottom = 20.dp),
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = composer,
                        onValueChange = { composer = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 6,
                        label = { Text("Create a post") },
                        placeholder = { Text("What's happening on campus?") },
                    )
                    Text(
                        "Audience",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Everyone", "Campus", "Followers").forEach { item ->
                            FilterChip(
                                selected = composerAudience == item,
                                onClick = { composerAudience = item },
                                label = { Text(item) },
                            )
                        }
                    }

                    Text(
                        "Category",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("Campus Life", "Academic", "Events", "Sports", "Entertainment", "Marketplace").forEach { item ->
                            FilterChip(
                                selected = composerCategory == item,
                                onClick = { composerCategory = item },
                                label = { Text(item) },
                            )
                        }
                    }

                    OutlinedButton(onClick = { composerMoreSettings = !composerMoreSettings }) {
                        Text(if (composerMoreSettings) "Hide post settings" else "More post settings")
                    }

                    if (composerMoreSettings) {
                        OutlinedTextField(
                            value = composerLocation,
                            onValueChange = { composerLocation = it.take(120) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Location or campus tag") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = composerLink,
                            onValueChange = { composerLink = it.take(500) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Link") },
                            placeholder = { Text("https://example.com") },
                            isError = !composerLinkValid,
                            supportingText = {
                                if (!composerLinkValid) Text("Enter a valid web address.")
                            },
                            singleLine = true,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Allow comments", modifier = Modifier.weight(1f))
                            Switch(
                                checked = composerAllowComments,
                                onCheckedChange = { composerAllowComments = it },
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Hide like count", modifier = Modifier.weight(1f))
                            Switch(
                                checked = composerHideLikes,
                                onCheckedChange = { composerHideLikes = it },
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Temporary post", modifier = Modifier.weight(1f))
                            Switch(
                                checked = composerTemporary,
                                onCheckedChange = { composerTemporary = it },
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        state.client.createPost(
                                            text = composer,
                                            audience = composerAudience,
                                            category = composerCategory,
                                            location = composerLocation.trim().takeIf(String::isNotBlank),
                                            linkUrl = normalizedComposerLink(),
                                            allowComments = composerAllowComments,
                                            hideLikes = composerHideLikes,
                                            isDisappearing = composerTemporary,
                                            clientRequestId = composerRequestId,
                                        )
                                    }
                                        .onSuccess {
                                            composer = ""
                                            composerLocation = ""
                                            composerLink = ""
                                            composerAllowComments = true
                                            composerHideLikes = false
                                            composerTemporary = false
                                            composerRequestId = UUID.randomUUID().toString()
                                            reload()
                                        }
                                        .onFailure { error = it.message }
                                }
                            },
                            enabled = composer.isNotBlank() && composerLinkValid,
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Post")
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    reload()
                                    if (listState.firstVisibleItemIndex <= 10) {
                                        listState.animateScrollToItem(0)
                                    } else {
                                        listState.scrollToItem(0)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Refresh")
                        }
                    }
                }
            }
        }
        error?.let { item { InlineError(it) } }
        if (loading) item { LoadingRow() }
        if (!loading && posts.isEmpty()) item { EmptyState("No posts are available yet.") }
        items(posts, key = { it.id }) { post ->
            PostCard(
                post = post,
                onLike = {
                    val beforeLiked = post.isLiked
                    val beforeCount = post.likeCount
                    val optimisticLiked = !beforeLiked
                    val optimisticCount = (beforeCount + if (optimisticLiked) 1 else -1).coerceAtLeast(0)
                    posts = posts.map {
                        if (it.id == post.id) {
                            it.copy(isLiked = optimisticLiked, likeCount = optimisticCount)
                        } else {
                            it
                        }
                    }
                    scope.launch {
                        val actual = runCatching { state.client.toggleLike(post.id) }.getOrNull()
                        if (actual == null) {
                            posts = posts.map {
                                if (it.id == post.id) it.copy(isLiked = beforeLiked, likeCount = beforeCount) else it
                            }
                            error = "Couldn't update like."
                        } else if (actual != optimisticLiked) {
                            posts = posts.map {
                                if (it.id == post.id) {
                                    it.copy(
                                        isLiked = actual,
                                        likeCount = if (actual == beforeLiked) beforeCount else optimisticCount,
                                    )
                                } else {
                                    it
                                }
                            }
                        }
                    }
                },
                onBookmark = {
                    val before = post.isBookmarked
                    val optimistic = !before
                    posts = posts.map {
                        if (it.id == post.id) it.copy(isBookmarked = optimistic) else it
                    }
                    scope.launch {
                        val actual = runCatching { state.client.toggleBookmark(post.id) }.getOrNull()
                        if (actual == null) {
                            posts = posts.map {
                                if (it.id == post.id) it.copy(isBookmarked = before) else it
                            }
                            error = "Couldn't update saved post."
                        } else if (actual != optimistic) {
                            posts = posts.map {
                                if (it.id == post.id) it.copy(isBookmarked = actual) else it
                            }
                        }
                    }
                },
                onRepost = {
                    val beforeReposted = post.isRepostedByMe
                    val beforeCount = post.repostCount
                    val optimisticReposted = !beforeReposted
                    val optimisticCount = (beforeCount + if (optimisticReposted) 1 else -1).coerceAtLeast(0)
                    posts = posts.map {
                        if (it.id == post.id) {
                            it.copy(isRepostedByMe = optimisticReposted, repostCount = optimisticCount)
                        } else {
                            it
                        }
                    }
                    scope.launch {
                        val result = runCatching { state.client.toggleRepost(post.id) }.getOrNull()
                        if (result == null) {
                            posts = posts.map {
                                if (it.id == post.id) {
                                    it.copy(isRepostedByMe = beforeReposted, repostCount = beforeCount)
                                } else {
                                    it
                                }
                            }
                            error = "Couldn't update repost."
                        } else {
                            posts = posts.map {
                                if (it.id == post.id) {
                                    it.copy(isRepostedByMe = result.first, repostCount = result.second)
                                } else {
                                    it
                                }
                            }
                        }
                    }
                },
                onComments = { commentsFor = if (commentsFor == post.id) null else post.id },
                onCopyLink = {
                    DesktopShareLinkManager.copyToClipboard(post.id, post.isReel)
                },
                xFeedStyle = true,
            )
            if (commentsFor == post.id) CommentsPanel(state, post.id)
        }
    }
}

@Composable
private fun CommentsPanel(state: DesktopAppState, postId: String) {
    var comments by remember(postId) { mutableStateOf<List<DesktopComment>>(emptyList()) }
    var draft by remember(postId) { mutableStateOf("") }
    var loading by remember(postId) { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        loading = true
        comments = runCatching { state.client.fetchComments(postId) }.getOrDefault(emptyList())
        loading = false
    }
    LaunchedEffect(postId) { reload() }

    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Comments", fontWeight = FontWeight.Bold)
            if (loading) LoadingRow()
            comments.forEach { comment ->
                DesktopPremiumCommentSurface(
                    premiumStyleId = comment.premiumStyleId,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = if (comment.parentCommentId == null) 0.dp else 44.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(if (comment.premiumStyleId.isNullOrBlank()) 0.dp else 13.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AvatarInitial(comment.authorName)
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(comment.authorName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                if (comment.authorVerified) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Rounded.Verified, contentDescription = "Verified", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                }
                            }
                            Text(comment.content, fontSize = 13.sp)
                        }
                    }
                }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Write a comment") },
                trailingIcon = {
                    Button(
                        onClick = {
                            scope.launch {
                                runCatching { state.client.addComment(postId, draft) }
                                    .onSuccess { draft = ""; reload() }
                            }
                        },
                        enabled = draft.isNotBlank(),
                    ) { Text("Send") }
                },
            )
        }
    }
}

@Composable
fun ReelsScreen(state: DesktopAppState) {
    var reels by remember { mutableStateOf<List<DesktopFeedPost>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        reels = runCatching { state.client.fetchFeed(reelsOnly = true) }.getOrDefault(emptyList())
        loading = false
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ScreenHeader("Reels", "Short videos from the same Blinkng feed") }
        if (loading) item { LoadingRow() }
        if (!loading && reels.isEmpty()) item { EmptyState("No reels are available yet.") }
        items(reels, key = { it.id }) { reel ->
            Surface(shape = RoundedCornerShape(22.dp), tonalElevation = 2.dp) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    VerifiedName(reel.authorName, reel.authorVerified)
                    reel.caption?.takeIf(String::isNotBlank)?.let { Text(it) }
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(320.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                reel.videoUrl?.let { "Video ready: ${it.take(80)}" } ?: "Reel video is unavailable",
                                modifier = Modifier.padding(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text("${reel.viewCount} views • ${reel.likeCount} likes • ${reel.commentCount} comments", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(
                        onClick = {
                            DesktopShareLinkManager.copyToClipboard(reel.id, isReel = true)
                        },
                    ) {
                        Text("Copy link")
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(state: DesktopAppState) {
    var query by remember { mutableStateOf(state.globalSearch) }
    var results by remember { mutableStateOf(DesktopSearchResults(emptyList(), emptyList())) }
    var loading by remember { mutableStateOf(false) }
    var selectedProfile by remember { mutableStateOf<DesktopProfile?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun searchNow() {
        val clean = query.trim()
        state.globalSearch = clean
        if (clean.isBlank()) {
            results = DesktopSearchResults(emptyList(), emptyList())
            return
        }
        loading = true
        results = runCatching { state.client.search(clean) }.getOrDefault(DesktopSearchResults(emptyList(), emptyList()))
        loading = false
    }

    LaunchedEffect(state.globalSearch) {
        if (state.globalSearch.isNotBlank()) {
            query = state.globalSearch
            searchNow()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("Search", "Find people and posts") }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    Button(onClick = { scope.launch { searchNow() } }, enabled = query.isNotBlank()) { Text("Search") }
                },
                placeholder = { Text("Search Blinkng") },
            )
        }
        if (loading) item { LoadingRow() }
        if (results.profiles.isNotEmpty()) {
            item { SectionTitle("People") }
            items(results.profiles, key = { "profile-${it.id}" }) { profile ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.clickable { selectedProfile = profile },
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        PresenceAvatar(profile.fullName, profile.isOnline)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            VerifiedName(profile.fullName, profile.isVerified)
                            Text("@${profile.username} • ${profile.university ?: "Blinkng"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                desktopPresenceStatus(profile.isOnline, profile.lastSeenAt),
                                fontSize = 10.sp,
                                color = if (profile.isOnline) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { selectedProfile = profile }) { Text("View") }
                    }
                }
            }
        }
        if (results.posts.isNotEmpty()) {
            item { SectionTitle("Posts") }
            items(results.posts, key = { "post-${it.id}" }) { post ->
                PostCard(
                    post = post,
                    onLike = {
                        val beforeLiked = post.isLiked
                        val beforeCount = post.likeCount
                        val optimisticLiked = !beforeLiked
                        val optimisticCount = (beforeCount + if (optimisticLiked) 1 else -1).coerceAtLeast(0)
                        results = results.copy(
                            posts = results.posts.map {
                                if (it.id == post.id) it.copy(isLiked = optimisticLiked, likeCount = optimisticCount) else it
                            }
                        )
                        scope.launch {
                            val actual = runCatching { state.client.toggleLike(post.id) }.getOrNull()
                            if (actual == null) {
                                results = results.copy(
                                    posts = results.posts.map {
                                        if (it.id == post.id) it.copy(isLiked = beforeLiked, likeCount = beforeCount) else it
                                    }
                                )
                            }
                        }
                    },
                    onBookmark = {
                        val before = post.isBookmarked
                        val optimistic = !before
                        results = results.copy(
                            posts = results.posts.map {
                                if (it.id == post.id) it.copy(isBookmarked = optimistic) else it
                            }
                        )
                        scope.launch {
                            val actual = runCatching { state.client.toggleBookmark(post.id) }.getOrNull()
                            if (actual == null) {
                                results = results.copy(
                                    posts = results.posts.map {
                                        if (it.id == post.id) it.copy(isBookmarked = before) else it
                                    }
                                )
                            }
                        }
                    },
                    onRepost = {
                        val beforeReposted = post.isRepostedByMe
                        val beforeCount = post.repostCount
                        val optimisticReposted = !beforeReposted
                        val optimisticCount = (beforeCount + if (optimisticReposted) 1 else -1).coerceAtLeast(0)
                        results = results.copy(
                            posts = results.posts.map {
                                if (it.id == post.id) {
                                    it.copy(isRepostedByMe = optimisticReposted, repostCount = optimisticCount)
                                } else {
                                    it
                                }
                            }
                        )
                        scope.launch {
                            val actual = runCatching { state.client.toggleRepost(post.id) }.getOrNull()
                            results = results.copy(
                                posts = results.posts.map {
                                    if (it.id != post.id) {
                                        it
                                    } else if (actual == null) {
                                        it.copy(isRepostedByMe = beforeReposted, repostCount = beforeCount)
                                    } else {
                                        it.copy(isRepostedByMe = actual.first, repostCount = actual.second)
                                    }
                                }
                            )
                        }
                    },
                    onComments = {},
                    onCopyLink = {
                        DesktopShareLinkManager.copyToClipboard(post.id, post.isReel)
                    },
                )
            }
        }
        if (!loading && query.isNotBlank() && results.profiles.isEmpty() && results.posts.isEmpty()) {
            item { EmptyState("No results for “$query”.") }
        }
    }

    selectedProfile?.let { profile ->
        DesktopUserProfileDialog(
            state = state,
            initialProfile = profile,
            onDismiss = { selectedProfile = null },
        )
    }
}

@Composable
fun NotificationsScreen(state: DesktopAppState) {
    var notifications by remember { mutableStateOf<List<DesktopNotification>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        loading = true
        notifications = runCatching { state.client.fetchNotifications() }.getOrDefault(emptyList())
        loading = false
    }
    LaunchedEffect(Unit) { reload() }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenHeader("Notifications", "Activity, messages and official updates") }
        if (loading) item { LoadingRow() }
        if (!loading && notifications.isEmpty()) item { EmptyState("You're all caught up.") }
        items(notifications, key = { it.id }) { item ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    if (!item.isRead) scope.launch {
                        runCatching { state.client.markNotificationRead(item.id) }
                        notifications = notifications.map { if (it.id == item.id) it.copy(isRead = true) else it }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                tonalElevation = if (item.isRead) 0.dp else 3.dp,
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(10.dp).clip(CircleShape).background(
                            if (item.isRead) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary,
                        ),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.text, fontWeight = if (item.isRead) FontWeight.Normal else FontWeight.Bold)
                        }
                        item.subText?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(formatTime(item.createdAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun MarketplaceScreen(state: DesktopAppState) {
    var itemsList by remember { mutableStateOf<List<DesktopMarketItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showCreate by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Other") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        loading = true
        itemsList = runCatching { state.client.fetchMarketplace() }.getOrDefault(emptyList())
        loading = false
    }
    LaunchedEffect(Unit) { reload() }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ScreenHeader("Marketplace", "Real campus listings")
                Button(onClick = { showCreate = !showCreate }) { Text(if (showCreate) "Close" else "Sell item") }
            }
        }
        if (showCreate) {
            item {
                Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 2.dp) {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Title") })
                        OutlinedTextField(description, { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Description") })
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(price, { price = it.filter(Char::isDigit) }, label = { Text("Price (NGN)") })
                            OutlinedTextField(category, { category = it }, label = { Text("Category") })
                        }
                        error?.let { InlineError(it) }
                        Button(onClick = {
                            scope.launch {
                                runCatching { state.client.createMarketplaceItem(title, description, price.toLongOrNull() ?: 0L, category) }
                                    .onSuccess { title = ""; description = ""; price = ""; showCreate = false; reload() }
                                    .onFailure { error = it.message }
                            }
                        }, enabled = title.isNotBlank() && price.isNotBlank()) { Text("Publish listing") }
                    }
                }
            }
        }
        if (loading) item { LoadingRow() }
        items(itemsList, key = { it.id }) { market ->
            Surface(shape = RoundedCornerShape(18.dp), tonalElevation = if (market.isFeatured) 3.dp else 1.dp) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(market.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("${market.currency} ${market.price}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                    }
                    Text(market.description, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    VerifiedName(market.sellerName, market.sellerVerified)
                    Text("${market.university} • ${market.location} • ${market.condition}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (market.isFeatured) Text("Featured", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ConnectScreen(state: DesktopAppState) {
    var listings by remember { mutableStateOf<List<DesktopConnectListing>>(emptyList()) }
    var students by remember { mutableStateOf<List<DesktopProfile>>(emptyList()) }
    var followingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var inbox by remember { mutableStateOf(DesktopConnectInbox()) }
    var loading by remember { mutableStateOf(true) }
    var showCreate by remember { mutableStateOf(false) }
    var selectedPane by remember { mutableStateOf(0) }

    var listingQuery by remember { mutableStateOf("") }
    var listingGroup by remember { mutableStateOf("All") }

    var studentQuery by remember { mutableStateOf("") }
    var studentFilter by remember { mutableStateOf("all") }
    var studentSort by remember { mutableStateOf("recommended") }

    var type by remember { mutableStateOf("community") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var pulsePolicy by remember { mutableStateOf(BlinkActivityPulseDefaults.policy) }
    var liveActivityAvailable by remember { mutableStateOf(false) }
    var pulseImpressionRecorded by remember { mutableStateOf(false) }
    var selectedStudentProfile by remember { mutableStateOf<DesktopProfile?>(null) }
    val scope = rememberCoroutineScope()

    fun connectGroup(listing: DesktopConnectListing): String {
        val text = buildString {
            append(listing.listingType)
            append(' ')
            append(listing.title)
            append(' ')
            append(listing.tags.joinToString(" "))
        }.lowercase()

        return when {
            listOf("room", "housing", "accommodation", "agent", "relocation").any { text.contains(it) } -> "Housing"
            listOf("study", "reading", "course", "research", "project", "accountability").any { text.contains(it) } -> "Study"
            listOf("career", "mentor", "intern", "skill", "founder", "freelance", "alumni").any { text.contains(it) } -> "Career & Skills"
            listOf("game", "challenge", "quiz").any { text.contains(it) } -> "Games"
            else -> "People & Community"
        }
    }

    suspend fun reload() {
        loading = true
        val listingsResult = runCatching { state.client.fetchConnectListings() }
        val studentsResult = runCatching { state.client.fetchOnboardingSuggestions(100) }
        val followingResult = runCatching { state.client.fetchFollowingIds() }
        val inboxResult = runCatching { state.client.fetchConnectInbox() }
        val policyResult = runCatching { state.client.fetchActivityPulsePolicy() }

        listings = listingsResult.getOrDefault(listings)
        students = studentsResult.getOrDefault(students).filterNot { it.id == state.profile?.id }
        followingIds = followingResult.getOrDefault(followingIds)
        inbox = inboxResult.getOrDefault(inbox)
        pulsePolicy = policyResult.getOrDefault(pulsePolicy).normalized()
        liveActivityAvailable = studentsResult.isSuccess
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    val myCampus = state.profile?.university.orEmpty()
    val myDepartment = state.profile?.department.orEmpty()

    val visibleListings = remember(listings, listingQuery, listingGroup) {
        val query = listingQuery.trim()
        listings.filter { listing ->
            val groupMatches = listingGroup == "All" || connectGroup(listing) == listingGroup
            val queryMatches = query.isBlank() ||
                listing.title.contains(query, ignoreCase = true) ||
                listing.description.contains(query, ignoreCase = true) ||
                listing.listingType.contains(query, ignoreCase = true) ||
                listing.university.orEmpty().contains(query, ignoreCase = true) ||
                listing.department.orEmpty().contains(query, ignoreCase = true) ||
                listing.location.orEmpty().contains(query, ignoreCase = true) ||
                listing.tags.any { it.contains(query, ignoreCase = true) }

            groupMatches && queryMatches
        }
    }

    val visibleStudents = remember(
        students,
        studentQuery,
        studentFilter,
        studentSort,
        myCampus,
        myDepartment,
        followingIds
    ) {
        val query = studentQuery.trim()
        val filtered = students.filter { profile ->
            val matchesQuery = query.isBlank() ||
                profile.fullName.contains(query, ignoreCase = true) ||
                profile.username.contains(query, ignoreCase = true) ||
                profile.university.orEmpty().contains(query, ignoreCase = true) ||
                profile.faculty.orEmpty().contains(query, ignoreCase = true) ||
                profile.department.orEmpty().contains(query, ignoreCase = true) ||
                profile.academicLevel.orEmpty().contains(query, ignoreCase = true)

            val matchesFilter = when (studentFilter) {
                "campus" -> myCampus.isNotBlank() &&
                    profile.university.orEmpty().equals(myCampus, ignoreCase = true)
                "department" -> myDepartment.isNotBlank() &&
                    profile.department.orEmpty().equals(myDepartment, ignoreCase = true)
                "online" -> profile.isOnline
                "verified" -> profile.isVerified
                else -> true
            }

            matchesQuery && matchesFilter
        }

        when (studentSort) {
            "active" -> filtered.sortedWith(
                compareByDescending<DesktopProfile> { it.isOnline }
                    .thenByDescending { it.lastSeenAt.orEmpty() }
                    .thenBy { it.fullName.lowercase() }
            )
            "campus" -> filtered.sortedWith(
                compareByDescending<DesktopProfile> {
                    myCampus.isNotBlank() &&
                        it.university.orEmpty().equals(myCampus, ignoreCase = true)
                }.thenByDescending { it.isOnline }
                    .thenBy { it.fullName.lowercase() }
            )
            "name" -> filtered.sortedBy { it.fullName.ifBlank { it.username }.lowercase() }
            else -> filtered.sortedWith(
                compareByDescending<DesktopProfile> { it.isOnline }
                    .thenByDescending { it.id in followingIds }
                    .thenByDescending {
                        myDepartment.isNotBlank() &&
                            it.department.orEmpty().equals(myDepartment, ignoreCase = true)
                    }
                    .thenByDescending {
                        myCampus.isNotBlank() &&
                            it.university.orEmpty().equals(myCampus, ignoreCase = true)
                    }
                    .thenBy { it.fullName.lowercase() }
            )
        }
    }

    val policy = remember(pulsePolicy) { pulsePolicy.normalized() }
    val realOnlineCount = remember(students) { 1 + students.count { it.isOnline } }
    val reduceMotion = state.settings?.reduceMotion == true
    val communityActivity = rememberDesktopManagedPulse(
        key = "desktop-connect:" + state.profile?.username.orEmpty().lowercase(),
        range = communityActivityRange(realOnlineCount, policy),
        tickMillis = policy.connectTickMillis,
        policy = policy,
        liveDataAvailable = liveActivityAvailable,
        reduceMotion = reduceMotion,
    )
    var previousRealOnline by remember { mutableIntStateOf(realOnlineCount) }
    var activityTrend by remember { mutableStateOf(BlinkPulseTrend.STABLE) }
    LaunchedEffect(realOnlineCount) {
        activityTrend = pulseTrend(previousRealOnline, realOnlineCount)
        previousRealOnline = realOnlineCount
    }

    val realCampusOnline = remember(students, myCampus) {
        students.count {
            it.isOnline && myCampus.isNotBlank() && it.university.orEmpty().equals(myCampus, ignoreCase = true)
        } + if (myCampus.isNotBlank()) 1 else 0
    }
    val campusLabel = remember(realCampusOnline, policy) {
        campusActivityLabel(realCampusOnline, policy)
    }
    val onlinePreview = remember(students, policy.onlinePreviewLimit) {
        students.filter { it.isOnline }.take(policy.onlinePreviewLimit)
    }

    LaunchedEffect(communityActivity, liveActivityAvailable, pulseImpressionRecorded) {
        if (communityActivity != null && liveActivityAvailable && !pulseImpressionRecorded) {
            state.client.recordActivityPulseEvent(
                surface = "connect",
                eventType = "impression",
                realCount = realOnlineCount,
                displayedValue = communityActivity,
                metadata = mapOf("trend" to activityTrend.label),
            )
            pulseImpressionRecorded = true
        }
    }

    val sameDepartmentCount = remember(students, myDepartment) {
        students.count {
            myDepartment.isNotBlank() &&
                it.department.orEmpty().equals(myDepartment, ignoreCase = true)
        }
    }
    val pendingInboxCount = remember(inbox, state.profile?.id) {
        val currentId = state.profile?.id.orEmpty()
        inbox.requests.count {
            it.direction.equals("incoming", true) && it.status.equals("pending", true)
        } + inbox.challenges.count {
            it.opponentId == currentId && it.status.equals("pending", true)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ScreenHeader("Connect", "People, study, housing, skills and challenges")
                    if (selectedPane == 0) {
                        Button(onClick = { showCreate = true }) { Text("Create") }
                    }
                }
            }

            if (policy.enabled) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 1.dp,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Community Activity", fontWeight = FontWeight.Black, fontSize = 15.sp)
                                    Text(
                                        if (liveActivityAvailable) "Activity Pulse • ${activityTrend.label}"
                                        else "Last known activity • offline",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = Color(0xFF22C55E).copy(alpha = 0.13f),
                                ) {
                                    Text(
                                        communityActivity?.toString() ?: "—",
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                        color = Color(0xFF22C55E),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                    )
                                }
                            }

                            Text(
                                "Confirmed online now: $realOnlineCount",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (myCampus.isNotBlank()) {
                                Text(
                                    "$myCampus activity: $campusLabel • $realCampusOnline confirmed online",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            if (onlinePreview.isNotEmpty()) {
                                HorizontalDivider()
                                Text(
                                    "Actually online",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    onlinePreview.forEach { profile ->
                                        Column(
                                            modifier = Modifier
                                                .width(76.dp)
                                                .clickable {
                                                    scope.launch {
                                                        state.client.recordActivityPulseEvent(
                                                            surface = "connect",
                                                            eventType = "online_preview_open",
                                                            realCount = realOnlineCount,
                                                            displayedValue = communityActivity,
                                                            metadata = mapOf("username" to profile.username),
                                                        )
                                                    }
                                                },
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            AvatarInitial(profile.fullName.ifBlank { profile.username }, 34.dp)
                                            Text(
                                                "@${profile.username}",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = 9.5.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilterChip(
                        selected = selectedPane == 0,
                        onClick = { selectedPane = 0 },
                        label = { Text("Connect Hub") },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = selectedPane == 1,
                        onClick = { selectedPane = 1 },
                        label = { Text("Students") },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = selectedPane == 2,
                        onClick = { selectedPane = 2 },
                        label = {
                            Text(if (pendingInboxCount > 0) "Inbox · $pendingInboxCount" else "Inbox")
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (loading) {
                item { LoadingRow() }
            } else if (selectedPane == 0) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            listOf(
                                Triple("People for you", "$sameDepartmentCount same department", 1),
                                Triple("Housing", "${listings.count { connectGroup(it) == "Housing" }} active", 0),
                                Triple("Study", "${listings.count { connectGroup(it) == "Study" }} active", 0),
                                Triple("Career & Skills", "${listings.count { connectGroup(it) == "Career & Skills" }} active", 0),
                            ).forEach { (label, detail, destination) ->
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            if (destination == 1) selectedPane = 1
                                            else listingGroup = label
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    tonalElevation = 1.dp,
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(label, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                        Text(
                                            detail,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = listingQuery,
                            onValueChange = { listingQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Search Connect Hub") },
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "All",
                                "Housing",
                                "Study",
                                "Career & Skills",
                                "People & Community",
                                "Games",
                            ).forEach { group ->
                                FilterChip(
                                    selected = listingGroup == group,
                                    onClick = { listingGroup = group },
                                    label = { Text(group) },
                                )
                            }
                        }
                    }
                }

                if (visibleListings.isEmpty()) {
                    item {
                        Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("No Connect listings match these filters.", fontWeight = FontWeight.Bold)
                                Text(
                                    "Clear the search or switch category to see more.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                )
                                OutlinedButton(
                                    onClick = {
                                        listingQuery = ""
                                        listingGroup = "All"
                                    },
                                ) { Text("Clear filters") }
                            }
                        }
                    }
                } else {
                    items(visibleListings, key = { it.id }) { listing ->
                        Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(listing.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    Text(
                                        connectGroup(listing),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Text(listing.description)
                                Text(
                                    listOfNotNull(
                                        listing.listingType,
                                        listing.university,
                                        listing.department,
                                        listing.academicLevel,
                                        listing.location,
                                    ).filter { it.isNotBlank() }.joinToString(" • "),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                )
                                if (listing.tags.isNotEmpty()) {
                                    Text(
                                        listing.tags.joinToString("  ") { "#$it" },
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (selectedPane == 1) {
                item {
                    OutlinedTextField(
                        value = studentQuery,
                        onValueChange = { studentQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search name, username, campus, department or level") },
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "all" to "All",
                                "campus" to "Same campus",
                                "department" to "Same department",
                                "online" to "Online",
                                "verified" to "Verified",
                            ).forEach { (key, label) ->
                                FilterChip(
                                    selected = studentFilter == key,
                                    onClick = { studentFilter = key },
                                    label = { Text(label) },
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "recommended" to "Recommended",
                                "active" to "Recently active",
                                "campus" to "Campus first",
                                "name" to "A–Z",
                            ).forEach { (key, label) ->
                                FilterChip(
                                    selected = studentSort == key,
                                    onClick = { studentSort = key },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }

                if (visibleStudents.isEmpty()) {
                    item {
                        Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("No students match these filters.", fontWeight = FontWeight.Bold)
                                OutlinedButton(
                                    onClick = {
                                        studentQuery = ""
                                        studentFilter = "all"
                                        studentSort = "recommended"
                                    },
                                ) { Text("Clear filters") }
                            }
                        }
                    }
                } else {
                    items(visibleStudents, key = { it.id }) { profile ->
                        val isFollowing = profile.id in followingIds
                        Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    VerifiedName(profile.fullName.ifBlank { profile.username }, profile.isVerified)
                                    Text(
                                        "@" + profile.username,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        listOfNotNull(
                                            profile.university,
                                            profile.faculty,
                                            profile.department,
                                            profile.academicLevel,
                                        ).filter { it.isNotBlank() }.joinToString(" • "),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        if (profile.isOnline) "Online" else "Offline",
                                        fontSize = 11.sp,
                                        color = if (profile.isOnline) Color(0xFF22C55E)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                OutlinedButton(onClick = { selectedStudentProfile = profile }) {
                                    Text("View")
                                }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            followingIds = runCatching {
                                                state.client.setFollowing(profile.id, !isFollowing)
                                            }.getOrDefault(followingIds)
                                        }
                                    },
                                ) {
                                    Text(if (isFollowing) "Following" else "Follow")
                                }
                            }
                        }
                    }
                }
            } else {
                val currentId = state.profile?.id.orEmpty()
                val requestItems = inbox.requests.sortedByDescending { it.createdAt }
                val challengeItems = inbox.challenges.sortedByDescending { it.createdAt }

                if (requestItems.isEmpty() && challengeItems.isEmpty()) {
                    item {
                        Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
                            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                                Text("Connect Inbox is clear", fontWeight = FontWeight.Black)
                                Text(
                                    "Incoming and outgoing Connect requests and challenges will appear here.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                } else {
                    items(requestItems, key = { "request-${it.kind}-${it.requestId}" }) { request ->
                        val person = students.firstOrNull { it.id == request.otherUserId }
                        val canRespond = request.direction.equals("incoming", true) &&
                            request.status.equals("pending", true)

                        Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        request.title.ifBlank { request.kind.replace('_', ' ') },
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        (person?.fullName?.ifBlank { person.username } ?: "Blink user") +
                                            " • " + request.direction + " • " + request.status,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                    )
                                }
                                if (canRespond) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                runCatching {
                                                    state.client.respondConnectRequest(
                                                        request.kind,
                                                        request.requestId,
                                                        true,
                                                    )
                                                }
                                                reload()
                                            }
                                        },
                                    ) { Text("Accept") }
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                runCatching {
                                                    state.client.respondConnectRequest(
                                                        request.kind,
                                                        request.requestId,
                                                        false,
                                                    )
                                                }
                                                reload()
                                            }
                                        },
                                    ) { Text("Decline") }
                                }
                            }
                        }
                    }

                    items(challengeItems, key = { "challenge-${it.id}" }) { challenge ->
                        val otherId = if (challenge.challengerId == currentId) {
                            challenge.opponentId
                        } else {
                            challenge.challengerId
                        }
                        val person = students.firstOrNull { it.id == otherId }
                        val canRespond = challenge.opponentId == currentId &&
                            challenge.status.equals("pending", true)

                        Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Game challenge • ${challenge.gameType.replace('_', ' ')}", fontWeight = FontWeight.Bold)
                                    Text(
                                        (person?.fullName?.ifBlank { person.username } ?: "Blink user") +
                                            " • " + challenge.status,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                    )
                                }
                                if (canRespond) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                runCatching {
                                                    state.client.respondGameChallenge(challenge.id, true)
                                                }
                                                reload()
                                            }
                                        },
                                    ) { Text("Accept") }
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                runCatching {
                                                    state.client.respondGameChallenge(challenge.id, false)
                                                }
                                                reload()
                                            }
                                        },
                                    ) { Text("Decline") }
                                }
                            }
                        }
                    }
                }
            }
        }

        selectedStudentProfile?.let { profile ->
            DesktopUserProfileDialog(
                state = state,
                initialProfile = profile,
                onDismiss = {
                    selectedStudentProfile = null
                    scope.launch {
                        followingIds = runCatching { state.client.fetchFollowingIds() }.getOrDefault(followingIds)
                    }
                },
            )
        }

        if (showCreate) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = .24f)),
            )
        }

        AnimatedVisibility(
            visible = showCreate,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(290)) + fadeIn(tween(180)),
            exit = slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(220)) + fadeOut(tween(160)),
            label = "desktopConnectSlidePanel",
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(.95f).fillMaxHeight(),
                shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 16.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Create Connect listing", fontWeight = FontWeight.Black, fontSize = 24.sp)
                            Text(
                                "Create a focused listing without leaving the Connect workspace.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { showCreate = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close Connect create panel")
                        }
                    }
                    HorizontalDivider()
                    OutlinedTextField(type, { type = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Type") }, singleLine = true)
                    OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Title") })
                    OutlinedTextField(description, { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 4)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    runCatching { state.client.createConnectListing(type, title, description) }
                                        .onSuccess {
                                            title = ""
                                            description = ""
                                            showCreate = false
                                            reload()
                                        }
                                }
                            },
                            enabled = title.isNotBlank(),
                        ) { Text("Publish") }
                        OutlinedButton(onClick = { showCreate = false }) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

@Composable
fun StoreScreen(state: DesktopAppState) {
    var catalog by remember { mutableStateOf<List<DesktopStoreItem>>(emptyList()) }
    var inventory by remember { mutableStateOf<List<DesktopInventoryItem>>(emptyList()) }
    var balance by remember { mutableStateOf(0L) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val store = runCatching { state.client.fetchStore() }.getOrDefault(emptyList<DesktopStoreItem>() to emptyList())
        catalog = store.first
        inventory = store.second
        balance = runCatching { state.client.fetchCoinBalance() }.getOrDefault(0L)
        loading = false
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenHeader("Blink Store", "$balance Blink Coins available") }
        if (loading) item { LoadingRow() }
        item { SectionTitle("Store") }
        items(catalog, key = { "catalog-${it.id}" }) { item ->
            Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.Bold)
                        Text(item.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        item.durationSeconds?.let { Text("Duration: ${it / 86400} days", fontSize = 11.sp) }
                    }
                    Text("${item.price} coins", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { SectionTitle("My purchases") }
        if (inventory.isEmpty()) item { EmptyState("You haven't purchased store items yet.") }
        items(inventory, key = { "inventory-${it.id}" }) { item ->
            Surface(shape = RoundedCornerShape(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(item.catalogId, fontWeight = FontWeight.SemiBold)
                        Text("${item.status} • quantity ${item.quantity}", fontSize = 12.sp)
                    }
                    Text(item.expiresAt?.let { "Expires ${formatTime(it)}" } ?: "No expiry", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun LeaderboardScreen(state: DesktopAppState) {
    var entries by remember { mutableStateOf<List<DesktopLeaderboardEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var previousRanks by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var worldRankMovement by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var rankUpsInWindow by remember { mutableIntStateOf(0) }
    var pulsePolicy by remember { mutableStateOf(BlinkActivityPulseDefaults.policy) }
    var liveActivityAvailable by remember { mutableStateOf(false) }
    var pulseImpressionRecorded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        pulsePolicy = runCatching { state.client.fetchActivityPulsePolicy() }
            .getOrDefault(pulsePolicy)
            .normalized()

        while (true) {
            val result = runCatching { state.client.fetchLeaderboard() }
            val loaded = result.getOrDefault(entries)
            val currentRanks = loaded.mapNotNull { entry ->
                entry.worldRank?.let { rank -> entry.userId to rank }
            }.toMap()

            val movement = if (previousRanks.isEmpty()) {
                emptyMap()
            } else {
                currentRanks.mapValues { (userId, rank) ->
                    val previous = previousRanks[userId]
                    if (previous == null) 0 else previous - rank
                }
            }
            worldRankMovement = movement
            rankUpsInWindow = movement.values.count { it > 0 }
            entries = loaded
            if (result.isSuccess) previousRanks = currentRanks
            liveActivityAvailable = result.isSuccess
            loading = false
            delay(pulsePolicy.rankWindowMillis)
        }
    }

    val policy = remember(pulsePolicy) { pulsePolicy.normalized() }
    val reduceMotion = state.settings?.reduceMotion == true
    val rankPulse = rememberDesktopManagedPulse(
        key = "desktop-leaderboard:" + state.profile?.username.orEmpty().lowercase(),
        range = rankPulseRange(rankUpsInWindow, policy),
        tickMillis = policy.rankTickMillis,
        policy = policy,
        liveDataAvailable = liveActivityAvailable,
        reduceMotion = reduceMotion,
    )
    val rankStatus = remember(rankUpsInWindow, policy.hotRankUpsThreshold) {
        when {
            rankUpsInWindow >= policy.hotRankUpsThreshold -> "Heating up"
            rankUpsInWindow > 0 -> "Active"
            else -> "Stable"
        }
    }
    val recentMovers = remember(worldRankMovement, entries) {
        worldRankMovement
            .filterValues { it > 0 }
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .mapNotNull { movement ->
                entries.firstOrNull { it.userId == movement.key }?.let { it to movement.value }
            }
    }

    LaunchedEffect(rankPulse, liveActivityAvailable, pulseImpressionRecorded) {
        if (rankPulse != null && liveActivityAvailable && !pulseImpressionRecorded) {
            state.client.recordActivityPulseEvent(
                surface = "leaderboard",
                eventType = "impression",
                realCount = rankUpsInWindow,
                displayedValue = rankPulse,
                metadata = mapOf("status" to rankStatus),
            )
            pulseImpressionRecorded = true
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ScreenHeader("Leaderboard", "Ranks #1–#20 from live Blink activity") }

        if (policy.enabled) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 1.dp,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Rank Pulse", fontWeight = FontWeight.Black, fontSize = 15.sp)
                                Text(
                                    if (liveActivityAvailable) (policy.rankWindowMillis / 1000).toString() + "-second activity window • " + rankStatus
                                    else "Last known rank activity • offline",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            ) {
                                Text(
                                    rankPulse?.toString() ?: "—",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                        }
                        Text(
                            "Confirmed rank-ups in this window: $rankUpsInWindow",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (recentMovers.isNotEmpty()) {
                            HorizontalDivider()
                            recentMovers.forEach { (entry, places) ->
                                Text(
                                    "@${entry.handle} moved up $places place" + if (places == 1) "" else "s",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (loading) item { LoadingRow() }
        items(
            entries.sortedBy { it.worldRank ?: Int.MAX_VALUE }.take(20),
            key = { it.userId }
        ) { entry ->
            Surface(
                modifier = if (reduceMotion) Modifier else Modifier.animateItem(),
                shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("#${entry.worldRank ?: "–"}", modifier = Modifier.width(64.dp), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        VerifiedName(entry.name, entry.verificationTier.lowercase() != "none" && entry.verificationTier.isNotBlank())
                        Text("@${entry.handle} • ${entry.university ?: "Blinkng"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${entry.worldScore} pts", fontWeight = FontWeight.Bold)
                        Text("Campus #${entry.campusRank ?: "–"}", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(state: DesktopAppState) {
    val profile = state.profile
    val scope = rememberCoroutineScope()
    val actions = remember(state.client) { DesktopRpcActions(state.client) }
    var economyPayload by remember { mutableStateOf<JSONObject?>(null) }
    var missionsPayload by remember { mutableStateOf<JSONObject?>(null) }
    var economyMessage by remember { mutableStateOf<String?>(null) }
    var economyBusy by remember { mutableStateOf(false) }
    var cashCheckoutBusy by remember { mutableStateOf(false) }
    var showCoinPacks by remember { mutableStateOf(false) }
    var pendingPaystackOrderId by remember { mutableStateOf<String?>(null) }
    var missionBusyKey by remember { mutableStateOf<String?>(null) }
    var premiumCatalogIds by remember(profile?.username) { mutableStateOf<List<String>>(emptyList()) }
    var premiumIsVip by remember(profile?.username) { mutableStateOf(profile?.isBlinkVip == true) }

    suspend fun reloadRewards() {
        economyPayload = runCatching { actions.getEconomyStatus() }.getOrNull()
        missionsPayload = runCatching { actions.getDailyMissions() }.getOrNull()
    }

    fun openHostedPaystackCheckout(payload: JSONObject) {
        val orderId = payload.optString("order_id").trim()
        val authorizationUrl = payload.optString("authorization_url").trim()
        if (orderId.isBlank() || authorizationUrl.isBlank()) error("Paystack did not return a complete checkout session.")
        require(Desktop.isDesktopSupported()) { "The default browser is unavailable." }
        Desktop.getDesktop().browse(URI(authorizationUrl))
        pendingPaystackOrderId = orderId
        economyMessage = "Complete the secure Paystack checkout in your browser, then check the payment here."
    }

    LaunchedEffect(profile?.id) {
        if (profile != null) reloadRewards()
    }

    val policy = remember(economyPayload?.toString()) { parseDesktopEconomyPolicy(economyPayload) }
    val missions = remember(missionsPayload?.toString()) { parseDesktopDailyMissions(missionsPayload) }
    val balance = economyPayload?.optLong("balance", profile?.coinBalance ?: 0L) ?: profile?.coinBalance ?: 0L
    val remaining = policy.verificationCoinsRemaining(balance)

    LaunchedEffect(profile?.username) {
        val username = profile?.username.orEmpty()
        if (username.isNotBlank()) {
            runCatching { actions.getPublicPremiumStyle(username) }.onSuccess { style ->
                premiumIsVip = style.optBoolean("is_vip", profile?.isBlinkVip == true)
                val items = style.optJSONArray("items")
                premiumCatalogIds = buildList {
                    if (items != null) for (index in 0 until items.length()) {
                        val catalogId = items.optJSONObject(index)?.optString("catalog_id").orEmpty()
                        if (catalogId.isNotBlank()) add(catalogId)
                    }
                }
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ScreenHeader("Profile", "Your Blink identity") }
        if (profile == null) {
            item { LoadingRow() }
        } else {
            val xp = xpProgress(profile.totalXp, profile.xpLevel)

            item {
                DesktopPremiumProfileSurface(
                    catalogIds = premiumCatalogIds,
                    isVip = premiumIsVip,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DesktopPremiumAvatarFrame(
                                catalogIds = premiumCatalogIds,
                                isVip = premiumIsVip,
                                size = 72.dp,
                            ) {
                                PresenceAvatar(profile.fullName, profile.isOnline, 64.dp)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                VerifiedName(profile.fullName, profile.isVerified, 21.sp)
                                Text("@${profile.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    desktopPresenceStatus(profile.isOnline, profile.lastSeenAt),
                                    fontSize = 11.sp,
                                    color = if (profile.isOnline) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(listOfNotNull(profile.university, profile.faculty, profile.department).joinToString(" • "), fontSize = 12.sp)
                            }
                        }
                        profile.bio?.takeIf(String::isNotBlank)?.let { Text(it) }
                        HorizontalDivider()
                        Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                            Stat("Posts", profile.postsCount.toString())
                            Stat("Followers", profile.followerCount.toString())
                            Stat("Following", profile.followingCount.toString())
                            Stat("Coins", balance.toString())
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("Level ${xp.level} • ${xp.tierLabel}", fontWeight = FontWeight.Black, fontSize = 16.sp)
                                Text("${xp.totalXp} XP", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                if (xp.level >= 100) "MAX LEVEL" else "${xp.xpToNextLevel} XP to Lv. ${xp.level + 1}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { xp.progressFraction },
                            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(100.dp)),
                        )
                        Text(
                            if (xp.level >= 100) "Long-term BLINK progression complete."
                            else "${xp.xpIntoLevel} / ${xp.xpForLevel} XP in this level",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("BLINK Verified", fontWeight = FontWeight.Black, fontSize = 18.sp)
                                Text(
                                    if (profile.isVerified) "Premium BLINK status active"
                                    else "$balance / ${policy.blueVerificationCoinCost} coins",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.Rounded.Verified,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                        }

                        LinearProgressIndicator(
                            progress = { if (profile.isVerified) 1f else policy.verificationProgress(balance) },
                            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(100.dp)),
                        )

                        Text(
                            if (profile.isVerified) {
                                "Your BLINK Verified status is active across supported identity surfaces."
                            } else {
                                "Use ${policy.blueVerificationCoinCost} Blink Coins, or ₦${policy.blueVerificationCashNgn} for ${policy.blueVerificationValidDays} days through secure Paystack checkout."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (!profile.isVerified) {
                            Button(
                                onClick = {
                                    economyBusy = true
                                    economyMessage = null
                                    scope.launch {
                                        runCatching { actions.purchaseBlueVerificationWithCoins() }
                                            .onSuccess {
                                                reloadRewards()
                                                state.refreshProfile()
                                                economyMessage = "BLINK Verified activated."
                                            }
                                            .onFailure { economyMessage = it.message ?: "Verification could not be completed." }
                                        economyBusy = false
                                    }
                                },
                                enabled = !economyBusy && remaining == 0L,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    if (economyBusy) "Activating…"
                                    else if (remaining == 0L) "Use ${policy.blueVerificationCoinCost} coins"
                                    else "Need $remaining more coins",
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        if (!profile.isVerified) {
                            OutlinedButton(
                                onClick = {
                                    cashCheckoutBusy = true
                                    economyMessage = null
                                    scope.launch {
                                        runCatching { state.client.initializePaystackVerificationCheckout() }
                                            .onSuccess { payload -> runCatching { openHostedPaystackCheckout(payload) }.onFailure { economyMessage = it.message } }
                                            .onFailure { economyMessage = it.message }
                                        cashCheckoutBusy = false
                                    }
                                },
                                enabled = !cashCheckoutBusy && policy.cashCheckoutEnabled,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (policy.cashCheckoutEnabled) "Pay ₦${policy.blueVerificationCashNgn} securely • ${policy.blueVerificationValidDays} days" else "Paystack cash checkout is not live yet")
                            }
                        }

                        OutlinedButton(
                            onClick = { showCoinPacks = !showCoinPacks },
                            enabled = !cashCheckoutBusy && policy.cashCheckoutEnabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (showCoinPacks) "Hide Blink Coin packs" else "Buy Blink Coins with Paystack") }

                        AnimatedVisibility(visible = showCoinPacks && policy.cashCheckoutEnabled) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                policy.coinPacks.forEach { pack ->
                                    OutlinedButton(
                                        onClick = {
                                            cashCheckoutBusy = true
                                            economyMessage = null
                                            scope.launch {
                                                runCatching { state.client.initializePaystackCoinCheckout(pack.id) }
                                                    .onSuccess { payload -> runCatching { openHostedPaystackCheckout(payload) }.onSuccess { showCoinPacks = false }.onFailure { economyMessage = it.message } }
                                                    .onFailure { economyMessage = it.message }
                                                cashCheckoutBusy = false
                                            }
                                        }, enabled = !cashCheckoutBusy, modifier = Modifier.fillMaxWidth()
                                    ) { Text("₦${pack.priceNgn} → ${pack.coins} coins" + if (pack.bonusCoins > 0) " (+${pack.bonusCoins} bonus)" else "") }
                                }
                            }
                        }

                        pendingPaystackOrderId?.let { orderId ->
                            OutlinedButton(
                                onClick = {
                                    cashCheckoutBusy = true
                                    economyMessage = null
                                    scope.launch {
                                        runCatching { state.client.verifyPaystackCashOrder(orderId) }
                                            .onSuccess { payload ->
                                                if (payload.optString("status").equals("fulfilled", true)) {
                                                    pendingPaystackOrderId = null
                                                    reloadRewards()
                                                    state.refreshProfile()
                                                    economyMessage = "Payment confirmed. Your BLINK purchase is ready."
                                                } else economyMessage = "Paystack has not confirmed this payment yet."
                                            }.onFailure { economyMessage = it.message }
                                        cashCheckoutBusy = false
                                    }
                                }, enabled = !cashCheckoutBusy, modifier = Modifier.fillMaxWidth()
                            ) { Text(if (cashCheckoutBusy) "Checking…" else "Check Paystack payment") }
                        }

                        Text(
                            "Rewarded ads are Android-only. Daily missions, XP, wallet balance and verification are shared across Android and Windows.",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        economyMessage?.let { message ->
                            Text(
                                message,
                                fontSize = 11.sp,
                                color = if (message.contains("activated", ignoreCase = true)) Color(0xFF22C55E) else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            item {
                DesktopProgressHubPanel(state = state, actions = actions)
            }

            item {
                Surface(shape = RoundedCornerShape(22.dp), tonalElevation = 1.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Daily Missions", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(
                            "Complete meaningful activity for up to 20 Blink Coins + 80 XP per day.",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (missions.isEmpty()) {
                            Text("Missions are syncing…", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        missions.forEach { mission ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (mission.claimed) Color(0xFF22C55E).copy(alpha = 0.08f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(mission.title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text(mission.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(
                                            "${mission.progress.coerceAtMost(mission.target)}/${mission.target}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (mission.completed) Color(0xFF22C55E) else MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { mission.progressFraction },
                                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(100.dp)),
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("+${mission.coinReward} coins • +${mission.xpReward} XP", fontSize = 10.sp)
                                        OutlinedButton(
                                            onClick = {
                                                missionBusyKey = mission.key
                                                economyMessage = null
                                                scope.launch {
                                                    runCatching { actions.claimDailyMission(mission.key) }
                                                        .onSuccess {
                                                            reloadRewards()
                                                            state.refreshProfile()
                                                            economyMessage = "${mission.title} claimed."
                                                        }
                                                        .onFailure { economyMessage = it.message ?: "Mission claim failed." }
                                                    missionBusyKey = null
                                                }
                                            },
                                            enabled = mission.claimable && missionBusyKey == null,
                                        ) {
                                            Text(
                                                when {
                                                    mission.claimed -> "Claimed"
                                                    missionBusyKey == mission.key -> "Claiming…"
                                                    mission.claimable -> "Claim"
                                                    else -> "In progress"
                                                },
                                                fontSize = 10.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GamesScreen(state: DesktopAppState) {
    DesktopGeneralStudyGame()
}

@Composable
fun SettingsScreen(state: DesktopAppState) {
    var local by remember(state.settings) {
        mutableStateOf(
            state.settings ?: DesktopUserSettings(
                theme = "system",
                language = "en",
                pushNotificationsEnabled = true,
                emailNotificationsEnabled = true,
                dmPrivacy = "everyone",
                privateAccount = false,
                showOnlineStatus = true,
                readReceipts = true,
                autoplayVideos = true,
                dataSaver = false,
                reduceMotion = false,
            ),
        )
    }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("Settings", "Account, privacy and desktop preferences") }
        item { SettingToggle("Private account", local.privateAccount) { local = local.copy(privateAccount = it); saved = false } }
        item { SettingToggle("Show online status", local.showOnlineStatus) { local = local.copy(showOnlineStatus = it); saved = false } }
        item { SettingToggle("Read receipts", local.readReceipts) { local = local.copy(readReceipts = it); saved = false } }
        item { SettingToggle("Push notifications", local.pushNotificationsEnabled) { local = local.copy(pushNotificationsEnabled = it); saved = false } }
        item { SettingToggle("Email notifications", local.emailNotificationsEnabled) { local = local.copy(emailNotificationsEnabled = it); saved = false } }
        item { SettingToggle("Autoplay videos", local.autoplayVideos) { local = local.copy(autoplayVideos = it); saved = false } }
        item { SettingToggle("Data saver", local.dataSaver) { local = local.copy(dataSaver = it); saved = false } }
        item { SettingToggle("Reduce motion", local.reduceMotion) { local = local.copy(reduceMotion = it); saved = false } }
        item {
            OutlinedTextField(local.theme, { local = local.copy(theme = it.lowercase()); saved = false }, label = { Text("Theme: system / light / dark") })
        }
        item {
            Button(onClick = {
                scope.launch {
                    runCatching { state.updateSettings(local) }.onSuccess { saved = true }
                }
            }) { Text(if (saved) "Saved" else "Save settings") }
        }
        item {
            OutlinedButton(onClick = state::signOut) { Text("Sign out") }
        }
    }
}

@Composable
fun AdminScreen(state: DesktopAppState) {
    val capability = state.adminCapability
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenHeader("Admin", "Protected Blinkng administration") }
        if (!capability.allowed) {
            item { EmptyState("Your account does not have server-authorized admin access.") }
        } else {
            item {
                Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 2.dp) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Admin access verified by Supabase", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("Role: ${capability.role ?: "admin"}")
                        if (capability.isOwner) Text("Owner account", fontWeight = FontWeight.Black)
                        Text("Sensitive admin operations remain routed through the existing server-side admin RPCs, not client metadata.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun PostCard(
    post: DesktopFeedPost,
    onLike: () -> Unit,
    onBookmark: () -> Unit,
    onRepost: () -> Unit,
    onComments: () -> Unit,
    onCopyLink: () -> Unit,
    xFeedStyle: Boolean = false,
) {
    var expandedText by remember(post.id) { mutableStateOf(false) }
    var textCanExpand by remember(post.id) { mutableStateOf(false) }
    val mediaUrls = remember(post.id, post.imageUrl, post.images) {
        buildList {
            post.imageUrl?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", true) }?.let(::add)
            post.images.map(String::trim)
                .filter { it.isNotBlank() && !it.equals("null", true) }
                .forEach(::add)
        }.distinct()
    }

    Surface(
        shape = RoundedCornerShape(if (xFeedStyle) 0.dp else 20.dp),
        tonalElevation = if (xFeedStyle) 0.dp else 1.dp,
        color = if (xFeedStyle) Color.Black else MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = if (xFeedStyle) 14.dp else 18.dp,
                vertical = if (xFeedStyle) 10.dp else 18.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (xFeedStyle) 8.dp else 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PresenceAvatar(post.authorName, post.authorOnline, showStatus = false)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    VerifiedName(post.authorName, post.authorVerified)
                    Text(
                        "@${post.authorUsername} • ${formatTime(post.createdAt)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!xFeedStyle) {
                    IconButton(onClick = onBookmark) {
                        Icon(
                            if (post.isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = if (post.isBookmarked) "Remove saved post" else "Save post",
                            tint = if (post.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val body = post.text?.takeIf(String::isNotBlank) ?: post.caption.orEmpty()
            if (body.isNotBlank()) {
                Text(
                    text = body,
                    modifier = Modifier.padding(start = if (xFeedStyle) 58.dp else 0.dp),
                    fontSize = 15.sp,
                    maxLines = if (expandedText) Int.MAX_VALUE else 7,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result ->
                        if (!expandedText && result.hasVisualOverflow) textCanExpand = true
                    },
                )
                if (textCanExpand || expandedText) {
                    Text(
                        text = if (expandedText) "Show less" else "See more",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(start = if (xFeedStyle) 58.dp else 0.dp)
                            .clickable { expandedText = !expandedText },
                    )
                }
            }

            if (mediaUrls.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = if (xFeedStyle) 58.dp else 0.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    mediaUrls.forEachIndexed { index, url ->
                        var loadingMedia by remember(url) { mutableStateOf(true) }
                        var failedMedia by remember(url) { mutableStateOf(false) }
                        Surface(
                            modifier = Modifier.width(if (xFeedStyle) 440.dp else 520.dp)
                                .height(if (xFeedStyle) 360.dp else 320.dp),
                            shape = RoundedCornerShape(if (xFeedStyle) 14.dp else 16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = "Post image ${index + 1} of ${mediaUrls.size}",
                                    contentScale = if (xFeedStyle) ContentScale.Crop else ContentScale.Fit,
                                    onLoading = {
                                        loadingMedia = true
                                        failedMedia = false
                                    },
                                    onSuccess = {
                                        loadingMedia = false
                                        failedMedia = false
                                    },
                                    onError = {
                                        loadingMedia = false
                                        failedMedia = true
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (loadingMedia) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                                }
                                if (failedMedia) {
                                    Text(
                                        "Image unavailable",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                    )
                                }
                                if (mediaUrls.size > 1) {
                                    Surface(
                                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                                        shape = RoundedCornerShape(100.dp),
                                        color = Color.Black.copy(alpha = 0.62f),
                                    ) {
                                        Text(
                                            "${index + 1}/${mediaUrls.size}",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (xFeedStyle) {
                // Align media and action metrics with the post text, as on X's timeline.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 58.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onComments) {
                            Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = "Comments")
                        }
                        Text("${post.commentCount}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onRepost) {
                            Icon(
                                Icons.Rounded.Repeat,
                                contentDescription = if (post.isRepostedByMe) "Undo repost" else "Repost",
                                tint = if (post.isRepostedByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("${post.repostCount}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onLike) {
                            Icon(
                                if (post.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = if (post.isLiked) "Unlike" else "Like",
                                tint = if (post.isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("${post.likeCount}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${post.viewCount} views", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = onBookmark) {
                        Icon(
                            if (post.isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = if (post.isBookmarked) "Remove saved post" else "Save post"
                        )
                    }
                    TextButton(onClick = onCopyLink) {
                        Text("Share", fontSize = 11.sp)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            } else {
                val primaryMetrics = buildList {
                    add("${post.viewCount} views")
                    if (post.likeCount > 0) add("${post.likeCount} likes")
                }
                val secondaryMetrics = buildList {
                    if (post.commentCount > 0) add("${post.commentCount} comments")
                    if (post.shareCount > 0) add("${post.shareCount} shares")
                    if (post.repostCount > 0) add("${post.repostCount} reposts")
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        primaryMetrics.joinToString("  ·  "),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    if (secondaryMetrics.isNotEmpty()) {
                        Text(
                            secondaryMetrics.joinToString("  ·  "),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
    
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onLike) {
                        Icon(
                            if (post.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (post.isLiked) "Unlike" else "Like",
                            tint = if (post.isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onComments) {
                        Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = "Comments")
                    }
                    IconButton(onClick = onRepost) {
                        Icon(
                            Icons.Rounded.Repeat,
                            contentDescription = if (post.isRepostedByMe) "Undo repost" else "Repost",
                            tint = if (post.isRepostedByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = onCopyLink) {
                        Text("Copy link", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String) {
    Column {
        Text(title, fontWeight = FontWeight.Black, fontSize = 28.sp)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
}

@Composable
private fun SettingToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, fontWeight = FontWeight.Black, fontSize = 19.sp)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VerifiedName(name: String, verified: Boolean, size: androidx.compose.ui.unit.TextUnit = 14.sp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, fontWeight = FontWeight.Bold, fontSize = size)
        if (verified) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.Verified, contentDescription = "Verified", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AvatarInitial(name: String, size: androidx.compose.ui.unit.Dp = 38.dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.trim().firstOrNull()?.uppercase() ?: "B", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun LoadingRow() {
    Row(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun EmptyState(message: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
        Text(message, modifier = Modifier.fillMaxWidth().padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InlineError(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
}

private fun parseDesktopDailyMissions(payload: JSONObject?): List<BlinkDailyMission> {
    val array = payload?.optJSONArray("missions") ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            val key = row.optString("key").trim()
            val title = row.optString("title").trim()
            val target = row.optInt("target", 0)
            if (key.isBlank() || title.isBlank() || target <= 0) continue
            add(
                BlinkDailyMission(
                    key = key,
                    title = title,
                    description = row.optString("description").trim(),
                    progress = row.optInt("progress", 0).coerceAtLeast(0),
                    target = target,
                    coinReward = row.optInt("coin_reward", 0).coerceAtLeast(0),
                    xpReward = row.optInt("xp_reward", 0).coerceAtLeast(0),
                    claimed = row.optBoolean("claimed", false),
                )
            )
        }
    }
}

private fun parseDesktopEconomyPolicy(payload: JSONObject?): BlinkEconomyPolicy {
    val fallback = BlinkEconomyDefaults.policy
    if (payload == null) return fallback

    val milestones = payload.optJSONArray("rewarded_milestones")?.let { array ->
        buildList {
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: continue
                val ads = row.optInt("ads", 0)
                val total = row.optInt("total_coins", 0)
                if (ads > 0 && total > 0) add(BlinkRewardMilestone(ads, total))
            }
        }.sortedBy { it.ads }
    }.orEmpty().ifEmpty { fallback.rewardedMilestones }

    val packs = payload.optJSONArray("coin_packs")?.let { array ->
        buildList {
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: continue
                val id = row.optString("id").trim()
                val price = row.optInt("price_ngn", 0)
                val coins = row.optInt("coins", 0)
                if (id.isNotBlank() && price > 0 && coins > 0) add(BlinkCoinPack(id, price, coins))
            }
        }
    }.orEmpty().ifEmpty { fallback.coinPacks }

    return BlinkEconomyPolicy(
        rewardedAdBaseCoins = payload.optInt("rewarded_ad_base_coins", fallback.rewardedAdBaseCoins).coerceAtLeast(1),
        rewardedAdDailyLimit = payload.optInt("rewarded_ad_daily_limit", fallback.rewardedAdDailyLimit).coerceIn(1, 100),
        rewardedMilestones = milestones,
        blueVerificationCashNgn = payload.optInt("blue_verification_cash_ngn", fallback.blueVerificationCashNgn).coerceAtLeast(1),
        blueVerificationCoinCost = payload.optInt("blue_verification_coin_cost", fallback.blueVerificationCoinCost).coerceAtLeast(1),
        blueVerificationValidDays = payload.optInt("blue_verification_valid_days", fallback.blueVerificationValidDays).coerceIn(1, 366),
        coinPacks = packs,
        cashCheckoutEnabled = payload.optBoolean("cash_checkout_enabled", fallback.cashCheckoutEnabled),
    )
}

@Composable
private fun rememberDesktopManagedPulse(
    key: String,
    range: IntRange,
    tickMillis: Long,
    policy: BlinkActivityPulsePolicy,
    liveDataAvailable: Boolean,
    reduceMotion: Boolean,
): Int? {
    val normalized = policy.normalized()
    val minValue = minOf(range.first, range.last)
    val maxValue = maxOf(range.first, range.last)
    var value by remember(key) {
        mutableStateOf(BlinkPulseSessionStore.get(key))
    }

    LaunchedEffect(
        key,
        minValue,
        maxValue,
        tickMillis,
        normalized.minHoldMillis,
        normalized.maxStep,
        normalized.transitionStepMultiplier,
        liveDataAvailable,
        reduceMotion,
    ) {
        if (!liveDataAvailable || !normalized.enabled) return@LaunchedEffect

        if (value == null) {
            val seeded = if (minValue == maxValue) minValue else Random.nextInt(minValue, maxValue + 1)
            value = seeded
            BlinkPulseSessionStore.put(key, seeded)
        }

        val hold = maxOf(tickMillis, normalized.minHoldMillis) * if (reduceMotion) 2L else 1L
        while (true) {
            delay(hold)
            val current = value ?: continue
            val magnitude = Random.nextInt(1, normalized.maxStep + 1)
            val direction = if (Random.nextBoolean()) 1 else -1
            var next = nextPulseValue(
                current = current,
                range = minValue..maxValue,
                requestedStep = magnitude * direction,
                maxStep = normalized.maxStep,
                transitionStepMultiplier = normalized.transitionStepMultiplier,
            )
            if (next == current && current in minValue..maxValue && minValue < maxValue) {
                next = nextPulseValue(
                    current = current,
                    range = minValue..maxValue,
                    requestedStep = if (current <= minValue) 1 else -1,
                    maxStep = normalized.maxStep,
                    transitionStepMultiplier = normalized.transitionStepMultiplier,
                )
            }
            value = next
            BlinkPulseSessionStore.put(key, next)
        }
    }

    return value
}

private fun formatTime(value: String): String = runCatching {
    val instant = Instant.parse(value)
    DateTimeFormatter.ofPattern("dd MMM, HH:mm").withZone(ZoneId.systemDefault()).format(instant)
}.getOrDefault(value.take(16))
