package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.LeaderboardUser
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.ui.components.VerifiedMark
import com.example.ui.theme.BlinkBlue
import com.example.ui.theme.BlinkGold
import com.example.ui.theme.BlinkPink
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LeaderboardScreen(
    users: List<LeaderboardUser>,
    userProfile: UserProfile = UserProfile(),
    onProfileClick: (String) -> Unit,
    isDark: Boolean,
    onRefresh: () -> Unit = {}
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val ranked = users
        .filter { it.username.isNotBlank() }
        .sortedWith(compareByDescending<LeaderboardUser> { it.points }.thenBy { it.username.lowercase() })
        .mapIndexed { index, user -> user.copy(rank = index + 1) }
    val campus = ranked.filter { it.university.isNotBlank() && it.university.equals(userProfile.university, ignoreCase = true) }
    val world = ranked

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.EmojiEvents,
                contentDescription = "Leaderboard",
                tint = BlinkGold,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Leaderboard", fontSize = 22.sp, fontWeight = FontWeight.Black)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)
                    ) {
                        Text(
                            "Live Supabase",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "Snapshots refresh hourly",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh live leaderboard")
            }
        }

        TabRow(selectedTabIndex = pagerState.currentPage) {
            Tab(selected = pagerState.currentPage == 0, onClick = { scope.launch { pagerState.animateScrollToPage(0) } }, text = { Text("Campus") })
            Tab(selected = pagerState.currentPage == 1, onClick = { scope.launch { pagerState.animateScrollToPage(1) } }, text = { Text("World") })
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            val list = if (page == 0) campus else world
            val myUser = list.firstOrNull { it.username.equals(userProfile.username, ignoreCase = true) }
            val title = if (page == 0) "Your Campus Points" else "Your World Points"
            val accent = if (page == 0) BlinkGold else BlinkBlue

            LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                item {
                    Card(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${myUser?.points ?: 0}", fontSize = 28.sp, fontWeight = FontWeight.Black, color = accent)
                                Spacer(Modifier.width(8.dp))
                                Text("points", fontSize = 13.sp)
                                Spacer(Modifier.weight(1f))
                                Text("#${myUser?.rank ?: "—"}", fontWeight = FontWeight.Bold)
                            }
                            Text("Create posts, like, comment, follow, message and view content to earn points.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (list.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text("No real leaderboard data yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                itemsIndexed(list) { index, user ->
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { onProfileClick(user.username) },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("#${index + 1}", Modifier.width(42.dp), fontWeight = FontWeight.Black)
                            AsyncImage(model = user.avatar, contentDescription = user.fullName, contentScale = ContentScale.Crop, modifier = Modifier.size(46.dp).clip(CircleShape))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(user.fullName.ifBlank { user.username }, fontWeight = FontWeight.Bold)
                                    if (user.verificationBadge != VerificationBadge.NONE) {
                                        Spacer(Modifier.width(4.dp))
                                        VerifiedMark(badge = user.verificationBadge, size = 13.dp)
                                    }
                                }
                                Text("@${user.username} • ${user.university}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${user.points} points", fontSize = 12.sp, color = BlinkPink, fontWeight = FontWeight.SemiBold)
                            }
                            Text("${user.streakDays}d 🔥", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
