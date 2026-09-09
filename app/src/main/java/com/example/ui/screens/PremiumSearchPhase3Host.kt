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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.flow.snapshotFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

/**
 * Phase 3 host. If the new backend contract is not deployed, Blink automatically keeps
 * the proven Phase 2 surface instead of exposing dead tabs or crashing Testlab.
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
    val phase3: SearchDiscoveryViewModel = viewModel()
    val state by phase3.state.collectAsState()

    when {
        !state.backendChecked -> Phase3BootstrapLoading()
        !state.backendAvailable -> PremiumSearchPhase2Host(
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
        else -> AdvancedUniversalSearch(
            profiles = (profiles + serverProfiles).distinctBy { it.id.ifBlank { it.username } },
            posts = (posts + serverPosts).distinctBy { it.id },
            viewModel = phase3,
            onProfileClick = onProfileClick,
            onPostClick = onPostClick,
        )
    }
}

@Composable
private fun Phase3BootstrapLoading() {
    val colors = BlinkThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Search Blink", fontSize = 26.sp, fontWeight = FontWeight.Black, color = colors.textPrimary)
        Surface(
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp),
            color = colors.input,
            border = BorderStroke(1.dp, colors.borderSoft),
        ) {}
        repeat(5) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(88.dp),
                shape = RoundedCornerShape(18.dp),
                color = colors.surfaceElevated,
                border = BorderStroke(1.dp, colors.borderSoft),
            ) {}
        }
    }
}

private data class SearchCategoryUi(
    val type: DiscoveryResultType?,
    val label: String,
    val icon: ImageVector,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedUniversalSearch(
    profiles: List<UserProfile>,
    posts: List<FeedPost>,
    viewModel: SearchDiscoveryViewModel,
    onProfileClick: (String) -> Unit,
    onPostClick: (FeedPost) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val colors = BlinkThemeTokens.colors
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showFilters by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var pickedImage by remember { mutableStateOf<android.net.Uri?>(null) }
    var showImageInfo by remember { mutableStateOf(false) }
    var detailResult by remember { mutableStateOf<DiscoveryResult?>(null) }
    var heroResult by remember { mutableStateOf<DiscoveryResult?>(null) }
    var placeholderIndex by remember { mutableIntStateOf(0) }

    val placeholders = remember {
        listOf(
            "Search people, posts and reels",
            "Search communities and events",
            "Search pages, brands and marketplace",
            "Search what your friends follow",
            "Search saved content",
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
                ?.takeIf(String::isNotBlank)
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
        pickedImage = uri
        if (uri != null) showImageInfo = true
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            readEphemeralSearchLocation(context) { lat, lng -> viewModel.setLocation(lat, lng) }
        }
    }
    fun enableNearMe() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            readEphemeralSearchLocation(context) { lat, lng -> viewModel.setLocation(lat, lng) }
        } else {
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    val categories = remember(state.capabilities) {
        buildList {
            add(SearchCategoryUi(null, "All", Icons.Rounded.Search))
            add(SearchCategoryUi(DiscoveryResultType.PROFILE, "People", Icons.Rounded.Person))
            add(SearchCategoryUi(DiscoveryResultType.POST, "Posts", Icons.Rounded.Photo))
            add(SearchCategoryUi(DiscoveryResultType.REEL, "Reels", Icons.Rounded.VideoLibrary))
            if (state.capabilities.communities) add(SearchCategoryUi(DiscoveryResultType.COMMUNITY, "Communities", Icons.Rounded.Groups))
            if (state.capabilities.events) add(SearchCategoryUi(DiscoveryResultType.EVENT, "Events", Icons.Rounded.Event))
            if (state.capabilities.pagesBrands) add(SearchCategoryUi(DiscoveryResultType.PAGE, "Pages", Icons.Rounded.Storefront))
            if (state.capabilities.marketplace) add(SearchCategoryUi(DiscoveryResultType.MARKET_ITEM, "Market", Icons.Rounded.Storefront))
        }
    }

    val visibleKeys by remember(listState) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.map { it.key?.toString().orEmpty() }.toSet()
        }
    }

    LaunchedEffect(listState, state.hasMore, state.results.size) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 5
        }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd && state.hasMore) viewModel.loadMore() }
    }

    fun openWithHero(result: DiscoveryResult) {
        heroResult = result
        scope.launch {
            delay(170)
            when (result.type) {
                DiscoveryResultType.PROFILE -> result.username.takeIf(String::isNotBlank)?.let(onProfileClick)
                DiscoveryResultType.POST, DiscoveryResultType.REEL -> {
                    val realPost = posts.firstOrNull { it.id == result.id } ?: result.asFeedPost()
                    onPostClick(realPost)
                }
                else -> detailResult = result
            }
            heroResult = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Search Blink", fontSize = 27.sp, fontWeight = FontWeight.Black, color = colors.textPrimary)
                        Text(
                            "Live discovery · synced across devices",
                            fontSize = 10.sp,
                            color = colors.textMuted,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.primaryBright.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, colors.primaryBright.copy(alpha = 0.22f)),
                    ) {
                        Text(
                            "Phase 3",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryBright,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                PremiumUniversalSearchField(
                    query = state.query,
                    placeholder = placeholders[placeholderIndex],
                    onQueryChange = viewModel::setQuery,
                    onVoice = ::launchVoiceSearch,
                    onImage = { imagePicker.launch("image/*") },
                    onFilters = { showFilters = true },
                    activeFilters = listOf(state.followingOnly, state.savedOnly, state.sort != DiscoverySort.RELEVANT).count { it },
                )

                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(categories, key = { it.label }) { item ->
                        PremiumCategoryChip(
                            label = item.label,
                            icon = item.icon,
                            selected = state.selectedType == item.type,
                            onClick = { viewModel.selectType(item.type) },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    item {
                        PremiumFilterChip(
                            label = state.sort.label,
                            icon = Icons.Rounded.Sort,
                            active = state.sort != DiscoverySort.RELEVANT,
                            onClick = { showSort = true },
                        )
                    }
                    if (state.capabilities.following) {
                        item {
                            PremiumFilterChip(
                                label = "Following",
                                icon = Icons.Rounded.Person,
                                active = state.followingOnly,
                                onClick = viewModel::toggleFollowingOnly,
                            )
                        }
                    }
                    if (state.capabilities.saved) {
                        item {
                            PremiumFilterChip(
                                label = "Saved",
                                icon = Icons.Rounded.Bookmark,
                                active = state.savedOnly,
                                onClick = viewModel::toggleSavedOnly,
                            )
                        }
                    }
                    if (state.capabilities.distanceSort) {
                        item {
                            PremiumFilterChip(
                                label = if (state.latitude != null) "Near me" else "Enable location",
                                icon = Icons.Rounded.LocationOn,
                                active = state.latitude != null,
                                onClick = ::enableNearMe,
                            )
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.query.isBlank() && state.history.isNotEmpty() && !state.privateHistory) {
                    item(key = "history-heading") {
                        SectionHeading(
                            title = "Recent searches",
                            subtitle = "Synced to your Blink account so they follow you between devices",
                        )
                    }
                    item(key = "history-row") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.history.take(12), key = { it.id }) { history ->
                                Surface(
                                    onClick = { viewModel.useHistory(history) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = colors.surfaceElevated,
                                    border = BorderStroke(1.dp, colors.borderSoft),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Rounded.History, null, modifier = Modifier.size(14.dp), tint = colors.textMuted)
                                        Spacer(Modifier.width(6.dp))
                                        Text(history.query, fontSize = 11.sp, color = colors.textPrimary, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "result-heading") {
                    val heading = when {
                        state.query.isNotBlank() -> "Results"
                        state.followingOnly -> "From people you follow"
                        state.savedOnly -> "Your saved discovery"
                        state.sort == DiscoverySort.GROWING -> "Fastest growing"
                        state.sort == DiscoverySort.TRENDING -> "Trending now"
                        else -> "Discover"
                    }
                    SectionHeading(
                        title = heading,
                        subtitle = buildResultSubtitle(state.results.size, state.selectedType, state.sort),
                    )
                }

                state.errorMessage?.takeIf(String::isNotBlank)?.let { message ->
                    item(key = "search-error") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = colors.error.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, colors.error.copy(alpha = 0.24f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(message, modifier = Modifier.weight(1f), fontSize = 11.sp, color = colors.textSecondary)
                                TextButton(onClick = viewModel::refresh) { Text("Retry", color = colors.primaryBright) }
                            }
                        }
                    }
                }

                if (state.isLoading) {
                    items(6, key = { "search-skeleton-$it" }) {
                        SearchResultSkeleton()
                    }
                } else if (state.results.isEmpty()) {
                    item(key = "empty") {
                        PremiumSearchEmpty(
                            query = state.query,
                            followingOnly = state.followingOnly,
                            savedOnly = state.savedOnly,
                        )
                    }
                } else {
                    itemsIndexed(
                        state.results,
                        key = { _, result -> "result-${result.type.backendValue}-${result.id}" },
                    ) { _, result ->
                        val key = "result-${result.type.backendValue}-${result.id}"
                        PremiumDiscoveryResultCard(
                            result = result,
                            autoplay = state.capabilities.autoplayPreviews && state.autoplayPreviews && key in visibleKeys,
                            onClick = { openWithHero(result) },
                        )
                    }
                }

                if (state.isLoadingMore) {
                    item(key = "loading-more") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = colors.primaryBright, strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = heroResult != null,
            modifier = Modifier.fillMaxSize().zIndex(20f),
            enter = fadeIn(tween(120)) + scaleIn(tween(170), initialScale = 0.94f),
            exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 1.02f),
        ) {
            heroResult?.let { SearchHeroOverlay(it) }
        }
    }

    if (showSort) {
        ModalBottomSheet(
            onDismissRequest = { showSort = false },
            containerColor = colors.surfaceElevated,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text("Sort results", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
                Text("Ranking is calculated from real Blink data.", fontSize = 10.sp, color = colors.textMuted)
                Spacer(Modifier.height(12.dp))
                DiscoverySort.entries.forEach { sort ->
                    val requiresLocation = sort == DiscoverySort.DISTANCE
                    val enabled = !requiresLocation || state.latitude != null
                    Surface(
                        onClick = {
                            if (enabled) {
                                viewModel.setSort(sort)
                                showSort = false
                            } else enableNearMe()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (sort == state.sort) colors.primaryBright.copy(alpha = 0.10f) else colors.surfaceElevated,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (sort == DiscoverySort.GROWING || sort == DiscoverySort.TRENDING) Icons.Rounded.TrendingUp else if (requiresLocation) Icons.Rounded.LocationOn else Icons.Rounded.Sort,
                                null,
                                modifier = Modifier.size(18.dp),
                                tint = if (sort == state.sort) colors.primaryBright else colors.textSecondary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(sort.label, modifier = Modifier.weight(1f), fontSize = 13.sp, color = if (enabled) colors.textPrimary else colors.textMuted)
                            if (sort == state.sort) Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(17.dp), tint = colors.primaryBright)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
            containerColor = colors.surfaceElevated,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text("Search preferences", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
                Text("Control privacy, discovery scope and preview behavior.", fontSize = 10.sp, color = colors.textMuted)
                Spacer(Modifier.height(14.dp))
                PreferenceSwitchRow("Following only", "Only people and content connected to accounts you follow", state.followingOnly, viewModel::toggleFollowingOnly)
                PreferenceSwitchRow("Saved only", "Search your bookmarks and marketplace wishlist", state.savedOnly, viewModel::toggleSavedOnly)
                PreferenceSwitchRow("Private search", "Do not sync new searches to your account history", state.privateHistory) { viewModel.setPrivateHistory(it) }
                PreferenceSwitchRow("Autoplay reel previews", "Muted previews play only while their card is visible", state.autoplayPreviews) { viewModel.toggleAutoplay() }
                if (state.capabilities.distanceSort) {
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = ::enableNearMe,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    ) {
                        Icon(Icons.Rounded.LocationOn, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(if (state.latitude == null) "Use approximate location" else "Refresh approximate location")
                    }
                    Text(
                        "Search uses this device location ephemerally for distance ranking; it is not saved to your profile.",
                        modifier = Modifier.padding(top = 7.dp),
                        fontSize = 9.sp,
                        color = colors.textMuted,
                    )
                }
                Spacer(Modifier.height(22.dp))
            }
        }
    }

    detailResult?.let { result ->
        ModalBottomSheet(
            onDismissRequest = { detailResult = null },
            containerColor = colors.surfaceElevated,
        ) {
            SearchEntityDetail(
                result = state.results.firstOrNull { it.type == result.type && it.id == result.id } ?: result,
                onToggle = { viewModel.toggleEntity(result) },
                onOpenSeller = {
                    result.username.takeIf(String::isNotBlank)?.let(onProfileClick)
                    detailResult = null
                },
            )
        }
    }

    if (showImageInfo) {
        ModalBottomSheet(
            onDismissRequest = { showImageInfo = false },
            containerColor = colors.surfaceElevated,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                pickedImage?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = "Selected search image",
                        modifier = Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.height(14.dp))
                }
                Text("Visual search", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
                Spacer(Modifier.height(5.dp))
                Text(
                    if (state.capabilities.imageSimilarity)
                        "Blink visual search is ready to compare this image."
                    else
                        "The secure image-similarity backend contract is installed, but Blink has no production visual-embedding model yet. This image was not uploaded or indexed.",
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.primaryBright.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, colors.primaryBright.copy(alpha = 0.20f)),
                ) {
                    Text(
                        "Capability-gated: no fake visual matches",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primaryBright,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PremiumUniversalSearchField(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onVoice: () -> Unit,
    onImage: () -> Unit,
    onFilters: () -> Unit,
    activeFilters: Int,
) {
    val colors = BlinkThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        color = colors.input,
        border = BorderStroke(1.dp, if (query.isNotBlank()) colors.primaryBright.copy(alpha = 0.45f) else colors.borderSoft),
        shadowElevation = if (query.isNotBlank()) 6.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, modifier = Modifier.size(20.dp), tint = if (query.isNotBlank()) colors.primaryBright else colors.textMuted)
            Spacer(Modifier.width(9.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isBlank()) {
                    Text(placeholder, fontSize = 12.sp, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp, color = colors.textPrimary, fontWeight = FontWeight.Medium),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {}),
                )
            }
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.Close, "Clear search", modifier = Modifier.size(17.dp), tint = colors.textSecondary)
                }
            }
            IconButton(onClick = onVoice, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.Mic, "Voice search", modifier = Modifier.size(18.dp), tint = colors.textSecondary)
            }
            IconButton(onClick = onImage, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.PhotoCamera, "Image search", modifier = Modifier.size(18.dp), tint = colors.textSecondary)
            }
            Box {
                IconButton(onClick = onFilters, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.FilterList, "Search filters", modifier = Modifier.size(18.dp), tint = if (activeFilters > 0) colors.primaryBright else colors.textSecondary)
                }
                if (activeFilters > 0) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).size(15.dp),
                        shape = CircleShape,
                        color = colors.primaryBright,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(activeFilters.toString(), fontSize = 8.sp, fontWeight = FontWeight.Black, color = colors.background)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumCategoryChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val colors = BlinkThemeTokens.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.primaryBright.copy(alpha = 0.14f) else colors.surfaceElevated,
        border = BorderStroke(1.dp, if (selected) colors.primaryBright.copy(alpha = 0.36f) else colors.borderSoft),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = if (selected) colors.primaryBright else colors.textMuted)
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) colors.primaryBright else colors.textSecondary)
        }
    }
}

@Composable
private fun PremiumFilterChip(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val colors = BlinkThemeTokens.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(13.dp),
        color = if (active) colors.primary.copy(alpha = 0.11f) else colors.surface,
        border = BorderStroke(1.dp, if (active) colors.primaryBright.copy(alpha = 0.28f) else colors.borderSoft),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, modifier = Modifier.size(13.dp), tint = if (active) colors.primaryBright else colors.textMuted)
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = if (active) colors.primaryBright else colors.textSecondary)
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    val colors = BlinkThemeTokens.colors
    Column(modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 2.dp)) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
        Text(subtitle, fontSize = 9.sp, color = colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PremiumDiscoveryResultCard(
    result: DiscoveryResult,
    autoplay: Boolean,
    onClick: () -> Unit,
) {
    val colors = BlinkThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(19.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.borderSoft),
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ResultAvatar(result)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            result.title.ifBlank { result.type.label },
                            modifier = Modifier.weight(1f, fill = false),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (result.verified) {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.Verified, "Verified", modifier = Modifier.size(15.dp), tint = colors.primaryBright)
                        }
                    }
                    if (result.subtitle.isNotBlank()) {
                        Text(result.subtitle, fontSize = 10.sp, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                TypeBadge(result.type)
            }

            if (result.type == DiscoveryResultType.REEL && !result.videoUrl.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                SearchMutedAutoplayPreview(
                    videoUrl = result.videoUrl,
                    autoplay = autoplay,
                    matchedMomentMs = result.matchedMomentMs,
                )
            } else if (!result.imageUrl.isNullOrBlank() && result.type != DiscoveryResultType.PROFILE) {
                Spacer(Modifier.height(10.dp))
                AsyncImage(
                    model = result.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(168.dp).clip(RoundedCornerShape(15.dp)),
                    contentScale = ContentScale.Crop,
                )
            }

            if (result.body.isNotBlank() && result.type != DiscoveryResultType.REEL) {
                Spacer(Modifier.height(9.dp))
                Text(result.body, fontSize = 11.sp, lineHeight = 16.sp, color = colors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }

            Spacer(Modifier.height(9.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (result.reason.isNotBlank()) item { MetricPill(result.reason, Icons.Rounded.TrendingUp) }
                if (result.mutualCount > 0) item { MetricPill("${result.mutualCount} mutual", Icons.Rounded.Groups) }
                result.distanceKm?.takeIf { it.isFinite() }?.let { distance -> item { MetricPill(formatDistance(distance), Icons.Rounded.LocationOn) } }
                if (abs(result.trendPercent) >= 0.1) item { MetricPill(formatTrend(result.trendPercent), Icons.Rounded.TrendingUp) }
                result.matchedMomentMs?.let { ms -> item { MetricPill("Match ${formatMoment(ms)}", Icons.Rounded.PlayArrow) } }
                if (result.saved) item { MetricPill("Saved", Icons.Rounded.Bookmark) }
                if (result.following) item { MetricPill("Connected", Icons.Rounded.CheckCircle) }
                if (result.memberCount > 0) item { MetricPill(compactCount(result.memberCount) + " members", Icons.Rounded.Groups) }
                if (result.attendeeCount > 0) item { MetricPill(compactCount(result.attendeeCount) + " going", Icons.Rounded.Event) }
                if (result.followerCount > 0) item { MetricPill(compactCount(result.followerCount) + " followers", Icons.Rounded.Person) }
                result.price?.let { price -> item { MetricPill(formatPrice(price, result.currency), Icons.Rounded.Storefront) } }
            }
        }
    }
}

@Composable
private fun ResultAvatar(result: DiscoveryResult) {
    val colors = BlinkThemeTokens.colors
    val model = result.avatarUrl ?: if (result.type == DiscoveryResultType.PROFILE) result.imageUrl else null
    if (!model.isNullOrBlank()) {
        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = Modifier.size(46.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Surface(modifier = Modifier.size(46.dp), shape = CircleShape, color = colors.primaryBright.copy(alpha = 0.12f)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(iconForType(result.type), null, modifier = Modifier.size(21.dp), tint = colors.primaryBright)
            }
        }
    }
}

@Composable
private fun TypeBadge(type: DiscoveryResultType) {
    val colors = BlinkThemeTokens.colors
    Surface(shape = RoundedCornerShape(10.dp), color = colors.surfaceHighest, border = BorderStroke(1.dp, colors.borderSoft)) {
        Text(type.label, modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = colors.textMuted)
    }
}

@Composable
private fun MetricPill(label: String, icon: ImageVector) {
    val colors = BlinkThemeTokens.colors
    Surface(shape = RoundedCornerShape(10.dp), color = colors.surfaceHighest) {
        Row(modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(11.dp), tint = colors.primaryBright)
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun SearchResultSkeleton() {
    val colors = BlinkThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth().height(92.dp),
        shape = RoundedCornerShape(19.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.borderSoft),
    ) {
        Row(modifier = Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(46.dp), shape = CircleShape, color = colors.surfaceHighest) {}
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(modifier = Modifier.fillMaxWidth(0.52f).height(10.dp), shape = RoundedCornerShape(6.dp), color = colors.surfaceHighest) {}
                Surface(modifier = Modifier.fillMaxWidth(0.82f).height(8.dp), shape = RoundedCornerShape(6.dp), color = colors.surfaceHighest) {}
                Surface(modifier = Modifier.fillMaxWidth(0.38f).height(8.dp), shape = RoundedCornerShape(6.dp), color = colors.surfaceHighest) {}
            }
        }
    }
}

@Composable
private fun PremiumSearchEmpty(query: String, followingOnly: Boolean, savedOnly: Boolean) {
    val colors = BlinkThemeTokens.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 42.dp, horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(modifier = Modifier.size(58.dp), shape = CircleShape, color = colors.primaryBright.copy(alpha = 0.10f)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Search, null, modifier = Modifier.size(26.dp), tint = colors.primaryBright)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("No results yet", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                followingOnly -> "Try turning off Following only or use a broader phrase."
                savedOnly -> "Nothing saved matches this search yet."
                query.isNotBlank() -> "Check the spelling, use fewer words, or switch categories."
                else -> "Blink discovery will appear here as real content becomes available."
            },
            fontSize = 10.sp,
            color = colors.textMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun PreferenceSwitchRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    val colors = BlinkThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Text(subtitle, fontSize = 9.sp, color = colors.textMuted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SearchEntityDetail(result: DiscoveryResult, onToggle: () -> Unit, onOpenSeller: () -> Unit) {
    val colors = BlinkThemeTokens.colors
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        result.imageUrl?.takeIf(String::isNotBlank)?.let { image ->
            AsyncImage(
                model = image,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(14.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ResultAvatar(result)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(result.title, fontSize = 20.sp, fontWeight = FontWeight.Black, color = colors.textPrimary)
                Text(result.subtitle.ifBlank { result.type.label }, fontSize = 10.sp, color = colors.textSecondary)
            }
        }
        if (result.body.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(result.body, fontSize = 12.sp, lineHeight = 18.sp, color = colors.textSecondary)
        }
        Spacer(Modifier.height(14.dp))
        if (result.reason.isNotBlank()) {
            Surface(shape = RoundedCornerShape(12.dp), color = colors.primaryBright.copy(alpha = 0.10f)) {
                Text(result.reason, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = colors.primaryBright)
            }
        }
        Spacer(Modifier.height(16.dp))
        when (result.type) {
            DiscoveryResultType.COMMUNITY -> PrimaryDetailButton(if (result.following) "Leave community" else "Join community", onToggle)
            DiscoveryResultType.EVENT -> PrimaryDetailButton(if (result.following) "Cancel attendance" else "I'm going", onToggle)
            DiscoveryResultType.PAGE -> PrimaryDetailButton(if (result.following) "Following" else "Follow page", onToggle)
            DiscoveryResultType.MARKET_ITEM -> if (result.username.isNotBlank()) PrimaryDetailButton("View seller", onOpenSeller)
            else -> Unit
        }
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun PrimaryDetailButton(label: String, onClick: () -> Unit) {
    val colors = BlinkThemeTokens.colors
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SearchHeroOverlay(result: DiscoveryResult) {
    val colors = BlinkThemeTokens.colors
    Box(
        modifier = Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.96f)).padding(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = colors.surfaceElevated,
            border = BorderStroke(1.dp, colors.primaryBright.copy(alpha = 0.24f)),
            shadowElevation = 18.dp,
        ) {
            Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ResultAvatar(result)
                Spacer(Modifier.height(10.dp))
                Text(result.title, fontSize = 18.sp, fontWeight = FontWeight.Black, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(result.type.label, fontSize = 9.sp, color = colors.primaryBright)
            }
        }
    }
}

private fun iconForType(type: DiscoveryResultType): ImageVector = when (type) {
    DiscoveryResultType.PROFILE -> Icons.Rounded.Person
    DiscoveryResultType.POST -> Icons.Rounded.Photo
    DiscoveryResultType.REEL -> Icons.Rounded.VideoLibrary
    DiscoveryResultType.COMMUNITY -> Icons.Rounded.Groups
    DiscoveryResultType.EVENT -> Icons.Rounded.Event
    DiscoveryResultType.PAGE -> Icons.Rounded.Storefront
    DiscoveryResultType.MARKET_ITEM -> Icons.Rounded.Storefront
}

private fun buildResultSubtitle(count: Int, selected: DiscoveryResultType?, sort: DiscoverySort): String {
    val scope = selected?.label ?: "all categories"
    return "$count loaded · $scope · ${sort.label.lowercase()} · cursor pagination"
}

private fun formatMoment(ms: Int): String {
    val totalSeconds = (ms.coerceAtLeast(0) / 1000)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatDistance(km: Double): String = when {
    km < 1 -> "${(km * 1000).toInt().coerceAtLeast(1)} m"
    km < 10 -> "%.1f km".format(km)
    else -> "%.0f km".format(km)
}

private fun formatTrend(value: Double): String = if (value >= 0) "+%.1f%% / 24h".format(value) else "%.1f%% / 24h".format(value)

private fun compactCount(value: Int): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0).removeSuffix(".0M") + if (value % 1_000_000 == 0) "M" else ""
    value >= 1_000 -> "%.1fK".format(value / 1_000.0).replace(".0K", "K")
    else -> value.toString()
}

private fun formatPrice(price: Long, currency: String): String {
    val symbol = when (currency.uppercase()) {
        "NGN" -> "₦"
        "USD" -> "$"
        "GBP" -> "£"
        "EUR" -> "€"
        else -> "$currency "
    }
    return symbol + "%,d".format(price)
}

@Suppress("MissingPermission")
private fun readEphemeralSearchLocation(context: Context, onResult: (Double?, Double?) -> Unit) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        onResult(null, null)
        return
    }
    LocationServices.getFusedLocationProviderClient(context)
        .lastLocation
        .addOnSuccessListener { location -> onResult(location?.latitude, location?.longitude) }
        .addOnFailureListener { onResult(null, null) }
}
