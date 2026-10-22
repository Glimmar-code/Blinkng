package com.example.ui.screens

import com.example.R
import android.widget.Toast
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.example.data.repository.FollowStateStore
import kotlinx.coroutines.launch
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import com.example.data.models.FeedPost
import com.example.data.models.StudyCircleSummary
import com.example.data.repository.StudyCircleRepository
import com.example.ui.components.StudyCirclesPanel
import com.example.ui.components.PostCard
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.UserProfile
import com.example.ui.theme.BlinkOnlineGreen

/**
 * Visual-first campus discovery surface. All student identities are supplied by the
 * existing Supabase-backed ConnectSection; no fabricated members, posts or counts.
 *
 * Community imagery, membership, and campus post ingestion will be connected in
 * follow-up phases. Legacy Connect Hub tools are still reachable via onOpenHub.
 * The app's existing five-item navigation bar is rendered by MainActivity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectDiscoveryLanding(
    profiles: List<UserProfile>,
    currentUsername: String,
    myCampus: String,
    userAvatar: String,
    onSearchClick: () -> Unit,
    onOpenGame: () -> Unit,
    onOpenNotifications: () -> Unit,
    onProfileClick: (String) -> Unit,
    onOpenHub: () -> Unit,
    onOpenCampusFeed: () -> Unit,
    onMessageStudent: (String, String?, String?) -> Unit = { _, _, _ -> },
    hasUnreadNotifications: Boolean = false,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    posts: List<FeedPost> = emptyList(),
    isDark: Boolean = false,
    onLikePost: (String) -> Unit = {},
    onCommentPost: (String) -> Unit = {},
    onBookmarkPost: (String) -> Unit = {},
    onRepostPost: (String) -> Unit = {},
    onSharePost: (String) -> Unit = {},
    onOptionsClick: (FeedPost) -> Unit = {},
    onVotePoll: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val followingIds by FollowStateStore.followingIds.collectAsState()
    val followPending = remember { mutableStateMapOf<String, Boolean>() }
    var selectedCircleName by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(currentUsername) {
        if (currentUsername.isNotBlank()) FollowStateStore.refresh()
    }
    fun toggleFollow(id: String, follow: Boolean) {
        if (id.isBlank() || followPending[id] == true) return
        followPending[id] = true
        scope.launch {
            val success = FollowStateStore.setFollowing(id, follow)
            followPending.remove(id)
            if (!success) {
                Toast.makeText(context, "Could not update follow. Try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var circlesRefreshKey by remember { mutableIntStateOf(0) }
    var showStudyCircles by rememberSaveable { mutableStateOf(false) }
    var circles by remember { mutableStateOf<List<StudyCircleSummary>>(emptyList()) }
    var circlesLoading by remember { mutableStateOf(true) }
    var circlesLoadFailed by remember { mutableStateOf(false) }
    val circlesRepository = remember { StudyCircleRepository() }
    LaunchedEffect(currentUsername, circlesRefreshKey) {
        // Reads existing server-authorized RPC; no new schema or sample data.
        // Refresh when user deliberately pulls to refresh, but not on every recomposition.
        circlesLoading = true
        runCatching { circlesRepository.fetchCircles(limit = 12) }
            .onSuccess { circles = it; circlesLoadFailed = false }
            .onFailure { circlesLoadFailed = true }
        circlesLoading = false
    }

    val peers = remember(profiles, currentUsername) {
        profiles.filter { profile ->
            profile.username.isNotBlank() &&
                !profile.username.equals(currentUsername, ignoreCase = true)
        }.distinctBy { it.id.ifBlank { it.username.lowercase() } }
    }
    val campusPeers = remember(peers, myCampus) {
        peers.filter { com.blinkng.shared.discovery.BlinkCampusFeedPolicy.isSameCampus(it.university, myCampus) }
    }
    // The study-circle directory is global. Only label a circle as a campus
    // community when its creator's public profile matches the viewer's school.
    // Otherwise show honest discovery topics rather than another school's club.
    val campusCircles = remember(circles, profiles, myCampus) {
        if (myCampus.isBlank()) emptyList()
        else {
            val campusOwners = profiles.asSequence()
                .filter {
                    com.blinkng.shared.discovery.BlinkCampusFeedPolicy.isSameCampus(it.university, myCampus)
                }
                .map { it.id }
                .filter { it.isNotBlank() }
                .toSet()
            circles.filter { it.ownerId in campusOwners }
        }
    }

    val people = remember(peers, campusPeers, selectedTab) {
        when (selectedTab) {
            1 -> campusPeers
            2 -> peers.sortedByDescending { it.followerCount }
            else -> campusPeers.ifEmpty { peers }
        }
    }
    val suggested = remember(peers, campusPeers) {
        (campusPeers + peers).distinctBy { it.id.ifBlank { it.username.lowercase() } }
    }
    val campusPosts = remember(posts, profiles, myCampus, selectedTab) {
        val profileByUsername = profiles.associateBy { it.username.trim().removePrefix("@").lowercase() }
        val campusOnly = posts.asSequence()
            .filter { !it.isReel && !it.isSponsored }
            .filter { post ->
                val key = post.authorUsername.trim().removePrefix("@").lowercase()
                val author = profileByUsername[key]
                myCampus.isNotBlank() && author != null &&
                    com.blinkng.shared.discovery.BlinkCampusFeedPolicy.isSameCampus(author.university, myCampus)
            }
            .toList()
        if (selectedTab == 2) {
            campusOnly.sortedWith(
                compareByDescending<FeedPost> { com.blinkng.shared.discovery.BlinkCampusFeedPolicy.previewScore(it.likes, it.commentsCount, it.sharesCount) }
                    .thenByDescending { it.createdAt }
            ).take(3)
        } else {
            campusOnly.sortedByDescending { it.createdAt }.take(3)
        }
    }
    val textColor = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 9.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConnectRoundAvatar(
                avatar = userAvatar,
                description = "Your profile",
                modifier = Modifier.size(39.dp).clickable { onProfileClick(currentUsername) }
            )
            Text(
                "BLINK",
                modifier = Modifier.weight(1f).padding(start = 14.dp),
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
                letterSpacing = 4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = textColor
            )
            IconButton(onClick = onSearchClick, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Default.Search, contentDescription = "Search students", tint = textColor)
            }
            IconButton(onClick = onOpenGame, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Default.SportsEsports, contentDescription = "Game", tint = textColor)
            }
            Box(Modifier.size(42.dp)) {
                IconButton(onClick = onOpenNotifications, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications", tint = textColor)
                }
                if (hasUnreadNotifications) {
                    Box(
                        Modifier.size(7.dp).align(Alignment.TopEnd)
                            .offset(x = (-7).dp, y = 7.dp)
                            .background(Color(0xFFE53935), CircleShape)
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            listOf("Connect", "Campus", "Trending").forEachIndexed { index, title ->
                Column(
                    modifier = Modifier.weight(1f).clickable { selectedTab = index },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        title,
                        modifier = Modifier.padding(vertical = 11.dp),
                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedTab == index) textColor else muted,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(.72f)
                            .height(2.dp)
                            .background(if (selectedTab == index) textColor else Color.Transparent)
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { circlesRefreshKey++; onRefresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                item {
                    ConnectDiscoveryTitle(
                        title = when (selectedTab) {
                            1 -> "Students on your campus"
                            2 -> "Popular students"
                            else -> "People near you"
                        },
                        onSeeAll = onSearchClick
                    )
                    Text(
                        "Based on university profiles, not precise location",
                        modifier = Modifier.padding(start = 16.dp, bottom = 10.dp),
                        fontSize = 11.sp,
                        color = muted
                    )
                    if (people.isEmpty()) {
                        Text(
                            if (selectedTab == 1) "No students from your university yet"
                            else "Students will appear as they join BLINK",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 19.dp),
                            color = muted,
                            fontSize = 13.sp
                        )
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(people.take(15), key = { it.id.ifBlank { it.username } }) { profile ->
                                ConnectPeerPreview(profile) { onProfileClick(profile.username) }
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(15.dp))
                    ConnectDiscoveryTitle(title = "Campus Communities", onSeeAll = {
                        selectedCircleName = ""
                        showStudyCircles = true
                    })
                    if (circlesLoading) {
                        Text(
                            "Loading communities…",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            fontSize = 12.sp, color = muted
                        )
                    } else if (circlesLoadFailed) {
                        Text(
                            "Communities could not be loaded. Pull down to retry.",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            fontSize = 12.sp, color = muted
                        )
                    } else if (campusCircles.isEmpty()) {
                        Text(
                            "No study circles from your campus are available yet. Browse topics below.",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            fontSize = 12.sp, color = muted
                        )
                    }
                    if (!circlesLoading && !circlesLoadFailed && campusCircles.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            items(campusCircles, key = { it.id }) { circle ->
                                ConnectCommunityPreview(
                                    title = circle.name,
                                    icon = communityIcon(circle.name),
                                    colors = communityColors(circle.name),
                                    label = "${circle.memberCount} members",
                                    photoUrl = illustrativeCoverForTopic(circle.name),
                                    onClick = {
                                        selectedCircleName = circle.name
                                        showStudyCircles = true
                                    }
                                )
                            }
                        }
                    } else if (!circlesLoading) {
                        // Topic cards reproduce the reference's photo-led visual layout.
                        // These are discovery shortcuts, NOT created clubs or member claims.
                        val topics = listOf("Music Club", "Sports", "Study Buddies", "Photography")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            items(topics) { topic ->
                                ConnectCommunityPreview(
                                    title = topic,
                                    icon = communityIcon(topic),
                                    colors = communityColors(topic),
                                    label = "Explore topic",
                                    photoUrl = illustrativeCoverForTopic(topic),
                                    onClick = {
                                        selectedCircleName = when (topic) {
                                            "Music Club" -> "Music"
                                            "Study Buddies" -> "Study"
                                            else -> topic
                                        }
                                        showStudyCircles = true
                                    }
                                )
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(17.dp))
                    ConnectDiscoveryTitle(
                        title = if (selectedTab == 2) "Trending on your campus"
                            else "What's happening on campus",
                        onSeeAll = onOpenCampusFeed
                    )
                    if (campusPosts.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(15.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "No campus posts available yet",
                                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    "Posts from students with your university on their BLINK profile will appear here.",
                                    fontSize = 12.sp, color = muted, lineHeight = 17.sp
                                )
                                TextButton(onClick = onOpenCampusFeed) {
                                    Text("Explore BLINK posts")
                                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                                }
                            }
                        }
                    }
                }
                items(campusPosts, key = { "campus-post-${it.id}" }) { post ->
                    PostCard(
                        post = post,
                        isDark = isDark,
                        onLike = { onLikePost(post.id) },
                        onComment = { onCommentPost(post.id) },
                        onBookmark = { onBookmarkPost(post.id) },
                        onRepost = { onRepostPost(post.id) },
                        onShare = { onSharePost(post.id) },
                        onOptionsClick = { onOptionsClick(post) },
                        onProfileClick = onProfileClick,
                        onVotePoll = onVotePoll
                    )
                }
                item {
                    Spacer(Modifier.height(15.dp))
                    ConnectDiscoveryTitle(title = "Suggested for you", onSeeAll = onSearchClick)
                    if (suggested.isEmpty()) {
                        Text(
                            "Recommendations will appear when students join.",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            fontSize = 12.sp, color = muted
                        )
                    } else {
                        suggested.take(3).forEach { profile ->
                            ConnectSuggestedPeer(
                                profile = profile,
                                following = profile.id in followingIds,
                                pending = followPending[profile.id] == true,
                                onProfileClick = { onProfileClick(profile.username) },
                                onFollow = { toggleFollow(profile.id, true) },
                                onUnfollow = { toggleFollow(profile.id, false) },
                                onMessage = {
                                    onMessageStudent(profile.username, profile.fullName, profile.avatarUrl)
                                }
                            )
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenHub).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Explore Connect Hub", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    "Spin, Roommates, Housing, Mentors, Reading Mates and challenges",
                                    fontSize = 12.sp, lineHeight = 17.sp, color = muted
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = "Open Connect Hub")
                        }
                    }
                }
            }
        }
        if (showStudyCircles) {
            ModalBottomSheet(onDismissRequest = {
                showStudyCircles = false
                circlesRefreshKey++
            }) {
                Column(
                    Modifier.fillMaxWidth().fillMaxHeight(.85f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp).padding(bottom = 25.dp)
                ) {
                    StudyCirclesPanel(initialQuery = selectedCircleName)
                }
            }
        }
    }
}

@Composable
private fun ConnectDiscoveryTitle(title: String, onSeeAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 9.dp, top = 8.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            color = MaterialTheme.colorScheme.onSurface
        )
        TextButton(onClick = onSeeAll, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp)) {
            Text("See all", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(
                Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConnectRoundAvatar(avatar: String, description: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(CircleShape), contentAlignment = Alignment.Center) {
        if (avatar.isBlank()) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape) {
                Icon(
                    Icons.Default.Person, contentDescription = description,
                    modifier = Modifier.fillMaxSize().padding(9.dp)
                )
            }
        } else {
            AsyncImage(
                model = avatar,
                contentDescription = description,
                error = painterResource(R.drawable.ic_default_profile),
                fallback = painterResource(R.drawable.ic_default_profile),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ConnectPeerPreview(profile: UserProfile, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(78.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(69.dp)) {
            ConnectRoundAvatar(
                avatar = profile.avatarUrl,
                description = profile.fullName,
                modifier = Modifier.fillMaxSize()
            )
            if (profile.onlineNow) {
                Box(
                    Modifier.size(13.dp).align(Alignment.BottomEnd)
                        .background(Color.White, CircleShape).padding(2.dp)
                        .background(BlinkOnlineGreen, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            profile.fullName.ifBlank { profile.username }.substringBefore(" "),
            fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
        Text(
            profile.department.ifBlank { profile.faculty.ifBlank { profile.university } },
            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ConnectCommunityPreview(
    title: String,
    icon: ImageVector,
    colors: List<Color>,
    label: String,
    photoUrl: String? = null,
    onClick: (() -> Unit)? = null
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(
        modifier = Modifier.width(126.dp).height(148.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Brush.verticalGradient(colors))
            .then(clickModifier)
    ) {
        // Artwork is intentionally illustrative until communities have real
        // user-supplied cover images. Never invent members or use another user's photo.
        Box(
            Modifier.size(105.dp).align(Alignment.TopEnd)
                .offset(x = 27.dp, y = (-22).dp)
                .background(Color.White.copy(alpha = .14f), CircleShape)
        )
        Box(
            Modifier.size(68.dp).align(Alignment.CenterStart)
                .offset(x = (-29).dp, y = 18.dp)
                .background(Color.Black.copy(alpha = .15f), CircleShape)
        )
        Surface(
            modifier = Modifier.size(71.dp).align(Alignment.Center),
            color = Color.White.copy(alpha = .12f),
            shape = RoundedCornerShape(24.dp)
        ) {
            Icon(
                icon, contentDescription = null,
                modifier = Modifier.padding(16.dp),
                tint = Color.White
            )
        }
        if (photoUrl != null) {
            // Small, resized licensed illustrations: never claim these show actual
            // members or events from the user's university. Coil handles caching.
            AsyncImage(
                model = photoUrl,
                contentDescription = "Illustrative community topic photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = .04f), Color.Transparent, Color.Black.copy(alpha = .62f))
                )
            )
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .82f))))
                .padding(horizontal = 9.dp, vertical = 9.dp)
        ) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            Text(label, color = Color.White.copy(alpha = .83f), fontSize = 10.sp, maxLines = 1)
        }
    }
}

/**
 * Display-only stock photography for unowned category previews; these are not
 * community covers and must never imply a club has members. URLs use small crops
 * (about 260x320) to conserve mobile data; Coil caches files automatically.
 * See docs/connect-community-photo-credits.md for license/source.
 */
