package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.FeedPost
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.ui.components.PostCard
import com.example.ui.theme.BlinkPink
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    profiles: List<UserProfile>,
    posts: List<FeedPost>,
    currentUsername: String,
    onProfileClick: (String) -> Unit,
    onPostClick: (FeedPost) -> Unit,
    onLikePost: (String) -> Unit = {},
    onCommentPost: (String) -> Unit = {},
    onBookmarkPost: (String) -> Unit = {},
    onSharePost: (String) -> Unit = {},
    onOptionsClick: (FeedPost) -> Unit = {},
    onDeletePost: (String) -> Unit = {},
    onRemoteSearch: suspend (String) -> Pair<List<UserProfile>, List<FeedPost>> = { emptyList<UserProfile>() to emptyList() },
    isDark: Boolean
) {
    var query by rememberSaveable { mutableStateOf("") }
    val clean = query.trim().removePrefix("#")
    var remoteProfiles by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var remotePosts by remember { mutableStateOf<List<FeedPost>>(emptyList()) }
    var remoteLoading by remember { mutableStateOf(false) }

    LaunchedEffect(clean) {
        if (clean.length < 2) {
            remoteProfiles = emptyList()
            remotePosts = emptyList()
            remoteLoading = false
        } else {
            delay(300)
            remoteLoading = true
            val result = onRemoteSearch(clean)
            remoteProfiles = result.first
            remotePosts = result.second
            remoteLoading = false
        }
    }

    val realProfiles = remember(profiles, remoteProfiles, clean) {
        val source = if (clean.isBlank()) profiles else remoteProfiles + profiles
        source.filter { it.username.isNotBlank() && !it.username.equals("null", true) }
            .distinctBy { it.id.ifBlank { it.username.lowercase() } }
    }
    val people = remember(realProfiles, clean) {
        if (clean.isBlank()) realProfiles.sortedWith(compareByDescending<UserProfile> { it.onlineNow }.thenByDescending { it.points }).take(20)
        else realProfiles.filter {
            it.username.contains(clean, true) || it.fullName.contains(clean, true) ||
            it.university.contains(clean, true) || it.faculty.contains(clean, true) || it.department.contains(clean, true)
        }.take(40)
    }
    val allSearchPosts = remember(posts, remotePosts, clean) {
        (if (clean.isBlank()) posts else remotePosts + posts).distinctBy { it.id }
    }
    val hashtags = remember(allSearchPosts, clean) {
        allSearchPosts.flatMap { it.tags }.map { it.trim().removePrefix("#").lowercase() }
            .filter { it.isNotBlank() }.groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.filter { clean.isBlank() || it.key.contains(clean, true) }.take(15)
    }
    val matchingPosts = remember(allSearchPosts, clean) {
        if (clean.isBlank()) allSearchPosts.take(30) else allSearchPosts.filter { post ->
            post.author.contains(clean, true) || post.text.contains(clean, true) ||
            post.tags.any { it.removePrefix("#").contains(clean, true) }
        }.take(60)
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("Discover", fontSize = 27.sp, fontWeight = FontWeight.Black)
                Text("Real students, posts and hashtags from Blink", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (remoteLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    },
                    placeholder = { Text("Search all people, posts or #hashtags") }, shape = RoundedCornerShape(22.dp)
                )
            }
        }
        if (people.isNotEmpty()) {
            item { Text(if (clean.isBlank()) "People to discover" else "People", Modifier.padding(horizontal=16.dp,vertical=8.dp), fontWeight=FontWeight.Bold,fontSize=16.sp) }
            item {
                LazyRow(contentPadding=PaddingValues(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    items(people,key={it.id.ifBlank{it.username}}) { person ->
                        Surface(
                            modifier=Modifier.width(150.dp).clickable{onProfileClick(person.username)},
                            shape=RoundedCornerShape(20.dp), border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),
                            color=MaterialTheme.colorScheme.surface
                        ) {
                            Column(Modifier.padding(13.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                AsyncImage(model=person.avatarUrl,contentDescription=person.fullName,contentScale=ContentScale.Crop,modifier=Modifier.size(58.dp).clip(CircleShape))
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Text(person.fullName.ifBlank{person.username},fontWeight=FontWeight.Bold,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                    if(person.verificationBadge!=VerificationBadge.NONE){Spacer(Modifier.width(3.dp));Icon(Icons.Default.Verified,null,tint=BlinkPink,modifier=Modifier.size(13.dp))}
                                }
                                Text("@${person.username}",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                person.university.takeUnless{it.isBlank()||it.equals("null",true)}?.let{Text(it,fontSize=9.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
                            }
                        }
                    }
                }
            }
        }
        if (hashtags.isNotEmpty()) {
            item { Text("Trending hashtags",Modifier.padding(horizontal=16.dp,vertical=12.dp),fontWeight=FontWeight.Bold,fontSize=16.sp) }
            item {
                LazyRow(contentPadding=PaddingValues(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(hashtags,key={it.key}) { tag ->
                        AssistChip(onClick={query="#${tag.key}"},leadingIcon={Icon(Icons.Default.Tag,null,modifier=Modifier.size(15.dp))},label={Text("#${tag.key} • ${tag.value}")})
                    }
                }
            }
        }
        item { Text(if(clean.isBlank())"Latest posts" else "Posts",Modifier.padding(horizontal=16.dp,vertical=14.dp),fontWeight=FontWeight.Bold,fontSize=16.sp) }
        if(matchingPosts.isEmpty()){
            item { Box(Modifier.fillMaxWidth().padding(44.dp),contentAlignment=Alignment.Center){Text(if(clean.isBlank())"No live posts yet." else "No results for “$query”.",color=MaterialTheme.colorScheme.onSurfaceVariant)} }
        }else{
            items(matchingPosts,key={it.id}) { post ->
                var visible by remember(post.id){mutableStateOf(false)}
                LaunchedEffect(post.id){delay(25);visible=true}
                AnimatedVisibility(visible=visible,enter=fadeIn()+slideInVertically(initialOffsetY={it/12})) {
                    PostCard(
                        post=post,isDark=isDark,onLike={onLikePost(post.id)},onComment={onCommentPost(post.id)},
                        onBookmark={onBookmarkPost(post.id)},onShare={onSharePost(post.id)},onOptionsClick={onOptionsClick(post)},
                        onProfileClick=onProfileClick,isAuthor=post.author.equals(currentUsername,true),
                        onDelete={onDeletePost(post.id)},modifier=Modifier.clickable{onPostClick(post)}
                    )
                }
            }
        }
    }
}
