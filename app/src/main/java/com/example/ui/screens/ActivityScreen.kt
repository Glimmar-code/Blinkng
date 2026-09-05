package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.ActivityItem
import com.example.data.models.NotificationFilter
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.data.supabase.SupabaseService
import com.example.ui.components.VerifiedMark
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkPurple
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    activities: List<ActivityItem>,
    onBack: () -> Unit,
    onProfileClick: (String) -> Unit,
    onNotificationClick: (ActivityItem) -> Unit,
    isDark: Boolean,
    isConnected: Boolean = true,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRefresh: () -> Unit = {},
    onMarkAllRead: () -> Unit = {}
) {
    var filter by remember { mutableStateOf(NotificationFilter.ALL) }
    val profileService = remember { SupabaseService() }
    var actorProfiles by remember { mutableStateOf<Map<String, UserProfile>>(emptyMap()) }

    // Activity rows store actor_id for integrity. Resolve those IDs to public profile
    // details only while this screen is visible so UUIDs are never rendered to people.
    LaunchedEffect(activities, isConnected) {
        if (!isConnected || activities.isEmpty()) return@LaunchedEffect

        val actors = activities
            .map { it.user.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val next = actorProfiles.toMutableMap()
        actors.filterNot(next::containsKey)
            .chunked(6)
            .forEach { chunk ->
                val resolved = coroutineScope {
                    chunk.map { actor ->
                        async { actor to profileService.fetchProfileById(actor) }
                    }.awaitAll()
                }
                resolved.forEach { (actor, profile) ->
                    if (profile != null && profile.username.isNotBlank()) {
                        next[actor] = profile
                    }
                }
                // Paint progressively instead of waiting for every historical actor.
                actorProfiles = next.toMap()
            }
    }

    val visible = remember(activities, filter) {
        if (filter == NotificationFilter.ALL) activities
        else activities.filter { resolvedNotificationCategory(it) == filter }
    }
    val unread = activities.count { it.isUnread }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                title = {
                    Column {
                        Text("Notifications", fontWeight = FontWeight.Bold)
                        Text(
                            if (unread == 0) "You're all caught up" else "$unread unread",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (unread > 0) {
                        IconButton(onClick = onMarkAllRead) {
                            Icon(Icons.Default.Check, "Mark all as read")
                        }
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(NotificationFilter.values()) { item ->
                    val selected = filter == item
                    FilterChip(
                        selected = selected,
                        onClick = { filter = item },
                        label = { Text(item.label, fontSize = 12.sp) },
                        leadingIcon = { Text(item.icon, fontSize = 12.sp) }
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (!isConnected) {
                NotificationConnectionNotice()
            } else if (!errorMessage.isNullOrBlank() && activities.isNotEmpty()) {
                NotificationRefreshNotice(onRefresh)
            }

            when {
                isLoading && activities.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                !errorMessage.isNullOrBlank() && activities.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Refresh,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Notifications couldn't load", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            errorMessage,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = onRefresh) { Text("Try again") }
                    }
                }

                visible.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = BlinkPink.copy(alpha = .12f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                null,
                                tint = BlinkPink,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("No notifications", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "Likes, comments, follows and profile views will appear here.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visible, key = { it.id }) { item ->
                        NotificationCard(
                            item = item,
                            profile = actorProfiles[item.user],
                            onProfileClick = onProfileClick,
                            onNotificationClick = onNotificationClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationConnectionNotice() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(9.dp))
            Column {
                Text("Notifications are offline", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    "Showing your last available activity.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NotificationRefreshNotice(onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Couldn't refresh. Showing recent activity.",
            modifier = Modifier.weight(1f),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onRefresh) { Text("Retry") }
    }
}

@Composable
private fun NotificationCard(
    item: ActivityItem,
    profile: UserProfile?,
    onProfileClick: (String) -> Unit,
    onNotificationClick: (ActivityItem) -> Unit
) {
    val category = resolvedNotificationCategory(item)
    val accent = when (category) {
        NotificationFilter.LIKES -> BlinkPink
        NotificationFilter.COMMENTS -> BlinkPurple
        NotificationFilter.MARKET -> Color(0xFF22C55E)
        NotificationFilter.ALL -> BlinkPink
    }

    val rawUser = item.user.trim().removePrefix("@")
    val username = profile?.username?.takeIf { it.isNotBlank() }
        ?: rawUser.takeUnless(::looksLikeUuid).orEmpty()
    val displayName = profile?.fullName?.takeIf { it.isNotBlank() }
        ?: username.takeIf { it.isNotBlank() }
        ?: "Someone on Blink"
    val avatar = profile?.avatarUrl?.takeIf { it.isNotBlank() } ?: item.avatar
    val verificationBadge = profile?.verificationBadge ?: item.verificationBadge
    val isActive = profile?.onlineNow == true
    val canOpenProfile = username.isNotBlank()
    val initial = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "B"

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (item.isUnread) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = .38f)
        } else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (item.isUnread) accent.copy(alpha = .32f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNotificationClick(item) }
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(50.dp)
                    .clickable(enabled = canOpenProfile) { onProfileClick(username) }
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            initial,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (avatar.isNotBlank()) {
                    AsyncImage(
                        model = avatar,
                        contentDescription = "$displayName profile picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = accent,
                    modifier = Modifier
                        .size(18.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(category.icon, fontSize = 8.sp)
                    }
                }

                if (isActive) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF22C55E),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .size(13.dp)
                            .align(Alignment.BottomEnd)
                    ) {}
                }
            }

            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable(enabled = canOpenProfile) { onProfileClick(username) }
                    )
                    if (verificationBadge != VerificationBadge.NONE) {
                        Spacer(Modifier.width(4.dp))
                        VerifiedMark(verificationBadge, size = 12.dp)
                    }
                }

                Spacer(Modifier.height(3.dp))
                Text(
                    item.action,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!item.previewText.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.previewText!!,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (username.isNotBlank()) {
                        Text(
                            "@$username",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "  •  ",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isActive) {
                        Text(
                            "Active",
                            fontSize = 10.5.sp,
                            color = Color(0xFF22C55E),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "  •  ",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        item.time,
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (item.isUnread) {
                Surface(shape = CircleShape, color = accent, modifier = Modifier.size(9.dp)) {}
            }
        }
    }
}

private fun resolvedNotificationCategory(item: ActivityItem): NotificationFilter {
    if (item.category != NotificationFilter.ALL) return item.category

    val action = item.action.lowercase()
    return when {
        item.targetType.equals("market", ignoreCase = true) || action.contains("market") -> NotificationFilter.MARKET
        action.contains("like") || action.contains("save") || action.contains("repost") -> NotificationFilter.LIKES
        action.contains("comment") || action.contains("reply") || action.contains("mention") -> NotificationFilter.COMMENTS
        else -> NotificationFilter.ALL
    }
}

private fun looksLikeUuid(value: String): Boolean =
    value.length == 36 && value.count { it == '-' } == 4
