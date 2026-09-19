package com.example.ui.screens

import com.example.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.ActivityItem
import com.example.data.models.NotificationFilter
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.data.supabase.AdminAnnouncementDetail
import com.example.data.supabase.AdminAnnouncementService
import com.example.data.supabase.SupabaseService
import com.example.ui.components.VerifiedMark
import com.example.ui.components.BlinkVipMarkForUsername
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkPurple
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private enum class NotificationViewMode(val label: String) {
    ALL("All"),
    UNREAD("Unread")
}

private data class NotificationSection(
    val title: String,
    val items: List<ActivityItem>,
    val showCaughtUpAfter: Boolean = false
)

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
    var viewMode by remember { mutableStateOf(NotificationViewMode.ALL) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val profileService = remember { SupabaseService() }
    val announcementService = remember { AdminAnnouncementService() }
    val scope = rememberCoroutineScope()
    var actorProfiles by remember { mutableStateOf<Map<String, UserProfile>>(emptyMap()) }
    var selectedAdminMessage by remember { mutableStateOf<AdminAnnouncementDetail?>(null) }
    var adminMessageLoading by remember { mutableStateOf(false) }
    var adminMessageError by remember { mutableStateOf<String?>(null) }

    selectedAdminMessage?.let { detail ->
        AdminMessageDetailScreen(
            detail = detail,
            onBack = { selectedAdminMessage = null }
        )
        return
    }

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
                actorProfiles = next.toMap()
            }
    }

    val unread = activities.count { it.isUnread }
    val filtered = remember(activities, actorProfiles, filter, viewMode, searchQuery) {
        activities.filter { item ->
            val categoryMatches = filter == NotificationFilter.ALL || resolvedNotificationCategory(item) == filter
            val unreadMatches = viewMode == NotificationViewMode.ALL || item.isUnread
            val query = searchQuery.trim().lowercase()
            val profile = actorProfiles[item.user]
            val searchable = buildString {
                append(item.action)
                append(' ')
                append(item.previewText.orEmpty())
                append(' ')
                append(profile?.fullName.orEmpty())
                append(' ')
                append(profile?.username.orEmpty())
                append(' ')
                append(item.user)
            }.lowercase()
            val searchMatches = query.isBlank() || searchable.contains(query)
            categoryMatches && unreadMatches && searchMatches
        }
    }
    val sections = remember(filtered, viewMode) { buildNotificationSections(filtered, viewMode) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    },
                    title = {
                        Column {
                            Text("Notifications", fontWeight = FontWeight.Bold)
                            Text(
                                when {
                                    unread == 0 -> "You're all caught up"
                                    unread == 1 -> "1 unread"
                                    else -> "$unread unread"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            searchVisible = !searchVisible
                            if (!searchVisible) searchQuery = ""
                        }) {
                            Icon(
                                if (searchVisible) Icons.Default.Close else Icons.Default.Search,
                                if (searchVisible) "Close search" else "Search notifications"
                            )
                        }
                        if (unread > 0) {
                            TextButton(onClick = onMarkAllRead) {
                                Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Read all", fontSize = 12.sp)
                            }
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )

                if (searchVisible) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        placeholder = { Text("Search people or activity") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            NotificationControls(
                filter = filter,
                onFilterChange = { filter = it },
                viewMode = viewMode,
                onViewModeChange = { viewMode = it }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))

            if (adminMessageLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            adminMessageError?.let { message ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            message,
                            modifier = Modifier.weight(1f),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = { adminMessageError = null }) { Text("Dismiss") }
                    }
                }
            }

            if (!isConnected) {
                NotificationConnectionNotice()
            } else if (!errorMessage.isNullOrBlank() && activities.isNotEmpty()) {
                NotificationRefreshNotice(onRefresh)
            }

            when {
                isLoading && activities.isEmpty() -> NotificationSkeletonList()

                !errorMessage.isNullOrBlank() && activities.isEmpty() -> NotificationLoadError(
                    message = errorMessage,
                    onRefresh = onRefresh
                )

                filtered.isEmpty() -> NotificationEmptyState(
                    filter = filter,
                    viewMode = viewMode,
                    hasSearch = searchQuery.isNotBlank()
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    sections.forEach { section ->
                        item(key = "section_${section.title}") {
                            NotificationSectionHeader(section.title)
                        }
                        items(section.items, key = { it.id }) { item ->
                            NotificationCard(
                                item = item,
                                profile = actorProfiles[item.user],
                                onProfileClick = onProfileClick,
                                onNotificationClick = { tapped ->
                                    if (!tapped.targetType.equals("notification", ignoreCase = true)) {
                                        onNotificationClick(tapped)
                                    } else if (!adminMessageLoading) {
                                        scope.launch {
                                            adminMessageLoading = true
                                            adminMessageError = null
                                            announcementService.fetchForActivity(tapped.id)
                                                .onSuccess { detail ->
                                                    if (detail != null) {
                                                        selectedAdminMessage = detail
                                                        onRefresh()
                                                    } else {
                                                        onNotificationClick(tapped)
                                                    }
                                                }
                                                .onFailure {
                                                    adminMessageError = it.message
                                                        ?: "Couldn't open this Blink message."
                                                }
                                            adminMessageLoading = false
                                        }
                                    }
                                }
                            )
                        }
                        if (section.showCaughtUpAfter) {
                            item(key = "caught_up_${section.title}") {
                                CaughtUpDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationControls(
    filter: NotificationFilter,
    onFilterChange: (NotificationFilter) -> Unit,
    viewMode: NotificationViewMode,
    onViewModeChange: (NotificationViewMode) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NotificationViewMode.values().forEach { mode ->
                FilterChip(
                    selected = viewMode == mode,
                    onClick = { onViewModeChange(mode) },
                    label = { Text(mode.label, fontSize = 12.sp) }
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(NotificationFilter.values()) { item ->
                FilterChip(
                    selected = filter == item,
                    onClick = { onFilterChange(item) },
                    label = { Text(item.label, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            filterIcon(item),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun NotificationSectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)
        )
    }
}

@Composable
private fun CaughtUpDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .65f),
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DoneAll,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "You're all caught up",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
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
private fun NotificationLoadError(message: String, onRefresh: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
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
                message,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onRefresh) { Text("Try again") }
        }
    }
}

@Composable
private fun NotificationEmptyState(
    filter: NotificationFilter,
    viewMode: NotificationViewMode,
    hasSearch: Boolean
) {
    val title = when {
        hasSearch -> "No matching notifications"
        viewMode == NotificationViewMode.UNREAD -> "No unread notifications"
        filter == NotificationFilter.COMMENTS -> "No mentions or comments"
        filter == NotificationFilter.LIKES -> "No likes or saves"
        filter == NotificationFilter.MARKET -> "No campus or market activity"
        else -> "No notifications"
    }
    val description = when {
        hasSearch -> "Try another name or activity keyword."
        viewMode == NotificationViewMode.UNREAD -> "You're all caught up."
        filter == NotificationFilter.COMMENTS -> "Mentions, comments and replies will appear here."
        filter == NotificationFilter.LIKES -> "Likes, saves and repost activity will appear here."
        filter == NotificationFilter.MARKET -> "Campus and marketplace updates will appear here."
        else -> "Likes, comments, follows and profile views will appear here."
    }

    Box(Modifier.fillMaxSize(), Alignment.Center) {
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
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(
                description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NotificationSkeletonList() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        repeat(6) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(50.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {}
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Surface(
                        modifier = Modifier.width(130.dp).height(12.dp),
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {}
                    Surface(
                        modifier = Modifier.fillMaxWidth(.78f).height(10.dp),
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)
                    ) {}
                    Surface(
                        modifier = Modifier.width(82.dp).height(8.dp),
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)
                    ) {}
                }
            }
        }
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
    val isOfficial = item.targetType.equals("notification", ignoreCase = true)
    val accent = if (item.vipPriority) Color(0xFFF59E0B) else notificationAccent(category, isOfficial)

    val rawUser = item.user.trim().removePrefix("@")
    val username = profile?.username?.takeIf { it.isNotBlank() }
        ?: rawUser.takeUnless(::looksLikeUuid).orEmpty()
    val displayName = profile?.fullName?.takeIf { it.isNotBlank() }
        ?: username.takeIf { it.isNotBlank() }
        ?: if (isOfficial) "Blink" else "Someone on Blink"
    val avatar = profile?.avatarUrl?.takeIf { it.isNotBlank() } ?: item.avatar
    val verificationBadge = profile?.verificationBadge ?: item.verificationBadge
    val isActive = profile?.onlineNow == true
    val canOpenProfile = username.isNotBlank() && !isOfficial

    Surface(
        color = if (item.isUnread) {
            accent.copy(alpha = .075f)
        } else {
            MaterialTheme.colorScheme.background
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNotificationClick(item) }
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(50.dp)
                    .clickable(enabled = canOpenProfile) { onProfileClick(username) }
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = if (isOfficial) accent.copy(alpha = .12f) else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    if (isOfficial) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Campaign,
                                contentDescription = "Blink official message",
                                tint = accent,
                                modifier = Modifier.size(25.dp)
                            )
                        }
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_default_profile),
                                contentDescription = "$displayName default profile picture",
                                tint = Color.Unspecified,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                if (!isOfficial && avatar.isNotBlank()) {
                    AsyncImage(
                        model = avatar,
                        error = painterResource(R.drawable.ic_default_profile),
                        fallback = painterResource(R.drawable.ic_default_profile),
                        contentDescription = "$displayName profile picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = accent,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.background),
                    modifier = Modifier.size(20.dp).align(Alignment.BottomEnd)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            notificationIcon(item),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }

                if (isActive && !isOfficial) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF22C55E),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.background),
                        modifier = Modifier.size(12.dp).align(Alignment.TopEnd)
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
                    BlinkVipMarkForUsername(
                        username = username,
                        knownVip = if (item.actorIsVip || profile?.isBlinkVip == true) true else null,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    if (isOfficial) {
                        Spacer(Modifier.width(7.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = accent.copy(alpha = .12f)
                        ) {
                            Text(
                                "OFFICIAL",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                        }
                    }

                }

                Spacer(Modifier.height(2.dp))
                Text(
                    item.action,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!item.previewText.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.previewText!!,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.time,
                        fontSize = 10.5.sp,
                        color = if (item.isUnread) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (item.isUnread) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (isActive && !isOfficial) {
                        Text(
                            "  •  Active",
                            fontSize = 10.5.sp,
                            color = Color(0xFF22C55E),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (item.isUnread) {
                Spacer(Modifier.width(10.dp))
                Surface(shape = CircleShape, color = accent, modifier = Modifier.size(9.dp)) {}
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 78.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)
    )
}

private fun buildNotificationSections(
    items: List<ActivityItem>,
    viewMode: NotificationViewMode
): List<NotificationSection> {
    if (items.isEmpty()) return emptyList()

    if (viewMode == NotificationViewMode.UNREAD) {
        return listOf(NotificationSection("New", items))
    }

    val unreadItems = items.filter { it.isUnread }
    val readItems = items.filterNot { it.isUnread }
    val sections = mutableListOf<NotificationSection>()

    if (unreadItems.isNotEmpty()) {
        sections += NotificationSection(
            title = "New",
            items = unreadItems,
            showCaughtUpAfter = readItems.isNotEmpty()
        )
    }

    listOf("Today", "Yesterday", "This week", "Earlier").forEach { bucket ->
        val bucketItems = readItems.filter { notificationDateBucket(it) == bucket }
        if (bucketItems.isNotEmpty()) {
            sections += NotificationSection(bucket, bucketItems)
        }
    }

    return sections
}

private fun notificationDateBucket(item: ActivityItem): String {
    val value = item.time.trim().lowercase()
    if (value.isBlank()) return "Earlier"
    if (value.contains("yesterday")) return "Yesterday"
    if (value.contains("today") || value.contains("just now") || value == "now") return "Today"

    val hours = Regex("(\\d+)\\s*(h|hr|hrs|hour|hours)").find(value)
        ?.groupValues?.getOrNull(1)?.toIntOrNull()
    if (hours != null && hours < 24) return "Today"

    val minutes = Regex("(\\d+)\\s*(m|min|mins|minute|minutes)").find(value)
    if (minutes != null) return "Today"

    val days = Regex("(\\d+)\\s*(d|day|days)").find(value)
        ?.groupValues?.getOrNull(1)?.toIntOrNull()
    if (days != null) {
        return when {
            days <= 1 -> "Yesterday"
            days <= 7 -> "This week"
            else -> "Earlier"
        }
    }

    return "Earlier"
}

private fun filterIcon(filter: NotificationFilter): ImageVector = when (filter) {
    NotificationFilter.ALL -> Icons.Default.Notifications
    NotificationFilter.COMMENTS -> Icons.Default.ChatBubble
    NotificationFilter.LIKES -> Icons.Default.Favorite
    NotificationFilter.MARKET -> Icons.Default.Storefront
}

private fun notificationIcon(item: ActivityItem): ImageVector {
    val action = item.action.lowercase()
    return when {
        item.targetType.equals("notification", ignoreCase = true) -> Icons.Default.Campaign
        item.targetType.equals("market", ignoreCase = true) || action.contains("market") -> Icons.Default.Storefront
        action.contains("mention") -> Icons.Default.AlternateEmail
        action.contains("reply") -> Icons.Default.Reply
        action.contains("comment") -> Icons.Default.ChatBubble
        action.contains("follow") -> Icons.Default.PersonAdd
        action.contains("save") || action.contains("bookmark") -> Icons.Default.Bookmark
        action.contains("repost") -> Icons.Default.Repeat
        action.contains("view") -> Icons.Default.Visibility
        action.contains("like") -> Icons.Default.Favorite
        else -> Icons.Default.Notifications
    }
}

private fun notificationAccent(category: NotificationFilter, isOfficial: Boolean): Color {
    if (isOfficial) return BlinkPurple
    return when (category) {
        NotificationFilter.LIKES -> BlinkPink
        NotificationFilter.COMMENTS -> BlinkPurple
        NotificationFilter.MARKET -> Color(0xFF22C55E)
        NotificationFilter.ALL -> BlinkPink
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
