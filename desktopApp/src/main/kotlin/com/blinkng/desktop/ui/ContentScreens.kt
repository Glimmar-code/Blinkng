package com.blinkng.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.blinkng.desktop.data.DesktopConnectListing
import com.blinkng.desktop.data.DesktopFeedPost
import com.blinkng.desktop.data.DesktopInventoryItem
import com.blinkng.desktop.data.DesktopLeaderboardEntry
import com.blinkng.desktop.data.DesktopMarketItem
import com.blinkng.desktop.data.DesktopNotification
import com.blinkng.desktop.data.DesktopSearchResults
import com.blinkng.desktop.data.DesktopStoreItem
import com.blinkng.desktop.data.DesktopUserSettings
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(state: DesktopAppState) {
    var posts by remember { mutableStateOf<List<DesktopFeedPost>>(emptyList()) }
    var composer by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var commentsFor by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        loading = true
        runCatching { state.client.fetchFeed() }
            .onSuccess { posts = it; error = null }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ScreenHeader("Home", "Your live Blinkng feed") }
        item {
            Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 2.dp) {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    runCatching { state.client.createPost(composer) }
                                        .onSuccess { composer = ""; reload() }
                                        .onFailure { error = it.message }
                                }
                            },
                            enabled = composer.isNotBlank(),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Post")
                        }
                        OutlinedButton(onClick = { scope.launch { reload() } }) {
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
                    scope.launch {
                        val liked = runCatching { state.client.toggleLike(post.id) }.getOrNull() ?: return@launch
                        posts = posts.map {
                            if (it.id == post.id) it.copy(
                                isLiked = liked,
                                likeCount = (it.likeCount + if (liked) 1 else -1).coerceAtLeast(0),
                            ) else it
                        }
                    }
                },
                onComments = { commentsFor = if (commentsFor == post.id) null else post.id },
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        PresenceAvatar(profile.fullName, profile.isOnline)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            VerifiedName(profile.fullName, profile.isVerified)
                            Text("@${profile.username} • ${profile.university ?: "Blinkng"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                desktopPresenceStatus(profile.isOnline, profile.lastSeenAt),
                                fontSize = 10.sp,
                                color = if (profile.isOnline) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (results.posts.isNotEmpty()) {
            item { SectionTitle("Posts") }
            items(results.posts, key = { "post-${it.id}" }) { PostCard(it, {}, {}) }
        }
        if (!loading && query.isNotBlank() && results.profiles.isEmpty() && results.posts.isEmpty()) {
            item { EmptyState("No results for “$query”.") }
        }
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
                            if (item.actorIsVip || item.vipPriority) {
                                Spacer(Modifier.width(6.dp))
                                Text("VIP", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
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
    var loading by remember { mutableStateOf(true) }
    var showCreate by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf("community") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        loading = true
        listings = runCatching { state.client.fetchConnectListings() }.getOrDefault(emptyList())
        loading = false
    }
    LaunchedEffect(Unit) { reload() }

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
                    ScreenHeader("Connect", "Study circles, mentors, communities and campus connections")
                    Button(onClick = { showCreate = true }) { Text("Create") }
                }
            }
            if (loading) item { LoadingRow() }
            items(listings, key = { it.id }) { listing ->
                Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(listing.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(listing.description)
                        Text(
                            listOfNotNull(listing.listingType, listing.university, listing.department, listing.academicLevel, listing.location).joinToString(" • "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                        if (listing.tags.isNotEmpty()) Text(listing.tags.joinToString("  ") { "#$it" }, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                }
            }
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
                                "Create without pushing the form to the end of the Connect list.",
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
    LaunchedEffect(Unit) {
        entries = runCatching { state.client.fetchLeaderboard() }.getOrDefault(emptyList())
        loading = false
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ScreenHeader("Leaderboard", "World and campus ranking") }
        if (loading) item { LoadingRow() }
        items(entries, key = { it.userId }) { entry ->
            Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp) {
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
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ScreenHeader("Profile", "Your Blink identity") }
        if (profile == null) {
            item { LoadingRow() }
        } else {
            item {
                Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 2.dp) {
                    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PresenceAvatar(profile.fullName, profile.isOnline, 64.dp)
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
                            Stat("Coins", profile.coinBalance.toString())
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GamesScreen(state: DesktopAppState) {
    var challenges by remember { mutableStateOf<Int?>(null) }
    var coins by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        val summary = runCatching { state.client.fetchGameSummary() }.getOrDefault(0 to 0L)
        challenges = summary.first
        coins = summary.second
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenHeader("Games", "Challenges, rewards and Blink Coins") }
        item {
            Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 2.dp) {
                Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(42.dp)) {
                    Stat("Available challenges", challenges?.toString() ?: "…")
                    Stat("Coin balance", coins?.toString() ?: "…")
                }
            }
        }
    }
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
private fun PostCard(post: DesktopFeedPost, onLike: () -> Unit, onComments: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 1.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PresenceAvatar(post.authorName, post.authorOnline)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    VerifiedName(post.authorName, post.authorVerified)
                    Text("@${post.authorUsername} • ${formatTime(post.createdAt)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val body = post.text?.takeIf(String::isNotBlank) ?: post.caption.orEmpty()
            if (body.isNotBlank()) Text(body, fontSize = 15.sp)
            if (post.imageUrl != null || post.images.isNotEmpty()) {
                Surface(modifier = Modifier.fillMaxWidth().height(180.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Image attachment", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onLike) {
                    Icon(if (post.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = "Like", tint = if (post.isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(post.likeCount.toString(), fontSize = 12.sp)
                IconButton(onClick = onComments) { Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = "Comments") }
                Text(post.commentCount.toString(), fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text("${post.viewCount} views", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun formatTime(value: String): String = runCatching {
    val instant = Instant.parse(value)
    DateTimeFormatter.ofPattern("dd MMM, HH:mm").withZone(ZoneId.systemDefault()).format(instant)
}.getOrDefault(value.take(16))
