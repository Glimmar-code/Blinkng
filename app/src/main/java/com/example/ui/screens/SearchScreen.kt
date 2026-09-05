package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import coil.compose.AsyncImage
import com.example.data.models.FeedPost
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.ui.components.PostCard
import com.example.ui.theme.BlinkOnlineGreen
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.FeedBackground
import com.example.ui.theme.FeedBorder
import com.example.ui.theme.FeedCardSurface
import com.example.ui.theme.FeedTextPrimary
import com.example.viewmodel.BlinkViewModel
import com.example.viewmodel.MainTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    isDark: Boolean
) {
    @Suppress("UNUSED_VARIABLE")
    val legacyPostOpenCallback = onPostClick // Whole-card taps are intentionally disabled in Discover.

    val activity = LocalActivity.current
    val rootViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[BlinkViewModel::class.java] }
    }
    val density = LocalDensity.current
    val dragOffset = remember { Animatable(0f) }
    val gestureScope = rememberCoroutineScope()
    val dismissThresholdPx = with(density) { 96.dp.toPx() }

    fun goHome() {
        rootViewModel?.setTab(MainTab.HOME)
    }

    BackHandler { goHome() }

    var query by rememberSaveable { mutableStateOf("") }
    val clean = query.trim().removePrefix("#")
    LaunchedEffect(clean) { onSearchQueryChange(clean) }

    val realProfiles = remember(profiles) {
        profiles.filter { it.username.isNotBlank() }
            .distinctBy { it.id.ifBlank { it.username.lowercase() } }
    }
    val people = remember(realProfiles, serverProfiles, clean) {
        if (clean.isBlank()) {
            realProfiles.sortedWith(
                compareByDescending<UserProfile> { it.onlineNow }
                    .thenByDescending { it.points }
            ).take(20)
        } else {
            serverProfiles
        }
    }
    val hashtags = remember(posts, clean) {
        posts.flatMap { it.tags }
            .map { it.trim().removePrefix("#").lowercase() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .filter { clean.isBlank() || it.key.contains(clean, true) }
            .take(15)
    }
    val matchingPosts = remember(posts, serverPosts, clean) {
        if (clean.isBlank()) posts.distinctBy { it.id }.take(30)
        else serverPosts.distinctBy { it.id }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FeedBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .blur(7.dp)
                .graphicsLayer { alpha = 0.42f },
            contentPadding = PaddingValues(top = 18.dp, bottom = 100.dp)
        ) {
            items(posts.filterNot { it.isReel }.take(4), key = { "discover_backdrop_${it.id}" }) { post ->
                PostCard(
                    post = post,
                    isDark = true,
                    onLike = {},
                    onComment = {},
                    onBookmark = {},
                    onShare = {},
                    onOptionsClick = {},
                    onProfileClick = {},
                    isAuthor = false
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(.95f)
                .fillMaxHeight()
                .offset { IntOffset(dragOffset.value.roundToInt(), 0) }
                .shadow(24.dp, RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp), clip = false)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            gestureScope.launch {
                                dragOffset.snapTo((dragOffset.value + dragAmount).coerceAtLeast(0f))
                            }
                        },
                        onDragEnd = {
                            gestureScope.launch {
                                if (dragOffset.value >= dismissThresholdPx) {
                                    goHome()
                                } else {
                                    dragOffset.animateTo(
                                        0f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            gestureScope.launch {
                                dragOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                    )
                },
            shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
            color = FeedBackground,
            border = BorderStroke(1.dp, FeedBorder)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 110.dp)
            ) {
                item {
                    Column(
                        Modifier
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = ::goHome, modifier = Modifier.size(46.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Home",
                                    tint = FeedTextPrimary
                                )
                            }
                            Spacer(Modifier.width(3.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Discover", fontSize = 23.sp, fontWeight = FontWeight.Black, color = FeedTextPrimary)
                                Text(
                                    "People, posts and hashtags",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            placeholder = { Text("Search people, posts or #hashtags") },
                            shape = RoundedCornerShape(22.dp)
                        )
                    }
                }

                if (isSearching && clean.isNotBlank()) {
                    item {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    }
                }

                if (people.isNotEmpty()) {
                    item {
                        Text(
                            if (clean.isBlank()) "People to discover" else "People",
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(people, key = { it.id.ifBlank { it.username } }) { person ->
                                Surface(
                                    modifier = Modifier.width(150.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    color = MaterialTheme.colorScheme.surface,
                                    onClick = { onProfileClick(person.username) }
                                ) {
                                    Column(
                                        Modifier.padding(13.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box {
                                            AsyncImage(
                                                model = person.avatarUrl,
                                                contentDescription = person.fullName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(58.dp).clip(CircleShape)
                                            )
                                            if (person.onlineNow) {
                                                Box(
                                                    Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .size(13.dp)
                                                        .background(BlinkOnlineGreen, CircleShape)
                                                        .padding(2.dp)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                person.fullName.ifBlank { person.username },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (person.verificationBadge != VerificationBadge.NONE) {
                                                Spacer(Modifier.width(3.dp))
                                                Icon(
                                                    Icons.Default.Verified,
                                                    null,
                                                    tint = BlinkPink,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            "@${person.username}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        person.university
                                            .takeUnless { it.isBlank() || it.equals("null", true) }
                                            ?.let {
                                                Text(
                                                    it,
                                                    fontSize = 9.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                    }
                                }
                            }
                        }
                    }
                }

                if (hashtags.isNotEmpty()) {
                    item {
                        Text(
                            "Trending hashtags",
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(hashtags, key = { it.key }) { tag ->
                                AssistChip(
                                    onClick = { query = "#${tag.key}" },
                                    leadingIcon = {
                                        Icon(Icons.Default.Tag, null, modifier = Modifier.size(15.dp))
                                    },
                                    label = { Text("#${tag.key} • ${tag.value}") }
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        if (clean.isBlank()) "Latest posts" else "Posts",
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                if (matchingPosts.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (clean.isBlank()) "No live posts yet." else "No results for “$query”.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(matchingPosts, key = { it.id }) { post ->
                        var visible by remember(post.id) { mutableStateOf(false) }
                        LaunchedEffect(post.id) {
                            delay(25)
                            visible = true
                        }
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 12 })
                        ) {
                            PostCard(
                                post = post,
                                isDark = isDark,
                                onLike = { onLikePost(post.id) },
                                onComment = { onCommentPost(post.id) },
                                onBookmark = { onBookmarkPost(post.id) },
                                onShare = { onSharePost(post.id) },
                                onOptionsClick = { onOptionsClick(post) },
                                onProfileClick = onProfileClick,
                                isAuthor = post.author.equals(currentUsername, true),
                                onDelete = { onDeletePost(post.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
