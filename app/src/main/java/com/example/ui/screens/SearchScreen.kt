package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.example.data.models.FeedPost
import com.example.data.models.VerificationBadge
import com.example.ui.components.FacultyBadge
import com.example.ui.components.VerifiedMark
import com.example.ui.theme.BlinkGold
import com.example.ui.theme.BlinkPink
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ============================================================================ */
/* ROUTING — every user action below resolves to one of these callbacks so a   */
/* NavController-backed parent can hook navigation up without touching this    */
/* file. See SearchRoutes at the bottom for suggested route strings.            */
/* ============================================================================ */

data class SearchScreenActions(
    val onNavigateBack: () -> Unit = {},
    val onNavigateToProfile: (String) -> Unit = {},
    val onNavigateToPostDetail: (FeedPost) -> Unit = {},
    val onNavigateToComments: (FeedPost) -> Unit = {},
    val onNavigateToTagResults: (String) -> Unit = {},
    val onNavigateToFacultyResults: (String) -> Unit = {},
    val onNavigateToPeopleResults: (String) -> Unit = {},
    val onNavigateToVoiceSearch: () -> Unit = {},
    val onNavigateToCreatePost: () -> Unit = {},
    val onNavigateToShareSheet: (FeedPost) -> Unit = {},
    val onNavigateToReportPost: (FeedPost) -> Unit = {},
    val onNavigateToSavedPosts: () -> Unit = {},
    val onNavigateToFilterSettings: () -> Unit = {},
    val onAnalyticsEvent: (String, Map<String, String>) -> Unit = { _, _ -> }
)

enum class SearchCategory(val label: String) {
    ALL("All"), PEOPLE("People"), POSTS("Posts"), TAGS("Tags"), FACULTIES("Faculties")
}

enum class SortOption(val label: String) {
    RELEVANT("Most relevant"), RECENT("Most recent"), POPULAR("Most popular")
}

enum class ResultLayout { GRID, LIST }

data class RecentSearch(val query: String, val timestamp: Long = System.currentTimeMillis())

