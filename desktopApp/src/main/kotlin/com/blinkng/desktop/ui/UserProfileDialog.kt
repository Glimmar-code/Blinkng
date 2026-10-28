package com.blinkng.desktop.ui

import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.blinkng.shared.BlinkProfileThemes
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
    val isMe = initialProfile.id == state.session?.userId
    var content by remember(initialProfile.id) { mutableStateOf<List<DesktopFeedPost>>(emptyList()) }
    var likedContent by remember(initialProfile.id) { mutableStateOf<List<DesktopFeedPost>>(emptyList()) }
    var savedContent by remember(initialProfile.id) { mutableStateOf<List<DesktopFeedPost>>(emptyList()) }
    var followingIds by remember(initialProfile.id) { mutableStateOf<Set<String>>(emptySet()) }
    var notificationMode by remember(initialProfile.id) { mutableStateOf(DesktopProfileNotificationMode.OFF) }
    var muted by remember(initialProfile.id) { mutableStateOf(false) }
    var loading by remember(initialProfile.id) { mutableStateOf(true) }
    var postsFailed by remember(initialProfile.id) { mutableStateOf(false) }
    var likedFailed by remember(initialProfile.id) { mutableStateOf(false) }
    var savedFailed by remember(initialProfile.id) { mutableStateOf(false) }
    var busy by remember(initialProfile.id) { mutableStateOf(false) }
    var selectedTab by remember(initialProfile.id) { mutableStateOf("Posts") }
    var shareReelId by remember(initialProfile.id) { mutableStateOf<String?>(null) }
    var searchActive by remember(initialProfile.id) { mutableStateOf(false) }
    var searchQuery by remember(initialProfile.id) { mutableStateOf("") }
    var showNotifications by remember(initialProfile.id) { mutableStateOf(false) }
    var showReport by remember(initialProfile.id) { mutableStateOf(false) }
    var showBlock by remember(initialProfile.id) { mutableStateOf(false) }
    var reportReason by remember(initialProfile.id) { mutableStateOf("") }
    var connectionKind by remember(initialProfile.id) { mutableStateOf<String?>(null) }
    var connectionProfiles by remember(initialProfile.id) { mutableStateOf<List<DesktopProfile>>(emptyList()) }
    var statusMessage by remember(initialProfile.id) { mutableStateOf<String?>(null) }
    var showThemePicker by remember(initialProfile.id) { mutableStateOf(false) }
    var showCreatorStudio by remember(initialProfile.id) { mutableStateOf(false) }
    var creatorAiPrompt by remember(initialProfile.id) { mutableStateOf<String?>(null) }
    var chosenTheme by remember(initialProfile.id) { mutableStateOf(BlinkProfileThemes.normalize(initialProfile.profileTheme)) }

    suspend fun reload() {
        loading = true
        profile = state.client.fetchProfileDetail(initialProfile.id) ?: initialProfile
        postsFailed = false
        likedFailed = false
        savedFailed = false
        runCatching { state.client.fetchProfileContent(initialProfile.id) }
            .onSuccess { content = it }
            .onFailure { postsFailed = true; content = emptyList(); statusMessage = it.message }
        if (isMe) {
            runCatching { state.client.fetchMyProfileRelationContent(saved = false) }
                .onSuccess { likedContent = it }
                .onFailure { likedFailed = true; likedContent = emptyList(); statusMessage = it.message }
            runCatching { state.client.fetchMyProfileRelationContent(saved = true) }
                .onSuccess { savedContent = it }
                .onFailure { savedFailed = true; savedContent = emptyList(); statusMessage = it.message }
        }
        followingIds = runCatching { state.client.fetchFollowingIds() }.getOrDefault(emptySet())
        notificationMode = runCatching {
            state.client.getProfileNotificationPreference(initialProfile.id)
        }.getOrDefault(DesktopProfileNotificationMode.OFF)
        muted = runCatching { state.client.isProfileMuted(initialProfile.id) }.getOrDefault(false)
        loading = false
    }

    LaunchedEffect(initialProfile.id) { reload() }

    val filteredContent = remember(content, likedContent, savedContent, selectedTab, searchQuery) {
        val reels = selectedTab == "Reels"
        val source = when (selectedTab) {
            "Liked" -> if (isMe) likedContent else emptyList()
            "Saved" -> if (isMe) savedContent else emptyList()
            else -> content
        }
        source
            .asSequence()
            .filter { selectedTab == "Liked" || selectedTab == "Saved" || it.isReel == reels }
            .filter {
                searchQuery.isBlank() ||
                    it.text.orEmpty().contains(searchQuery, ignoreCase = true) ||
                    it.caption.orEmpty().contains(searchQuery, ignoreCase = true) ||
                    it.hashtags.any { tag -> tag.contains(searchQuery, ignoreCase = true) }
            }
            .sortedWith(compareByDescending<DesktopFeedPost> { it.isPinned }.thenByDescending { it.createdAt })
            .toList()
    }

    val theme = BlinkProfileThemes.resolve(profile.profileTheme)
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(theme.accentArgb),
            onPrimary = Color.Black,
            background = Color(theme.bottomArgb),
            onBackground = Color.White,
            surface = Color(theme.cardArgb),
            onSurface = Color.White,
            surfaceVariant = Color(theme.cardArgb),
            onSurfaceVariant = Color.White.copy(alpha = .78f),
        )
    ) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(.88f).fillMaxHeight(.9f),
            shape = RoundedCornerShape(26.dp),
            color = Color(theme.bottomArgb),
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(theme.topArgb), Color(theme.bottomArgb)))
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!profile.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = profile.avatarUrl,
                                contentDescription = "Profile avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Text(
                                profile.fullName.ifBlank { profile.username }.take(1).uppercase(),
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                            )
                        }
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
                        com.blinkng.shared.BlinkVerifiedIdentitySignature.label(
                            profile.verificationTier,
                            profile.isVerified &&
                                (profile.verificationExpiresAtMillis == 0L ||
                                    profile.verificationExpiresAtMillis > System.currentTimeMillis())
                        )?.let { signature ->
                            Surface(
                                color = if (profile.verificationTier.equals("GOLD", ignoreCase = true))
                                    Color(0xFF503C17) else Color(0xFF203C59),
                                shape = RoundedCornerShape(9.dp)
                            ) {
                                Text(signature, color = Color.White, fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                        val campus = listOfNotNull(profile.university, profile.faculty, profile.department)
                            .filter { it.isNotBlank() }
                            .joinToString(" • ")
                        if (campus.isNotBlank()) {
                            Text(campus, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (isMe) {
                        TextButton(onClick = {
                            chosenTheme = BlinkProfileThemes.normalize(profile.profileTheme)
                            showThemePicker = true
                        }) { Text("Edit theme", color = Color.White) }
                         TextButton(onClick = { showCreatorStudio = true }) {
                             Text("Creator Studio", color = Color.White)
                         }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
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
                        if (!profile.coverPhotoUrl.isNullOrBlank()) {
                            item(key = "desktop_profile_cover") {
                                AsyncImage(
                                    model = profile.coverPhotoUrl,
                                    contentDescription = "Profile cover photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().height(155.dp)
                                        .clip(RoundedCornerShape(17.dp))
                                )
                            }
                        }
                        profile.bio?.takeIf(String::isNotBlank)?.let { bio ->
                            item {
                                Surface(shape = RoundedCornerShape(18.dp),
                                    color = Color(theme.cardArgb)) {
                                    Text(bio, color = Color.White, modifier = Modifier.padding(14.dp))
                                }
                            }
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
                                (if (isMe) listOf("Posts", "Reels", "Liked", "Saved", "About") else listOf("Posts", "Reels", "About")).forEach { tab ->
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
                        } else if (filteredContent.isEmpty() && (
                            when (selectedTab) {
                                "Liked" -> likedFailed
                                "Saved" -> savedFailed
                                else -> postsFailed
                            }
                        )) {
                            item(key = "desktop_profile_content_error") {
                                Column {
                                    Text("Could not load profile content.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(onClick = { scope.launch { reload() } }) { Text("Retry") }
                                }
                            }
                        } else if (filteredContent.isEmpty()) {
                            item {
                                Text(
                                    when (selectedTab) {
                                        "Reels" -> "No reels yet."
                                        "Liked" -> "No liked posts yet."
                                        "Saved" -> "No saved posts yet."
                                        else -> "No posts yet."
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else if (selectedTab == "Reels") {
                            // Three-column Reel browser, mirroring the mobile profile density.
                            // Never instantiate nine media players just to draw thumbnail tiles.
                            items(
                                items = filteredContent.chunked(3),
                                key = { row -> "desktop_profile_reels_" + row.first().id }
                            ) { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    row.forEach { reel ->
                                        Surface(
                                            modifier = Modifier.weight(1f).aspectRatio(.72f)
                                                .clickable {
                                                    runCatching {
                                                        java.awt.Desktop.getDesktop().browse(
                                                            java.net.URI(DesktopShareLinkManager.generate(reel.id, true))
                                                        )
                                                    }.onFailure {
                                                        statusMessage = "Unable to open Reel. Use Copy link instead."
                                                    }
                                                },
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f),
                                        ) {
                                            Box(Modifier.fillMaxSize()) {
                                                reel.images.firstOrNull { it.isNotBlank() }
                                                    ?.let { thumbnail ->
                                                        AsyncImage(
                                                            model = thumbnail,
                                                            contentDescription = "Reel cover",
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    }
                                                Column(
                                                Modifier.fillMaxSize().background(
                                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                                        listOf(Color.Transparent, Color(0x66000000), Color(0xCC000000))
                                                    )
                                                ).padding(8.dp),
                                                verticalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("▶", fontSize = 24.sp,
                                                        color = Color.White)
                                                    Text("↗", modifier = Modifier.clickable {
                                                        shareReelId = reel.id
                                                    }, color = Color.White, fontSize = 20.sp)
                                                }
                                                Text(
                                                    reel.caption?.takeIf(String::isNotBlank)
                                                        ?: reel.text.orEmpty().ifBlank { "Reel" },
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontSize = 11.sp
                                                )
                                                Text(
                                                    "${reel.viewCount.coerceAtLeast(0)} views",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 10.sp
                                                )
                                                }
                                            }
                                        }
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
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
    }

    shareReelId?.let { id ->
        DesktopBlinkShareDialog(
            state = state, postId = id, isReel = true,
            onDismiss = { shareReelId = null }
        )
    }

    if (showCreatorStudio && isMe) {
        DesktopCreatorStudioDialog(
            profile = profile,
            posts = content,
            loading = loading,
            onDismiss = { showCreatorStudio = false },
            onRefresh = { scope.launch { reload() } },
            onUseAiPrompt = { prompt -> creatorAiPrompt = prompt },
        )
    }
    creatorAiPrompt?.let { prompt ->
        DesktopBlinkAiDialog(
            state = state,
            onDismiss = { creatorAiPrompt = null },
            initialPrompt = prompt,
        )
    }

    if (showThemePicker && isMe) {
        AlertDialog(
            onDismissRequest = { if (!busy) showThemePicker = false },
            title = { Text("Profile theme") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Other people will see your chosen colors.", fontSize = 12.sp)
                    BlinkProfileThemes.all.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(Color(item.cardArgb))
                                .clickable { chosenTheme = item.id }
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(27.dp).clip(CircleShape).background(Color(item.topArgb))
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(item.label, color = Color.White, modifier = Modifier.weight(1f))
                            if (chosenTheme == item.id) {
                                Text("✓", color = Color(item.accentArgb), fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        runCatching { state.client.saveProfileTheme(chosenTheme) }
                            .onSuccess { profile = it; statusMessage = "Profile theme saved."; showThemePicker = false }
                            .onFailure { statusMessage = it.message ?: "Unable to save theme." }
                        busy = false
                    }
                }) { Text(if (busy) "Saving…" else "Save theme") }
            },
            dismissButton = { TextButton(onClick = { showThemePicker = false }) { Text("Cancel") } }
        )
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
