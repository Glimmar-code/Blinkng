package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.models.LeaderboardUser
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.ui.theme.BlinkGold
import com.example.ui.theme.BlinkPink

private enum class LeaderboardScope(val label: String) {
    WORLD("World"),
    CAMPUS("Campus"),
    FACULTY("Faculty"),
    LEVEL("Level")
}

@Composable
fun LeaderboardScreen(
    users: List<LeaderboardUser>,
    userProfile: UserProfile = UserProfile(),
    onProfileClick: (String) -> Unit,
    isDark: Boolean,
    onRefresh: () -> Unit = {}
) {
    val context = LocalContext.current
    var scope by remember { mutableStateOf(LeaderboardScope.WORLD) }
    var verifiedOnly by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showInfo by remember { mutableStateOf(false) }
    var previousWorldRanks by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var worldRankMovement by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    val campusName = userProfile.university.cleanLabel()
    val facultyName = userProfile.faculty.cleanLabel()
    val levelName = userProfile.academicLevel.cleanLabel()

    val world = remember(users) {
        users.asSequence()
            .filter { it.username.isNotBlank() }
            .sortedWith(
                compareByDescending<LeaderboardUser> { it.points }
                    .thenBy { it.username.lowercase() }
            )
            .mapIndexed { index, user -> user.copy(rank = index + 1) }
            .toList()
    }

    LaunchedEffect(world.map { it.username to it.rank }) {
        val current = world.associate { it.username.lowercase() to it.rank }
        if (previousWorldRanks.isNotEmpty()) {
            worldRankMovement = current.mapValues { (username, rank) ->
                val oldRank = previousWorldRanks[username]
                if (oldRank == null) 0 else oldRank - rank
            }
        }
        previousWorldRanks = current
    }

    val scoped = remember(world, scope, campusName, facultyName, levelName, verifiedOnly) {
        val source = when (scope) {
            LeaderboardScope.WORLD -> world
            LeaderboardScope.CAMPUS -> world.filter { it.university.equals(campusName, ignoreCase = true) }
            LeaderboardScope.FACULTY -> world.filter { it.faculty.equals(facultyName, ignoreCase = true) }
            LeaderboardScope.LEVEL -> world.filter { it.level.equals(levelName, ignoreCase = true) }
        }

        source.asSequence()
            .filter { !verifiedOnly || it.verificationBadge != VerificationBadge.NONE }
            .sortedWith(
                compareByDescending<LeaderboardUser> { it.points }
                    .thenBy { it.username.lowercase() }
            )
            .mapIndexed { index, user -> user.copy(rank = index + 1) }
            .toList()
    }

    // Product rule: only the Top 10 is ever rendered on the leaderboard.
    val topTen = remember(scoped) { scoped.take(10) }
    val visibleTopTen = remember(topTen, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            topTen
        } else {
            topTen.filter {
                it.username.contains(query, ignoreCase = true) ||
                    it.fullName.contains(query, ignoreCase = true) ||
                    it.university.contains(query, ignoreCase = true) ||
                    it.faculty.contains(query, ignoreCase = true)
            }
        }
    }

    val currentUser = remember(scoped, userProfile.username) {
        scoped.firstOrNull { it.username.equals(userProfile.username, ignoreCase = true) }
    }
    val nextUser = currentUser?.let { current -> scoped.getOrNull(current.rank - 2) }
    val pointsToNext = if (currentUser != null && nextUser != null) {
        (nextUser.points - currentUser.points + 1).coerceAtLeast(1)
    } else {
        0
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text("Got it") }
            },
            icon = { Icon(Icons.Default.EmojiEvents, null, tint = BlinkGold) },
            title = { Text("How the leaderboard works") },
            text = {
                Text(
                    "Blink ranks users by live leaderboard points. Higher points rank first; ties use username ordering for a stable result. " +
                        "The page intentionally shows only the Top 10 for the selected scope. Your personal rank card can still show your own position outside the Top 10."
                )
            }
        )
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
                        "Top 10 from live Blink activity",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showInfo = true }) {
                    Icon(Icons.Default.Info, "How leaderboard works")
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, "Refresh live leaderboard")
                }
            }
        }

        item(key = "leaderboard_scope", contentType = "filters") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LeaderboardScope.entries.forEach { option ->
                    val enabled = when (option) {
                        LeaderboardScope.WORLD -> true
                        LeaderboardScope.CAMPUS -> campusName.isNotBlank()
                        LeaderboardScope.FACULTY -> facultyName.isNotBlank()
                        LeaderboardScope.LEVEL -> levelName.isNotBlank()
                    }
                    FilterChip(
                        selected = scope == option,
                        enabled = enabled,
                        onClick = { scope = option },
                        label = { Text(option.label) }
                    )
                }
                FilterChip(
                    selected = verifiedOnly,
                    onClick = { verifiedOnly = !verifiedOnly },
                    leadingIcon = {
                        Icon(Icons.Default.Verified, null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text("Verified") }
                )
            }
        }

        item(key = "leaderboard_search", contentType = "search") {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Search the Top 10") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(18.dp)
            )
        }

        item(key = "leaderboard_summary", contentType = "summary") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryPill("Top 10", "${topTen.size}/10", Modifier.weight(1f))
                SummaryPill("Scope", scope.label, Modifier.weight(1f))
                SummaryPill(
                    "Leader",
                    topTen.firstOrNull()?.points?.let { "$it pts" } ?: "—",
                    Modifier.weight(1f)
                )
            }
        }

        item(key = "my_rank", contentType = "my_rank") {
            MyRankCard(
                currentUser = currentUser,
                nextUser = nextUser,
                pointsToNext = pointsToNext,
                onProfileClick = onProfileClick
            )
        }

        if (visibleTopTen.isEmpty()) {
            item(key = "leaderboard_empty", contentType = "empty") {
                Box(
                    Modifier.fillMaxWidth().padding(50.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No Top 10 result matches this view.", fontWeight = FontWeight.Bold)
                        Text(
                            "Try another scope or clear the search.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = {
                            searchQuery = ""
                            verifiedOnly = false
                            scope = LeaderboardScope.WORLD
                        }) { Text("Reset filters") }
                    }
                }
            }
        } else {
            if (searchQuery.isBlank()) {
                item(key = "leaderboard_podium", contentType = "podium") {
                    Podium(
                        topThree = visibleTopTen.take(3),
                        isDark = isDark,
                        onProfileClick = onProfileClick,
                        onShare = { shareLeaderboardUser(context, it) }
                    )
                }
            }

            val listUsers = if (searchQuery.isBlank()) visibleTopTen.drop(3) else visibleTopTen
            itemsIndexed(
                items = listUsers,
                key = { _, user -> user.username },
                contentType = { _, _ -> "leaderboard_user" }
            ) { _, user ->
                LeaderboardRow(
                    user = user,
                    isCurrentUser = user.username.equals(userProfile.username, ignoreCase = true),
                    movement = if (scope == LeaderboardScope.WORLD) {
                        worldRankMovement[user.username.lowercase()] ?: 0
                    } else {
                        0
                    },
                    onProfileClick = onProfileClick,
                    onShare = { shareLeaderboardUser(context, it) }
                )
            }
        }
    }
}

