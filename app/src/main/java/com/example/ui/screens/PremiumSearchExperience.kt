package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.models.FeedPost
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.ui.components.BlinkCard
import com.example.ui.components.BlinkChip
import com.example.ui.components.BlinkEmptyState
import com.example.ui.components.BlinkSkeleton
import com.example.ui.components.PostCard
import com.example.ui.components.VerifiedMark
import com.example.ui.theme.BlinkThemeTokens
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.min

private enum class PremiumSearchCategory(val label: String, val icon: ImageVector) {
    ALL("All", Icons.Rounded.Search),
    PEOPLE("People", Icons.Rounded.Person),
    POSTS("Posts", Icons.Rounded.Photo),
    REELS("Reels", Icons.Rounded.VideoLibrary),
    HASHTAGS("Hashtags", Icons.Rounded.Tag),
    CAMPUS("Campus", Icons.Rounded.School),
}

private enum class PremiumSearchSort(val label: String) {
    RELEVANT("Most relevant"),
    RECENT("Most recent"),
    VIEWED("Most viewed"),
    LIKED("Most liked"),
    COMMENTED("Most commented"),
    SHARED("Most shared"),
    TRENDING("Trending now"),
}

private enum class PremiumMediaFilter(val label: String) {
    ANY("Any media"), PHOTO("Photos"), VIDEO("Videos"), POLL("Polls"), LINK("Links")
}

private data class PremiumRecentSearch(
    val query: String,
    val timestamp: Long,
    val pinned: Boolean = false,
)

private sealed interface PremiumSuggestion {
    val value: String
    data class Person(val profile: UserProfile, override val value: String) : PremiumSuggestion
    data class Hashtag(val tag: String, val count: Int, override val value: String) : PremiumSuggestion
    data class Campus(val label: String, override val value: String) : PremiumSuggestion
}

