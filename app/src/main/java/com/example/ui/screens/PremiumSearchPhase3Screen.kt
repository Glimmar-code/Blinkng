package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.models.DiscoveryResult
import com.example.data.models.DiscoveryResultType
import com.example.data.models.DiscoverySort
import com.example.data.models.FeedPost
import com.example.data.models.UserProfile
import com.example.ui.theme.BlinkThemeTokens
import com.example.viewmodel.SearchDiscoveryViewModel
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

/**
 * Phase 3 entry point. Until search_discovery_v2 is deployed, the existing Phase 2 UI
 * remains the fallback so Testlab never exposes controls that cannot work.
 */
@Composable
internal fun PremiumSearchPhase3Host(
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
    val searchVm: SearchDiscoveryViewModel = viewModel()
    val state by searchVm.state.collectAsStateWithLifecycle()

    if (!state.backendChecked) {
        Phase3SearchLoading()
        return
    }

    if (!state.backendAvailable) {
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
        return
    }

    Phase3SearchContent(
        localPosts = (posts + serverPosts).distinctBy { it.id },
        viewModel = searchVm,
        onProfileClick = onProfileClick,
        onPostClick = onPostClick,
    )
}

@Composable
private fun Phase3SearchLoading() {
    val colors = BlinkThemeTokens.colors
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Search Blink", fontSize = 26.sp, fontWeight = FontWeight.Black, color = colors.textPrimary)
        Surface(Modifier.fillMaxWidth().height(54.dp), RoundedCornerShape(18.dp), colors.input) {}
        repeat(5) {
            Surface(Modifier.fillMaxWidth().height(86.dp), RoundedCornerShape(18.dp), colors.surfaceElevated) {}
        }
    }
}

