package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.MainActivity
import com.example.R
import com.example.data.local.rememberPersistentTextState
import com.example.data.models.FeedPost
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.sharing.ShareContentType
import com.example.sharing.ShareLinkManager
import com.example.ui.components.PostCard
import com.example.ui.components.VerifiedMark
import com.example.ui.theme.BlinkOnlineGreen
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.FeedBackground
import com.example.ui.theme.FeedBorder
import com.example.ui.theme.FeedTextPrimary
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class ProfessionalDiscoverSection(val label: String) {
    ALL("All"),
    PEOPLE("People"),
    POSTS("Posts"),
    REELS("Reels"),
    HASHTAGS("Hashtags")
}

private enum class ProfessionalDiscoverSort(val label: String) {
    RELEVANCE("Relevant"),
    NEWEST("Newest"),
    POPULAR("Popular")
}

private const val PROFESSIONAL_RECENT_SEARCHES_PREF = "blink_discover_recent_searches"
private const val PROFESSIONAL_RECENT_SEARCHES_KEY = "recent_queries"
private const val PROFESSIONAL_RECENT_SEPARATOR = "\u001F"

@Composable
internal fun ProfessionalSearchScreen(
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
    isDark: Boolean
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val dragOffset = remember { Animatable(0f) }
    val gestureScope = rememberCoroutineScope()
    val dismissThresholdPx = with(density) { 96.dp.toPx() }

    BackHandler { onBackToHome() }

    var query by rememberPersistentTextState(key = "com/example/ui/screens/SearchScreen.kt:query:1")
    var selectedSection by rememberSaveable { mutableStateOf(ProfessionalDiscoverSection.ALL) }
    var selectedSort by rememberSaveable { mutableStateOf(ProfessionalDiscoverSort.RELEVANCE) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var verifiedOnly by rememberSaveable { mutableStateOf(false) }
    var selectedUniversity by rememberSaveable { mutableStateOf("") }
    var selectedFaculty by rememberSaveable { mutableStateOf("") }
    var selectedLevel by rememberSaveable { mutableStateOf("") }

    val recentPrefs = remember {
        context.getSharedPreferences(PROFESSIONAL_RECENT_SEARCHES_PREF, Context.MODE_PRIVATE)
    }
    var recentSearches by remember {
        mutableStateOf(
            recentPrefs.getString(PROFESSIONAL_RECENT_SEARCHES_KEY, "")
                .orEmpty()
                .split(PROFESSIONAL_RECENT_SEPARATOR)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(8)
        )
    }

    fun saveRecent(raw: String) {
        val value = raw.trim()
        if (value.isBlank()) return
        recentSearches = (listOf(value) + recentSearches.filterNot { it.equals(value, true) }).take(8)
        recentPrefs.edit()
            .putString(PROFESSIONAL_RECENT_SEARCHES_KEY, recentSearches.joinToString(PROFESSIONAL_RECENT_SEPARATOR))
            .apply()
    }

    fun clearRecents() {
        recentSearches = emptyList()
        recentPrefs.edit().remove(PROFESSIONAL_RECENT_SEARCHES_KEY).apply()
    }

    fun openContent(post: FeedPost) {
        saveRecent(query)
        val routed = runCatching {
            val type = if (post.isReel) ShareContentType.REEL else ShareContentType.POST
            val appLink = ShareLinkManager.generateAppLink(type, post.id)
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(appLink),
                context,
                MainActivity::class.java
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        }.isSuccess
        if (!routed) onPostClick(post)
    }

    val rawClean = query.trim()
    val clean = rawClean.removePrefix("#").removePrefix("@").trim()

    LaunchedEffect(clean) { onSearchQueryChange(clean) }
    LaunchedEffect(rawClean.firstOrNull()) {
        when (rawClean.firstOrNull()) {
            '@' -> selectedSection = ProfessionalDiscoverSection.PEOPLE
            '#' -> selectedSection = ProfessionalDiscoverSection.HASHTAGS
        }
    }

    val realProfiles = remember(profiles) {
        profiles.filter { it.username.isNotBlank() }
            .distinctBy { it.id.ifBlank { it.username.lowercase() } }
    }

    val universities = remember(realProfiles, serverProfiles) {
        (realProfiles + serverProfiles)
            .map { it.university.trim() }
            .filter { it.isNotBlank() && !it.equals("null", true) }
            .distinctBy { it.lowercase() }
            .take(8)
    }
    val faculties = remember(realProfiles, serverProfiles) {
        (realProfiles + serverProfiles)
            .map { it.faculty.trim() }
            .filter { it.isNotBlank() && !it.equals("null", true) }
            .distinctBy { it.lowercase() }
            .take(8)
    }
    val levels = remember(realProfiles, serverProfiles) {
        (realProfiles + serverProfiles)
            .map { it.academicLevel.trim() }
            .filter { it.isNotBlank() && !it.equals("null", true) }
            .distinctBy { it.lowercase() }
            .take(8)
    }

    val basePeople = remember(realProfiles, serverProfiles, clean) {
        if (clean.isBlank()) realProfiles else serverProfiles
    }

    val people = remember(
        basePeople,
        clean,
        verifiedOnly,
        selectedUniversity,
        selectedFaculty,
        selectedLevel,
        selectedSort
    ) {
        basePeople
            .filter { person ->
                (!verifiedOnly || person.verificationBadge != VerificationBadge.NONE) &&
                    (selectedUniversity.isBlank() || person.university.equals(selectedUniversity, true)) &&
                    (selectedFaculty.isBlank() || person.faculty.equals(selectedFaculty, true)) &&
                    (selectedLevel.isBlank() || person.academicLevel.equals(selectedLevel, true))
            }
            .sortedWith(
                when (selectedSort) {
                    ProfessionalDiscoverSort.POPULAR -> compareByDescending<UserProfile> { it.followerCount }
                        .thenByDescending { it.points }
                    ProfessionalDiscoverSort.NEWEST -> compareByDescending<UserProfile> { it.joinedLabel }
                    ProfessionalDiscoverSort.RELEVANCE -> compareByDescending<UserProfile> {
                        professionalProfileRelevance(it, clean)
                    }.thenByDescending { it.onlineNow }.thenByDescending { it.points }
                }
            )
            .take(if (clean.isBlank()) 20 else 60)
    }

    val allSearchPosts = remember(posts, serverPosts, clean) {
        if (clean.isBlank()) posts.distinctBy { it.id }
        else serverPosts.distinctBy { it.id }
    }

    val filteredPosts = remember(
        allSearchPosts,
        realProfiles,
        clean,
        verifiedOnly,
        selectedUniversity,
        selectedFaculty,
        selectedLevel,
        selectedSort
    ) {
        val profilesByUsername = realProfiles.associateBy { it.username.lowercase() }
        allSearchPosts
            .filter { post ->
                val authorKey = post.authorUsername.ifBlank { post.author }.lowercase()
                val authorProfile = profilesByUsername[authorKey]
                val verifiedPass = !verifiedOnly || post.verificationBadge != VerificationBadge.NONE || post.isVerified
                val universityPass = selectedUniversity.isBlank() || authorProfile?.university.equals(selectedUniversity, true)
                val facultyPass = selectedFaculty.isBlank() ||
                    post.facultyTag.equals(selectedFaculty, true) ||
                    authorProfile?.faculty.equals(selectedFaculty, true)
                val levelPass = selectedLevel.isBlank() || authorProfile?.academicLevel.equals(selectedLevel, true)
                verifiedPass && universityPass && facultyPass && levelPass
            }
            .sortedWith(
                when (selectedSort) {
                    ProfessionalDiscoverSort.POPULAR -> compareByDescending<FeedPost> {
                        (it.likes * 2L) + it.viewsCount + (it.commentsCount * 3L) + (it.sharesCount * 4L)
                    }
                    ProfessionalDiscoverSort.NEWEST -> compareByDescending<FeedPost> { it.createdAt }
                    ProfessionalDiscoverSort.RELEVANCE -> compareByDescending<FeedPost> {
                        professionalPostRelevance(it, clean)
                    }.thenByDescending { it.createdAt }
                }
            )
    }

    val matchingPosts = remember(filteredPosts) { filteredPosts.filterNot { it.isReel }.take(60) }
    val matchingReels = remember(filteredPosts) { filteredPosts.filter { it.isReel }.take(60) }

    val hashtags = remember(posts, serverPosts, clean) {
        (posts + serverPosts)
            .distinctBy { it.id }
            .flatMap { it.tags }
            .map { it.trim().removePrefix("#").lowercase() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .filter { clean.isBlank() || it.key.contains(clean, true) }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(30)
    }

    val suggestions = remember(realProfiles, posts, clean) {
        if (clean.length < 2) emptyList()
        else {
            val peopleSuggestions = realProfiles
                .sortedByDescending { professionalProfileRelevance(it, clean) }
                .filter { professionalProfileRelevance(it, clean) > 0 }
                .take(4)
                .map { "@${it.username}" }
            val tagSuggestions = posts
                .flatMap { it.tags }
                .map { it.trim().removePrefix("#") }
                .filter { it.contains(clean, true) }
                .distinctBy { it.lowercase() }
                .take(4)
                .map { "#$it" }
            (peopleSuggestions + tagSuggestions).distinct().take(6)
        }
    }

    val hasAnyResults = people.isNotEmpty() || matchingPosts.isNotEmpty() || matchingReels.isNotEmpty() || hashtags.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FeedBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .blur(5.dp)
                .graphicsLayer { alpha = 0.30f },
            contentPadding = PaddingValues(top = 18.dp, bottom = 100.dp)
        ) {
            items(posts.filterNot { it.isReel }.take(3), key = { "discover_backdrop_${it.id}" }) { post ->
                PostCard(
                    post = post,
                    isDark = isDark,
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
                .fillMaxWidth(.97f)
                .fillMaxHeight()
                .offset { IntOffset(dragOffset.value.roundToInt(), 0) }
                .shadow(22.dp, RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp), clip = false)
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
                                if (dragOffset.value >= dismissThresholdPx) onBackToHome()
                                else dragOffset.animateTo(
                                    0f,
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
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
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBackToHome, modifier = Modifier.size(46.dp)) {
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
                                "Search people, posts, reels and hashtags",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { showFilters = !showFilters }) {
                            Text(if (showFilters) "Hide filters" else "Filters")
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        trailingIcon = if (query.isNotBlank()) {
                            {
                                IconButton(onClick = {
                                    query = ""
                                    selectedSection = ProfessionalDiscoverSection.ALL
                                    focusManager.clearFocus()
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        } else null,
                        placeholder = { Text("Search name, @username, post or #hashtag") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                saveRecent(query)
                                focusManager.clearFocus()
                            }
                        ),
                        shape = RoundedCornerShape(22.dp)
                    )

                    Spacer(Modifier.height(9.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ProfessionalDiscoverSection.entries, key = { it.name }) { section ->
                            FilterChip(
                                selected = selectedSection == section,
                                onClick = { selectedSection = section },
                                label = { Text(section.label) }
                            )
                        }
                    }

                    AnimatedVisibility(showFilters) {
                        Column(Modifier.padding(top = 6.dp)) {
                            Text("Sort", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                items(ProfessionalDiscoverSort.entries, key = { it.name }) { sort ->
                                    FilterChip(
                                        selected = selectedSort == sort,
                                        onClick = { selectedSort = sort },
                                        label = { Text(sort.label) }
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = verifiedOnly,
                                        onClick = { verifiedOnly = !verifiedOnly },
                                        label = { Text("Verified") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Verified, null, modifier = Modifier.size(14.dp))
                                        }
                                    )
                                }
                            }

                            if (universities.isNotEmpty()) {
                                ProfessionalFilterRow(
                                    title = "University",
                                    values = universities,
                                    selected = selectedUniversity,
                                    onSelect = { selectedUniversity = it }
                                )
                            }
                            if (faculties.isNotEmpty()) {
                                ProfessionalFilterRow(
                                    title = "Faculty",
                                    values = faculties,
                                    selected = selectedFaculty,
                                    onSelect = { selectedFaculty = it }
                                )
                            }
                            if (levels.isNotEmpty()) {
                                ProfessionalFilterRow(
                                    title = "Level",
                                    values = levels,
                                    selected = selectedLevel,
                                    onSelect = { selectedLevel = it }
                                )
                            }

                            if (verifiedOnly || selectedUniversity.isNotBlank() || selectedFaculty.isNotBlank() || selectedLevel.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        verifiedOnly = false
                                        selectedUniversity = ""
                                        selectedFaculty = ""
                                        selectedLevel = ""
                                    }
                                ) { Text("Reset filters") }
                            }
                        }
                    }
                }

                if (isSearching && clean.isNotBlank()) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 110.dp)
                ) {
                    if (rawClean.isBlank() && recentSearches.isNotEmpty()) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Recent searches", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                TextButton(onClick = ::clearRecents) { Text("Clear all") }
                            }
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(recentSearches, key = { it.lowercase() }) { recent ->
                                    AssistChip(
                                        onClick = {
                                            query = recent
                                            saveRecent(recent)
                                        },
                                        label = { Text(recent, maxLines = 1) },
                                        leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(15.dp)) }
                                    )
                                }
                            }
                        }
                    }

                    if (rawClean.isNotBlank() && suggestions.isNotEmpty() && !isSearching) {
                        item { ProfessionalSectionHeader("Suggestions") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(suggestions, key = { it.lowercase() }) { suggestion ->
                                    AssistChip(
                                        onClick = {
                                            query = suggestion
                                            saveRecent(suggestion)
                                        },
                                        label = { Text(suggestion) }
                                    )
                                }
                            }
                        }
                    }

                    if ((selectedSection == ProfessionalDiscoverSection.ALL || selectedSection == ProfessionalDiscoverSection.PEOPLE) && people.isNotEmpty()) {
                        item {
                            ProfessionalSectionHeader(
                                title = if (clean.isBlank()) "People to discover" else "People",
                                count = if (clean.isBlank()) null else people.size
                            )
                        }

                        if (clean.isBlank()) {
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(people, key = { it.id.ifBlank { it.username } }) { person ->
                                        ProfessionalPersonCard(person = person, onProfileClick = onProfileClick)
                                    }
                                }
                            }
                        } else {
                            items(people, key = { "person_${it.id.ifBlank { it.username }}" }) { person ->
                                ProfessionalPersonRow(person = person, onProfileClick = onProfileClick)
                            }
                        }
                    }

                    if ((selectedSection == ProfessionalDiscoverSection.ALL || selectedSection == ProfessionalDiscoverSection.HASHTAGS) && hashtags.isNotEmpty()) {
                        item {
                            ProfessionalSectionHeader(
                                title = if (clean.isBlank()) "Popular in your feed" else "Hashtags",
                                count = hashtags.size
                            )
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(hashtags, key = { it.key }) { tag ->
                                    AssistChip(
                                        onClick = {
                                            query = "#${tag.key}"
                                            selectedSection = ProfessionalDiscoverSection.HASHTAGS
                                            saveRecent("#${tag.key}")
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Tag, null, modifier = Modifier.size(15.dp))
                                        },
                                        label = { Text("#${tag.key}  ·  ${tag.value} posts") }
                                    )
                                }
                            }
                        }
                    }

                    if ((selectedSection == ProfessionalDiscoverSection.ALL || selectedSection == ProfessionalDiscoverSection.REELS) && matchingReels.isNotEmpty()) {
                        item { ProfessionalSectionHeader("Reels", matchingReels.size) }
                        items(matchingReels.chunked(2), key = { row -> row.joinToString("_") { it.id } }) { row ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                row.forEach { reel ->
                                    ProfessionalReelSearchTile(
                                        post = reel,
                                        modifier = Modifier.weight(1f),
                                        onClick = { openContent(reel) }
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }

                    if (selectedSection == ProfessionalDiscoverSection.ALL || selectedSection == ProfessionalDiscoverSection.POSTS) {
                        if (matchingPosts.isNotEmpty()) {
                            item {
                                ProfessionalSectionHeader(
                                    title = if (clean.isBlank()) "Latest posts" else "Posts",
                                    count = matchingPosts.size
                                )
                            }
                            items(matchingPosts, key = { "post_${it.id}" }) { post ->
                                var visible by remember(post.id) { mutableStateOf(false) }
                                LaunchedEffect(post.id) {
                                    delay(20)
                                    visible = true
                                }
                                AnimatedVisibility(
                                    visible = visible,
                                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 14 })
                                ) {
                                    Column {
                                        Row(
                                            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                if (post.isPinned) "Pinned result" else "Search result",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f)
                                            )
                                            TextButton(onClick = { openContent(post) }) {
                                                Text("Open post")
                                            }
                                        }
                                        PostCard(
                                            post = post,
                                            isDark = isDark,
                                            onLike = { onLikePost(post.id) },
                                            onComment = { onCommentPost(post.id) },
                                            onBookmark = { onBookmarkPost(post.id) },
                                            onShare = { onSharePost(post.id) },
                                            onOptionsClick = { onOptionsClick(post) },
                                            onProfileClick = onProfileClick,
                                            isAuthor = post.author.equals(currentUsername, true) ||
                                                post.authorUsername.equals(currentUsername, true),
                                            onDelete = { onDeletePost(post.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!isSearching && clean.isNotBlank() && !hasAnyResults) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Column(
                                    Modifier.padding(horizontal = 22.dp, vertical = 28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.Search, null, modifier = Modifier.size(34.dp))
                                    Spacer(Modifier.height(10.dp))
                                    Text("No results for “$query”", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        "Try a shorter name, @username, #hashtag, or remove one of your filters.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                    if (verifiedOnly || selectedUniversity.isNotBlank() || selectedFaculty.isNotBlank() || selectedLevel.isNotBlank()) {
                                        Spacer(Modifier.height(8.dp))
                                        TextButton(onClick = {
                                            verifiedOnly = false
                                            selectedUniversity = ""
                                            selectedFaculty = ""
                                            selectedLevel = ""
                                        }) { Text("Clear filters") }
                                    }
                                }
                            }
                        }
                    }

                    if (selectedSection == ProfessionalDiscoverSection.REELS && matchingReels.isEmpty() && clean.isBlank()) {
                        item { ProfessionalSimpleEmptyState("No reels are available yet.") }
                    }
                    if (selectedSection == ProfessionalDiscoverSection.HASHTAGS && hashtags.isEmpty() && clean.isBlank()) {
                        item { ProfessionalSimpleEmptyState("No hashtags are available yet.") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfessionalSectionHeader(title: String, count: Int? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (count != null) {
            Text(count.toString(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProfessionalFilterRow(
    title: String,
    values: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            FilterChip(
                selected = selected.isBlank(),
                onClick = { onSelect("") },
                label = { Text("All") }
            )
        }
        items(values, key = { it.lowercase() }) { value ->
            FilterChip(
                selected = selected.equals(value, true),
                onClick = { onSelect(if (selected.equals(value, true)) "" else value) },
                label = { Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

@Composable
private fun ProfessionalPersonCard(person: UserProfile, onProfileClick: (String) -> Unit) {
    Surface(
        modifier = Modifier.width(156.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
        onClick = { onProfileClick(person.username) }
    ) {
        Column(Modifier.padding(13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ProfessionalProfileAvatar(person, 58)
            Spacer(Modifier.height(8.dp))
            ProfessionalVerifiedName(person, 12)
            Text(
                "@${person.username}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            person.university.takeUnless { it.isBlank() || it.equals("null", true) }?.let {
                Text(it, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ProfessionalPersonRow(person: UserProfile, onProfileClick: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = { onProfileClick(person.username) }
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfessionalProfileAvatar(person, 52)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                ProfessionalVerifiedName(person, 14)
                Text("@${person.username}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val details = listOf(person.university, person.faculty, person.department, person.academicLevel)
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.equals("null", true) }
                    .take(3)
                    .joinToString(" · ")
                if (details.isNotBlank()) {
                    Text(
                        details,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (person.followerCount > 0) {
                    Text(
                        "${person.followerCount} followers",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text("View", color = BlinkPink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProfessionalProfileAvatar(person: UserProfile, size: Int) {
    Box {
        AsyncImage(
            model = person.avatarUrl,
            error = painterResource(R.drawable.ic_default_profile),
            fallback = painterResource(R.drawable.ic_default_profile),
            contentDescription = "${person.fullName.ifBlank { person.username }} profile photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(CircleShape)
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(14.dp)
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                .background(if (person.onlineNow) BlinkOnlineGreen else Color(0xFF8B5A2B), CircleShape)
        )
    }
}

@Composable
private fun ProfessionalVerifiedName(person: UserProfile, fontSize: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            person.fullName.ifBlank { person.username },
            fontWeight = FontWeight.Bold,
            fontSize = fontSize.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (person.verificationBadge != VerificationBadge.NONE) {
            Spacer(Modifier.width(3.dp))
            VerifiedMark(person.verificationBadge, size = (fontSize + 2).dp)
        }
    }
}

@Composable
private fun ProfessionalReelSearchTile(
    post: FeedPost,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.aspectRatio(0.78f),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onClick
    ) {
        Box(Modifier.fillMaxSize()) {
            val preview = post.images.firstOrNull()
            if (!preview.isNullOrBlank()) {
                AsyncImage(
                    model = preview,
                    contentDescription = "Reel by ${post.author}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text("REEL", fontWeight = FontWeight.Black, fontSize = 22.sp)
                }
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(
                        post.author,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (post.text.isNotBlank()) {
                        Text(post.text, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        "${post.viewsCount} views",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfessionalSimpleEmptyState(message: String) {
    Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun professionalProfileRelevance(profile: UserProfile, query: String): Int {
    if (query.isBlank()) return 0
    val clean = query.lowercase()
    val username = profile.username.lowercase()
    val fullName = profile.fullName.lowercase()
    return when {
        username == clean -> 100
        fullName == clean -> 95
        username.startsWith(clean) -> 90
        fullName.startsWith(clean) -> 85
        username.contains(clean) -> 75
        fullName.contains(clean) -> 70
        profile.university.contains(clean, true) -> 45
        profile.faculty.contains(clean, true) -> 40
        profile.department.contains(clean, true) -> 38
        profile.academicLevel.contains(clean, true) -> 30
        else -> 0
    }
}

private fun professionalPostRelevance(post: FeedPost, query: String): Int {
    if (query.isBlank()) return 0
    val clean = query.lowercase()
    val tags = post.tags.map { it.removePrefix("#").lowercase() }
    return when {
        post.authorUsername.equals(clean, true) -> 100
        post.author.equals(clean, true) -> 95
        tags.any { it == clean } -> 90
        post.text.startsWith(clean, true) -> 80
        post.text.contains(clean, true) -> 70
        tags.any { it.contains(clean, true) } -> 65
        post.facultyTag.contains(clean, true) -> 45
        post.category.contains(clean, true) -> 40
        else -> 0
    }
}