private fun illustrativeCoverForTopic(topic: String): String? {
    val source = when {
        topic.contains("music", ignoreCase = true) -> "photo-1648160091944-54aab390a7ae"
        topic.contains("sport", ignoreCase = true) || topic.contains("basket", ignoreCase = true) ->
            "photo-1651680550893-f676f4801b64"
        topic.contains("study", ignoreCase = true) || topic.contains("reading", ignoreCase = true) ->
            "photo-1522202176988-66273c2fd55f"
        topic.contains("photo", ignoreCase = true) || topic.contains("media", ignoreCase = true) ->
            "photo-1492691527719-9d1e07e534b4"
        else -> return null
    }
    return "https://images.unsplash.com/$source?auto=format&fit=crop&w=260&h=320&q=65"
}

private fun communityIcon(name: String): ImageVector = when {
    name.contains("music", ignoreCase = true) -> Icons.Default.MusicNote
    name.contains("sport", ignoreCase = true) || name.contains("basket", ignoreCase = true) ->
        Icons.Default.SportsBasketball
    name.contains("photo", ignoreCase = true) || name.contains("media", ignoreCase = true) ->
        Icons.Default.PhotoCamera
    else -> Icons.Default.MenuBook
}

private fun communityColors(name: String): List<Color> = when {
    name.contains("music", ignoreCase = true) -> listOf(Color(0xFF8244C0), Color(0xFF281344))
    name.contains("sport", ignoreCase = true) -> listOf(Color(0xFFC48D63), Color(0xFF392A25))
    name.contains("photo", ignoreCase = true) || name.contains("media", ignoreCase = true) ->
        listOf(Color(0xFF5E7AA1), Color(0xFF1D2944))
    else -> listOf(Color(0xFF7C9DAD), Color(0xFF182937))
}