/* ============================================================================ */
/* SEARCH SCREEN                                                                */
/* ============================================================================ */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    posts: List<FeedPost>,
    isDark: Boolean,
    isServerConnected: Boolean = true,
    isLoadingMore: Boolean = false,
    onProfileClick: (String) -> Unit = {},
    onPostClick: (FeedPost) -> Unit = {},
    actions: SearchScreenActions = SearchScreenActions(
        onNavigateToProfile = onProfileClick,
        onNavigateToPostDetail = onPostClick
    ),
    onLoadMore: () -> Unit = {},
    onRefresh: () -> Unit = {}
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedTag by rememberSaveable { mutableStateOf("All") }
    var selectedCategory by rememberSaveable { mutableStateOf(SearchCategory.ALL) }
    var selectedFaculty by rememberSaveable { mutableStateOf<String?>(null) }
    var sortOption by remember { mutableStateOf(SortOption.RELEVANT) }
    var layout by remember { mutableStateOf(ResultLayout.GRID) }
    var isSearchFocused by remember { mutableStateOf(false) }
    var isDebouncing by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var likedPostIds by remember { mutableStateOf(setOf<String>()) }
    var bookmarkedPostIds by remember { mutableStateOf(setOf<String>()) }
    var burstHeartPostId by remember { mutableStateOf<String?>(null) }
    var contextMenuPost by remember { mutableStateOf<FeedPost?>(null) }

    var recentSearches by rememberSaveable {
        mutableStateOf(listOf("UNILAG fresher week", "#TechLagos", "Faculty of Law moot court"))
    }

    val trendingTags = listOf(
        "All", "#UNILAG", "#AlutaMarket", "#CampusGala2026",
        "#TechLagos", "#SIMME", "#MootCourt", "#DesignWeek"
    )
    val facultyFilters = listOf("SIMME", "ENGINEERING", "LAW", "ARTS", "SCIENCE", "MEDICINE")

    // Debounced, multi-field, multi-filter search — feature: debounce, feature: multi-filter
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            isDebouncing = true
            delay(300)
            isDebouncing = false
        }
    }

    val filteredPosts = remember(searchQuery, selectedTag, selectedFaculty, selectedCategory, sortOption, posts) {
        posts.filter { post ->
            val matchQuery = searchQuery.isBlank() ||
                post.text.contains(searchQuery, ignoreCase = true) ||
                post.author.contains(searchQuery, ignoreCase = true) ||
                post.facultyTag.contains(searchQuery, ignoreCase = true)
            val matchTag = selectedTag == "All" ||
                post.text.contains(selectedTag.replace("#", ""), ignoreCase = true) ||
                post.facultyTag.equals(selectedTag, ignoreCase = true)
            val matchFaculty = selectedFaculty == null ||
                post.facultyTag.equals(selectedFaculty, ignoreCase = true)
            matchQuery && matchTag && matchFaculty
        }.let { list ->
            when (sortOption) {
                SortOption.RELEVANT -> list
                SortOption.RECENT -> list.reversed()
                SortOption.POPULAR -> list.sortedByDescending { it.likes }
            }
        }
    }

    val autocompleteSuggestions = remember(searchQuery, posts) {
        if (searchQuery.isBlank()) emptyList()
        else posts.map { it.author }.distinct()
            .filter { it.contains(searchQuery, ignoreCase = true) }
            .take(5)
    }

    val activeFilterCount = listOf(
        selectedTag != "All",
        selectedFaculty != null,
        selectedCategory != SearchCategory.ALL,
        sortOption != SortOption.RELEVANT
    ).count { it }

    val gridState = rememberLazyGridState()
    val showScrollToTop by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 6 }
    }
    // Feature: infinite scroll trigger
    LaunchedEffect(gridState, filteredPosts.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisible ->
                if (lastVisible != null && lastVisible >= filteredPosts.size - 4 && filteredPosts.isNotEmpty()) {
                    onLoadMore()
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 44.dp)
                .testTag("search_screen")
        ) {

            // Feature: offline banner, slides in/out
            AnimatedVisibility(
                visible = !isServerConnected,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut()
            ) {
                OfflineBanner(onRetry = onRefresh)
            }

            // ---- Search bar (feature: focus-expand, voice search, clear, debounce spinner) ----
            AnimatedSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                isDark = isDark,
                isFocused = isSearchFocused,
                isDebouncing = isDebouncing,
                onFocusChanged = { isSearchFocused = it },
                onSubmit = {
                    if (searchQuery.isNotBlank() && recentSearches.firstOrNull() != searchQuery) {
                        recentSearches = (listOf(searchQuery) + recentSearches).take(8)
                    }
                    isSearchFocused = false
                    actions.onAnalyticsEvent("search_submit", mapOf("query" to searchQuery))
                },
                onVoiceSearch = actions.onNavigateToVoiceSearch,
                onClear = { searchQuery = "" }
            )

            // ---- Recent searches + autocomplete overlay (feature: recent search history) ----
            AnimatedVisibility(
                visible = isSearchFocused && (recentSearches.isNotEmpty() || autocompleteSuggestions.isNotEmpty()),
                enter = expandVertically(tween(220)) + fadeIn(),
                exit = shrinkVertically(tween(180)) + fadeOut()
            ) {
                RecentAndSuggestionsPanel(
                    recentSearches = recentSearches,
                    suggestions = autocompleteSuggestions,
                    onSelectRecent = { searchQuery = it },
                    onRemoveRecent = { toRemove -> recentSearches = recentSearches.filterNot { it == toRemove } },
                    onClearAll = { recentSearches = emptyList() },
                    onSelectSuggestion = {
                        searchQuery = it
                        isSearchFocused = false
                        actions.onNavigateToPeopleResults(it)
                    }
                )
            }

            // ---- Category tabs (feature: People/Posts/Tags/Faculties routing) ----
            CategoryTabRow(
                selected = selectedCategory,
                onSelected = { category ->
                    selectedCategory = category
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    when (category) {
                        SearchCategory.PEOPLE -> actions.onNavigateToPeopleResults(searchQuery)
                        SearchCategory.FACULTIES -> Unit
                        else -> Unit
                    }
                }
            )

            // ---- Trending tags carousel (feature: bounce-select, routed) ----
            Text(
                text = "Trending now",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(trendingTags) { tag ->
                    BouncyTagChip(
                        text = tag,
                        selected = selectedTag == tag,
                        onClick = {
                            selectedTag = tag
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (tag != "All") actions.onNavigateToTagResults(tag)
                        }
                    )
                }
            }

            // ---- Faculty filter chips (feature: faculty filter, color-coded) ----
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(facultyFilters) { faculty ->
                    FacultyFilterChip(
                        text = faculty,
                        selected = selectedFaculty == faculty,
                        color = facultyColor(faculty),
                        onClick = {
                            selectedFaculty = if (selectedFaculty == faculty) null else faculty
                            if (selectedFaculty != null) actions.onNavigateToFacultyResults(faculty)
                        }
                    )
                }
            }

            // ---- Toolbar: result count, sort, layout toggle, clear filters ----
            ResultsToolbar(
                resultCount = filteredPosts.size,
                sortOption = sortOption,
                sortMenuExpanded = sortMenuExpanded,
                onSortMenuToggle = { sortMenuExpanded = it },
                onSortSelected = { sortOption = it },
                layout = layout,
                onLayoutToggle = {
                    layout = if (layout == ResultLayout.GRID) ResultLayout.LIST else ResultLayout.GRID
                },
                activeFilterCount = activeFilterCount,
                onClearFilters = {
                    selectedTag = "All"
                    selectedFaculty = null
                    selectedCategory = SearchCategory.ALL
                    sortOption = SortOption.RELEVANT
                },
                onOpenFilterSettings = actions.onNavigateToFilterSettings
            )

            // ---- Results (feature: grid/list cross-fade, staggered entrance, pull-to-refresh) ----
            AnimatedContent(
                targetState = layout,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "layoutSwitch",
                modifier = Modifier.weight(1f)
            ) { currentLayout ->

                when {
                    filteredPosts.isEmpty() -> NoResultsState(
                        hasActiveSearch = searchQuery.isNotBlank() || activeFilterCount > 0,
                        onClear = {
                            searchQuery = ""
                            selectedTag = "All"
                            selectedFaculty = null
                        },
                        onCreatePost = actions.onNavigateToCreatePost
                    )

                    currentLayout == ResultLayout.GRID -> LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(filteredPosts) { index, post ->
                            StaggeredGridEntrance(index = index) {
                                SearchResultCard(
                                    post = post,
                                    isLiked = post.id in likedPostIds,
                                    isBookmarked = post.id in bookmarkedPostIds,
                                    showHeartBurst = burstHeartPostId == post.id,
                                    onOpen = { actions.onNavigateToPostDetail(post) },
                                    onProfile = { actions.onNavigateToProfile(post.author) },
                                    onDoubleTapLike = {
                                        likedPostIds = likedPostIds + post.id
                                        burstHeartPostId = post.id
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        scope.launch {
                                            delay(650)
                                            burstHeartPostId = null
                                        }
                                    },
                                    onBookmarkToggle = {
                                        bookmarkedPostIds = if (post.id in bookmarkedPostIds) {
                                            bookmarkedPostIds - post.id
                                        } else {
                                            bookmarkedPostIds + post.id
                                        }
                                    },
                                    onLongPress = { contextMenuPost = post }
                                )
                            }
                        }

                        if (isLoadingMore) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                                LoadMoreSpinner()
                            }
                        }
                    }

                    else -> LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(filteredPosts) { index, post ->
                            StaggeredGridEntrance(index = index) {
                                SearchResultRow(
                                    post = post,
                                    isBookmarked = post.id in bookmarkedPostIds,
                                    onOpen = { actions.onNavigateToPostDetail(post) },
                                    onProfile = { actions.onNavigateToProfile(post.author) },
                                    onBookmarkToggle = {
                                        bookmarkedPostIds = if (post.id in bookmarkedPostIds) {
                                            bookmarkedPostIds - post.id
                                        } else {
                                            bookmarkedPostIds + post.id
                                        }
                                    },
                                    onLongPress = { contextMenuPost = post }
                                )
                            }
                        }

                        if (isLoadingMore) {
                            item { LoadMoreSpinner() }
                        }
                    }
                }
            }
        }

        // ---- Scroll-to-top FAB (feature) ----
        AnimatedVisibility(
            visible = showScrollToTop && layout == ResultLayout.GRID,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp),
            enter = scaleIn(spring(dampingRatio = 0.6f)) + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            SmallFloatingActionButton(
                onClick = { scope.launch { gridState.animateScrollToItem(0) } },
                containerColor = BlinkPink,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to top")
            }
        }

        // ---- Long-press context menu (feature: view profile / save / share / report) ----
        contextMenuPost?.let { post ->
            PostContextMenu(
                post = post,
                isBookmarked = post.id in bookmarkedPostIds,
                onDismiss = { contextMenuPost = null },
                onViewProfile = { actions.onNavigateToProfile(post.author); contextMenuPost = null },
                onSave = {
                    bookmarkedPostIds = bookmarkedPostIds + post.id
                    actions.onNavigateToSavedPosts()
                    contextMenuPost = null
                },
                onShare = { actions.onNavigateToShareSheet(post); contextMenuPost = null },
                onReport = { actions.onNavigateToReportPost(post); contextMenuPost = null },
                onComment = { actions.onNavigateToComments(post); contextMenuPost = null }
            )
        }
    }
}

