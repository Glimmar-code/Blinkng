package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.FeedPost
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private data class SearchPerson(val username: String, val name: String, val avatar: String, val faculty: String)

@Composable
fun SearchScreen(posts: List<FeedPost>, onProfileClick: (String) -> Unit, onPostClick: (FeedPost) -> Unit, isDark: Boolean) {
    var query by rememberSaveable { mutableStateOf("") }
    var people by remember { mutableStateOf<List<SearchPerson>>(emptyList()) }
    var remotePosts by remember { mutableStateOf<List<FeedPost>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            people = emptyList(); remotePosts = emptyList(); loading = false; return@LaunchedEffect
        }
        delay(250)
        loading = true
        val result = withContext(Dispatchers.IO) { searchEverywhere(q) }
        people = result.first
        remotePosts = result.second
        loading = false
    }

    val allPosts = remember(query, posts, remotePosts) {
        val local = if (query.isBlank()) emptyList() else posts.filter {
            it.text.contains(query, true) || it.author.contains(query, true) || it.tags.any { tag -> tag.contains(query, true) }
        }
        (remotePosts + local).distinctBy { it.id }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text("Search", fontWeight = FontWeight.Bold, fontSize = 25.sp)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Search everyone, posts, usernames…") }
        )
        if (query.isBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Search for anyone or any post in Blink.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (people.isNotEmpty()) {
                item { Text("People", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 6.dp)) }
                items(people) { person ->
                    ListItem(
                        headlineContent = { Text(person.name.ifBlank { person.username }, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("@${person.username}${person.faculty.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""}") },
                        leadingContent = { AsyncImage(model = person.avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(46.dp).clip(CircleShape)) },
                        modifier = Modifier.fillMaxWidth().clickable { onProfileClick(person.username) }
                    )
                }
            }
            if (allPosts.isNotEmpty()) {
                item { Text("Posts", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)) }
                items(allPosts) { post ->
                    ListItem(
                        headlineContent = { Text(post.author, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(post.text.ifBlank { if (post.videoUrl != null) "Reel" else "Post" }, maxLines = 3) },
                        trailingContent = {
                            if (post.videoUrl != null) Text("Reel", fontSize = 10.sp)
                            else if (post.images.isNotEmpty()) Text("${post.images.size} image${if (post.images.size == 1) "" else "s"}", fontSize = 10.sp)
                        },
                        modifier = Modifier.fillMaxWidth().clickable { onPostClick(post) }
                    )
                }
            }
            if (people.isEmpty() && allPosts.isEmpty()) item {
                Text("No results for \"$query\".", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private suspend fun searchEverywhere(query: String): Pair<List<SearchPerson>, List<FeedPost>> {
    // Treat wildcard characters as literals so user input cannot alter the PostgREST ilike pattern.
    val safeQuery = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    val pattern = "%$safeQuery%"
    val encoded = URLEncoder.encode(pattern, "UTF-8")
    val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    fun get(path: String): JSONArray {
        val token = SupabaseService.accessToken() ?: SupabaseConfig.anonKey
        val request = Request.Builder()
            .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/$path")
            .addHeader("apikey", SupabaseConfig.anonKey)
            .addHeader("Authorization", "Bearer $token")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) JSONArray() else JSONArray(response.body?.string().orEmpty())
        }
    }

    return try {
        val profileRows = get("profiles?or=(username.ilike.$encoded,full_name.ilike.$encoded)&select=username,full_name,avatar_url,faculty&limit=100")
        val people = buildList {
            for (i in 0 until profileRows.length()) {
                val row = profileRows.optJSONObject(i) ?: continue
                add(SearchPerson(row.optString("username"), row.optString("full_name"), row.optString("avatar_url"), row.optString("faculty")))
            }
        }.filter { it.username.isNotBlank() }

        val postRows = get("feed_posts?or=(text.ilike.$encoded,caption.ilike.$encoded)&select=*&order=created_at.desc&limit=100")
        val posts = mutableListOf<FeedPost>()
        for (i in 0 until postRows.length()) {
            val row = postRows.optJSONObject(i) ?: continue
            val images = mutableListOf<String>()
            val imageValue = row.optString("image_url")
            if (imageValue.startsWith("[")) runCatching {
                val a = JSONArray(imageValue); for (j in 0 until a.length()) images.add(a.optString(j))
            } else if (imageValue.isNotBlank() && imageValue != "null") images.add(imageValue)
            row.optJSONArray("images")?.let { a -> images.clear(); for (j in 0 until a.length()) images.add(a.optString(j)) }
            posts.add(
                FeedPost(
                    id = row.optString("id"),
                    author = row.optString("author_username", row.optString("username")),
                    authorAvatar = row.optString("author_avatar", row.optString("avatar_url")),
                    timeAgo = "Recently",
                    text = row.optString("text", row.optString("caption")),
                    images = images,
                    likes = row.optInt("likes_count", 0),
                    commentsCount = row.optInt("comments_count", 0),
                    sharesCount = row.optInt("shares_count", 0),
                    viewsCount = row.optInt("views_count", 0),
                    isReel = row.optBoolean("is_reel", false) || !row.optString("video_url").isNullOrBlank(),
                    videoUrl = row.optString("video_url").takeIf { it.isNotBlank() && it != "null" }
                )
            )
        }
        people to posts
    } catch (_: Exception) {
        emptyList<SearchPerson>() to emptyList()
    }
}
