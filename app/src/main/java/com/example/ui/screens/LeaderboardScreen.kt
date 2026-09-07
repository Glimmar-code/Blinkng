package com.example.ui.screens

import com.example.R
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.LeaderboardUser
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.ui.components.BlinkVipMarkForUsername
import com.example.ui.theme.BlinkGold
import com.example.ui.theme.BlinkPink

@Composable
fun LeaderboardScreen(
    users: List<LeaderboardUser>,
    userProfile: UserProfile = UserProfile(),
    onProfileClick: (String) -> Unit,
    isDark: Boolean,
    onRefresh: () -> Unit = {}
) {
    var campusOnly by remember { mutableStateOf(false) }
    val all = remember(users) {
        users.asSequence()
            .filter { it.username.isNotBlank() }
            .sortedWith(
                compareByDescending<LeaderboardUser> { it.points }
                    .thenBy { it.username.lowercase() }
            )
            .mapIndexed { index, user -> user.copy(rank = index + 1) }
            .toList()
    }
    val campusName = userProfile.university
        .takeUnless { it.isBlank() || it.equals("null", true) }
        .orEmpty()
    val visible = remember(all, campusOnly, campusName) {
        if (campusOnly && campusName.isNotBlank()) {
            all.asSequence()
                .filter { it.university.equals(campusName, true) }
                .mapIndexed { index, user -> user.copy(rank = index + 1) }
                .toList()
        } else {
            all
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item(key = "leaderboard_header", contentType = "header") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = CircleShape, color = BlinkGold.copy(alpha = .14f)) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        null,
                        tint = BlinkGold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Leaderboard", fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text(
                        "Live rankings from real Blink activity",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, "Refresh live leaderboard")
                }
            }
        }

        item(key = "leaderboard_filters", contentType = "filters") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !campusOnly,
                    onClick = { campusOnly = false },
                    label = { Text("World") }
                )
                FilterChip(
                    selected = campusOnly,
                    enabled = campusName.isNotBlank(),
                    onClick = { campusOnly = true },
                    label = { Text(if (campusName.isBlank()) "Campus unavailable" else "My campus") }
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "${visible.size} ranked",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (visible.isNotEmpty()) {
            item(key = "leaderboard_podium", contentType = "podium") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    listOf(1, 0, 2).forEach { idx ->
                        visible.getOrNull(idx)?.let { user ->
                            Surface(
                                Modifier
                                    .weight(1f)
                                    .height(if (idx == 0) 142.dp else 118.dp)
                                    .clickable { onProfileClick(user.username) },
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (idx == 0) BlinkGold else MaterialTheme.colorScheme.outlineVariant
                                ),
                                color = if (idx == 0) BlinkGold.copy(alpha = .09f) else MaterialTheme.colorScheme.surface
                            ) {
                                Column(
                                    Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(if (idx == 0) "👑" else "#${idx + 1}", fontWeight = FontWeight.Black)
                                    AsyncImage(
                                        model = user.avatar,
                                        error = painterResource(R.drawable.ic_default_profile),
                                        fallback = painterResource(R.drawable.ic_default_profile),
                                        contentDescription = user.fullName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(if (idx == 0) 48.dp else 40.dp)
                                            .clip(CircleShape)
                                    )
                                    Text(
                                        user.fullName.ifBlank { user.username },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${user.points} pts",
                                        fontSize = 10.sp,
                                        color = BlinkPink,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (visible.isEmpty()) {
            item(key = "leaderboard_empty", contentType = "empty") {
                Box(
                    Modifier.fillMaxWidth().padding(50.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No live ranking data yet.", fontWeight = FontWeight.Bold)
                        TextButton(onClick = onRefresh) { Text("Refresh from Supabase") }
                    }
                }
            }
        } else {
            itemsIndexed(
                items = visible,
                key = { _, user -> user.username },
                contentType = { _, _ -> "leaderboard_user" }
            ) { index, user ->
                Surface(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                        .clickable { onProfileClick(user.username) },
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("#${index + 1}", modifier = Modifier.width(42.dp), fontWeight = FontWeight.Black)
                        AsyncImage(
                            model = user.avatar,
                            error = painterResource(R.drawable.ic_default_profile),
                            fallback = painterResource(R.drawable.ic_default_profile),
                            contentDescription = user.fullName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                        )
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    user.fullName.ifBlank { user.username },
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (user.verificationBadge != VerificationBadge.NONE) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.Verified,
                                        null,
                                        tint = BlinkPink,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                BlinkVipMarkForUsername(
                                    username = user.username,
                                    knownVip = if (user.isVip) true else null,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                            Text(
                                "@${user.username}",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            user.university
                                .takeUnless { it.isBlank() || it.equals("null", true) }
                                ?.let {
                                    Text(
                                        it,
                                        fontSize = 9.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${user.points}", fontWeight = FontWeight.Black, color = BlinkPink)
                            Text("points", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