private data class Phase3Category(val type: DiscoveryResultType?, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Phase3SearchContent(
    localPosts: List<FeedPost>,
    viewModel: SearchDiscoveryViewModel,
    onProfileClick: (String) -> Unit,
    onPostClick: (FeedPost) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = BlinkThemeTokens.colors
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var showFilters by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showImageSheet by remember { mutableStateOf(false) }
    var selectedImage by remember { mutableStateOf<android.net.Uri?>(null) }
    var detailResult by remember { mutableStateOf<DiscoveryResult?>(null) }
    var heroResult by remember { mutableStateOf<DiscoveryResult?>(null) }
    var placeholderIndex by remember { mutableIntStateOf(0) }

    val placeholders = remember {
        listOf(
            "Search people, posts and reels",
            "Search communities and events",
            "Search pages, brands and marketplace",
            "Search your saved content",
            "Search what people you follow are sharing",
        )
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000)
            placeholderIndex = (placeholderIndex + 1) % placeholders.size
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let(viewModel::setQuery)
        }
    }
    fun launchVoiceSearch() {
        runCatching {
            voiceLauncher.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Search Blink")
                }
            )
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedImage = uri
        showImageSheet = uri != null
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) readSearchLocation(context, viewModel::setLocation)
    }
    fun requestNearMe() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            readSearchLocation(context, viewModel::setLocation)
        } else {
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    val categories = remember(state.capabilities) {
        buildList {
            add(Phase3Category(null, "All", Icons.Rounded.Search))
            add(Phase3Category(DiscoveryResultType.PROFILE, "People", Icons.Rounded.Person))
            add(Phase3Category(DiscoveryResultType.POST, "Posts", Icons.Rounded.Photo))
            add(Phase3Category(DiscoveryResultType.REEL, "Reels", Icons.Rounded.VideoLibrary))
            if (state.capabilities.communities) add(Phase3Category(DiscoveryResultType.COMMUNITY, "Communities", Icons.Rounded.Groups))
            if (state.capabilities.events) add(Phase3Category(DiscoveryResultType.EVENT, "Events", Icons.Rounded.Event))
            if (state.capabilities.pagesBrands) add(Phase3Category(DiscoveryResultType.PAGE, "Pages", Icons.Rounded.Storefront))
            if (state.capabilities.marketplace) add(Phase3Category(DiscoveryResultType.MARKET_ITEM, "Market", Icons.Rounded.ShoppingBag))
        }
    }

    val visibleResultKeys by remember(listState) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.map { it.key?.toString().orEmpty() }.toSet() }
    }

    LaunchedEffect(listState, state.hasMore, state.results.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 5
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && state.hasMore) viewModel.loadMore()
        }
    }

    fun openResult(result: DiscoveryResult) {
        heroResult = result
        scope.launch {
            delay(170)
            when (result.type) {
                DiscoveryResultType.PROFILE -> if (result.username.isNotBlank()) onProfileClick(result.username)
                DiscoveryResultType.POST, DiscoveryResultType.REEL -> {
                    onPostClick(localPosts.firstOrNull { it.id == result.id } ?: result.asFeedPost())
                }
                else -> detailResult = result
            }
            heroResult = null
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Search Blink", fontSize = 27.sp, fontWeight = FontWeight.Black, color = colors.textPrimary)
                        Text("Live universal discovery · account-synced history", fontSize = 10.sp, color = colors.textMuted)
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.primaryBright.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, colors.primaryBright.copy(alpha = 0.24f)),
                    ) {
                        Text("Premium", Modifier.padding(horizontal = 9.dp, vertical = 6.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = colors.primaryBright)
                    }
                }

                Spacer(Modifier.height(11.dp))
                Phase3SearchField(
                    query = state.query,
                    placeholder = placeholders[placeholderIndex],
                    activeFilterCount = listOf(state.followingOnly, state.savedOnly, state.sort != DiscoverySort.RELEVANT).count { it },
                    onQuery = viewModel::setQuery,
                    onVoice = ::launchVoiceSearch,
                    onImage = { imagePicker.launch("image/*") },
                    onFilters = { showFilters = true },
                )

                Spacer(Modifier.height(9.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(categories, key = { it.label }) { category ->
                        Phase3Chip(category.label, category.icon, state.selectedType == category.type) {
                            viewModel.selectType(category.type)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    item { Phase3FilterChip(state.sort.label, Icons.Rounded.Sort, state.sort != DiscoverySort.RELEVANT) { showSort = true } }
                    if (state.capabilities.following) item { Phase3FilterChip("Following", Icons.Rounded.Person, state.followingOnly) { viewModel.toggleFollowingOnly() } }
                    if (state.capabilities.saved) item { Phase3FilterChip("Saved", Icons.Rounded.Bookmark, state.savedOnly) { viewModel.toggleSavedOnly() } }
                    if (state.capabilities.distanceSort) item {
                        Phase3FilterChip(
                            if (state.latitude == null) "Near me" else "Location on",
                            Icons.Rounded.LocationOn,
                            state.latitude != null,
                            ::requestNearMe,
                        )
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.query.isBlank() && state.history.isNotEmpty() && !state.privateHistory) {
                    item(key = "history-title") {
                        SearchSectionTitle("Recent searches", "Synced privately to your Blink account")
                    }
                    item(key = "history-row") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(state.history.take(12), key = { it.id }) { entry ->
                                Surface(
                                    onClick = { viewModel.useHistory(entry) },
                                    shape = RoundedCornerShape(13.dp),
                                    color = colors.surfaceElevated,
                                    border = BorderStroke(1.dp, colors.borderSoft),
                                ) {
                                    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.History, null, Modifier.size(13.dp), tint = colors.textMuted)
                                        Spacer(Modifier.width(5.dp))
                                        Text(entry.query, fontSize = 10.sp, color = colors.textPrimary, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "results-title") {
                    SearchSectionTitle(
                        when {
                            state.query.isNotBlank() -> "Results"
                            state.savedOnly -> "Saved discovery"
                            state.followingOnly -> "From people you follow"
                            state.sort == DiscoverySort.GROWING -> "Fastest growing"
                            state.sort == DiscoverySort.TRENDING -> "Trending now"
                            else -> "Discover"
                        },
                        "${state.results.size} loaded · ${state.selectedType?.label ?: "all categories"} · cursor pagination",
                    )
                }

                state.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    item(key = "error") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(15.dp),
                            color = colors.error.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, colors.error.copy(alpha = 0.24f)),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(message, Modifier.weight(1f), fontSize = 10.sp, color = colors.textSecondary)
                                TextButton(onClick = viewModel::refresh) { Text("Retry", color = colors.primaryBright) }
                            }
                        }
                    }
                }

                when {
                    state.isLoading -> items(6, key = { "skeleton-$it" }) { SearchSkeleton() }
                    state.results.isEmpty() -> item(key = "empty") { SearchEmptyState(state.query, state.savedOnly, state.followingOnly) }
                    else -> itemsIndexed(
                        items = state.results,
                        key = { _, item -> "result-${item.type.backendValue}-${item.id}" },
                    ) { _, result ->
                        val key = "result-${result.type.backendValue}-${result.id}"
                        DiscoveryCard(
                            result = result,
                            autoplay = state.autoplayPreviews && state.capabilities.autoplayPreviews && visibleResultKeys.contains(key),
                            onClick = { openResult(result) },
                        )
                    }
                }

                if (state.isLoadingMore) {
                    item(key = "loading-more") {
                        Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = colors.primaryBright, strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = heroResult != null,
            modifier = Modifier.fillMaxSize().zIndex(20f),
            enter = fadeIn(tween(120)) + scaleIn(tween(170), initialScale = 0.94f),
            exit = fadeOut(tween(90)) + scaleOut(tween(90), targetScale = 1.02f),
        ) {
            heroResult?.let { SearchHeroCard(it) }
        }
    }

    if (showSort) {
        ModalBottomSheet(onDismissRequest = { showSort = false }, containerColor = colors.surfaceElevated) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text("Sort results", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
                Text("Trend and growth percentages come from real backend snapshots.", fontSize = 10.sp, color = colors.textMuted)
                Spacer(Modifier.height(10.dp))
                DiscoverySort.entries.forEach { sort ->
                    val needsLocation = sort == DiscoverySort.DISTANCE
                    Surface(
                        onClick = {
                            if (needsLocation && state.latitude == null) requestNearMe() else {
                                viewModel.setSort(sort)
                                showSort = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(13.dp),
                        color = if (state.sort == sort) colors.primaryBright.copy(alpha = 0.10f) else colors.surfaceElevated,
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (needsLocation) Icons.Rounded.LocationOn else if (sort in listOf(DiscoverySort.TRENDING, DiscoverySort.GROWING)) Icons.Rounded.TrendingUp else Icons.Rounded.Sort, null, Modifier.size(17.dp), tint = if (state.sort == sort) colors.primaryBright else colors.textMuted)
                            Spacer(Modifier.width(9.dp))
                            Text(sort.label, Modifier.weight(1f), fontSize = 12.sp, color = colors.textPrimary)
                            if (state.sort == sort) Icon(Icons.Rounded.CheckCircle, null, Modifier.size(17.dp), tint = colors.primaryBright)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }, containerColor = colors.surfaceElevated) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text("Search preferences", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
                Text("Privacy and result scope", fontSize = 10.sp, color = colors.textMuted)
                Spacer(Modifier.height(10.dp))
                ToggleRow("Following only", "Only connected accounts and their content", state.followingOnly) { viewModel.toggleFollowingOnly() }
                ToggleRow("Saved only", "Bookmarks and marketplace wishlist", state.savedOnly) { viewModel.toggleSavedOnly() }
                ToggleRow("Private search", "New searches are not synced", state.privateHistory) { viewModel.setPrivateHistory(it) }
                ToggleRow("Autoplay reel previews", "Muted and only while a result is visible", state.autoplayPreviews) { viewModel.toggleAutoplay() }
                if (state.capabilities.distanceSort) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = ::requestNearMe,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    ) {
                        Icon(Icons.Rounded.LocationOn, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(if (state.latitude == null) "Use approximate location" else "Refresh approximate location")
                    }
                    Text("Location is used ephemerally for this search and is not written to your profile.", Modifier.padding(top = 6.dp), fontSize = 9.sp, color = colors.textMuted)
                }
                Spacer(Modifier.height(22.dp))
            }
        }
    }

    detailResult?.let { original ->
        val result = state.results.firstOrNull { it.type == original.type && it.id == original.id } ?: original
        ModalBottomSheet(onDismissRequest = { detailResult = null }, containerColor = colors.surfaceElevated) {
            SearchEntitySheet(
                result = result,
                onToggle = { viewModel.toggleEntity(result) },
                onSeller = {
                    if (result.username.isNotBlank()) onProfileClick(result.username)
                    detailResult = null
                },
            )
        }
    }

    if (showImageSheet) {
        ModalBottomSheet(onDismissRequest = { showImageSheet = false }, containerColor = colors.surfaceElevated) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                selectedImage?.let { uri ->
                    AsyncImage(uri, "Selected search image", Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.height(12.dp))
                }
                Text("Visual search", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
                Text(
                    if (state.capabilities.imageSimilarity) "Blink can compare this image against indexed media."
                    else "The image-similarity database contract is prepared, but no production visual-embedding model is connected yet. Your selected image was not uploaded.",
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(11.dp), color = colors.primaryBright.copy(alpha = 0.10f)) {
                    Text("No fake matches · capability-gated", Modifier.padding(horizontal = 9.dp, vertical = 7.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = colors.primaryBright)
                }
                Spacer(Modifier.height(22.dp))
            }
        }
    }
}