@Composable
private fun ConnectSuggestedPeer(
    profile: UserProfile,
    following: Boolean,
    pending: Boolean,
    onProfileClick: () -> Unit,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    onMessage: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConnectRoundAvatar(
            avatar = profile.avatarUrl,
            description = profile.fullName,
            modifier = Modifier.size(46.dp).clickable(onClick = onProfileClick)
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f).clickable(onClick = onProfileClick)) {
            Text(
                profile.fullName.ifBlank { profile.username },
                fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                profile.university.ifBlank { profile.department.ifBlank { "BLINK student" } },
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable(
                    enabled = !pending,
                    onClick = {
                        when {
                            profile.id.isBlank() -> onProfileClick()
                            following -> menuExpanded = true
                            else -> onFollow()
                        }
                    }
                )
            ) {
                Text(
                    when {
                        pending -> "..."
                        profile.id.isBlank() -> "View"
                        following -> "Interact"
                        else -> "Follow"
                    },
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.surface,
                    maxLines = 1
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Message") },
                    leadingIcon = { Icon(Icons.Default.Send, contentDescription = null) },
                    onClick = { menuExpanded = false; onMessage() }
                )
                DropdownMenuItem(
                    text = { Text("View profile") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    onClick = { menuExpanded = false; onProfileClick() }
                )
                DropdownMenuItem(
                    text = { Text("I follow · Unfollow") },
                    leadingIcon = { Icon(Icons.Default.HowToReg, contentDescription = null) },
                    onClick = { menuExpanded = false; onUnfollow() }
                )
            }
        }
    }
}