/* ============================================================================ */
/* SEARCH BAR                                                                    */
/* ============================================================================ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimatedSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isDark: Boolean,
    isFocused: Boolean,
    isDebouncing: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onVoiceSearch: () -> Unit,
    onClear: () -> Unit
) {
    val elevation by animateDpAsState(
        targetValue = if (isFocused) 4.dp else 0.dp,
        animationSpec = tween(200),
        label = "searchBarElevation"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) BlinkPink else Color.Transparent,
        animationSpec = tween(200),
        label = "searchBarBorder"
    )

    Surface(
        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFEFEFF4),
        shape = RoundedCornerShape(100.dp),
        shadowElevation = elevation,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = if (isFocused) BlinkPink else MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        "Search students, posts, #tags, faculties...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.5.sp
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_text_input")
                    .onFocusChanged { onFocusChanged(it.isFocused) }
            )

            AnimatedVisibility(visible = isDebouncing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(16.dp)
                        .padding(end = 6.dp),
                    strokeWidth = 2.dp,
                    color = BlinkPink
                )
            }

            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = query.isEmpty()) {
                IconButton(onClick = onVoiceSearch) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/* ============================================================================ */
/* RECENT + AUTOCOMPLETE PANEL                                                  */
/* ============================================================================ */

@Composable
private fun RecentAndSuggestionsPanel(
    recentSearches: List<String>,
    suggestions: List<String>,
    onSelectRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearAll: () -> Unit,
    onSelectSuggestion: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {

        if (suggestions.isNotEmpty()) {
            Text(
                text = "Suggestions",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            suggestions.forEach { suggestion ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSuggestion(suggestion) }
                        .padding(vertical = 8.dp)
                ) {
                    Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(suggestion, fontSize = 13.sp)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (recentSearches.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Clear all",
                    fontSize = 11.sp,
                    color = BlinkPink,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onClearAll() }
                )
            }
            recentSearches.forEach { recent ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = recent,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectRecent(recent) }
                    )
                    IconButton(onClick = { onRemoveRecent(recent) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

/* ============================================================================ */
/* CATEGORY TABS                                                                 */
/* ============================================================================ */

@Composable
private fun CategoryTabRow(
    selected: SearchCategory,
    onSelected: (SearchCategory) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(SearchCategory.entries.toList()) { category ->
            val isSelected = category == selected
            val bg by animateColorAsState(
                targetValue = if (isSelected) BlinkPink else Color.Transparent,
                animationSpec = tween(180),
                label = "categoryBg"
            )
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = bg,
                border = if (!isSelected) androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.outlineVariant
                ) else null,
                modifier = Modifier.clickable { onSelected(category) }
            ) {
                Text(
                    text = category.label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/* ============================================================================ */
/* TAG + FACULTY CHIPS                                                           */
/* ============================================================================ */

@Composable
private fun BouncyTagChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "tagChipScale"
    )
    val bg by animateColorAsState(
        targetValue = if (selected) BlinkPink else Color(0xFFECEFF1),
        animationSpec = tween(180),
        label = "tagChipBg"
    )

    Surface(
        shape = RoundedCornerShape(100.dp),
        color = bg,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else Color.Black,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun FacultyFilterChip(text: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) color.copy(alpha = 0.18f) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) color else Color.Gray.copy(alpha = 0.3f)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) color else Color.Gray,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

private fun facultyColor(faculty: String): Color = when (faculty) {
    "SIMME" -> Color(0xFF5E60CE)
    "ENGINEERING" -> Color(0xFFEF6C00)
    "LAW" -> Color(0xFF2E7D32)
    "ARTS" -> Color(0xFFAD1457)
    "SCIENCE" -> Color(0xFF0277BD)
    "MEDICINE" -> Color(0xFFC62828)
    else -> Color.Gray
}

/* ============================================================================ */
/* TOOLBAR: sort, layout toggle, filter count, clear                            */
/* ============================================================================ */

@Composable
private fun ResultsToolbar(
    resultCount: Int,
    sortOption: SortOption,
    sortMenuExpanded: Boolean,
    onSortMenuToggle: (Boolean) -> Unit,
    onSortSelected: (SortOption) -> Unit,
    layout: ResultLayout,
    onLayoutToggle: () -> Unit,
    activeFilterCount: Int,
    onClearFilters: () -> Unit,
    onOpenFilterSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$resultCount result${if (resultCount == 1) "" else "s"}",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )

        AnimatedVisibility(visible = activeFilterCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Clear filters ($activeFilterCount)",
                    fontSize = 11.sp,
                    color = BlinkPink,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onClearFilters() }
                        .padding(end = 10.dp)
                )
            }
        }

        Box {
            IconButton(onClick = { onSortMenuToggle(true) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Sort, contentDescription = "Sort", modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { onSortMenuToggle(false) }) {
                SortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { onSortSelected(option); onSortMenuToggle(false) },
                        trailingIcon = {
                            if (option == sortOption) Icon(Icons.Default.Check, contentDescription = null)
                        }
                    )
                }
            }
        }

        IconButton(onClick = onLayoutToggle, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = if (layout == ResultLayout.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                contentDescription = "Toggle layout",
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(onClick = onOpenFilterSettings, modifier = Modifier.size(32.dp)) {
            BadgedBox(badge = {
                if (activeFilterCount > 0) Badge(containerColor = BlinkPink) { Text("$activeFilterCount") }
            }) {
                Icon(Icons.Default.Tune, contentDescription = "Filter settings", modifier = Modifier.size(18.dp))
            }
        }
    }
}