@Composable
private fun Phase3SearchField(
    query: String,
    placeholder: String,
    activeFilterCount: Int,
    onQuery: (String) -> Unit,
    onVoice: () -> Unit,
    onImage: () -> Unit,
    onFilters: () -> Unit,
) {
    val colors = BlinkThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        color = colors.input,
        border = BorderStroke(1.dp, if (query.isNotBlank()) colors.primaryBright.copy(alpha = 0.42f) else colors.borderSoft),
        shadowElevation = if (query.isNotBlank()) 5.dp else 0.dp,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, Modifier.size(19.dp), tint = if (query.isNotBlank()) colors.primaryBright else colors.textMuted)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isBlank()) Text(placeholder, fontSize = 11.sp, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp, color = colors.textPrimary, fontWeight = FontWeight.Medium),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {}),
                )
            }
            if (query.isNotBlank()) IconButton({ onQuery("") }, Modifier.size(33.dp)) { Icon(Icons.Rounded.Close, "Clear", Modifier.size(16.dp), tint = colors.textSecondary) }
            IconButton(onVoice, Modifier.size(33.dp)) { Icon(Icons.Rounded.Mic, "Voice search", Modifier.size(17.dp), tint = colors.textSecondary) }
            IconButton(onImage, Modifier.size(33.dp)) { Icon(Icons.Rounded.PhotoCamera, "Image search", Modifier.size(17.dp), tint = colors.textSecondary) }
            Box {
                IconButton(onFilters, Modifier.size(33.dp)) { Icon(Icons.Rounded.FilterList, "Filters", Modifier.size(17.dp), tint = if (activeFilterCount > 0) colors.primaryBright else colors.textSecondary) }
                if (activeFilterCount > 0) {
                    Surface(Modifier.align(Alignment.TopEnd).size(14.dp), CircleShape, colors.primaryBright) {
                        Box(contentAlignment = Alignment.Center) { Text(activeFilterCount.toString(), fontSize = 7.sp, fontWeight = FontWeight.Black, color = colors.background) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Phase3Chip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val colors = BlinkThemeTokens.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.primaryBright.copy(alpha = 0.14f) else colors.surfaceElevated,
        border = BorderStroke(1.dp, if (selected) colors.primaryBright.copy(alpha = 0.34f) else colors.borderSoft),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(13.dp), tint = if (selected) colors.primaryBright else colors.textMuted)
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) colors.primaryBright else colors.textSecondary)
        }
    }
}