private const val PREMIUM_SEARCH_PREFS = "blink_premium_search"
private const val PREMIUM_SEARCH_RECENTS = "recent_searches_v2"
private const val PREMIUM_SEARCH_PRIVATE = "private_history"
private const val PREMIUM_SEARCH_CATEGORY = "last_category"
private const val PREMIUM_SEARCH_SORT = "last_sort"
private const val PREMIUM_SEARCH_SEPARATOR = "\u001E"
private const val PREMIUM_SEARCH_FIELD_SEPARATOR = "\u001F"
private const val PREMIUM_SEARCH_EXPIRY_MS = 30L * 24L * 60L * 60L * 1000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PremiumSearchExperience(
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
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val colors = BlinkThemeTokens.colors
    val prefs = remember { context.getSharedPreferences(PREMIUM_SEARCH_PREFS, Context.MODE_PRIVATE) }

    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable {
        mutableStateOf(
            runCatching { PremiumSearchCategory.valueOf(prefs.getString(PREMIUM_SEARCH_CATEGORY, PremiumSearchCategory.ALL.name).orEmpty()) }
                .getOrDefault(PremiumSearchCategory.ALL)
        )
    }
    var sort by rememberSaveable {
        mutableStateOf(
            runCatching { PremiumSearchSort.valueOf(prefs.getString(PREMIUM_SEARCH_SORT, PremiumSearchSort.RELEVANT.name).orEmpty()) }
                .getOrDefault(PremiumSearchSort.RELEVANT)
        )
    }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showSort by rememberSaveable { mutableStateOf(false) }
    var showClearHistoryConfirm by rememberSaveable { mutableStateOf(false) }
    var recentsExpanded by rememberSaveable { mutableStateOf(true) }
    var privateHistory by rememberSaveable { mutableStateOf(prefs.getBoolean(PREMIUM_SEARCH_PRIVATE, false)) }
    var verifiedOnly by rememberSaveable { mutableStateOf(false) }
    var vipOnly by rememberSaveable { mutableStateOf(false) }
    var selectedUniversity by rememberSaveable { mutableStateOf("") }
    var selectedFaculty by rememberSaveable { mutableStateOf("") }
    var selectedDepartment by rememberSaveable { mutableStateOf("") }
    var selectedLevel by rememberSaveable { mutableStateOf("") }
    var mediaFilter by rememberSaveable { mutableStateOf(PremiumMediaFilter.ANY) }
    var placeholderIndex by rememberSaveable { mutableIntStateOf(0) }

    val placeholders = remember {
        listOf(
            "Search people on Blink",
            "Search @usernames",
            "Search #campus trends",
            "Search posts and reels",
            "Search faculty or department",
            "Search places on campus",
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2_800)
            placeholderIndex = (placeholderIndex + 1) % placeholders.size
        }
    }

    var recentSearches by remember {
        mutableStateOf(loadPremiumRecents(prefs))
    }

    fun persistRecents(next: List<PremiumRecentSearch>) {
        recentSearches = next
        prefs.edit().putString(PREMIUM_SEARCH_RECENTS, encodePremiumRecents(next)).apply()
    }

    fun saveRecent(raw: String) {
        val value = raw.trim()
        if (value.isBlank() || privateHistory) return
        val existing = recentSearches.firstOrNull { it.query.equals(value, true) }
        val next = listOf(PremiumRecentSearch(value, System.currentTimeMillis(), existing?.pinned == true)) +
            recentSearches.filterNot { it.query.equals(value, true) }
        persistRecents(next.sortedWith(compareByDescending<PremiumRecentSearch> { it.pinned }.thenByDescending { it.timestamp }).take(20))
    }

    fun removeRecent(value: String) {
        persistRecents(recentSearches.filterNot { it.query.equals(value, true) })
    }

    fun togglePinned(value: String) {
        persistRecents(
            recentSearches.map {
                if (it.query.equals(value, true)) it.copy(pinned = !it.pinned, timestamp = System.currentTimeMillis()) else it
            }.sortedWith(compareByDescending<PremiumRecentSearch> { it.pinned }.thenByDescending { it.timestamp })
        )
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty().trim()
            if (spoken.isNotBlank()) query = spoken
        }
    }

    fun launchVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Search Blink")
        }
        runCatching { voiceLauncher.launch(intent) }
    }

    val rawQuery = query.trim()
    val cleanQuery = rawQuery.removePrefix("@").removePrefix("#").trim()

    LaunchedEffect(cleanQuery) {
        delay(280)
        onSearchQueryChange(cleanQuery)
    }

    LaunchedEffect(rawQuery.firstOrNull()) {
        when (rawQuery.firstOrNull()) {
            '@' -> category = PremiumSearchCategory.PEOPLE
            '#' -> category = PremiumSearchCategory.HASHTAGS
        }
    }

    LaunchedEffect(category) {
        prefs.edit().putString(PREMIUM_SEARCH_CATEGORY, category.name).apply()
    }
    LaunchedEffect(sort) {
        prefs.edit().putString(PREMIUM_SEARCH_SORT, sort.name).apply()
    }

    val allProfiles = remember(profiles, serverProfiles) {
        (profiles + serverProfiles)
            .filter { it.username.isNotBlank() }
            .distinctBy { it.id.ifBlank { it.username.lowercase() } }
    }
    val allPosts = remember(posts, serverPosts) { (posts + serverPosts).distinctBy { it.id } }

    val universities = remember(allProfiles) { allProfiles.mapNotNull { cleanMeta(it.university) }.distinctBy { it.lowercase() }.sorted().take(15) }
    val faculties = remember(allProfiles) { allProfiles.mapNotNull { cleanMeta(it.faculty) }.distinctBy { it.lowercase() }.sorted().take(20) }
    val departments = remember(allProfiles) { allProfiles.mapNotNull { cleanMeta(it.department) }.distinctBy { it.lowercase() }.sorted().take(24) }
    val levels = remember(allProfiles) { allProfiles.mapNotNull { cleanMeta(it.academicLevel) }.distinctBy { it.lowercase() }.sorted().take(10) }

    val profilesByUsername = remember(allProfiles) { allProfiles.associateBy { it.username.lowercase() } }

    val filteredPeople = remember(
        allProfiles, cleanQuery, verifiedOnly, vipOnly, selectedUniversity, selectedFaculty, selectedDepartment, selectedLevel, sort
    ) {
        allProfiles
            .filter { person ->
                val relevance = premiumProfileRelevance(person, cleanQuery)
                (cleanQuery.isBlank() || relevance > 0) &&
                    (!verifiedOnly || person.verificationBadge != VerificationBadge.NONE) &&
                    (!vipOnly || person.isBlinkVip) &&
                    (selectedUniversity.isBlank() || person.university.equals(selectedUniversity, true)) &&
                    (selectedFaculty.isBlank() || person.faculty.equals(selectedFaculty, true)) &&
                    (selectedDepartment.isBlank() || person.department.equals(selectedDepartment, true)) &&
                    (selectedLevel.isBlank() || person.academicLevel.equals(selectedLevel, true))
            }
            .sortedWith(
                when (sort) {
                    PremiumSearchSort.RELEVANT -> compareByDescending<UserProfile> { premiumProfileRelevance(it, cleanQuery) }
                        .thenByDescending { it.onlineNow }.thenByDescending { it.followerCount }
                    PremiumSearchSort.RECENT -> compareByDescending { it.joinedLabel }
                    else -> compareByDescending<UserProfile> { it.followerCount }.thenByDescending { it.points }
                }
            )
            .take(80)
    }

    val filteredContent = remember(
        allPosts, cleanQuery, verifiedOnly, vipOnly, selectedUniversity, selectedFaculty, selectedDepartment, selectedLevel, mediaFilter, sort, profilesByUsername
    ) {
        allPosts
            .filter { post ->
                val relevance = premiumPostRelevance(post, cleanQuery)
                val author = profilesByUsername[post.authorUsername.ifBlank { post.author }.lowercase()]
                val mediaPass = when (mediaFilter) {
                    PremiumMediaFilter.ANY -> true
                    PremiumMediaFilter.PHOTO -> post.images.isNotEmpty() && !post.isReel
                    PremiumMediaFilter.VIDEO -> post.isReel || !post.videoUrl.isNullOrBlank()
                    PremiumMediaFilter.POLL -> post.poll != null
                    PremiumMediaFilter.LINK -> !post.linkUrl.isNullOrBlank()
                }
                (cleanQuery.isBlank() || relevance > 0) && mediaPass &&
                    (!verifiedOnly || post.verificationBadge != VerificationBadge.NONE || post.isVerified) &&
                    (!vipOnly || post.authorIsVip || author?.isBlinkVip == true) &&
                    (selectedUniversity.isBlank() || author?.university.equals(selectedUniversity, true)) &&
                    (selectedFaculty.isBlank() || post.facultyTag.equals(selectedFaculty, true) || author?.faculty.equals(selectedFaculty, true)) &&
                    (selectedDepartment.isBlank() || author?.department.equals(selectedDepartment, true)) &&
                    (selectedLevel.isBlank() || author?.academicLevel.equals(selectedLevel, true))
            }
            .sortedWith(
                when (sort) {
                    PremiumSearchSort.RELEVANT -> compareByDescending<FeedPost> { premiumPostRelevance(it, cleanQuery) }
                        .thenByDescending { premiumTrendScore(it) }
                    PremiumSearchSort.RECENT -> compareByDescending { it.createdAt }
                    PremiumSearchSort.VIEWED -> compareByDescending { it.viewsCount }
                    PremiumSearchSort.LIKED -> compareByDescending { it.likes }
                    PremiumSearchSort.COMMENTED -> compareByDescending { it.commentsCount }
                    PremiumSearchSort.SHARED -> compareByDescending { it.sharesCount }
                    PremiumSearchSort.TRENDING -> compareByDescending { premiumTrendScore(it) }
                }
            )
            .take(100)
    }

    val matchingPosts = remember(filteredContent) { filteredContent.filterNot { it.isReel } }
    val matchingReels = remember(filteredContent) { filteredContent.filter { it.isReel } }
    val hashtagCounts = remember(allPosts, cleanQuery) {
        allPosts.flatMap { it.tags }
            .map { it.trim().removePrefix("#").lowercase() }
            .filter { it.isNotBlank() && (cleanQuery.isBlank() || it.contains(cleanQuery, true)) }
            .groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(40)
    }

    val campusMatches = remember(allProfiles, allPosts, cleanQuery) {
        buildList {
            addAll(allProfiles.mapNotNull { cleanMeta(it.faculty) })
            addAll(allProfiles.mapNotNull { cleanMeta(it.department) })
            addAll(allProfiles.mapNotNull { cleanMeta(it.courseOfStudy) })
            addAll(allProfiles.mapNotNull { cleanMeta(it.currentCityState) })
            addAll(allPosts.mapNotNull { cleanMeta(it.location) })
        }.distinctBy { it.lowercase() }
            .filter { cleanQuery.isBlank() || it.contains(cleanQuery, true) }
            .take(40)
    }

    val suggestions = remember(allProfiles, hashtagCounts, campusMatches, cleanQuery) {
        if (cleanQuery.length < 1) emptyList() else buildList<PremiumSuggestion> {
            allProfiles.asSequence()
                .map { it to premiumProfileRelevance(it, cleanQuery) }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(5)
                .forEach { add(PremiumSuggestion.Person(it.first, "@${it.first.username}")) }
            hashtagCounts.take(4).forEach { add(PremiumSuggestion.Hashtag(it.key, it.value, "#${it.key}")) }
            campusMatches.filter { it.contains(cleanQuery, true) }.take(3).forEach { add(PremiumSuggestion.Campus(it, it)) }
        }.take(10)
    }

    val correction = remember(cleanQuery, allProfiles, allPosts, campusMatches) {
        premiumCorrection(cleanQuery, allProfiles, allPosts, campusMatches)
    }

    val trendingHashtags = remember(allPosts) {
        allPosts.flatMap { post -> post.tags.map { it.trim().removePrefix("#").lowercase() to premiumTrendScore(post) } }
            .filter { it.first.isNotBlank() }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, scores) -> scores.sum() }
            .entries.sortedByDescending { it.value }.take(8)
    }
    val trendingPosts = remember(allPosts) { allPosts.sortedByDescending(::premiumTrendScore).take(6) }
    val suggestedPeople = remember(allProfiles) { allProfiles.sortedWith(compareByDescending<UserProfile> { it.onlineNow }.thenByDescending { it.followerCount }).take(10) }

    val categoryCounts = remember(filteredPeople, matchingPosts, matchingReels, hashtagCounts, campusMatches) {
        mapOf(
            PremiumSearchCategory.PEOPLE to filteredPeople.size,
            PremiumSearchCategory.POSTS to matchingPosts.size,
            PremiumSearchCategory.REELS to matchingReels.size,
            PremiumSearchCategory.HASHTAGS to hashtagCounts.size,
            PremiumSearchCategory.CAMPUS to campusMatches.size,
        )
    }

    val activeFilterCount = listOf(
        verifiedOnly,
        vipOnly,
        selectedUniversity.isNotBlank(),
        selectedFaculty.isNotBlank(),
        selectedDepartment.isNotBlank(),
        selectedLevel.isNotBlank(),
        mediaFilter != PremiumMediaFilter.ANY,
    ).count { it }

    fun resetFilters() {
        verifiedOnly = false
        vipOnly = false
        selectedUniversity = ""
        selectedFaculty = ""
        selectedDepartment = ""
        selectedLevel = ""
        mediaFilter = PremiumMediaFilter.ANY
    }

    fun submitSearch(value: String = query) {
        query = value
        saveRecent(value)
        focusManager.clearFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackToHome) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Search Blink", fontSize = 24.sp, fontWeight = FontWeight.Black, color = colors.textPrimary)
                Text("People, posts, reels, hashtags and campus discovery", fontSize = 11.sp, color = colors.textSecondary)
            }
        }

        PremiumSearchBar(
            query = query,
            onQueryChange = { query = it },
            placeholder = placeholders[placeholderIndex],
            onSearch = { submitSearch() },
            onVoice = ::launchVoiceSearch,
            onClear = { query = ""; category = PremiumSearchCategory.ALL },
            onFilter = { showFilters = true },
            activeFilterCount = activeFilterCount,
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(PremiumSearchCategory.entries, key = { it.name }) { item ->
                val count = if (item == PremiumSearchCategory.ALL) null else categoryCounts[item]
                BlinkChip(
                    text = if (count != null && cleanQuery.isNotBlank()) "${item.label} $count" else item.label,
                    selected = category == item,
                    onClick = { category = item },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = { showSort = true },
                shape = RoundedCornerShape(14.dp),
                color = colors.surfaceElevated,
                border = BorderStroke(1.dp, colors.borderSoft),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Sort, null, modifier = Modifier.size(16.dp), tint = colors.primaryBright)
                    Spacer(Modifier.width(6.dp))
                    Text(sort.label, fontSize = 11.sp, color = colors.textSecondary)
                }
            }
            if (activeFilterCount > 0) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = ::resetFilters) { Text("Reset $activeFilterCount filters") }
            }
        }

        if (isSearching && cleanQuery.isNotBlank()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Searching Blink…", fontSize = 11.sp, color = colors.textSecondary)
            }
        }

        AnimatedContent(
            targetState = cleanQuery.isBlank(),
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
            label = "searchMode",
            modifier = Modifier.weight(1f),
        ) { isDiscoveryMode ->
            if (isDiscoveryMode) {
                PremiumDiscoveryHome(
                    recents = recentSearches,
                    expanded = recentsExpanded,
                    privateHistory = privateHistory,
                    trendingHashtags = trendingHashtags,
                    suggestedPeople = suggestedPeople,
                    trendingPosts = trendingPosts,
                    onToggleExpanded = { recentsExpanded = !recentsExpanded },
                    onTogglePrivateHistory = {
                        privateHistory = !privateHistory
                        prefs.edit().putBoolean(PREMIUM_SEARCH_PRIVATE, privateHistory).apply()
                    },
                    onRecent = { submitSearch(it) },
                    onRemoveRecent = ::removeRecent,
                    onPinRecent = ::togglePinned,
                    onClearHistory = { showClearHistoryConfirm = true },
                    onHashtag = { query = "#$it"; category = PremiumSearchCategory.HASHTAGS },
                    onProfileClick = onProfileClick,
                    onPostClick = onPostClick,
                )
            } else {
                PremiumSearchResults(
                    query = query,
                    cleanQuery = cleanQuery,
                    category = category,
                    suggestions = suggestions,
                    correction = correction,
                    people = filteredPeople,
                    posts = matchingPosts,
                    reels = matchingReels,
                    hashtags = hashtagCounts,
                    campusMatches = campusMatches,
                    isSearching = isSearching,
                    currentUsername = currentUsername,
                    isDark = isDark,
                    onSuggestion = { suggestion -> query = suggestion },
                    onSubmitSuggestion = { suggestion -> submitSearch(suggestion) },
                    onProfileClick = { saveRecent(query); onProfileClick(it) },
                    onPostClick = { saveRecent(query); onPostClick(it) },
                    onLikePost = onLikePost,
                    onCommentPost = onCommentPost,
                    onBookmarkPost = onBookmarkPost,
                    onSharePost = onSharePost,
                    onOptionsClick = onOptionsClick,
                    onDeletePost = onDeletePost,
                    onCorrection = { submitSearch(it) },
                )
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }, containerColor = colors.surfaceElevated) {
            PremiumFilterSheet(
                verifiedOnly = verifiedOnly,
                vipOnly = vipOnly,
                selectedUniversity = selectedUniversity,
                selectedFaculty = selectedFaculty,
                selectedDepartment = selectedDepartment,
                selectedLevel = selectedLevel,
                mediaFilter = mediaFilter,
                universities = universities,
                faculties = faculties,
                departments = departments,
                levels = levels,
                onVerified = { verifiedOnly = it },
                onVip = { vipOnly = it },
                onUniversity = { selectedUniversity = it },
                onFaculty = { selectedFaculty = it },
                onDepartment = { selectedDepartment = it },
                onLevel = { selectedLevel = it },
                onMedia = { mediaFilter = it },
                onReset = ::resetFilters,
                onDone = { showFilters = false },
            )
        }
    }

    if (showSort) {
        ModalBottomSheet(onDismissRequest = { showSort = false }, containerColor = colors.surfaceElevated) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Sort results", fontSize = 20.sp, fontWeight = FontWeight.Black, color = colors.textPrimary)
                Spacer(Modifier.height(10.dp))
                PremiumSearchSort.entries.forEach { option ->
                    Surface(
                        onClick = { sort = option; showSort = false },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = if (sort == option) colors.primary.copy(alpha = .15f) else colors.surface,
                    ) {
                        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(option.label, modifier = Modifier.weight(1f), color = colors.textPrimary)
                            if (sort == option) Icon(Icons.Rounded.Verified, null, tint = colors.primaryBright, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = { Text("Clear search history?") },
            text = { Text("This removes your recent searches stored on this device. Pinned searches will also be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    persistRecents(emptyList())
                    showClearHistoryConfirm = false
                }) { Text("Clear all") }
            },
            dismissButton = { TextButton(onClick = { showClearHistoryConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PremiumSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    onVoice: () -> Unit,
    onClear: () -> Unit,
    onFilter: () -> Unit,
    activeFilterCount: Int,
) {
    val colors = BlinkThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        shape = RoundedCornerShape(22.dp),
        color = colors.input,
        border = BorderStroke(1.dp, if (query.isNotBlank()) colors.primary.copy(alpha = .45f) else colors.borderSoft),
        shadowElevation = if (query.isNotBlank()) 4.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = if (query.isBlank()) colors.textMuted else colors.primaryBright)
            Spacer(Modifier.width(10.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).padding(vertical = 15.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primaryBright),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                decorationBox = { inner ->
                    Box {
                        if (query.isBlank()) Text(placeholder, color = colors.textMuted, style = MaterialTheme.typography.bodyMedium)
                        inner()
                    }
                }
            )
            if (query.isNotBlank()) {
                IconButton(onClick = onClear, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = colors.textSecondary, modifier = Modifier.size(19.dp))
                }
            } else {
                IconButton(onClick = onVoice, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.Mic, contentDescription = "Voice search", tint = colors.primaryBright, modifier = Modifier.size(20.dp))
                }
            }
            IconButton(onClick = onFilter, modifier = Modifier.size(42.dp)) {
                Box {
                    Icon(Icons.Rounded.FilterList, contentDescription = "Filters", tint = colors.textSecondary)
                    if (activeFilterCount > 0) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).size(15.dp),
                            shape = CircleShape,
                            color = colors.primary,
                        ) { Box(contentAlignment = Alignment.Center) { Text(activeFilterCount.toString(), fontSize = 8.sp, color = androidx.compose.ui.graphics.Color.White) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumDiscoveryHome(
    recents: List<PremiumRecentSearch>,
    expanded: Boolean,
    privateHistory: Boolean,
    trendingHashtags: List<Map.Entry<String, Long>>,
    suggestedPeople: List<UserProfile>,
    trendingPosts: List<FeedPost>,
    onToggleExpanded: () -> Unit,
    onTogglePrivateHistory: () -> Unit,
    onRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onPinRecent: (String) -> Unit,
    onClearHistory: () -> Unit,
    onHashtag: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    onPostClick: (FeedPost) -> Unit,
) {
    val colors = BlinkThemeTokens.colors
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Recent searches", fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text(if (privateHistory) "Private history is on" else "Stored on this device for 30 days", fontSize = 10.sp, color = colors.textMuted)
                }
                TextButton(onClick = onTogglePrivateHistory) { Text(if (privateHistory) "History off" else "Private") }
                if (recents.isNotEmpty()) TextButton(onClick = onClearHistory) { Text("Clear") }
                IconButton(onClick = onToggleExpanded) { Icon(Icons.Rounded.MoreHoriz, null) }
            }
        }
        if (expanded && recents.isNotEmpty()) {
            items(recents.take(10), key = { it.query.lowercase() }) { recent ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surface,
                    onClick = { onRecent(recent.query) },
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (recent.pinned) Icons.Rounded.Bolt else Icons.Rounded.History, null, tint = if (recent.pinned) colors.primaryBright else colors.textMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(recent.query, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.textPrimary)
                            Text(relativeRecentTime(recent.timestamp), fontSize = 9.sp, color = colors.textMuted)
                        }
                        TextButton(onClick = { onPinRecent(recent.query) }) { Text(if (recent.pinned) "Unpin" else "Pin", fontSize = 10.sp) }
                        IconButton(onClick = { onRemoveRecent(recent.query) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.Close, "Remove", modifier = Modifier.size(16.dp)) }
                    }
                }
            }
        } else if (expanded && recents.isEmpty()) {
            item { Text("Searches you submit will appear here.", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 11.sp, color = colors.textMuted) }
        }

        if (trendingHashtags.isNotEmpty()) {
            item { PremiumSectionTitle("Trending at FUTA", "Live topics from Blink activity") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(trendingHashtags, key = { it.key }) { trend ->
                        Surface(onClick = { onHashtag(trend.key) }, shape = RoundedCornerShape(18.dp), color = colors.surfaceElevated, border = BorderStroke(1.dp, colors.borderSoft)) {
                            Row(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.TrendingUp, null, tint = colors.primaryBright, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("#${trend.key}", fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                            }
                        }
                    }
                }
            }
        }

        if (suggestedPeople.isNotEmpty()) {
            item { PremiumSectionTitle("Suggested people", "Active and popular people on Blink") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(suggestedPeople, key = { it.id.ifBlank { it.username } }) { person ->
                        PremiumPersonMiniCard(person, onProfileClick)
                    }
                }
            }
        }

        if (trendingPosts.isNotEmpty()) {
            item { PremiumSectionTitle("Popular posts & reels", "High-engagement content right now") }
            items(trendingPosts, key = { "trend-${it.id}" }) { post ->
                PremiumDiscoveryPostPreview(post = post, onClick = { onPostClick(post) })
            }
        }
    }
}

