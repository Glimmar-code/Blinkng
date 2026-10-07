package com.blinkng.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.blinkng.desktop.DesktopAppState
import com.blinkng.desktop.data.DesktopFeedPost
import com.blinkng.desktop.data.DesktopProfile
import com.blinkng.desktop.data.DesktopProfileNotificationMode
import com.blinkng.desktop.sharing.DesktopShareLinkManager
import kotlinx.coroutines.launch
import kotlin.math.min
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun DesktopUserProfileDialog(
    state: DesktopAppState,
    initialProfile: DesktopProfile,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var profile by remember(initialProfile.id) { mutableStateOf(initialProfile) }
    var content by remember(initialProfile.id) { mutableStateOf<List<DesktopFeedPost>>(emptyList()) }
    var followingIds by remember(initialProfile.id) { mutableStateOf<Set<String>>(emptySet()) }
    var notificationMode by remember(initialProfile.id) { mutableStateOf(DesktopProfileNotificationMode.OFF) }
    var muted by remember(initialProfile.id) { mutableStateOf(false) }
    var loading by remember(initialProfile.id) { mutableStateOf(true) }
    var busy by remember(initialProfile.id) { mutableStateOf(false) }
    var selectedTab by remember(initialProfile.id) { mutableStateOf("Posts") }
    var searchActive by remember(initialProfile.id) { mutableStateOf(false) }
    var searchQuery by remember(initialProfile.id) { mutableStateOf("") }
    var showNotifications by remember(initialProfile.id) { mutableStateOf(false) }
    var showReport by remember(initialProfile.id) { mutableStateOf(false) }
    var showBlock by remember(initialProfile.id) { mutableStateOf(false) }
    var reportReason by remember(initialProfile.id) { mutableStateOf("") }
    var connectionKind by remember(initialProfile.id) { mutableStateOf<String?>(null) }
    var connectionProfiles by remember(initialProfile.id) { mutableStateOf<List<DesktopProfile>>(emptyList()) }
    var statusMessage by remember(initialProfile.id) { mutableStateOf<String?>(null) }

    suspend fun reload() {
        loading = true
        profile = state.client.fetchProfileDetail(initialProfile.id) ?: initialProfile
        content = runCatching { state.client.fetchProfileContent(initialProfile.id) }.getOrDefault(emptyList())
        followingIds = runCatching { state.client.fetchFollowingIds() }.getOrDefault(emptySet())
        notificationMode = runCatching {
            state.client.getProfileNotificationPreference(initialProfile.id)
        }.getOrDefault(DesktopProfileNotificationMode.OFF)
        muted = runCatching { state.client.isProfileMuted(initialProfile.id) }.getOrDefault(false)
        loading = false
    }

    LaunchedEffect(initialProfile.id) { reload() }

    val filteredContent = remember(content, selectedTab, searchQuery) {
        val reels = selectedTab == "Reels"
        content
            .asSequence()
            .filter { it.isReel == reels }
            .filter {
                searchQuery.isBlank() ||
                    it.text.orEmpty().contains(searchQuery, ignoreCase = true) ||
                    it.caption.orEmpty().contains(searchQuery, ignoreCase = true) ||
                    it.hashtags.any { tag -> tag.contains(searchQuery, ignoreCase = true) }
            }
            .sortedWith(compareByDescending<DesktopFeedPost> { it.isPinned }.thenByDescending { it.createdAt })
            .toList()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(.88f).fillMaxHeight(.9f),
            shape = RoundedCornerShape(26.dp),
            tonalElevation = 8.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            profile.fullName.ifBlank { profile.username }.take(1).uppercase(),
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(profile.fullName.ifBlank { profile.username }, fontWeight = FontWeight.Black, fontSize = 20.sp)
                            if (profile.isVerified) {
                                Spacer(Modifier.width(6.dp))
                                Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                            }
                        }
                        Text("@${profile.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val campus = listOfNotNull(profile.university, profile.faculty, profile.department)
                            .filter { it.isNotBlank() }
                            .joinToString(" • ")
                        if (campus.isNotBlank()) {
                            Text(campus, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        profile.bio?.takeIf(String::isNotBlank)?.let { bio ->
                            item { Text(bio) }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ProfileStat("Posts", content.count { !it.isReel }.toString())
                                ProfileStat(
                                    "Followers",
                                    profile.followerCount.toString(),
                                    modifier = Modifier.clickable {
                                        connectionKind = "FOLLOWERS"
                                        scope.launch {
                                            connectionProfiles = runCatching {
                                                state.client.fetchProfileConnections(profile.id, "FOLLOWERS")
                                            }.getOrDefault(emptyList())
                                        }
                                    },
                                )
                                ProfileStat(
                                    "Following",
                                    profile.followingCount.toString(),
                                    modifier = Modifier.clickable {
                                        connectionKind = "FOLLOWING"
                                        scope.launch {
                                            connectionProfiles = runCatching {
                                                state.client.fetchProfileConnections(profile.id, "FOLLOWING")
                                            }.getOrDefault(emptyList())
                                        }
                                    },
                                )
                            }
                        }

                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val isFollowing = profile.id in followingIds
                                Button(
                                    enabled = !busy,
                                    onClick = {
                                        scope.launch {
                                            busy = true
                                            followingIds = runCatching {
                                                state.client.setFollowing(profile.id, !isFollowing)
                                            }.getOrDefault(followingIds)
                                            busy = false
                                        }
                                    },
                                ) {
                                    Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(if (isFollowing) "Following" else "Follow")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val copied = DesktopShareLinkManager.copyProfileToClipboard(profile.username)
                                        statusMessage = if (copied) "Profile link copied." else "Unable to copy profile link."
                                    },
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Share")
                                }
                                OutlinedButton(onClick = { showNotifications = true }) {
                                    Icon(Icons.Rounded.Notifications, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Notify")
                                }
                            }
                        }

                        item {
                            DesktopProfileQr(
                                link = DesktopShareLinkManager.generateProfile(profile.username),
                                username = profile.username,
                            )
                        }

                        statusMessage?.let { message ->
                            item {
                                Text(message, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    enabled = !busy,
                                    onClick = {
                                        scope.launch {
                                            busy = true
                                            val desired = !muted
                                            muted = runCatching {
                                                state.client.setProfileMuted(profile.id, desired)
                                            }.getOrDefault(muted)
                                            statusMessage = if (muted) "Profile muted." else "Profile unmuted."
                                            busy = false
                                        }
                                    },
                                ) {
                                    Icon(Icons.Rounded.VolumeOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(if (muted) "Unmute" else "Mute")
                                }
                                OutlinedButton(onClick = { showReport = true }) {
                                    Icon(Icons.Rounded.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Report")
                                }
                                OutlinedButton(onClick = { showBlock = true }) {
                                    Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Block")
                                }
                                OutlinedButton(onClick = { searchActive = !searchActive }) {
                                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Search")
                                }
                            }
                        }

                        if (searchActive) {
                            item {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    placeholder = { Text("Search this profile's posts and reels") },
                                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                )
                            }
                        }

                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Posts", "Reels", "About").forEach { tab ->
                                    FilterChip(
                                        selected = selectedTab == tab,
                                        onClick = { selectedTab = tab },
                                        label = { Text(tab) },
                                    )
                                }
                            }
                        }

                        if (selectedTab == "About") {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ProfileAboutLine("University", profile.university)
                                    ProfileAboutLine("Faculty", profile.faculty)
                                    ProfileAboutLine("Department", profile.department)
                                    ProfileAboutLine("Level", profile.academicLevel)
                                    ProfileAboutLine("Email", profile.email)
                                    ProfileAboutLine("Phone", profile.phone)
                                    ProfileAboutLine("WhatsApp", profile.whatsapp)
                                    ProfileAboutLine("Joined", profile.createdAt)
                                }
                            }
                        } else if (filteredContent.isEmpty()) {
                            item {
                                Text(
                                    if (selectedTab == "Reels") "No reels yet." else "No posts yet.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            items(filteredContent, key = { it.id }) { post ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(7.dp),
                                    ) {
                                        if (post.isPinned) {
                                            Text("Pinned", fontSize = 10.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Text(
                                            post.text?.takeIf(String::isNotBlank)
                                                ?: post.caption?.takeIf(String::isNotBlank)
                                                ?: if (post.isReel) "Reel" else "Post",
                                            maxLines = 5,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "${post.viewCount} views • ${post.likeCount} likes • ${post.commentCount} comments • ${post.shareCount} shares",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        TextButton(onClick = {
                                            DesktopShareLinkManager.copyToClipboard(post.id, post.isReel)
                                        }) { Text("Copy link") }
                                    }
                                }
                            }
                        }

                        item { Spacer(Modifier.height(12.dp)) }
                    }
                }
            }
        }
    }

    if (showNotifications) {
        AlertDialog(
            onDismissRequest = { if (!busy) showNotifications = false },
            title = { Text("Profile notifications") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DesktopProfileNotificationMode.entries.forEach { mode ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = notificationMode == mode,
                                onClick = {
                                    if (!busy) {
                                        scope.launch {
                                            busy = true
                                            notificationMode = runCatching {
                                                state.client.setProfileNotificationPreference(profile.id, mode)
                                            }.getOrDefault(notificationMode)
                                            busy = false
                                        }
                                    }
                                },
                            )
                            Text(
                                when (mode) {
                                    DesktopProfileNotificationMode.ALL -> "All posts and reels"
                                    DesktopProfileNotificationMode.REELS -> "Reels only"
                                    DesktopProfileNotificationMode.IMPORTANT -> "Important pinned updates"
                                    DesktopProfileNotificationMode.OFF -> "Off"
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotifications = false }, enabled = !busy) { Text("Done") }
            },
        )
    }

    if (showReport) {
        AlertDialog(
            onDismissRequest = { if (!busy) showReport = false },
            title = { Text("Report profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("Spam or scam", "Harassment", "Impersonation", "Inappropriate content", "Other").forEach { option ->
                        FilterChip(
                            selected = reportReason == option,
                            onClick = { reportReason = option },
                            label = { Text(option) },
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = reportReason.isNotBlank() && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            val sent = runCatching {
                                state.client.reportProfile(profile.id, reportReason)
                            }.getOrDefault(false)
                            statusMessage = if (sent) "Report sent." else "Unable to send report."
                            if (sent) showReport = false
                            busy = false
                        }
                    },
                ) { Text(if (busy) "Sending…" else "Report") }
            },
            dismissButton = {
                TextButton(onClick = { showReport = false }, enabled = !busy) { Text("Cancel") }
            },
        )
    }

    if (showBlock) {
        AlertDialog(
            onDismissRequest = { if (!busy) showBlock = false },
            title = { Text("Block @${profile.username}?") },
            text = { Text("Blocking removes follow connections and prevents normal interaction between both accounts.") },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            val blocked = runCatching { state.client.blockProfile(profile.id) }.getOrDefault(false)
                            busy = false
                            if (blocked) {
                                showBlock = false
                                onDismiss()
                            } else {
                                statusMessage = "Unable to block profile."
                            }
                        }
                    },
                ) { Text(if (busy) "Blocking…" else "Block") }
            },
            dismissButton = {
                TextButton(onClick = { showBlock = false }, enabled = !busy) { Text("Cancel") }
            },
        )
    }

    connectionKind?.let { kind ->
        AlertDialog(
            onDismissRequest = {
                connectionKind = null
                connectionProfiles = emptyList()
            },
            title = { Text(if (kind == "FOLLOWERS") "Followers" else "Following") },
            text = {
                if (connectionProfiles.isEmpty()) {
                    Text("No profiles to show.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(360.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        items(connectionProfiles, key = { it.id }) { item ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.fullName.ifBlank { item.username }, fontWeight = FontWeight.Bold)
                                        Text("@${item.username}", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    connectionKind = null
                    connectionProfiles = emptyList()
                }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun DesktopProfileQr(
    link: String,
    username: String,
) {
    val matrix = remember(link) {
        runCatching { QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 41, 41) }.getOrNull()
    } ?: return

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = androidx.compose.ui.graphics.Color.White,
        ) {
            Canvas(modifier = Modifier.size(154.dp).padding(10.dp)) {
                val cell = min(size.width / matrix.width, size.height / matrix.height)
                val qrSize = cell * matrix.width
                val left = (size.width - qrSize) / 2f
                val top = (size.height - qrSize) / 2f
                for (x in 0 until matrix.width) {
                    for (y in 0 until matrix.height) {
                        if (matrix[x, y]) {
                            drawRect(
                                color = androidx.compose.ui.graphics.Color.Black,
                                topLeft = androidx.compose.ui.geometry.Offset(left + x * cell, top + y * cell),
                                size = androidx.compose.ui.geometry.Size(cell + 0.5f, cell + 0.5f),
                            )
                        }
                    }
                }
            }
        }
        Text(
            "Scan to open @$username on BLINK",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfileStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProfileAboutLine(label: String, value: String?) {
    value?.takeIf(String::isNotBlank)?.let {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.width(100.dp), fontWeight = FontWeight.SemiBold)
            Text(it, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
    }
}