@Composable
private fun Phase3FilterChip(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val colors = BlinkThemeTokens.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (active) colors.primary.copy(alpha = 0.10f) else colors.surface,
        border = BorderStroke(1.dp, if (active) colors.primaryBright.copy(alpha = 0.28f) else colors.borderSoft),
    ) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(12.dp), tint = if (active) colors.primaryBright else colors.textMuted)
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = if (active) colors.primaryBright else colors.textSecondary)
        }
    }
}

@Composable
private fun SearchSectionTitle(title: String, subtitle: String) {
    val colors = BlinkThemeTokens.colors
    Column(Modifier.fillMaxWidth().padding(top = 5.dp)) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
        Text(subtitle, fontSize = 9.sp, color = colors.textMuted)
    }
}

@Composable
private fun DiscoveryCard(result: DiscoveryResult, autoplay: Boolean, onClick: () -> Unit) {
    val colors = BlinkThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(19.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.borderSoft),
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchResultAvatar(result)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(result.title.ifBlank { result.type.label }, Modifier.weight(1f, fill = false), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (result.verified) {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.Verified, "Verified", Modifier.size(14.dp), tint = colors.primaryBright)
                        }
                    }
                    if (result.subtitle.isNotBlank()) Text(result.subtitle, fontSize = 9.sp, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SearchTypeBadge(result.type)
            }

            if (result.type == DiscoveryResultType.REEL && !result.videoUrl.isNullOrBlank()) {
                Spacer(Modifier.height(9.dp))
                SearchMutedAutoplayPreview(result.videoUrl, autoplay, result.matchedMomentMs)
            } else if (!result.imageUrl.isNullOrBlank() && result.type != DiscoveryResultType.PROFILE) {
                Spacer(Modifier.height(9.dp))
                AsyncImage(result.imageUrl, null, Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(15.dp)), contentScale = ContentScale.Crop)
            }

            if (result.body.isNotBlank() && result.type != DiscoveryResultType.REEL) {
                Spacer(Modifier.height(8.dp))
                Text(result.body, fontSize = 10.sp, lineHeight = 15.sp, color = colors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }

            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (result.reason.isNotBlank()) item { SearchMetric(result.reason, Icons.Rounded.TrendingUp) }
                if (result.mutualCount > 0) item { SearchMetric("${result.mutualCount} mutual", Icons.Rounded.Groups) }
                result.distanceKm?.let { item { SearchMetric(formatSearchDistance(it), Icons.Rounded.LocationOn) } }
                if (abs(result.trendPercent) >= 0.1) item { SearchMetric(formatSearchTrend(result.trendPercent), Icons.Rounded.TrendingUp) }
                result.matchedMomentMs?.let { item { SearchMetric("Match ${formatSearchMoment(it)}", Icons.Rounded.PlayArrow) } }
                if (result.saved) item { SearchMetric("Saved", Icons.Rounded.Bookmark) }
                if (result.following) item { SearchMetric("Connected", Icons.Rounded.CheckCircle) }
                if (result.memberCount > 0) item { SearchMetric("${compactSearchCount(result.memberCount)} members", Icons.Rounded.Groups) }
                if (result.attendeeCount > 0) item { SearchMetric("${compactSearchCount(result.attendeeCount)} going", Icons.Rounded.Event) }
                if (result.followerCount > 0) item { SearchMetric("${compactSearchCount(result.followerCount)} followers", Icons.Rounded.Person) }
                result.price?.let { item { SearchMetric(formatSearchPrice(it, result.currency), Icons.Rounded.ShoppingBag) } }
            }
        }
    }
}