@Composable
private fun PremiumSearchResults(
    query: String,
    cleanQuery: String,
    category: PremiumSearchCategory,
    suggestions: List<PremiumSuggestion>,
    correction: String?,
    people: List<UserProfile>,
    posts: List<FeedPost>,
    reels: List<FeedPost>,
    hashtags: List<Map.Entry<String, Int>>,
    campusMatches: List<String>,
    isSearching: Boolean,
    currentUsername: String,
    isDark: Boolean,
    onSuggestion: (String) -> Unit,
    onSubmitSuggestion: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    onPostClick: (FeedPost) -> Unit,
    onLikePost: (String) -> Unit,
    onCommentPost: (String) -> Unit,
    onBookmarkPost: (String) -> Unit,
    onSharePost: (String) -> Unit,
    onOptionsClick: (FeedPost) -> Unit,
    onDeletePost: (String) -> Unit,
    onCorrection: (String) -> Unit,
) {
    val colors = BlinkThemeTokens.colors
    val hasResults = people.isNotEmpty() || posts.isNotEmpty() || reels.isNotEmpty() || hashtags.isNotEmpty() || campusMatches.isNotEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (suggestions.isNotEmpty()) {
            item { PremiumSectionTitle("Suggestions", "Live autocomplete") }
            items(suggestions.take(7), key = { "suggest-${it.value.lowercase()}" }) { suggestion ->
                PremiumSuggestionRow(suggestion, cleanQuery, onSuggestion, onSubmitSuggestion)
            }
        }

        correction?.takeIf { !it.equals(cleanQuery, true) }?.let { corrected ->
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.primary.copy(alpha = .10f),
                    onClick = { onCorrection(corrected) },
                ) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Bolt, null, tint = colors.primaryBright, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Did you mean ", color = colors.textSecondary)
                        Text(corrected, fontWeight = FontWeight.Bold, color = colors.primaryBright)
                        Text("?", color = colors.textSecondary)
                    }
                }
            }
        }

        if (isSearching && !hasResults) {
            items(5) { index ->
                BlinkSkeleton(modifier = Modifier.fillMaxWidth().height(if (index == 0) 72.dp else 96.dp).padding(horizontal = 14.dp, vertical = 5.dp))
            }
        }

        if ((category == PremiumSearchCategory.ALL || category == PremiumSearchCategory.PEOPLE) && people.isNotEmpty()) {
            item { PremiumSectionTitle("People", "${people.size} result${if (people.size == 1) "" else "s"}") }
            items(people.take(if (category == PremiumSearchCategory.ALL) 8 else 60), key = { "person-${it.id.ifBlank { it.username }}" }) { person ->
                PremiumPersonResult(person, onProfileClick)
            }
        }

        if ((category == PremiumSearchCategory.ALL || category == PremiumSearchCategory.HASHTAGS) && hashtags.isNotEmpty()) {
            item { PremiumSectionTitle("Hashtags", "${hashtags.size} matches") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(hashtags, key = { it.key }) { tag ->
                        BlinkChip(text = "#${tag.key} · ${tag.value}", selected = false, onClick = { onSubmitSuggestion("#${tag.key}") })
                    }
                }
            }
        }

        if ((category == PremiumSearchCategory.ALL || category == PremiumSearchCategory.CAMPUS) && campusMatches.isNotEmpty()) {
            item { PremiumSectionTitle("Campus", "Faculty, department, course and places") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(campusMatches, key = { it.lowercase() }) { value ->
                        Surface(onClick = { onSubmitSuggestion(value) }, shape = RoundedCornerShape(16.dp), color = colors.surfaceElevated, border = BorderStroke(1.dp, colors.borderSoft)) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.School, null, modifier = Modifier.size(16.dp), tint = colors.primaryBright)
                                Spacer(Modifier.width(6.dp))
                                Text(value, fontSize = 11.sp, color = colors.textPrimary)
                            }
                        }
                    }
                }
            }
        }

        if ((category == PremiumSearchCategory.ALL || category == PremiumSearchCategory.REELS) && reels.isNotEmpty()) {
            item { PremiumSectionTitle("Reels", "${reels.size} matches") }
            items(reels.take(if (category == PremiumSearchCategory.ALL) 8 else 60).chunked(2), key = { row -> row.joinToString("_") { it.id } }) { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { reel -> PremiumReelTile(reel, Modifier.weight(1f), { onPostClick(reel) }) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if ((category == PremiumSearchCategory.ALL || category == PremiumSearchCategory.POSTS) && posts.isNotEmpty()) {
            item { PremiumSectionTitle("Posts", "${posts.size} matches") }
            items(posts.take(if (category == PremiumSearchCategory.ALL) 12 else 80), key = { "post-${it.id}" }) { post ->
                Column(Modifier.animateContentSize()) {
                    PostCard(
                        post = post,
                        isDark = isDark,
                        onLike = { onLikePost(post.id) },
                        onComment = { onCommentPost(post.id) },
                        onBookmark = { onBookmarkPost(post.id) },
                        onShare = { onSharePost(post.id) },
                        onOptionsClick = { onOptionsClick(post) },
                        onProfileClick = onProfileClick,
                        isAuthor = post.author.equals(currentUsername, true) || post.authorUsername.equals(currentUsername, true),
                        onDelete = { onDeletePost(post.id) },
                    )
                    TextButton(onClick = { onPostClick(post) }, modifier = Modifier.padding(start = 10.dp)) { Text("Open result") }
                }
            }
        }

        if (!isSearching && !hasResults) {
            item {
                BlinkEmptyState(
                    title = "No results for “$query”",
                    message = "Try a shorter name, @username, #hashtag, faculty, department, course or place.",
                    icon = Icons.Rounded.Search,
                )
            }
        }
    }
}

@Composable
private fun PremiumSuggestionRow(
    suggestion: PremiumSuggestion,
    cleanQuery: String,
    onFill: (String) -> Unit,
    onSearch: (String) -> Unit,
) {
    val colors = BlinkThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        onClick = { onSearch(suggestion.value) },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            when (suggestion) {
                is PremiumSuggestion.Person -> {
                    PremiumAvatar(suggestion.profile, 38)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HighlightedText(suggestion.profile.fullName.ifBlank { suggestion.profile.username }, cleanQuery)
                            if (suggestion.profile.verificationBadge != VerificationBadge.NONE) {
                                Spacer(Modifier.width(3.dp)); VerifiedMark(suggestion.profile.verificationBadge, size = 14.dp)
                            }
                        }
                        Text("@${suggestion.profile.username} · Person", fontSize = 10.sp, color = colors.textMuted)
                    }
                }
                is PremiumSuggestion.Hashtag -> {
                    Icon(Icons.Rounded.Tag, null, tint = colors.primaryBright)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        HighlightedText("#${suggestion.tag}", cleanQuery)
                        Text("${suggestion.count} posts · Hashtag", fontSize = 10.sp, color = colors.textMuted)
                    }
                }
                is PremiumSuggestion.Campus -> {
                    Icon(Icons.Rounded.School, null, tint = colors.primaryBright)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        HighlightedText(suggestion.label, cleanQuery)
                        Text("Campus discovery", fontSize = 10.sp, color = colors.textMuted)
                    }
                }
            }
            TextButton(onClick = { onFill(suggestion.value) }) { Text("Fill", fontSize = 10.sp) }
        }
    }
}

