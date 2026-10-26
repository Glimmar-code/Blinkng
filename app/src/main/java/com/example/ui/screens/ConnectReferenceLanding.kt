package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.models.FeedPost
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.data.repository.BlinkCommunity
import com.example.data.repository.BlinkCommunityRepository
import com.example.data.repository.FollowStateStore
import com.example.ui.components.VerifiedMark
import com.example.ui.theme.BlinkOnlineGreen
import kotlinx.coroutines.launch

/**
 * The BLINK campus discovery reference layout. Every avatar, community, count
 * and post is backed by live data; no example students/events are injected.
 * Device proximity is NOT claimed: "near you" means the chosen campus.
 */
@Composable
internal fun ConnectReferenceHeader(
    avatar: String,
    currentUsername: String,
    onProfile: (String) -> Unit,
    onSearch: () -> Unit,
    onGame: () -> Unit,
    onNotifications: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(39.dp).clip(CircleShape)
                .clickable(enabled = currentUsername.isNotBlank()) { onProfile(currentUsername) },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = avatar.takeIf { it.isNotBlank() },
                contentDescription = "Open your profile",
                error = androidx.compose.ui.res.painterResource(R.drawable.ic_default_profile),
                fallback = androidx.compose.ui.res.painterResource(R.drawable.ic_default_profile),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier.align(Alignment.BottomEnd).size(10.dp)
                    .background(BlinkOnlineGreen, CircleShape)
            )
        }
        Spacer(Modifier.width(17.dp))
        Text("BLINK", fontSize = 23.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Black,
            maxLines = 1, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
        IconButton(onClick = onSearch, modifier = Modifier.size(43.dp)) {
            Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(25.dp))
        }
        IconButton(onClick = onGame, modifier = Modifier.size(43.dp)) {
            Icon(Icons.Default.SportsEsports, contentDescription = "Games", modifier = Modifier.size(25.dp))
        }
        IconButton(onClick = onNotifications, modifier = Modifier.size(43.dp)) {
            Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications",
                modifier = Modifier.size(25.dp))
        }
    }
}