@Composable
private fun SearchResultAvatar(result: DiscoveryResult) {
    val colors = BlinkThemeTokens.colors
    if (!result.avatarUrl.isNullOrBlank()) {
        AsyncImage(result.avatarUrl, null, Modifier.size(45.dp).clip(CircleShape), contentScale = ContentScale.Crop)
    } else {
        Surface(Modifier.size(45.dp), CircleShape, colors.primaryBright.copy(alpha = 0.12f)) {
            Box(contentAlignment = Alignment.Center) { Icon(iconForSearchType(result.type), null, Modifier.size(20.dp), tint = colors.primaryBright) }
        }
    }
}

@Composable
private fun SearchTypeBadge(type: DiscoveryResultType) {
    val colors = BlinkThemeTokens.colors
    Surface(shape = RoundedCornerShape(9.dp), color = colors.surfaceHighest, border = BorderStroke(1.dp, colors.borderSoft)) {
        Text(type.label, Modifier.padding(horizontal = 7.dp, vertical = 5.dp), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = colors.textMuted)
    }
}

@Composable
private fun SearchMetric(label: String, icon: ImageVector) {
    val colors = BlinkThemeTokens.colors
    Surface(shape = RoundedCornerShape(10.dp), color = colors.surfaceHighest) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(10.dp), tint = colors.primaryBright)
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 8.sp, color = colors.textSecondary, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun SearchSkeleton() {
    val colors = BlinkThemeTokens.colors
    Surface(Modifier.fillMaxWidth().height(88.dp), RoundedCornerShape(18.dp), colors.surfaceElevated, border = BorderStroke(1.dp, colors.borderSoft)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(44.dp), CircleShape, colors.surfaceHighest) {}
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Surface(Modifier.fillMaxWidth(0.5f).height(9.dp), RoundedCornerShape(5.dp), colors.surfaceHighest) {}
                Surface(Modifier.fillMaxWidth(0.8f).height(8.dp), RoundedCornerShape(5.dp), colors.surfaceHighest) {}
                Surface(Modifier.fillMaxWidth(0.36f).height(8.dp), RoundedCornerShape(5.dp), colors.surfaceHighest) {}
            }
        }
    }
}

@Composable
private fun SearchEmptyState(query: String, saved: Boolean, following: Boolean) {
    val colors = BlinkThemeTokens.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(Modifier.size(56.dp), CircleShape, colors.primaryBright.copy(alpha = 0.10f)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Search, null, Modifier.size(25.dp), tint = colors.primaryBright) }
        }
        Spacer(Modifier.height(11.dp))
        Text("No results yet", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        Text(
            when {
                saved -> "No saved content matches these filters."
                following -> "Try a broader search or turn off Following only."
                query.isNotBlank() -> "Check the spelling, switch categories, or use fewer words."
                else -> "Real Blink discovery results will appear here."
            },
            fontSize = 10.sp,
            color = colors.textMuted,
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    val colors = BlinkThemeTokens.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Text(subtitle, fontSize = 9.sp, color = colors.textMuted)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked, onChecked)
    }
}