/* ============================================================================ */
/* GRID / LIST ENTRANCE                                                          */
/* ============================================================================ */

@Composable
private fun StaggeredGridEntrance(index: Int, content: @Composable () -> Unit) {
    val visibleState = remember(index) {
        MutableTransitionState(false).apply { targetState = true }
    }
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(tween(300, delayMillis = (index.coerceAtMost(8)) * 35)) +
            scaleIn(
                initialScale = 0.92f,
                animationSpec = tween(300, delayMillis = (index.coerceAtMost(8)) * 35)
            )
    ) {
        content()
    }
}

/* ============================================================================ */
/* RESULT CARD (grid)                                                            */
/* ============================================================================ */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultCard(
    post: FeedPost,
    isLiked: Boolean,
    isBookmarked: Boolean,
    showHeartBurst: Boolean,
    onOpen: () -> Unit,
    onProfile: () -> Unit,
    onDoubleTapLike: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onLongPress: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "cardScale"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onOpen,
                onLongClick = onLongPress,
                onDoubleClick = onDoubleTapLike
            )
    ) {
        Column {
            Box {
                if (post.images.isNotEmpty()) {
                    FadeInImage(
                        model = post.images[0],
                        contentDescription = post.text,
                        height = 130.dp
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(BlinkPink.copy(alpha = 0.15f))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = post.text.take(60) + "...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Feature: bookmark quick-action overlay
                IconButton(
                    onClick = onBookmarkToggle,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Bookmark",
                        tint = if (isBookmarked) BlinkGold else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Feature: double-tap heart burst
                androidx.compose.animation.AnimatedVisibility(
                    visible = showHeartBurst,
                    enter = scaleIn(initialScale = 0.4f, animationSpec = spring(dampingRatio = 0.4f)) + fadeIn(),
                    exit = fadeOut(tween(200)),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable { onProfile() }
                ) {
                    AsyncImage(
                        model = post.authorAvatar,
                        contentDescription = post.author,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(20.dp).clip(CircleShape)
                    )
                    Text(
                        text = post.author,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    AnimatedVerifiedBadge(post = post)
                }

                Spacer(modifier = Modifier.height(6.dp))
                FacultyBadge(tag = post.facultyTag)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultRow(
    post: FeedPost,
    isBookmarked: Boolean,
    onOpen: () -> Unit,
    onProfile: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onLongPress: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (post.images.isNotEmpty()) {
                AsyncImage(
                    model = post.images[0],
                    contentDescription = post.text,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BlinkPink.copy(alpha = 0.15f))
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { onProfile() }
                ) {
                    Text(post.author, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    AnimatedVerifiedBadge(post = post)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = post.text,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                FacultyBadge(tag = post.facultyTag)
            }

            IconButton(onClick = onBookmarkToggle) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Bookmark",
                    tint = if (isBookmarked) BlinkGold else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AnimatedVerifiedBadge(post: FeedPost) {
    if (post.verificationBadge != VerificationBadge.NONE) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val scale by animateFloatAsState(
            targetValue = if (shown) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
            label = "verifiedBadgeScale"
        )
        Box(modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
            VerifiedMark(badge = post.verificationBadge, size = 11.dp)
        }
    } else if (post.isVerified) {
        VerifiedMark(badge = VerificationBadge.BLUE, size = 11.dp)
    }
}

/* ============================================================================ */
/* IMAGE with shimmer + fade-in                                                  */
/* ============================================================================ */

@Composable
private fun FadeInImage(model: Any?, contentDescription: String?, height: androidx.compose.ui.unit.Dp) {
    val painter = rememberAsyncImagePainter(model)
    val state = painter.state

    Box(modifier = Modifier.fillMaxWidth().height(height)) {
        if (state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Empty) {
            ShimmerPlaceholder(modifier = Modifier.fillMaxSize())
        }
        val alphaAnim by animateFloatAsState(
            targetValue = if (state is AsyncImagePainter.State.Success) 1f else 0f,
            animationSpec = tween(300),
            label = "imageFadeIn"
        )
        Image(
            painter = painter,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(alphaAnim)
        )
    }
}

@Composable
private fun ShimmerPlaceholder(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    val brush = Brush.linearGradient(
        colors = listOf(Color(0xFFE0E0E0), Color(0xFFF5F5F5), Color(0xFFE0E0E0)),
        start = Offset(translateAnim - 300f, 0f),
        end = Offset(translateAnim, 300f)
    )
    Box(modifier = modifier.background(brush))
}

/* ============================================================================ */
/* EMPTY / NO RESULTS STATE                                                      */
/* ============================================================================ */

@Composable
private fun NoResultsState(hasActiveSearch: Boolean, onClear: () -> Unit, onCreatePost: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "noResultsFloat")
    val floatY by infiniteTransition.animateFloat(
        initialValue = -4f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "noResultsFloatY"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (hasActiveSearch) Icons.Default.SearchOff else Icons.Default.Explore,
            contentDescription = null,
            tint = BlinkPink,
            modifier = Modifier.size(56.dp).graphicsLayer { translationY = floatY }
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (hasActiveSearch) "No matches found" else "Start exploring",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (hasActiveSearch) "Try a different keyword or clear your filters." else "Search for people, posts, tags, and faculties.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (hasActiveSearch) {
            OutlinedButton(onClick = onClear) { Text("Clear search") }
        } else {
            Button(onClick = onCreatePost) { Text("Create a post") }
        }
    }
}

/* ============================================================================ */
/* OFFLINE BANNER + LOAD MORE SPINNER                                            */
/* ============================================================================ */

@Composable
private fun OfflineBanner(onRetry: () -> Unit) {
    var retrying by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (retrying) 360f else 0f,
        animationSpec = tween(500),
        label = "retryRotation"
    )
    Surface(color = Color(0xFFB71C1C), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.WifiOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("You're offline", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Retry",
                tint = Color.White,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = rotation }
                    .clickable { retrying = true; onRetry() }
            )
        }
    }
}

@Composable
private fun LoadMoreSpinner() {
    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = BlinkPink)
    }
}

/* ============================================================================ */
/* CONTEXT MENU (long-press)                                                     */
/* ============================================================================ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostContextMenu(
    post: FeedPost,
    isBookmarked: Boolean,
    onDismiss: () -> Unit,
    onViewProfile: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit,
    onComment: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            ContextMenuRow(Icons.Outlined.Person, "View ${post.author}'s profile", onViewProfile)
            ContextMenuRow(
                if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                if (isBookmarked) "Saved" else "Save post",
                onSave
            )
            ContextMenuRow(Icons.Outlined.ChatBubbleOutline, "View comments", onComment)
            ContextMenuRow(Icons.Default.Share, "Share post", onShare)
            ContextMenuRow(Icons.Outlined.Flag, "Report post", onReport, tint = Color(0xFFC62828))
        }
    }
}

@Composable
private fun ContextMenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, tint: Color = Color.Unspecified) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, fontSize = 14.sp, color = tint)
    }
}

/* ============================================================================ */
/* SUGGESTED NAVIGATION ROUTES — wire these into your NavHost                    */
/* ============================================================================ */

object SearchRoutes {
    const val PROFILE = "profile/{userId}"
    const val POST_DETAIL = "post/{postId}"
    const val COMMENTS = "post/{postId}/comments"
    const val TAG_RESULTS = "search/tag/{tag}"
    const val FACULTY_RESULTS = "search/faculty/{faculty}"
    const val PEOPLE_RESULTS = "search/people/{query}"
    const val VOICE_SEARCH = "search/voice"
    const val CREATE_POST = "post/create"
    const val SHARE_SHEET = "share/{postId}"
    const val REPORT_POST = "report/post/{postId}"
    const val SAVED_POSTS = "profile/saved"
    const val FILTER_SETTINGS = "search/filters"

    fun profile(userId: String) = "profile/$userId"
    fun postDetail(postId: String) = "post/$postId"
    fun comments(postId: String) = "post/$postId/comments"
    fun tagResults(tag: String) = "search/tag/${tag.removePrefix("#")}"
    fun facultyResults(faculty: String) = "search/faculty/$faculty"
    fun peopleResults(query: String) = "search/people/$query"
    fun shareSheet(postId: String) = "share/$postId"
    fun reportPost(postId: String) = "report/post/$postId"
}