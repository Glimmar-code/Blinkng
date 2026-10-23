package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import com.blinkng.shared.BlinkAdaptiveLayoutPolicy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.blinkng.shared.xpProgress
import com.example.R
import com.example.data.models.LeaderboardUser
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.ui.components.VerifiedMark

/**
 * Reference-inspired BLINK leaderboard. Only server-supplied students, ranks, scores and
 * verification are shown. The reference picture's sample students/XP/rewards are not data.
 * Friends needs a follow-relationship-backed ranking source, so its tab is disabled.
 */
@Composable
internal fun BlinkReferenceLeaderboard(
    visibleTopTen: List<LeaderboardUser>,
    campusPodiumUsers: List<LeaderboardUser>,
    userProfile: UserProfile,
    currentUser: LeaderboardUser?,
    currentWorldRank: Int?,
    scope: String,
    campusEnabled: Boolean,
    facultyEnabled: Boolean,
    levelEnabled: Boolean,
    verifiedOnly: Boolean,
    searchQuery: String,
    worldRankMovement: Map<String, Int>,
    isDark: Boolean,
    onScopeChange: (String) -> Unit,
    onVerifiedChange: () -> Unit,
    onSearchChange: (String) -> Unit,
    onResetFilters: () -> Unit,
    onProfileClick: (String) -> Unit,
    onShare: (LeaderboardUser) -> Unit,
    onInfo: () -> Unit,
    onRefresh: () -> Unit,
) {
    var showSearch by remember { mutableStateOf(false) }
    val soft = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF7F6FC)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val purple = Color(0xFF7548E8)
    val featuredCampus = scope == "World" && campusPodiumUsers.isNotEmpty() && searchQuery.isBlank()
    val topThree = if (searchQuery.isNotBlank()) emptyList()
        else if (featuredCampus) campusPodiumUsers else visibleTopTen.take(3)
    val tableUsers = when {
        searchQuery.isNotBlank() || featuredCampus -> visibleTopTen
        else -> visibleTopTen.drop(3)
    }
    val title = if (featuredCampus) "Top 3 on Campus" else when (scope) {
        "Campus" -> "Top 3 on Campus"
        "Faculty" -> "Top 3 in Faculty"
        "Academic Level" -> "Top 3 in Your Level"
        else -> "Top 3 on BLINK"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "reference_title") {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compactHeader = BlinkAdaptiveLayoutPolicy.useCompactPhoneLayout(
                    maxWidth.value, LocalDensity.current.fontScale
                )
                if (compactHeader) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Leaderboard",
                                modifier = Modifier.weight(1f),
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "See top students and climb your rank",
                                modifier = Modifier.weight(1f),
                                fontSize = 12.sp,
                                color = muted,
                                maxLines = 3
                            )
                            IconButton(onClick = onInfo) {
                                Icon(Icons.Default.Info, contentDescription = "How rankings work")
                            }
                            IconButton(onClick = onRefresh) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh leaderboard")
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(38.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Leaderboard", fontSize = 26.sp, fontWeight = FontWeight.Black)
                            Text(
                                "See top students and climb your rank",
                                fontSize = 12.sp, color = muted, maxLines = 2
                            )
                        }
                        IconButton(onClick = onInfo) {
                            Icon(Icons.Default.Info, contentDescription = "How rankings work")
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh leaderboard")
                        }
                    }
                }
            }
        }

        item(key = "reference_scopes") {
            Surface(shape = RoundedCornerShape(50.dp), color = soft) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Global", "Campus", "Friends").forEach { tab ->
                        val selected = (tab == "Global" && scope == "World") || scope == tab
                        val available = tab == "Global" || (tab == "Campus" && campusEnabled)
                        Surface(
                            modifier = Modifier.weight(1f).then(
                                if (available) Modifier.clickable {
                                    onScopeChange(if (tab == "Global") "World" else tab)
                                } else Modifier
                            ),
                            shape = RoundedCornerShape(50.dp),
                            color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent
                        ) {
                            Box(
                                Modifier.heightIn(min = 38.dp).padding(horizontal = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    tab,
                                    color = when {
                                        selected -> MaterialTheme.colorScheme.surface
                                        available -> MaterialTheme.colorScheme.onSurface
                                        else -> muted.copy(alpha = 0.55f)
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "reference_filter_tools") {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (facultyEnabled) {
                            FilterChip(
                                selected = scope == "Faculty",
                                onClick = { onScopeChange("Faculty") },
                                label = { Text("Faculty", fontSize = 11.sp) }
                            )
                        }
                        if (levelEnabled) {
                            FilterChip(
                                selected = scope == "Academic Level",
                                onClick = { onScopeChange("Academic Level") },
                                label = { Text("Level", fontSize = 11.sp) }
                            )
                        }
                        FilterChip(
                            selected = verifiedOnly,
                            onClick = onVerifiedChange,
                            label = { Text("Verified", fontSize = 11.sp) }
                        )
                    }
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "Search the top ten")
                    }
                }
                if (showSearch) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Search the Top 10") },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }

        item(key = "reference_my_rank") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = soft
            ) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ReferenceAvatar(
                            image = userProfile.avatarUrl.ifBlank { currentUser?.avatar.orEmpty() },
                            description = userProfile.fullName,
                            size = 66,
                            borderColor = purple
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    userProfile.fullName.ifBlank { userProfile.username.ifBlank { "Your profile" } },
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (userProfile.verificationBadge != VerificationBadge.NONE) {
                                    Spacer(Modifier.width(4.dp))
                                    VerifiedMark(userProfile.verificationBadge, size = 16.dp)
                                }
                            }
                            Text(
                                userProfile.university.ifBlank { "BLINK community" },
                                color = muted, fontSize = 11.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "Level " + xpProgress(userProfile.totalXp, userProfile.xpLevel).level,
                                fontWeight = FontWeight.Bold, fontSize = 12.sp, color = purple
                            )
                        }
                        Surface(shape = RoundedCornerShape(14.dp), color = purple.copy(alpha = .14f)) {
                            Text("♛", modifier = Modifier.padding(8.dp), color = purple, fontSize = 22.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReferenceRankTile(
                            emoji = "🌐", label = "World Rank",
                            rank = currentWorldRank ?: userProfile.worldRank.takeIf { it > 0 },
                            modifier = Modifier.weight(1f)
                        )
                        ReferenceRankTile(
                            emoji = "🏛", label = "Campus Rank",
                            rank = userProfile.campusRank.takeIf { it > 0 },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    val personalPoints = currentUser?.points ?: userProfile.points
                    Text(
                        "Your score: " + personalPoints + " points • " +
                            (if (currentUser == null) "Keep participating to join the board"
                            else "Tap a student to view their profile"),
                        fontSize = 11.sp, color = muted,
                        modifier = Modifier.then(
                            if (userProfile.username.isNotBlank()) Modifier.clickable {
                                onProfileClick(userProfile.username)
                            } else Modifier
                        )
                    )
                }
            }
        }

        if (topThree.isNotEmpty()) {
            item(key = "reference_podium") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = soft,
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 13.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(title, fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text("View rankings ›", fontSize = 11.sp, color = purple)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            listOf(1, 0, 2).forEach { position ->
                                Box(Modifier.weight(1f)) {
                                    topThree.getOrNull(position)?.let { user ->
                                        ReferencePodiumStudent(
                                            user = user,
                                            position = position,
                                            purple = purple,
                                            onProfileClick = onProfileClick
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "reference_table_head") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("#", modifier = Modifier.width(26.dp), color = muted, fontSize = 11.sp)
                Text("Student", modifier = Modifier.weight(1f), color = muted, fontSize = 11.sp)
                Text("Points", modifier = Modifier.width(74.dp), color = muted, fontSize = 11.sp)
                Text("Change", modifier = Modifier.width(48.dp), color = muted, fontSize = 11.sp)
            }
        }

        if (visibleTopTen.isEmpty()) {
            item(key = "reference_empty") {
                Column(
                    Modifier.fillMaxWidth().padding(30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No ranking results for this view.", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onResetFilters) { Text("Reset filters") }
                }
            }
        }
        items(tableUsers, key = { it.username }) { user ->
            ReferenceRankingRow(
                user = user,
                isCurrent = user.username.equals(userProfile.username, ignoreCase = true),
                movement = if (scope == "World") worldRankMovement[user.username.lowercase()] ?: 0 else 0,
                onProfileClick = onProfileClick,
                onShare = onShare
            )
        }
        item(key = "reference_footer") {
            Text(
                "Rankings use real BLINK activity points; XP levels are tracked separately. " +
                    "Changes appear only when new ranks have actually been observed.",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                fontSize = 10.sp, color = muted
            )
        }
    }
}

@Composable
private fun ReferenceRankTile(emoji: String, label: String, rank: Int?, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 19.sp)
            Spacer(Modifier.width(7.dp))
            Column {
                Text(rank?.let { "#" + it } ?: "—", fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ReferenceAvatar(image: String, description: String, size: Int, borderColor: Color) {
    Surface(shape = CircleShape, border = BorderStroke(2.dp, borderColor)) {
        AsyncImage(
            model = image,
            contentDescription = description,
            error = painterResource(R.drawable.ic_default_profile),
            fallback = painterResource(R.drawable.ic_default_profile),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(CircleShape)
        )
    }
}

@Composable
private fun ReferencePodiumStudent(
    user: LeaderboardUser,
    position: Int,
    purple: Color,
    onProfileClick: (String) -> Unit
) {
    val winner = position == 0
    val medal = when (position) { 0 -> "1"; 1 -> "2"; else -> "3" }
    val accent = when (position) {
        0 -> Color(0xFFF2B93B)
        1 -> Color(0xFFAAB4C4)
        else -> Color(0xFFB77941)
    }
    Column(
        modifier = Modifier.fillMaxWidth().clickable { onProfileClick(user.username) }
            .padding(top = if (winner) 0.dp else 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (winner) Text("♛", color = accent, fontSize = 25.sp)
        Box(contentAlignment = Alignment.BottomCenter) {
            ReferenceAvatar(user.avatar, user.fullName, if (winner) 74 else 60, accent)
            Surface(
                color = accent, shape = CircleShape,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                modifier = Modifier.offset(y = 9.dp)
            ) {
                Text(
                    medal,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            user.fullName.ifBlank { user.username },
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontSize = 11.sp, fontWeight = FontWeight.Bold
        )
        Text(
            user.points.toString() + " pts",
            fontSize = 11.sp,
            color = if (winner) purple else MaterialTheme.colorScheme.onSurface
        )
        if (user.verificationBadge != VerificationBadge.NONE) {
            VerifiedMark(user.verificationBadge, size = 13.dp)
        }
    }
}

@Composable
private fun ReferenceRankingRow(
    user: LeaderboardUser,
    isCurrent: Boolean,
    movement: Int,
    onProfileClick: (String) -> Unit,
    onShare: (LeaderboardUser) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .32f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable { onProfileClick(user.username) }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(user.rank.toString(), modifier = Modifier.width(26.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        ReferenceAvatar(
            user.avatar, user.fullName, 34, MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(Modifier.width(7.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                user.fullName.ifBlank { user.username },
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (user.verificationBadge != VerificationBadge.NONE) {
                Spacer(Modifier.width(3.dp))
                VerifiedMark(user.verificationBadge, size = 12.dp)
            }
        }
        Text(
            user.points.toString(),
            modifier = Modifier.width(60.dp),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            if (movement == 0) "—"
            else if (movement > 0) "▲ " + movement
            else "▼ " + -movement,
            modifier = Modifier.width(30.dp),
            fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = when {
                movement > 0 -> Color(0xFF16A34A)
                movement < 0 -> Color(0xFFE11D48)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Box {
            IconButton(onClick = { expanded = true }, modifier = Modifier.size(22.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options for " + user.username, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Share ranking") },
                    onClick = { expanded = false; onShare(user) }
                )
            }
        }
    }
}