@Composable
private fun SearchEntitySheet(result: DiscoveryResult, onToggle: () -> Unit, onSeller: () -> Unit) {
    val colors = BlinkThemeTokens.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        result.imageUrl?.takeIf { it.isNotBlank() }?.let {
            AsyncImage(it, null, Modifier.fillMaxWidth().height(205.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.height(12.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchResultAvatar(result)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(result.title, fontSize = 19.sp, fontWeight = FontWeight.Black, color = colors.textPrimary)
                Text(result.subtitle.ifBlank { result.type.label }, fontSize = 10.sp, color = colors.textSecondary)
            }
        }
        if (result.body.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(result.body, fontSize = 11.sp, lineHeight = 17.sp, color = colors.textSecondary)
        }
        if (result.reason.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            SearchMetric(result.reason, Icons.Rounded.TrendingUp)
        }
        Spacer(Modifier.height(14.dp))
        when (result.type) {
            DiscoveryResultType.COMMUNITY -> SearchActionButton(if (result.following) "Leave community" else "Join community", onToggle)
            DiscoveryResultType.EVENT -> SearchActionButton(if (result.following) "Cancel attendance" else "I'm going", onToggle)
            DiscoveryResultType.PAGE -> SearchActionButton(if (result.following) "Following" else "Follow page", onToggle)
            DiscoveryResultType.MARKET_ITEM -> if (result.username.isNotBlank()) SearchActionButton("View seller", onSeller)
            else -> Unit
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SearchActionButton(label: String, onClick: () -> Unit) {
    val colors = BlinkThemeTokens.colors
    Button(onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = colors.primary)) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SearchHeroCard(result: DiscoveryResult) {
    val colors = BlinkThemeTokens.colors
    Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.96f)).padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = colors.surfaceElevated,
            border = BorderStroke(1.dp, colors.primaryBright.copy(alpha = 0.24f)),
            shadowElevation = 16.dp,
        ) {
            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SearchResultAvatar(result)
                Spacer(Modifier.height(9.dp))
                Text(result.title, fontSize = 17.sp, fontWeight = FontWeight.Black, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(result.type.label, fontSize = 9.sp, color = colors.primaryBright)
            }
        }
    }
}

private fun iconForSearchType(type: DiscoveryResultType): ImageVector = when (type) {
    DiscoveryResultType.PROFILE -> Icons.Rounded.Person
    DiscoveryResultType.POST -> Icons.Rounded.Photo
    DiscoveryResultType.REEL -> Icons.Rounded.VideoLibrary
    DiscoveryResultType.COMMUNITY -> Icons.Rounded.Groups
    DiscoveryResultType.EVENT -> Icons.Rounded.Event
    DiscoveryResultType.PAGE -> Icons.Rounded.Storefront
    DiscoveryResultType.MARKET_ITEM -> Icons.Rounded.ShoppingBag
}

private fun formatSearchMoment(ms: Int): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun formatSearchDistance(km: Double): String = if (km < 1) "${(km * 1000).toInt().coerceAtLeast(1)} m" else if (km < 10) "%.1f km".format(km) else "%.0f km".format(km)
private fun formatSearchTrend(value: Double): String = if (value >= 0) "+%.1f%% / 24h".format(value) else "%.1f%% / 24h".format(value)
private fun compactSearchCount(value: Int): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0).replace(".0M", "M")
    value >= 1_000 -> "%.1fK".format(value / 1_000.0).replace(".0K", "K")
    else -> value.toString()
}
private fun formatSearchPrice(price: Long, currency: String): String = when (currency.uppercase()) {
    "NGN" -> "₦%,d".format(price)
    "USD" -> "$%,d".format(price)
    "GBP" -> "£%,d".format(price)
    "EUR" -> "€%,d".format(price)
    else -> "$currency %,d".format(price)
}

@Suppress("MissingPermission")
private fun readSearchLocation(context: Context, onResult: (Double?, Double?) -> Unit) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        onResult(null, null)
        return
    }
    LocationServices.getFusedLocationProviderClient(context).lastLocation
        .addOnSuccessListener { location -> onResult(location?.latitude, location?.longitude) }
        .addOnFailureListener { onResult(null, null) }
}
