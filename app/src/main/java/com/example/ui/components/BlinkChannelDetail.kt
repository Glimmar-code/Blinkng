package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.example.data.repository.BlinkChannelMediaService
import kotlinx.coroutines.delay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blinkng.shared.BlinkChannelPolicy
import com.blinkng.shared.BlinkChannelUpdate
import com.blinkng.shared.BlinkChannelReport
import org.json.JSONObject
import com.example.data.repository.BlinkCommunity
import com.example.data.repository.BlinkCommunityRepository
import kotlinx.coroutines.launch

/**
 * One-way broadcast reader. Returning with Android Back restores the channel directory;
 * followers cannot compose updates. All membership and permissions are server-authoritative.
 */
@Composable
fun BlinkChannelDetail(
    channel: BlinkCommunity,
    backend: BlinkCommunityRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var updates by remember(channel.id) { mutableStateOf<List<BlinkChannelUpdate>>(emptyList()) }
    var role by remember(channel.id) { mutableStateOf("none") }
    var followers by remember(channel.id) { mutableStateOf(0) }
    var loading by remember(channel.id) { mutableStateOf(true) }
    var saving by remember(channel.id) { mutableStateOf(false) }
    var error by remember(channel.id) { mutableStateOf<String?>(null) }
    var draft by remember(channel.id) { mutableStateOf("") }

    val context = LocalContext.current
    var muted by remember(channel.id) { mutableStateOf(false) }
    var showScheduled by remember(channel.id) { mutableStateOf(false) }
    var scheduled by remember(channel.id) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var unread by remember(channel.id) { mutableStateOf(0) }

    var showReports by remember(channel.id) { mutableStateOf(false) }
    var reports by remember(channel.id) { mutableStateOf<List<BlinkChannelReport>>(emptyList()) }
    var reportingUpdate by remember(channel.id) { mutableStateOf<String?>(null) }
    var reportReason by remember(channel.id) { mutableStateOf("Spam or misleading information") }
    var showStats by remember(channel.id) { mutableStateOf(false) }
    var stats by remember(channel.id) { mutableStateOf("") }
    var mediaKind by remember(channel.id) { mutableStateOf("text") }
    var selectedMedia by remember(channel.id) { mutableStateOf<Uri?>(null) }
    var pollQuestion by remember(channel.id) { mutableStateOf("") }
    var pollOptions by remember(channel.id) { mutableStateOf("") }
    var scheduledAt by remember(channel.id) { mutableStateOf("") }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedMedia = uri
    }

    suspend fun refresh() {
        loading = true
        try {
            role = backend.myChannelRole(channel.id)
            followers = backend.channelFollowerCount(channel.id)
            muted = backend.isChannelMuted(channel.id)
            updates = backend.channelUpdates(channel.id)
            unread = runCatching { backend.channelUnreadCount(channel.id) }.getOrDefault(0)
            runCatching { backend.markChannelSeen(channel.id) }
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Unable to load this channel."
        } finally {
            loading = false
        }
    }

    BackHandler(onBack = onBack)
    LaunchedEffect(channel.id) { refresh() }
    // Reconcile updates when the viewer returns to an already open channel.
    LaunchedEffect(channel.id) {
        while (true) {
            delay(45_000)
            if (!saving) runCatching { updates = backend.channelUpdates(channel.id) }
        }
    }
    val admin = role == "owner" || role == "moderator"
    val following = admin || role == "follower"
    val updatesListState = rememberLazyListState()
    // Like a broadcast conversation, opening a channel reveals its newest update.
    LaunchedEffect(channel.id, loading, updates.lastOrNull()?.id) {
        if (!loading && updates.isNotEmpty()) {
            updatesListState.scrollToItem(updates.size)
        }
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onBack) { Text("‹") }
            AsyncImage(model = channel.coverUrl, contentDescription = "Channel picture",
                modifier = Modifier.size(40.dp).clip(CircleShape))
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                Text(channel.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, maxLines = 1)
                Text("$followers followers" + if (unread > 0) " · $unread new" else "",
                    style = MaterialTheme.typography.labelMedium)
            }
            TextButton(
                onClick = {
                    scope.launch {
                        saving = true
                        try { backend.muteChannel(channel.id, !muted); muted = !muted }
                        catch (e: Exception) { error = e.message }
                        finally { saving = false }
                    }
                }, enabled = !loading && !saving
            ) { Text(if (muted) "🔕" else "🔔") }
            if (admin) TextButton(onClick = {
                scope.launch {
                    try { stats = backend.channelStats(channel.id).toString(2); showStats = true }
                    catch (e: Exception) { error = e.message }
                }
            }) { Text("Stats") }
            if (admin) TextButton(onClick = {
                scope.launch {
                    try {
                        scheduled = backend.scheduledChannelUpdates(channel.id)
                        showScheduled = true
                    } catch (e: Exception) { error = e.message }
                }
            }) { Text("Scheduled") }
            if (admin) TextButton(onClick = {
                scope.launch {
                    try {
                        reports = backend.channelReports(channel.id)
                        showReports = true
                    } catch (e: Exception) { error = e.message }
                }
            }) { Text("Reports") }
            TextButton(onClick = { scope.launch { refresh() } }, enabled = !loading && !saving) {
                Text("Refresh")
            }
        }
        HorizontalDivider()
        LazyColumn(
            state = updatesListState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(channel.name, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Text(channel.university + " · " + channel.category,
                        style = MaterialTheme.typography.labelLarge)
                    if (channel.description.isNotBlank()) Text(channel.description)
                    Text("Only channel administrators can publish updates.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (loading) item { CircularProgressIndicator(Modifier.padding(20.dp)) }
            if (!error.isNullOrBlank()) item {
                Text(error.orEmpty(), Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.error)
            }
            if (!loading && updates.isEmpty() && error == null) item {
                Text("No channel updates yet.", Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium)
            }
            items(updates, key = { it.id }) { update ->
                LaunchedEffect(update.id) {
                    runCatching { backend.recordChannelView(update.id) }
                }
                var signedMedia by remember(update.id, update.mediaPath) { mutableStateOf<String?>(null) }
                LaunchedEffect(update.mediaPath) {
                    signedMedia = update.mediaPath?.let { path ->
                        runCatching { BlinkChannelMediaService.signedUrl(path) }.getOrNull()
                    }
                }
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(channel.name, style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold)
                        if (update.pinned) Text("📌 Pinned update", color = MaterialTheme.colorScheme.primary)
                        if (update.body.isNotEmpty()) Text(update.body, style = MaterialTheme.typography.bodyLarge)
                        if (update.mediaKind == "image" && signedMedia != null) {
                            AsyncImage(
                                model = signedMedia,
                                contentDescription = "Channel image",
                                modifier = Modifier.fillMaxWidth().height(230.dp)
                            )
                        } else if (update.mediaPath != null) {
                            TextButton(onClick = {
                                signedMedia?.let { url ->
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            .addCategory(Intent.CATEGORY_BROWSABLE))
                                    }.onFailure { error = "No app can open this attachment." }
                                }
                            }) { Text(if (signedMedia == null) "Loading attachment…" else "Open ${update.mediaKind} attachment") }
                        }
                        if (update.mediaKind == "poll") {
                            Text(update.pollQuestion.orEmpty(), fontWeight = FontWeight.SemiBold)
                            update.pollOptions.forEachIndexed { index, option ->
                                val count = update.pollCounts[index] ?: 0
                                TextButton(
                                    enabled = following && !saving,
                                    onClick = {
                                        scope.launch {
                                            saving = true
                                            try { backend.voteChannelPoll(update.id, index); updates = backend.channelUpdates(channel.id) }
                                            catch (e: Exception) { error = e.message }
                                            finally { saving = false }
                                        }
                                    }
                                ) { Text("${if (update.myVote == index) "●" else "○"} $option · $count") }
                            }
                        }
                        Text("${update.viewCount} views", style = MaterialTheme.typography.labelSmall)
                        Text(update.createdAt.take(16).replace('T',' '),
                            style = MaterialTheme.typography.labelSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(BlinkChannelPolicy.reactions) { emoji ->
                                val count = update.reactions[emoji] ?: 0
                                TextButton(
                                    enabled = following && !saving,
                                    onClick = {
                                        scope.launch {
                                            saving = true
                                            try {
                                                backend.reactToChannelUpdate(update.id, emoji)
                                                updates = backend.channelUpdates(channel.id)
                                                error = null
                                            } catch (e: Exception) { error = e.message }
                                            finally { saving = false }
                                        }
                                    }
                                ) {
                                    Text(emoji + if (count > 0) " $count" else "",
                                        color = if (update.myReaction == emoji)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            item { TextButton(onClick = {
                                val message = "${channel.name} — BLINK Channel\n${update.body}"
                                context.startActivity(Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).setType("text/plain")
                                        .putExtra(Intent.EXTRA_TEXT, message),
                                    "Forward channel update"
                                ))
                            }) { Text("Forward ↗") } }
                            item {
                                TextButton(onClick = { reportingUpdate = update.id }) { Text("Report") }
                            }
                            if (admin) {
                                item { TextButton(onClick = {
                                    scope.launch {
                                        try {
                                            backend.moderateChannelUpdate(update.id,
                                                if (update.pinned) "unpin" else "pin")
                                            updates = backend.channelUpdates(channel.id)
                                        } catch (e: Exception) { error = e.message }
                                    }
                                }) { Text(if (update.pinned) "Unpin" else "Pin") } }
                                item { TextButton(onClick = {
                                    scope.launch {
                                        try {
                                            backend.moderateChannelUpdate(update.id, "remove")
                                            updates = backend.channelUpdates(channel.id)
                                        } catch (e: Exception) { error = e.message }
                                    }
                                }) { Text("Remove") } }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
        HorizontalDivider()
        if (admin) {
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(listOf("text", "image", "video", "audio", "document", "poll")) { kind ->
                    FilterChip(
                        selected = mediaKind == kind,
                        onClick = {
                            mediaKind = kind
                            if (kind in setOf("image","video","audio","document"))
                                mediaPicker.launch(when (kind) {
                                    "image" -> "image/*"
                                    "video" -> "video/*"
                                    "audio" -> "audio/*"
                                    else -> "application/pdf"
                                })
                        },
                        label = { Text(kind.replaceFirstChar(Char::uppercase)) }
                    )
                }
            }
            if (mediaKind == "poll") {
                OutlinedTextField(pollQuestion, { pollQuestion = it.take(300) },
                    label = { Text("Poll question") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
                OutlinedTextField(pollOptions, { pollOptions = it.take(800) },
                    label = { Text("2–8 options, one per line") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), maxLines = 5)
            }
            if (selectedMedia != null) {
                Text("Selected media: ${selectedMedia?.lastPathSegment.orEmpty()}",
                    modifier = Modifier.padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.labelMedium)
            }
            OutlinedTextField(
                scheduledAt, { scheduledAt = it.take(30) },
                label = { Text("Schedule (optional): 2026-11-01T12:00:00Z") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                singleLine = true
            )
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it.take(BlinkChannelPolicy.MAX_UPDATE_LENGTH) },
                    label = { Text("Broadcast an update") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4
                )
                Button(
                    enabled = !saving && (draft.isNotBlank() || (mediaKind == "poll" && pollQuestion.isNotBlank()) || selectedMedia != null),
                    onClick = {
                        scope.launch {
                            saving = true
                            try {
                                val upload = selectedMedia?.let { BlinkChannelMediaService.upload(context, channel.id, it) }
                                val chosenKind = upload?.kind ?: mediaKind
                                val schedule = scheduledAt.trim().takeIf { it.isNotEmpty() }?.let {
                                    java.time.Instant.parse(it).toString()
                                }
                                backend.publishRichUpdate(
                                    channelId = channel.id, body = draft,
                                    kind = chosenKind, mediaPath = upload?.objectPath,
                                    pollQuestion = if (chosenKind == "poll") pollQuestion else null,
                                    options = if (chosenKind == "poll") pollOptions.lines().filter { it.isNotBlank() } else emptyList(),
                                    scheduledAt = schedule
                                )
                                draft = ""; selectedMedia = null; pollQuestion = ""; pollOptions = ""
                                scheduledAt = ""; mediaKind = "text"
                                updates = backend.channelUpdates(channel.id)
                                error = null
                            } catch (e: Exception) { error = e.message }
                            finally { saving = false }
                        }
                    }
                ) { Text(if (scheduledAt.isBlank()) "Post" else "Schedule") }
            }
        } else if (!following) {
            Button(
                onClick = {
                    scope.launch {
                        saving = true
                        try {
                            val result = backend.requestJoin(channel.id)
                            role = if (result == "active") "follower" else result
                            refresh()
                        } catch (e: Exception) { error = e.message }
                        finally { saving = false }
                    }
                },
                enabled = !saving && role != "pending" && role != "banned",
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) { Text(when (role) { "pending" -> "Follow requested"; "banned" -> "Channel restricted"; else -> "Follow channel" }) }

        } else if (role == "follower") {
            TextButton(
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                onClick = {
                    scope.launch {
                        saving = true
                        try {
                            backend.leave(channel.id)
                            onBack()
                        } catch (e: Exception) { error = e.message }
                        finally { saving = false }
                    }
                }
            ) { Text("Unfollow channel") }
        }
        if (reportingUpdate != null) {
            AlertDialog(
                onDismissRequest = { reportingUpdate = null },
                title = { Text("Report this channel update") },
                text = {
                    OutlinedTextField(reportReason, { reportReason = it.take(280) },
                        label = { Text("Reason for reporting") })
                },
                dismissButton = {
                    TextButton(onClick = { reportingUpdate = null }) { Text("Cancel") }
                },
                confirmButton = {
                    TextButton(enabled = reportReason.trim().length >= 3 && !saving,
                        onClick = {
                            val selected = reportingUpdate ?: return@TextButton
                            scope.launch {
                                saving = true
                                try {
                                    backend.reportChannelUpdate(selected, reportReason)
                                    reportingUpdate = null
                                    error = null
                                } catch (e: Exception) { error = e.message }
                                finally { saving = false }
                            }
                        }
                    ) { Text("Submit report") }
                }
            )
        }
        if (showReports) {
            AlertDialog(
                onDismissRequest = { showReports = false },
                title = { Text("Channel moderation") },
                text = {
                    LazyColumn {
                        if (reports.isEmpty()) item { Text("No reports.") }
                        items(reports, key = { it.id }) { report ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(report.reason)
                                if (report.reviewed) Text("Reviewed") else {
                                    Row {
                                        TextButton(onClick = {
                                            scope.launch {
                                                try {
                                                    backend.reviewChannelReport(report.id, false)
                                                    reports = backend.channelReports(channel.id)
                                                } catch (e: Exception) { error = e.message }
                                            }
                                        }) { Text("Dismiss") }
                                        TextButton(onClick = {
                                            scope.launch {
                                                try {
                                                    backend.reviewChannelReport(report.id, true)
                                                    reports = backend.channelReports(channel.id)
                                                    updates = backend.channelUpdates(channel.id)
                                                } catch (e: Exception) { error = e.message }
                                            }
                                        }) { Text("Remove update") }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showReports = false }) { Text("Close") }
                }
            )
        }
        if (showScheduled) {
            AlertDialog(
                onDismissRequest = { showScheduled = false },
                title = { Text("Scheduled updates") },
                text = {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (scheduled.isEmpty()) item { Text("No scheduled announcements.") }
                        items(scheduled, key = { it.first }) { entry ->
                            Column {
                                Text(entry.second, style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = {
                                    scope.launch {
                                        try {
                                            backend.cancelScheduledChannelUpdate(entry.first)
                                            scheduled = backend.scheduledChannelUpdates(channel.id)
                                        } catch (e: Exception) { error = e.message }
                                    }
                                }) { Text("Cancel this scheduled update") }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showScheduled = false }) { Text("Close") }
                }
            )
        }
        if (showStats) {
            AlertDialog(
                onDismissRequest = { showStats = false },
                title = { Text("Channel analytics") },
                text = {
                    val values = runCatching { JSONObject(stats) }.getOrElse { JSONObject() }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("followers" to "Followers", "updates" to "Published updates",
                            "views" to "Unique views", "reactions" to "Emoji reactions",
                            "poll_votes" to "Poll votes").forEach { (key, label) ->
                            Text("$label: ${values.optLong(key, 0)}")
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showStats = false }) { Text("Close") } }
            )
        }
    }
}
