package com.blinkng.desktop.ui

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.desktop.DesktopAppState
import com.blinkng.desktop.data.DesktopFeedPost
import com.blinkng.desktop.data.DesktopProfile
import com.blinkng.desktop.data.DesktopSearchResults
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.prefs.Preferences

private enum class DesktopSearchCategory(val label: String) {
    ALL("All"), PEOPLE("People"), POSTS("Posts"), REELS("Reels"), HASHTAGS("Hashtags")
}

private enum class DesktopSearchSort(val label: String) {
    RELEVANT("Most relevant"), RECENT("Most recent"), VIEWED("Most viewed"), LIKED("Most liked"), TRENDING("Trending now")
}

private const val DESKTOP_SEARCH_RECENTS = "premium_search_recents"
private const val DESKTOP_SEARCH_SEPARATOR = "\u001F"

@Composable
fun PremiumSearchScreen(state: DesktopAppState) {
    val prefs = remember { Preferences.userRoot().node("blinkng/search") }
    var query by remember { mutableStateOf(state.globalSearch) }
    var results by remember { mutableStateOf(DesktopSearchResults(emptyList(), emptyList())) }
    var loading by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(DesktopSearchCategory.ALL) }
    var sort by remember { mutableStateOf(DesktopSearchSort.RELEVANT) }
    var verifiedOnly by remember { mutableStateOf(false) }
    var selectedUniversity by remember { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var recents by remember {
        mutableStateOf(
            prefs.get(DESKTOP_SEARCH_RECENTS, "").split(DESKTOP_SEARCH_SEPARATOR)
                .map(String::trim).filter(String::isNotBlank).distinct().take(12)
        )
    }
    val scope = rememberCoroutineScope()

    fun rememberSearch(value: String) {
        val clean = value.trim()
        if (clean.isBlank()) return
        recents = (listOf(clean) + recents.filterNot { it.equals(clean, true) }).take(12)
        prefs.put(DESKTOP_SEARCH_RECENTS, recents.joinToString(DESKTOP_SEARCH_SEPARATOR))
    }

    suspend fun searchNow(value: String = query) {
        val clean = value.trim().removePrefix("@").removePrefix("#")
        state.globalSearch = value.trim()
        if (clean.isBlank()) {
            results = DesktopSearchResults(emptyList(), emptyList())
            loading = false
            return
        }
        loading = true
        results = runCatching { state.client.search(clean) }.getOrDefault(DesktopSearchResults(emptyList(), emptyList()))
        loading = false
    }

    LaunchedEffect(query) {
        delay(280)
        searchNow(query)
    }

    LaunchedEffect(state.globalSearch) {
        if (state.globalSearch.isNotBlank() && query != state.globalSearch) query = state.globalSearch
    }

    val clean = query.trim().removePrefix("@").removePrefix("#")
    val people = remember(results.profiles, clean, verifiedOnly, selectedUniversity, sort) {
        results.profiles
            .filter { profile ->
                (!verifiedOnly || profile.isVerified) &&
                    (selectedUniversity.isBlank() || profile.university.equals(selectedUniversity, true)) &&
                    desktopProfileMatches(profile, clean)
            }
            .sortedWith(
                when (sort) {
                    DesktopSearchSort.RELEVANT -> compareByDescending<DesktopProfile> { desktopProfileScore(it, clean) }.thenByDescending { it.followerCount }
                    else -> compareByDescending<DesktopProfile> { it.followerCount }
                }
            )
    }
    val content = remember(results.posts, clean, sort) {
        results.posts.filter { desktopPostMatches(it, clean) }.sortedWith(
            when (sort) {
                DesktopSearchSort.RECENT -> compareByDescending<DesktopFeedPost> { it.createdAt }
                DesktopSearchSort.VIEWED -> compareByDescending { it.viewCount }
                DesktopSearchSort.LIKED -> compareByDescending { it.likeCount }
                DesktopSearchSort.TRENDING -> compareByDescending { desktopTrendScore(it) }
                DesktopSearchSort.RELEVANT -> compareByDescending<DesktopFeedPost> { desktopPostScore(it, clean) }.thenByDescending { desktopTrendScore(it) }
            }
        )
    }
    val posts = content.filterNot { it.isReel }
    val reels = content.filter { it.isReel }
    val hashtags = remember(results.posts, clean) {
        results.posts.flatMap { it.hashtags }
            .map { it.trim().removePrefix("#").lowercase() }
            .filter { it.isNotBlank() && (clean.isBlank() || it.contains(clean, true)) }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(30)
    }
    val universities = remember(results.profiles) {
        results.profiles.mapNotNull { it.university?.trim()?.takeIf(String::isNotBlank) }.distinct().sorted().take(20)
    }
    val suggestions = remember(people, hashtags, clean) {
        if (clean.isBlank()) emptyList() else buildList {
            addAll(people.take(5).map { "@${it.username}" })
            addAll(hashtags.take(5).map { "#${it.key}" })
        }.distinct().take(8)
    }
    val trending = remember(results.posts) {
        results.posts.flatMap { post -> post.hashtags.map { it.trim().removePrefix("#") to desktopTrendScore(post) } }
            .filter { it.first.isNotBlank() }
            .groupBy({ it.first.lowercase() }, { it.second })
            .mapValues { it.value.sum() }.entries.sortedByDescending { it.value }.take(8)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Search Blink", fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("People, posts, reels, hashtags and campus discovery", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
                Text("Windows parity", modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                },
                placeholder = { Text("Search name, @username, #hashtag, post or reel") },
                shape = RoundedCornerShape(18.dp),
            )
            IconButton(onClick = { showFilters = !showFilters }) { Icon(Icons.Rounded.FilterList, "Filters") }
            IconButton(onClick = { showSort = !showSort }) { Icon(Icons.Rounded.Sort, "Sort") }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            items(DesktopSearchCategory.entries, key = { it.name }) { item ->
                val count = when (item) {
                    DesktopSearchCategory.ALL -> null
                    DesktopSearchCategory.PEOPLE -> people.size
                    DesktopSearchCategory.POSTS -> posts.size
                    DesktopSearchCategory.REELS -> reels.size
                    DesktopSearchCategory.HASHTAGS -> hashtags.size
                }
                FilterChip(
                    selected = category == item,
                    onClick = { category = item },
                    label = { Text(if (clean.isNotBlank() && count != null) "${item.label} $count" else item.label) },
                )
            }
        }

        if (showFilters) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Filters", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = verifiedOnly, onClick = { verifiedOnly = !verifiedOnly }, label = { Text("Verified") }, leadingIcon = { Icon(Icons.Rounded.Verified, null, modifier = Modifier.size(16.dp)) })
                        FilterChip(selected = selectedUniversity.isBlank(), onClick = { selectedUniversity = "" }, label = { Text("All universities") })
                    }
                    if (universities.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(universities, key = { it }) { university ->
                                FilterChip(selected = selectedUniversity == university, onClick = { selectedUniversity = if (selectedUniversity == university) "" else university }, label = { Text(university, maxLines = 1) })
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (showSort) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                items(DesktopSearchSort.entries, key = { it.name }) { item ->
                    FilterChip(selected = sort == item, onClick = { sort = item; showSort = false }, label = { Text(item.label) })
                }
            }
        }

        if (loading && clean.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Searching Blink…", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
        }

        AnimatedContent(
            targetState = clean.isBlank(),
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
            label = "desktopSearchMode",
            modifier = Modifier.weight(1f),
        ) { discovery ->
            if (discovery) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Recent searches", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (recents.isNotEmpty()) TextButton(onClick = { recents = emptyList(); prefs.remove(DESKTOP_SEARCH_RECENTS) }) { Text("Clear") }
                        }
                    }
                    if (recents.isEmpty()) item { Text("Your recent searches will appear here.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(recents, key = { it.lowercase() }) { recent ->
                        Surface(onClick = { query = recent }, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
                            Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.History, null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(recent, modifier = Modifier.weight(1f))
                                IconButton(onClick = { recents = recents.filterNot { it == recent }; prefs.put(DESKTOP_SEARCH_RECENTS, recents.joinToString(DESKTOP_SEARCH_SEPARATOR)) }, modifier = Modifier.size(30.dp)) { Icon(Icons.Rounded.Close, "Remove", modifier = Modifier.size(15.dp)) }
                            }
                        }
                    }
                    if (trending.isNotEmpty()) {
                        item { DesktopSearchSection("Trending at FUTA", "Popular hashtags from current results") }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(trending, key = { it.key }) { trend ->
                                    Surface(onClick = { query = "#${trend.key}" }, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                                        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.TrendingUp, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                                            Spacer(Modifier.width(6.dp)); Text("#${trend.key}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Text("Start typing to see live suggestions and real search results.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (suggestions.isNotEmpty()) {
                        item { DesktopSearchSection("Suggestions", "Live autocomplete") }
                        items(suggestions, key = { it.lowercase() }) { suggestion ->
                            Surface(onClick = { query = suggestion }, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
                                Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (suggestion.startsWith("#")) Icons.Rounded.Tag else Icons.Rounded.Search, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(9.dp)); Text(suggestion, modifier = Modifier.weight(1f))
                                    Button(onClick = { query = suggestion; rememberSearch(suggestion); scope.launch { searchNow(suggestion) } }) { Text("Search") }
                                }
                            }
                        }
                    }
                    if ((category == DesktopSearchCategory.ALL || category == DesktopSearchCategory.PEOPLE) && people.isNotEmpty()) {
                        item { DesktopSearchSection("People", "${people.size} results") }
                        items(people, key = { "person-${it.id}" }) { profile -> DesktopPremiumPerson(profile) }
                    }
                    if ((category == DesktopSearchCategory.ALL || category == DesktopSearchCategory.HASHTAGS) && hashtags.isNotEmpty()) {
                        item { DesktopSearchSection("Hashtags", "${hashtags.size} matches") }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(hashtags, key = { it.key }) { tag ->
                                    FilterChip(selected = false, onClick = { query = "#${tag.key}"; rememberSearch(query) }, label = { Text("#${tag.key} · ${tag.value}") })
                                }
                            }
                        }
                    }
                    if ((category == DesktopSearchCategory.ALL || category == DesktopSearchCategory.REELS) && reels.isNotEmpty()) {
                        item { DesktopSearchSection("Reels", "${reels.size} matches") }
                        items(reels, key = { "reel-${it.id}" }) { post -> DesktopPremiumContent(post) }
                    }
                    if ((category == DesktopSearchCategory.ALL || category == DesktopSearchCategory.POSTS) && posts.isNotEmpty()) {
                        item { DesktopSearchSection("Posts", "${posts.size} matches") }
                        items(posts, key = { "post-${it.id}" }) { post -> DesktopPremiumContent(post) }
                    }
                    if (!loading && people.isEmpty() && posts.isEmpty() && reels.isEmpty() && hashtags.isEmpty()) {
                        item {
                            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f)) {
                                Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Rounded.Search, null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(8.dp))
                                    Text("No results for “$query”", fontWeight = FontWeight.Bold)
                                    Text("Try a shorter username, hashtag, post caption or university.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
private fun DesktopSearchSection(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DesktopPremiumPerson(profile: DesktopProfile) {
    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.fullName, fontWeight = FontWeight.Bold)
                    if (profile.isVerified) { Spacer(Modifier.width(4.dp)); Icon(Icons.Rounded.Verified, "Verified", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp)) }
                }
                Text("@${profile.username}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val meta = listOfNotNull(profile.university, profile.faculty, profile.department).filter(String::isNotBlank).take(3).joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${profile.followerCount} followers${if (profile.isOnline) " · Online" else ""}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DesktopPremiumContent(post: DesktopFeedPost) {
    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(if (post.isReel) Icons.Rounded.PlayCircle else Icons.Rounded.Tag, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(post.authorName, fontWeight = FontWeight.Bold)
                    if (post.authorVerified) { Spacer(Modifier.width(4.dp)); Icon(Icons.Rounded.Verified, "Verified", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp)) }
                }
                Text(post.caption ?: post.text ?: if (post.isReel) "Reel" else "Post", maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                Text("${post.viewCount} views · ${post.likeCount} likes · ${post.commentCount} comments · ${post.shareCount} shares", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (desktopTrendScore(post) > 0) Icon(Icons.Rounded.TrendingUp, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun desktopProfileMatches(profile: DesktopProfile, query: String): Boolean = query.isBlank() || desktopProfileScore(profile, query) > 0

private fun desktopProfileScore(profile: DesktopProfile, query: String): Int {
    if (query.isBlank()) return 1
    val q = query.lowercase()
    fun score(value: String?): Int {
        val v = value.orEmpty().lowercase()
        return when {
            v == q -> 100
            v.startsWith(q) -> 85
            v.contains(q) -> 70
            else -> 0
        }
    }
    return maxOf(score(profile.username), score(profile.fullName), score(profile.university), score(profile.faculty), score(profile.department), score(profile.bio))
}

private fun desktopPostMatches(post: DesktopFeedPost, query: String): Boolean = query.isBlank() || desktopPostScore(post, query) > 0

private fun desktopPostScore(post: DesktopFeedPost, query: String): Int {
    if (query.isBlank()) return 1
    val q = query.lowercase()
    return when {
        post.authorUsername.equals(q, true) -> 100
        post.authorName.equals(q, true) -> 95
        post.hashtags.any { it.removePrefix("#").equals(q, true) } -> 92
        post.caption?.contains(q, true) == true -> 80
        post.text?.contains(q, true) == true -> 76
        post.hashtags.any { it.contains(q, true) } -> 70
        else -> 0
    }
}

private fun desktopTrendScore(post: DesktopFeedPost): Long = post.viewCount.toLong() + post.likeCount * 4L + post.commentCount * 7L + post.shareCount * 9L
