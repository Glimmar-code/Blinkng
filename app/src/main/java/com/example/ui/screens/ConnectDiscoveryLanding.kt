package com.example.ui.screens

import com.example.R
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
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val campusPeers = remember(profiles, myCampus) {
        if (myCampus.isBlank() || myCampus.equals("null", ignoreCase = true)) emptyList()
        else profiles.filter {
            com.blinkng.shared.discovery.BlinkUniversitySearch.sameCampus(it.university, myCampus)
        }
    }
    val people = remember(profiles, campusPeers, selectedTab) {
        when (selectedTab) {
            1 -> campusPeers
            2 -> profiles.filter { it.onlineNow }
            else -> campusPeers.ifEmpty { profiles }
        }
    }
    val suggested = remember(profiles, campusPeers) {
        (campusPeers + profiles).distinctBy { it.id.ifBlank { it.username.lowercase() } }
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
            IconButton(onClick = onOpenNotifications, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications", tint = textColor)
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
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                item {
                    ConnectDiscoveryTitle(
                        title = if (selectedTab == 1) "Students on your campus" else "People near you",
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
                    ConnectDiscoveryTitle(title = "Campus Communities", onSeeAll = onOpenHub)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        item {
                            ConnectCommunityPreview(
                                "Music Club", Icons.Default.MusicNote,
                                listOf(Color(0xFF7034BD), Color(0xFF1E103D)), "Coming soon"
                            )
                        }
                        item {
                            ConnectCommunityPreview(
                                "Sports", Icons.Default.SportsBasketball,
                                listOf(Color(0xFFE0A261), Color(0xFF44301F)), "Coming soon"
                            )
                        }
                        item {
                            ConnectCommunityPreview(
                                "Study Buddies", Icons.Default.MenuBook,
                                listOf(Color(0xFF67849E), Color(0xFF182937)), "Open tools",
                                onClick = onOpenHub
                            )
                        }
                        item {
                            ConnectCommunityPreview(
                                "Photography", Icons.Default.PhotoCamera,
                                listOf(Color(0xFFBF8B72), Color(0xFF261D28)), "Coming soon"
                            )
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(17.dp))
                    ConnectDiscoveryTitle(title = "What's happening on campus", onSeeAll = onOpenCampusFeed)
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(15.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Campus moments and events",
                                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "Campus posts will appear here when the live campus feed is connected. Explore BLINK posts for now.",
                                fontSize = 12.sp, color = muted, lineHeight = 17.sp
                            )
                            Spacer(Modifier.height(9.dp))
                            TextButton(onClick = onOpenCampusFeed) {
                                Text("Explore posts")
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        }
                    }
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
                            ConnectSuggestedPeer(profile) { onProfileClick(profile.username) }
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
    onClick: (() -> Unit)? = null
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(
        modifier = Modifier.width(126.dp).height(148.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Brush.verticalGradient(colors))
            .then(clickModifier)
    ) {
        Icon(
            icon, contentDescription = null,
            modifier = Modifier.size(55.dp).align(Alignment.Center),
            tint = Color.White.copy(alpha = .83f)
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

@Composable
private fun ConnectSuggestedPeer(profile: UserProfile, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConnectRoundAvatar(
            avatar = profile.avatarUrl,
            description = profile.fullName,
            modifier = Modifier.size(46.dp)
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
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
        Surface(shape = RoundedCornerShape(9.dp), color = MaterialTheme.colorScheme.onSurface) {
            Text(
                "View", modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.surface
            )
        }
    }
}
