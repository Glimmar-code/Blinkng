package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.example.data.models.ConnectHubSnapshot
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.ui.components.VerifiedMark
import com.example.ui.theme.BlinkOnlineGreen

private enum class LivePeopleFilter(val label: String) {
    ALL("All"),
    SAME_CAMPUS("Same campus"),
    ONLINE("Online")
}

@Composable
fun ConnectSection(
    profiles: List<UserProfile>,
    currentUsername: String,
    userAvatar: String,
    isDark: Boolean,
    onOpenMenu: () -> Unit,
    onOpenActivity: () -> Unit,
    onProfileClick: (String) -> Unit,
    onDirectMessage: (partner: String, partnerName: String?, partnerAvatar: String?) -> Unit,
    connectHub: ConnectHubSnapshot = ConnectHubSnapshot(),
    connectHubActions: ConnectHubActions = ConnectHubActions(),
    isConnectHubLoading: Boolean = false,
    selectedTopTab: Int,
    onHomeClick: () -> Unit,
    onReelClick: () -> Unit,
    onConnectClick: () -> Unit,
    onGameClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(LivePeopleFilter.ALL) }

    val current = remember(profiles, currentUsername) {
        profiles.firstOrNull { it.username.equals(currentUsername, ignoreCase = true) }
    }

    val liveProfiles = remember(profiles, currentUsername) {
        profiles
            .asSequence()
            .filter { it.username.isNotBlank() }
            .filterNot { it.username.equals(currentUsername, ignoreCase = true) }
            .distinctBy { it.id.ifBlank { it.username.lowercase() } }
            .toList()
    }

    val visible = remember(liveProfiles, query, filter, current?.university) {
        val clean = query.trim()
        liveProfiles.filter { profile ->
            val matchesQuery = clean.isBlank() ||
                profile.fullName.contains(clean, ignoreCase = true) ||
                profile.username.contains(clean, ignoreCase = true) ||
                profile.university.contains(clean, ignoreCase = true) ||
                profile.faculty.contains(clean, ignoreCase = true) ||
                profile.department.contains(clean, ignoreCase = true)

            val matchesFilter = when (filter) {
                LivePeopleFilter.ALL -> true
                LivePeopleFilter.ONLINE -> profile.onlineNow
                LivePeopleFilter.SAME_CAMPUS -> {
                    val myCampus = current?.university.orEmpty()
                    myCampus.isNotBlank() &&
                        !myCampus.equals("null", ignoreCase = true) &&
                        profile.university.equals(myCampus, ignoreCase = true)
                }
            }

            matchesQuery && matchesFilter
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ConnectHeader(
            userAvatar = userAvatar,
            onMenuClick = onOpenMenu,
            onNotificationClick = onOpenActivity,
            onProfileClick = { onProfileClick("you") }
        )

        ConnectTopNavigation(
            selected = selectedTopTab,
            onHome = onHomeClick,
            onReel = onReelClick,
            onConnect = onConnectClick,
            onGame = onGameClick
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Discover students",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "Real profiles loaded directly from Supabase",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = BlinkOnlineGreen.copy(alpha = .13f)
                    ) {
                        Text(
                            "${liveProfiles.size} live",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = BlinkOnlineGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item {
                ConnectHubPremiumPanel(
                    current = current,
                    profiles = liveProfiles,
                    hub = connectHub,
                    actions = connectHubActions,
                    isLoading = isConnectHubLoading,
                    onProfileClick = onProfileClick,
                    onMessageUser = onDirectMessage
                )
                Spacer(Modifier.height(8.dp))
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text("Search real students") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LivePeopleFilter.values().forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text(option.label) }
                        )
                    }
                }
            }

            if (visible.isEmpty()) {
                item {
                    LivePeopleEmptyState(
                        hasProfiles = liveProfiles.isNotEmpty(),
                        query = query,
                        filter = filter
                    )
                }
            } else {
                items(
                    items = visible,
                    key = { it.id.ifBlank { it.username } }
                ) { profile ->
                    LiveProfileCard(
                        profile = profile,
                        onProfileClick = { onProfileClick(profile.username) },
                        onMessage = {
                            onDirectMessage(
                                profile.username,
                                profile.fullName,
                                profile.avatarUrl
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectHeader(
    userAvatar: String,
    onMenuClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 38.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMenuClick, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Default.MoreHoriz, contentDescription = "Menu")
        }

        Spacer(Modifier.weight(1f))

        Text(
            "Connect",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(Modifier.weight(1f))

        IconButton(onClick = onNotificationClick, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications")
        }

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(onClick = onProfileClick),
            contentAlignment = Alignment.Center
        ) {
            if (userAvatar.isBlank()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Profile",
                        modifier = Modifier.padding(9.dp)
                    )
                }
            } else {
                AsyncImage(
                    model = userAvatar,
                    contentDescription = "Profile",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun ConnectTopNavigation(
    selected: Int,
    onHome: () -> Unit,
    onReel: () -> Unit,
    onConnect: () -> Unit,
    onGame: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        ConnectTopTab("Home", selected == 0, onHome)
        Spacer(Modifier.width(8.dp))
        ConnectTopTab("Reel", selected == 1, onReel)
        Spacer(Modifier.width(8.dp))
        ConnectTopTab("Connect", selected == 2, onConnect)
        Spacer(Modifier.width(8.dp))
        ConnectTopTab("Game", selected == 3, onGame)
    }
}

@Composable
private fun ConnectTopTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(100.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    ) {
        Text(
            text = text,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun LiveProfileCard(
    profile: UserProfile,
    onProfileClick: () -> Unit,
    onMessage: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onProfileClick),
                    contentAlignment = Alignment.Center
                ) {
                    if (profile.avatarUrl.isBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = profile.fullName,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else {
                        AsyncImage(
                            model = profile.avatarUrl,
                            contentDescription = profile.fullName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (profile.onlineNow) {
                        Box(
                            Modifier
                                .size(13.dp)
                                .align(Alignment.BottomEnd)
                                .background(BlinkOnlineGreen, CircleShape)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            profile.fullName.ifBlank { profile.username },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (profile.verificationBadge != VerificationBadge.NONE) {
                            Spacer(Modifier.width(4.dp))
                            VerifiedMark(profile.verificationBadge, size = 12.dp)
                        }
                    }

                    Text(
                        "@${profile.username}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val detail = listOf(
                        profile.academicLevel.takeUnless { it.equals("null", true) }.orEmpty(),
                        profile.department.takeUnless { it.equals("null", true) }.orEmpty(),
                        profile.university.takeUnless { it.equals("null", true) }.orEmpty(),
                        profile.relationshipStatus.takeUnless { it.equals("null", true) }.orEmpty()
                    ).filter { it.isNotBlank() }.joinToString(" • ")

                    if (detail.isNotBlank()) {
                        Text(
                            detail,
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = if (profile.onlineNow) {
                            "Active now"
                        } else {
                            profile.lastSeenAt.takeIf { it.isNotBlank() }
                                ?.let { "Last seen ${it.replace("T", " ").take(16)}" }
                                ?: "Offline"
                        },
                        fontSize = 10.sp,
                        color = if (profile.onlineNow) BlinkOnlineGreen
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (profile.bio.isNotBlank() && !profile.bio.equals("null", true)) {
                Spacer(Modifier.height(10.dp))
                Text(
                    profile.bio,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                OutlinedButton(
                    onClick = onProfileClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Icon(
                        Icons.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("View profile")
                }

                Button(
                    onClick = onMessage,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Message")
                }
            }
        }
    }
}

@Composable
private fun LivePeopleEmptyState(
    hasProfiles: Boolean,
    query: String,
    filter: LivePeopleFilter
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier
                    .size(58.dp)
                    .padding(15.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            when {
                !hasProfiles -> "No live students yet"
                query.isNotBlank() -> "No matching students"
                filter == LivePeopleFilter.SAME_CAMPUS -> "No campus matches yet"
                filter == LivePeopleFilter.ONLINE -> "Nobody is marked online right now"
                else -> "No students found"
            },
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp
        )

        Spacer(Modifier.height(6.dp))

        Text(
            "This screen only shows profiles returned by Supabase. No sample users are inserted.",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