@Composable
private fun SummaryPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MyRankCard(
    currentUser: LeaderboardUser?,
    nextUser: LeaderboardUser?,
    pointsToNext: Int,
    onProfileClick: (String) -> Unit
) {
    val background = if (currentUser?.rank != null && currentUser.rank <= 10) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .then(
                if (currentUser != null) Modifier.clickable { onProfileClick(currentUser.username) }
                else Modifier
            ),
        shape = RoundedCornerShape(20.dp),
        color = background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your rank", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        currentUser?.let { "#${it.rank} · ${it.points} pts" } ?: "Unranked",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = .7f)
                ) {
                    Text(
                        if (currentUser != null && currentUser.rank <= 10) "TOP 10" else "KEEP CLIMBING",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            if (currentUser != null) {
                Spacer(Modifier.height(10.dp))
                if (currentUser.rank == 1) {
                    Text("You are leading this scope.", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                } else if (nextUser != null) {
                    Text(
                        "$pointsToNext more point${if (pointsToNext == 1) "" else "s"} to pass #${nextUser.rank}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    val target = nextUser.points.coerceAtLeast(1)
                    val progress = (currentUser.points.toFloat() / target.toFloat()).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Podium(
    topThree: List<LeaderboardUser>,
    isDark: Boolean,
    onProfileClick: (String) -> Unit,
    onShare: (LeaderboardUser) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(1, 0, 2).forEach { idx ->
            topThree.getOrNull(idx)?.let { user ->
                val isWinner = idx == 0
                val medal = when (idx) {
                    0 -> "👑"
                    1 -> "🥈"
                    else -> "🥉"
                }
                Surface(
                    Modifier
                        .weight(1f)
                        .height(if (isWinner) 160.dp else 134.dp)
                        .clickable { onProfileClick(user.username) },
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(
                        if (isWinner) 1.5.dp else 1.dp,
                        if (isWinner) BlinkGold else MaterialTheme.colorScheme.outlineVariant
                    ),
                    color = if (isWinner) {
                        BlinkGold.copy(alpha = if (isDark) .13f else .10f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ) {
                    Box {
                        Column(
                            Modifier.fillMaxSize().padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(medal, fontSize = if (isWinner) 24.sp else 19.sp)
                            AsyncImage(
                                model = user.avatar,
                                error = painterResource(R.drawable.ic_default_profile),
                                fallback = painterResource(R.drawable.ic_default_profile),
                                contentDescription = user.fullName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(if (isWinner) 54.dp else 44.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    user.fullName.ifBlank { user.username },
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (user.verificationBadge != VerificationBadge.NONE) {
                                    Spacer(Modifier.width(3.dp))
                                    Icon(
                                        Icons.Default.Verified,
                                        null,
                                        tint = BlinkPink,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            Text(
                                "#${user.rank} · ${user.points} pts",
                                fontSize = 10.sp,
                                color = BlinkPink,
                                fontWeight = FontWeight.Black
                            )
                        }
                        IconButton(
                            onClick = { onShare(user) },
                            modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                        ) {
                            Icon(Icons.Default.Share, "Share ${user.fullName}", modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(
    user: LeaderboardUser,
    isCurrentUser: Boolean,
    movement: Int,
    onProfileClick: (String) -> Unit,
    onShare: (LeaderboardUser) -> Unit
) {
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable { onProfileClick(user.username) },
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            if (isCurrentUser) 1.5.dp else 1.dp,
            if (isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        color = if (isCurrentUser) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.width(48.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text("#${user.rank}", fontWeight = FontWeight.Black)
                if (movement != 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (movement > 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = if (movement > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            kotlin.math.abs(movement).toString(),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

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
                    if (isCurrentUser) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "YOU",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    "@${user.username}",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val details = buildList {
                    user.university.cleanLabel().takeIf { it.isNotBlank() }?.let(::add)
                    user.faculty.cleanLabel().takeIf { it.isNotBlank() }?.let(::add)
                    user.level.cleanLabel().takeIf { it.isNotBlank() }?.let(::add)
                }.take(2).joinToString(" · ")
                if (details.isNotBlank()) {
                    Text(
                        details,
                        fontSize = 9.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (user.streakDays > 0 || user.coins > 0) {
                    Text(
                        buildString {
                            if (user.streakDays > 0) append("🔥 ${user.streakDays}d")
                            if (user.streakDays > 0 && user.coins > 0) append("  •  ")
                            if (user.coins > 0) append("🪙 ${user.coins}")
                        },
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text("${user.points}", fontWeight = FontWeight.Black, color = BlinkPink)
                Text("points", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { onShare(user) }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Share, "Share rank", modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

private fun String.cleanLabel(): String =
    takeUnless { isBlank() || equals("null", ignoreCase = true) }?.trim().orEmpty()

private fun shareLeaderboardUser(context: android.content.Context, user: LeaderboardUser) {
    val displayName = user.fullName.ifBlank { "@${user.username}" }
    val text = "$displayName is #${user.rank} on the Blink leaderboard with ${user.points} points."
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share leaderboard rank"))
}