@Composable
private fun PremiumPersonResult(person: UserProfile, onProfileClick: (String) -> Unit) {
    val colors = BlinkThemeTokens.colors
    BlinkCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        onClick = { onProfileClick(person.username) },
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            PremiumAvatar(person, 54)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(person.fullName.ifBlank { person.username }, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1)
                    if (person.verificationBadge != VerificationBadge.NONE) { Spacer(Modifier.width(4.dp)); VerifiedMark(person.verificationBadge, 16.dp) }
                    if (person.isBlinkVip) { Spacer(Modifier.width(5.dp)); Text("VIP", fontSize = 9.sp, fontWeight = FontWeight.Black, color = colors.primaryBright) }
                }
                Text("@${person.username}", fontSize = 11.sp, color = colors.textSecondary)
                val meta = listOf(person.faculty, person.department, person.academicLevel).mapNotNull(::cleanMeta).take(3).joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, fontSize = 10.sp, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (person.bio.isNotBlank()) Text(person.bio, fontSize = 10.sp, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${person.followerCount} followers${if (person.onlineNow) " · Online" else ""}", fontSize = 10.sp, color = colors.textMuted)
            }
            Text("View", fontWeight = FontWeight.Bold, color = colors.primaryBright, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PremiumPersonMiniCard(person: UserProfile, onProfileClick: (String) -> Unit) {
    val colors = BlinkThemeTokens.colors
    BlinkCard(modifier = Modifier.width(160.dp), onClick = { onProfileClick(person.username) }) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PremiumAvatar(person, 58)
            Spacer(Modifier.height(8.dp))
            Text(person.fullName.ifBlank { person.username }, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("@${person.username}", fontSize = 10.sp, color = colors.textMuted, maxLines = 1)
            if (person.isBlinkVip) Text("Blink VIP", color = colors.primaryBright, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("${person.followerCount} followers", fontSize = 9.sp, color = colors.textMuted)
        }
    }
}

@Composable
private fun PremiumAvatar(person: UserProfile, size: Int) {
    Box {
        AsyncImage(
            model = person.avatarUrl,
            error = painterResource(R.drawable.ic_default_profile),
            fallback = painterResource(R.drawable.ic_default_profile),
            contentDescription = person.fullName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(CircleShape),
        )
        if (person.onlineNow) {
            Box(Modifier.align(Alignment.BottomEnd).size(13.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
        }
    }
}

@Composable
private fun PremiumReelTile(post: FeedPost, modifier: Modifier, onClick: () -> Unit) {
    val colors = BlinkThemeTokens.colors
    Surface(
        modifier = modifier.aspectRatio(.78f),
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.borderSoft),
    ) {
        Box(Modifier.fillMaxSize()) {
            val preview = post.images.firstOrNull()
            if (!preview.isNullOrBlank()) {
                AsyncImage(preview, "Reel by ${post.author}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize().background(colors.surfaceHighest), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayCircle, null, modifier = Modifier.size(42.dp), tint = colors.primaryBright)
                }
            }
            Surface(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(), color = colors.background.copy(alpha = .84f)) {
                Column(Modifier.padding(9.dp)) {
                    Text(post.author, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = colors.textPrimary, maxLines = 1)
                    Text("${post.viewsCount} views · ${post.videoDuration}", fontSize = 9.sp, color = colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun PremiumDiscoveryPostPreview(post: FeedPost, onClick: () -> Unit) {
    val colors = BlinkThemeTokens.colors
    BlinkCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), onClick = onClick) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = colors.surfaceHighest, modifier = Modifier.size(66.dp)) {
                val preview = post.images.firstOrNull()
                if (!preview.isNullOrBlank()) AsyncImage(preview, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Box(contentAlignment = Alignment.Center) { Icon(if (post.isReel) Icons.Rounded.PlayCircle else Icons.Rounded.Photo, null, tint = colors.primaryBright) }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(post.author, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text(post.text.ifBlank { if (post.isReel) "Reel" else "Post" }, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = colors.textSecondary)
                Text("${post.viewsCount} views · ${post.likes} likes · ${post.commentsCount} comments", fontSize = 9.sp, color = colors.textMuted)
            }
            Icon(Icons.Rounded.TrendingUp, null, tint = colors.primaryBright)
        }
    }
}

@Composable
private fun PremiumSectionTitle(title: String, subtitle: String? = null) {
    val colors = BlinkThemeTokens.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colors.textPrimary)
        subtitle?.let { Text(it, fontSize = 10.sp, color = colors.textMuted) }
    }
}

@Composable
private fun HighlightedText(text: String, query: String) {
    val colors = BlinkThemeTokens.colors
    val index = text.indexOf(query, ignoreCase = true)
    if (query.isBlank() || index < 0) {
        Text(text, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    androidx.compose.material3.Text(
        text = androidx.compose.ui.text.buildAnnotatedString {
            append(text.substring(0, index))
            pushStyle(androidx.compose.ui.text.SpanStyle(color = colors.primaryBright, fontWeight = FontWeight.Bold))
            append(text.substring(index, index + query.length))
            pop()
            append(text.substring(index + query.length))
        },
        color = colors.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PremiumFilterSheet(
    verifiedOnly: Boolean,
    vipOnly: Boolean,
    selectedUniversity: String,
    selectedFaculty: String,
    selectedDepartment: String,
    selectedLevel: String,
    mediaFilter: PremiumMediaFilter,
    universities: List<String>,
    faculties: List<String>,
    departments: List<String>,
    levels: List<String>,
    onVerified: (Boolean) -> Unit,
    onVip: (Boolean) -> Unit,
    onUniversity: (String) -> Unit,
    onFaculty: (String) -> Unit,
    onDepartment: (String) -> Unit,
    onLevel: (String) -> Unit,
    onMedia: (PremiumMediaFilter) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = BlinkThemeTokens.colors
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Advanced filters", fontWeight = FontWeight.Black, fontSize = 21.sp, color = colors.textPrimary)
                    Text("Narrow discovery without changing your feed algorithm", fontSize = 10.sp, color = colors.textMuted)
                }
                TextButton(onClick = onReset) { Text("Reset") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BlinkChip("Verified", verifiedOnly, { onVerified(!verifiedOnly) })
                BlinkChip("Blink VIP", vipOnly, { onVip(!vipOnly) })
            }
        }
        item { PremiumFilterChips("Media", PremiumMediaFilter.entries.map { it.label }, mediaFilter.label) { label -> onMedia(PremiumMediaFilter.entries.first { it.label == label }) } }
        if (universities.isNotEmpty()) item { PremiumFilterChips("University", listOf("All") + universities, selectedUniversity.ifBlank { "All" }) { onUniversity(if (it == "All") "" else it) } }
        if (faculties.isNotEmpty()) item { PremiumFilterChips("Faculty", listOf("All") + faculties, selectedFaculty.ifBlank { "All" }) { onFaculty(if (it == "All") "" else it) } }
        if (departments.isNotEmpty()) item { PremiumFilterChips("Department", listOf("All") + departments, selectedDepartment.ifBlank { "All" }) { onDepartment(if (it == "All") "" else it) } }
        if (levels.isNotEmpty()) item { PremiumFilterChips("Level", listOf("All") + levels, selectedLevel.ifBlank { "All" }) { onLevel(if (it == "All") "" else it) } }
        item {
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Show results", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PremiumFilterChips(title: String, values: List<String>, selected: String, onSelect: (String) -> Unit) {
    val colors = BlinkThemeTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(values, key = { "$title-${it.lowercase()}" }) { value ->
                BlinkChip(value, value.equals(selected, true), { onSelect(value) })
            }
        }
    }
}

private fun cleanMeta(value: String?): String? = value?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", true) }

private fun premiumProfileRelevance(profile: UserProfile, query: String): Int {
    if (query.isBlank()) return 1
    val q = query.lowercase()
    fun score(value: String, exact: Int, starts: Int, contains: Int): Int {
        val v = value.lowercase()
        return when {
            v == q -> exact
            v.startsWith(q) -> starts
            v.contains(q) -> contains
            else -> 0
        }
    }
    return maxOf(
        score(profile.username, 140, 128, 110),
        score(profile.fullName, 136, 124, 106),
        score(profile.university, 78, 72, 66),
        score(profile.faculty, 76, 70, 64),
        score(profile.department, 74, 68, 62),
        score(profile.courseOfStudy, 72, 66, 60),
        score(profile.academicLevel, 58, 54, 48),
        score(profile.currentCityState, 56, 52, 46),
        score(profile.bio, 40, 36, 32),
    )
}

private fun premiumPostRelevance(post: FeedPost, query: String): Int {
    if (query.isBlank()) return 1
    val q = query.lowercase()
    val tags = post.tags.map { it.removePrefix("#").lowercase() }
    return when {
        post.authorUsername.equals(q, true) -> 140
        post.author.equals(q, true) -> 136
        tags.any { it == q } -> 132
        post.text.startsWith(q, true) -> 120
        post.text.contains(q, true) -> 108
        tags.any { it.startsWith(q) } -> 104
        tags.any { it.contains(q) } -> 96
        post.facultyTag.contains(q, true) -> 78
        post.category.contains(q, true) -> 72
        post.location?.contains(q, true) == true -> 70
        post.altText?.contains(q, true) == true -> 58
        else -> 0
    }
}

private fun premiumTrendScore(post: FeedPost): Long =
    post.viewsCount.toLong() + post.likes * 4L + post.commentsCount * 7L + post.sharesCount * 9L + post.repostsCount * 8L

private fun premiumCorrection(
    query: String,
    profiles: List<UserProfile>,
    posts: List<FeedPost>,
    campus: List<String>,
): String? {
    if (query.length < 3) return null
    val candidates = buildList {
        profiles.take(100).forEach { add(it.username); add(it.fullName) }
        posts.take(100).flatMapTo(this) { it.tags.map { tag -> tag.removePrefix("#") } }
        addAll(campus.take(60))
    }.map { it.trim() }.filter { it.length >= 3 }.distinctBy { it.lowercase() }
    if (candidates.any { it.equals(query, true) }) return null
    val best = candidates.asSequence()
        .map { it to levenshtein(it.lowercase(), query.lowercase()) }
        .filter { (_, distance) -> distance <= min(3, query.length / 2) }
        .minByOrNull { it.second }
    return best?.first
}

private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var previous = IntArray(b.length + 1) { it }
    for (i in a.indices) {
        val current = IntArray(b.length + 1)
        current[0] = i + 1
        for (j in b.indices) {
            val cost = if (a[i] == b[j]) 0 else 1
            current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + cost)
        }
        previous = current
    }
    return previous[b.length]
}

private fun loadPremiumRecents(prefs: android.content.SharedPreferences): List<PremiumRecentSearch> {
    val now = System.currentTimeMillis()
    return prefs.getString(PREMIUM_SEARCH_RECENTS, "").orEmpty()
        .split(PREMIUM_SEARCH_SEPARATOR)
        .mapNotNull { raw ->
            val parts = raw.split(PREMIUM_SEARCH_FIELD_SEPARATOR)
            val query = parts.getOrNull(0).orEmpty().trim()
            val timestamp = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            val pinned = parts.getOrNull(2) == "1"
            if (query.isBlank() || (!pinned && now - timestamp > PREMIUM_SEARCH_EXPIRY_MS)) null
            else PremiumRecentSearch(query, timestamp, pinned)
        }
        .distinctBy { it.query.lowercase() }
        .sortedWith(compareByDescending<PremiumRecentSearch> { it.pinned }.thenByDescending { it.timestamp })
        .take(20)
}

private fun encodePremiumRecents(recents: List<PremiumRecentSearch>): String = recents.joinToString(PREMIUM_SEARCH_SEPARATOR) {
    val safe = it.query.replace(PREMIUM_SEARCH_SEPARATOR, " ").replace(PREMIUM_SEARCH_FIELD_SEPARATOR, " ")
    listOf(safe, it.timestamp.toString(), if (it.pinned) "1" else "0").joinToString(PREMIUM_SEARCH_FIELD_SEPARATOR)
}

private fun relativeRecentTime(timestamp: Long): String {
    val minutes = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0L) / 60_000L)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1_440 -> "${minutes / 60}h ago"
        else -> "${minutes / 1_440}d ago"
    }
}