@Composable
internal fun ConnectReferenceTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp)) {
        listOf("Connect", "Campus", "Trending").forEachIndexed { index, label ->
            Column(
                modifier = Modifier.weight(1f).fillMaxSize().clickable { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(label, fontSize = 13.sp,
                    fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (index == selected) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 11.dp))
                Box(Modifier.fillMaxWidth(.83f).height(2.5.dp)
                    .background(if (index == selected) MaterialTheme.colorScheme.onBackground
                        else Color.Transparent, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
            }
        }
    }
    HorizontalDivider(thickness = .5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
}

@Composable
internal fun ConnectReferenceLanding(
    current: UserProfile?,
    profiles: List<UserProfile>,
    posts: List<FeedPost>,
    isLiveDataAvailable: Boolean,
    onProfile: (String) -> Unit,
    onOpenAllPosts: () -> Unit,
    onOpenPost: (FeedPost) -> Unit,
    onLikePost: (String) -> Unit,
    onCommentPost: (String) -> Unit,
    onBookmarkPost: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val accountId = current?.id.orEmpty()
    val campus = current?.university?.takeIf { it.isNotBlank() && it != "null" }.orEmpty()
    val following by FollowStateStore.followingIds.collectAsState()
    var showAllPeople by rememberSaveable(accountId) { mutableStateOf(false) }
    BackHandler(showAllPeople) { showAllPeople = false }

    val sameCampus = remember(profiles, campus, accountId) {
        profiles.filter { it.id != accountId && it.username.isNotBlank() &&
            campus.isNotBlank() && it.university.equals(campus, ignoreCase = true) }
            .distinctBy { it.id.ifBlank { it.username } }
            .sortedWith(compareByDescending<UserProfile> { it.onlineNow }
                .thenBy { it.fullName.lowercase() })
    }
    val people = remember(profiles, sameCampus, campus, accountId) {
        if (campus.isNotBlank()) sameCampus else profiles
            .filter { it.id != accountId && it.username.isNotBlank() }
            .distinctBy { it.id.ifBlank { it.username } }
            .sortedWith(compareByDescending<UserProfile> { it.onlineNow }
                .thenBy { it.fullName.lowercase() })
    }
    val campusPosts = remember(posts, profiles, campus) {
        val universityByUsername = profiles.associate {
            it.username.trim().lowercase() to it.university
        }
        posts.filter { post ->
            !post.isReel && post.videoUrl.isNullOrBlank() && !post.isSponsored &&
                (campus.isBlank() ||
                    universityByUsername[(post.authorUsername.ifBlank { post.author }).trim()
                        .removePrefix("@").lowercase()]?.equals(campus, true) == true ||
                    (post.location?.contains(campus, ignoreCase = true) == true))
        }.take(8)
    }
    var communities by remember(accountId) { mutableStateOf<List<BlinkCommunity>>(emptyList()) }
    var communitiesLoading by remember(accountId) { mutableStateOf(true) }
    LaunchedEffect(accountId, campus) {
        communitiesLoading = true
        communities = runCatching { BlinkCommunityRepository().discover(60) }
            .getOrDefault(emptyList())
            .filter { it.visibility.equals("public", true) &&
                (campus.isBlank() || it.university.equals(campus, ignoreCase = true)) }
        communitiesLoading = false
    }
    val openCommunities: () -> Unit = {
        runCatching {
            context.startActivity(Intent(context, com.example.ProfessionalCenterActivity::class.java)
                .putExtra("section", "communities"))
        }
        Unit
    }
    val toggleFollow: (UserProfile) -> Unit = { person ->
        if (person.id.isNotBlank()) scope.launch {
            FollowStateStore.setFollowing(person.id, person.id !in FollowStateStore.followingIds.value)
        }
    }
    val suggested = remember(people, following) {
        people.filter { it.id.isNotBlank() && it.id !in following }.take(6)
    }
    val mutedText = MaterialTheme.colorScheme.onSurfaceVariant
    val headerTitle = if (campus.isBlank()) "Students on BLINK" else "People near you"

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 114.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        if (showAllPeople) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showAllPeople = false }) { Text("‹ Back") }
                    Text("Discover students", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (people.isEmpty()) {
                item { ConnectReferenceEmpty("No students found for your campus yet.") }
            } else {
                items(people, key = { "all_" + it.id.ifBlank { it.username } }) { person ->
                    ConnectReferenceSuggestionRow(
                        person, person.id in following, isLiveDataAvailable,
                        onProfile = { onProfile(person.username) },
                        onFollow = { toggleFollow(person) },
                    )
                }
            }
        } else {
            item {
                ConnectReferenceSectionTitle(headerTitle, "See all", { showAllPeople = true })
            }
            if (people.isEmpty()) {
                item { ConnectReferenceEmpty("Students from your campus will appear here.") }
            } else {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(13.dp)
                    ) {
                        items(people.take(12), key = { "person_" + it.id.ifBlank { it.username } }) { person ->
                            ConnectReferencePerson(person, isLiveDataAvailable) {
                                onProfile(person.username)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(13.dp)) }
            item {
                ConnectReferenceSectionTitle("Campus Communities", "See all", openCommunities)
            }
            if (communities.isNotEmpty()) {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        items(communities.take(12), key = { "community_" + it.id }) { community ->
                            ConnectReferenceCommunity(community, openCommunities)
                        }
                    }
                }
            } else {
                item {
                    ConnectReferenceEmpty(
                        if (communitiesLoading) "Loading campus communities…"
                        else "No public communities yet. Explore or create one.",
                        onClick = openCommunities,
                        action = "Explore communities"
                    )
                }
            }
            item { Spacer(Modifier.height(13.dp)) }
            item {
                ConnectReferenceSectionTitle(
                    if (campus.isBlank()) "What's happening on BLINK" else "What's happening on campus",
                    "See all", onOpenAllPosts
                )
            }
            if (campusPosts.isEmpty()) {
                item { ConnectReferenceEmpty("No campus updates to show yet.", onOpenAllPosts, "Explore posts") }
            } else {
                items(campusPosts.take(2), key = { "campus_post_" + it.id }) { post ->
                    ConnectReferencePostCard(
                        post = post,
                        onProfile = { onProfile(post.authorUsername.ifBlank { post.author }) },
                        onOpen = { onOpenPost(post) },
                        onLike = { onLikePost(post.id) },
                        onComment = { onCommentPost(post.id) },
                        onSave = { onBookmarkPost(post.id) }
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item { ConnectReferenceSectionTitle("Suggested for you", "See all", { showAllPeople = true }) }
            if (suggested.isEmpty()) {
                item { ConnectReferenceEmpty("New suggestions will appear as students join.") }
            } else {
                items(suggested, key = { "suggested_" + it.id }) { person ->
                    ConnectReferenceSuggestionRow(
                        person, person.id in following, isLiveDataAvailable,
                        onProfile = { onProfile(person.username) },
                        onFollow = { toggleFollow(person) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectReferenceSectionTitle(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 13.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f),
            fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)) {
            Text(action, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(3.dp))
            Icon(Icons.Default.ArrowForwardIos, contentDescription = null,
                modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConnectReferencePerson(
    person: UserProfile, presenceFresh: Boolean, onClick: () -> Unit
) {
    Column(Modifier.width(70.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(66.dp), contentAlignment = Alignment.Center) {
            AsyncImage(model = person.avatarUrl.takeIf { it.isNotBlank() },
                contentDescription = person.fullName,
                error = androidx.compose.ui.res.painterResource(R.drawable.ic_default_profile),
                fallback = androidx.compose.ui.res.painterResource(R.drawable.ic_default_profile),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape))
            if (presenceFresh && person.onlineNow) {
                Box(Modifier.align(Alignment.BottomEnd).size(14.dp)
                    .background(MaterialTheme.colorScheme.background, CircleShape).padding(2.dp)
                    .background(BlinkOnlineGreen, CircleShape))
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(person.fullName.ifBlank { person.username }.substringBefore(" "),
            fontSize = 12.sp, maxLines = 1, fontWeight = FontWeight.SemiBold,
            overflow = TextOverflow.Ellipsis)
        Text(person.department.takeIf { it.isNotBlank() && it != "null" }
                ?: person.faculty.takeIf { it.isNotBlank() && it != "null" }
                ?: person.university,
            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ConnectReferenceCommunity(community: BlinkCommunity, onClick: () -> Unit) {
    val palette = when (community.category.lowercase()) {
        "music", "arts", "entertainment" -> listOf(Color(0xFF6845B9), Color(0xFF160D37))
        "sports", "fitness" -> listOf(Color(0xFFCD8746), Color(0xFF362111))
        "academic", "study", "education" -> listOf(Color(0xFF527E8C), Color(0xFF0F242C))
        else -> listOf(Color(0xFF496579), Color(0xFF152032))
    }
    Box(
        Modifier.width(112.dp).height(132.dp)
            .clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
            .background(Brush.verticalGradient(palette))
    ) {
        if (!community.coverUrl.isNullOrBlank()) {
            AsyncImage(model = community.coverUrl, contentDescription = community.name,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Default.Groups, contentDescription = null,
                tint = Color.White.copy(alpha = .35f),
                modifier = Modifier.size(46.dp).align(Alignment.Center))
        }
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .88f)))
        ))
        Column(Modifier.align(Alignment.BottomStart).padding(9.dp)) {
            Text(community.name, color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(community.category, color = Color.White.copy(alpha = .83f),
                fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ConnectReferencePostCard(
    post: FeedPost,
    onProfile: () -> Unit,
    onOpen: () -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onSave: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))
    ) {
        Column(Modifier.fillMaxWidth().padding(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onProfile)) {
                AsyncImage(model = post.authorAvatar.takeIf { it.isNotBlank() },
                    contentDescription = post.author,
                    error = androidx.compose.ui.res.painterResource(R.drawable.ic_default_profile),
                    fallback = androidx.compose.ui.res.painterResource(R.drawable.ic_default_profile),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(37.dp).clip(CircleShape))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(post.author, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        if (post.verificationBadge != VerificationBadge.NONE) {
                            Spacer(Modifier.width(3.dp))
                            VerifiedMark(post.verificationBadge, size = 12.dp)
                        } else if (post.isVerified) {
                            Spacer(Modifier.width(3.dp))
                            Icon(Icons.Default.Verified, contentDescription = "Verified",
                                modifier = Modifier.size(13.dp), tint = Color(0xFF248AF2))
                        }
                    }
                    Text(listOfNotNull(post.location?.takeIf { it.isNotBlank() },
                            post.timeAgo.takeIf { it.isNotBlank() })
                            .joinToString(" · ").ifBlank { "Campus update" },
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Default.MoreVert, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            if (post.text.isNotBlank()) {
                Spacer(Modifier.height(9.dp))
                Text(post.text, fontSize = 12.5.sp, lineHeight = 17.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen))
            }
            post.images.firstOrNull()?.takeIf { it.isNotBlank() }?.let { image ->
                Spacer(Modifier.height(9.dp))
                AsyncImage(model = image, contentDescription = "View campus post",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(178.dp)
                        .clip(RoundedCornerShape(9.dp)).clickable(onClick = onOpen))
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.clickable(onClick = onLike), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        modifier = Modifier.size(18.dp),
                        tint = if (post.isLiked) Color(0xFFE74C68) else MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(4.dp))
                    Text(post.likes.toString(), fontSize = 11.sp)
                }
                Row(Modifier.clickable(onClick = onComment), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Comments",
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(post.commentsCount.toString(), fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSave, modifier = Modifier.size(31.dp)) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = "Save post",
                        modifier = Modifier.size(21.dp),
                        tint = if (post.isBookmarked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun ConnectReferenceSuggestionRow(
    person: UserProfile, isFollowing: Boolean, presenceFresh: Boolean,
    onProfile: () -> Unit, onFollow: () -> Unit
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onProfile)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(49.dp)) {
            AsyncImage(model = person.avatarUrl.takeIf { it.isNotBlank() },
                contentDescription = person.fullName,
                error = androidx.compose.ui.res.painterResource(R.drawable.ic_default_profile),
                fallback = androidx.compose.ui.res.painterResource(R.drawable.ic_default_profile),
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
            if (presenceFresh && person.onlineNow) {
                Box(Modifier.size(11.dp).align(Alignment.BottomEnd)
                    .background(BlinkOnlineGreen, CircleShape))
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(person.fullName.ifBlank { person.username }, fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (person.verificationBadge != VerificationBadge.NONE) {
                    Spacer(Modifier.width(4.dp))
                    VerifiedMark(person.verificationBadge, size = 12.dp)
                }
            }
            Text(person.department.takeIf { it.isNotBlank() && it != "null" }
                    ?: person.university.ifBlank { "@"+person.username },
                fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(onClick = onFollow, modifier = Modifier.height(33.dp),
            shape = RoundedCornerShape(9.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 3.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isFollowing) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.onBackground,
                contentColor = if (isFollowing) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.background
            )) {
            Text(if (isFollowing) "Following" else "Follow", fontSize = 11.sp,
                fontWeight = FontWeight.Bold)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f),
        modifier = Modifier.padding(horizontal = 15.dp))
}

@Composable
private fun ConnectReferenceEmpty(
    message: String,
    onClick: (() -> Unit)? = null,
    action: String = ""
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onClick != null) TextButton(onClick = onClick) { Text(action) }
    }
}
