package com.blinkng.desktop.ui

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import javax.swing.JFileChooser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.blinkng.desktop.DesktopAppState
import com.blinkng.desktop.data.DesktopCommunity
import com.blinkng.shared.BlinkChannelPolicy
import com.blinkng.shared.BlinkChannelUpdate
import com.blinkng.shared.BlinkChannelReport
import org.json.JSONObject
import kotlinx.coroutines.launch

/** Broadcast-channel reader on Windows: same permissions and updates as Android. */
@Composable
fun DesktopChannelDetail(
    state: DesktopAppState,
    channel: DesktopCommunity,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var updates by remember(channel.id) { mutableStateOf<List<BlinkChannelUpdate>>(emptyList()) }
    var role by remember(channel.id) { mutableStateOf("none") }
    var followers by remember(channel.id) { mutableStateOf(0) }
    var loading by remember(channel.id) { mutableStateOf(true) }
    var saving by remember(channel.id) { mutableStateOf(false) }
    var error by remember(channel.id) { mutableStateOf<String?>(null) }
    var draft by remember(channel.id) { mutableStateOf("") }

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
    var selectedMedia by remember(channel.id) { mutableStateOf<File?>(null) }
    var pollQuestion by remember(channel.id) { mutableStateOf("") }
    var pollOptions by remember(channel.id) { mutableStateOf("") }
    var scheduledAt by remember(channel.id) { mutableStateOf("") }
    suspend fun refresh() {
        loading = true
        try {
            role = state.client.channelRole(channel.id)
            followers = state.client.channelFollowerCount(channel.id)
            muted = state.client.isChannelMuted(channel.id)
            updates = state.client.channelUpdates(channel.id)
            unread = runCatching { state.client.channelUnreadCount(channel.id) }.getOrDefault(0)
            runCatching { state.client.markChannelSeen(channel.id) }
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Unable to load channel."
        } finally { loading = false }
    }
    LaunchedEffect(channel.id) { refresh() }
    LaunchedEffect(channel.id) {
        while (true) {
            delay(45_000)
            if (!saving) runCatching { updates = state.client.channelUpdates(channel.id) }
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

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text("‹ Channels") }
            Column(Modifier.weight(1f)) {
                Text(channel.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("$followers followers" + if (unread > 0) " · $unread new" else "",
                    style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = {
                scope.launch {
                    saving = true
                    try { state.client.muteChannel(channel.id, !muted); muted = !muted }
                    catch (e: Exception) { error = e.message }
                    finally { saving = false }
                }
            }, enabled = !saving) { Text(if (muted) "🔕" else "🔔") }
            if (admin) TextButton(onClick = {
                scope.launch {
                    try { stats = state.client.channelStats(channel.id).toString(2); showStats = true }
                    catch (e: Exception) { error = e.message }
                }
            }) { Text("Analytics") }
            if (admin) TextButton(onClick = {
                scope.launch {
                    try {
                        scheduled = state.client.scheduledChannelUpdates(channel.id)
                        showScheduled = true
                    } catch (e: Exception) { error = e.message }
                }
            }) { Text("Scheduled") }
            if (admin) TextButton(onClick = {
                scope.launch {
                    try {
                        reports = state.client.channelReports(channel.id)
                        showReports = true
                    } catch (e: Exception) { error = e.message }
                }
            }) { Text("Reports") }
            TextButton(onClick = { scope.launch { refresh() } }, enabled = !loading && !saving) {
                Text("Refresh")
            }
        }
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxWidth().weight(1f),
            state = updatesListState,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(channel.name, style = MaterialTheme.typography.headlineMedium)
                    Text(channel.university + " · " + channel.category)
                    if (channel.description.isNotBlank()) Text(channel.description)
                    Text("Only channel administrators can publish updates.",
                        style = MaterialTheme.typography.labelMedium)
                }
            }
            if (loading) item { CircularProgressIndicator(Modifier.padding(12.dp)) }
            if (!error.isNullOrBlank()) item {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
            if (!loading && error == null && updates.isEmpty()) item {
                Text("No channel updates yet.", Modifier.padding(12.dp))
            }
            items(updates, key = { it.id }) { update ->
                LaunchedEffect(update.id) {
                    runCatching { state.client.recordChannelView(update.id) }
                }
                var signedMedia by remember(update.id, update.mediaPath) { mutableStateOf<String?>(null) }
                LaunchedEffect(update.mediaPath) {
                    signedMedia = update.mediaPath?.let {
                        runCatching { state.client.signedChannelMedia(it) }.getOrNull()
                    }
                }
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(channel.name, fontWeight = FontWeight.Bold)
                        if (update.pinned) Text("📌 Pinned announcement",
                            color = MaterialTheme.colorScheme.primary)
                        if (update.body.isNotBlank())
                            Text(update.body, style = MaterialTheme.typography.bodyLarge)
                        if (update.mediaPath != null) TextButton(
                            enabled = signedMedia != null,
                            onClick = {
                                signedMedia?.let { address ->
                                    runCatching { Desktop.getDesktop().browse(URI(address)) }
                                        .onFailure { error = "Unable to open channel media." }
                                }
                            }
                        ) { Text(if (signedMedia == null) "Loading attachment…" else "Open ${update.mediaKind}") }
                        if (update.mediaKind == "poll") {
                            Text(update.pollQuestion.orEmpty(), fontWeight = FontWeight.SemiBold)
                            update.pollOptions.forEachIndexed { index, option ->
                                TextButton(
                                    enabled = following && !saving,
                                    onClick = {
                                        scope.launch {
                                            saving = true
                                            try {
                                                state.client.voteChannelPoll(update.id, index)
                                                updates = state.client.channelUpdates(channel.id)
                                            } catch (e: Exception) { error = e.message }
                                            finally { saving = false }
                                        }
                                    }
                                ) {
                                    Text("${if (update.myVote == index) "●" else "○"} $option · ${update.pollCounts[index] ?: 0}")
                                }
                            }
                        }
                        Text("${update.viewCount} views", style = MaterialTheme.typography.labelSmall)
                        Text(update.createdAt.take(16).replace('T', ' '),
                            style = MaterialTheme.typography.labelSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(BlinkChannelPolicy.reactions) { emoji ->
                                TextButton(
                                    enabled = following && !saving,
                                    onClick = {
                                        scope.launch {
                                            saving = true
                                            try {
                                                state.client.reactToChannelUpdate(update.id, emoji)
                                                updates = state.client.channelUpdates(channel.id)
                                                error = null
                                            } catch (e: Exception) { error = e.message }
                                            finally { saving = false }
                                        }
                                    }
                                ) {
                                    Text(emoji + update.reactions[emoji]?.takeIf { it > 0 }?.let { " $it" }.orEmpty(),
                                        color = if (update.myReaction == emoji)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = {
                                Toolkit.getDefaultToolkit().systemClipboard.setContents(
                                    StringSelection("${channel.name} — BLINK Channel\n${update.body}"), null)
                            }) { Text("Copy / Forward") }
                            TextButton(onClick = { reportingUpdate = update.id }) { Text("Report") }
                            if (admin) {
                                TextButton(onClick = {
                                    scope.launch {
                                        try {
                                            state.client.moderateChannelUpdate(update.id,
                                                if (update.pinned) "unpin" else "pin")
                                            updates = state.client.channelUpdates(channel.id)
                                        } catch (e: Exception) { error = e.message }
                                    }
                                }) { Text(if (update.pinned) "Unpin" else "Pin") }
                                TextButton(onClick = {
                                    scope.launch {
                                        try {
                                            state.client.moderateChannelUpdate(update.id, "remove")
                                            updates = state.client.channelUpdates(channel.id)
                                        } catch (e: Exception) { error = e.message }
                                    }
                                }) { Text("Remove") }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
        HorizontalDivider()
        if (admin) {
            LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("text","image","video","audio","document","poll")) { kind ->
                    FilterChip(
                        selected = mediaKind == kind,
                        onClick = {
                            mediaKind = kind
                            if (kind in setOf("image","video","audio","document")) {
                                val chooser = JFileChooser()
                                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                    selectedMedia = chooser.selectedFile
                                }
                            }
                        },
                        label = { Text(kind.replaceFirstChar(Char::uppercase)) }
                    )
                }
            }
            if (mediaKind == "poll") {
                OutlinedTextField(pollQuestion, { pollQuestion = it.take(300) },
                    label = { Text("Poll question") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pollOptions, { pollOptions = it.take(800) },
                    label = { Text("2–8 options, one per line") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 5)
            }
            if (selectedMedia != null) {
                Text("Selected: ${selectedMedia?.name.orEmpty()}")
            }
            OutlinedTextField(scheduledAt, { scheduledAt = it.take(30) },
                label = { Text("Schedule UTC (optional): 2026-11-01T12:00:00Z") },
                modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(draft, { draft = it.take(BlinkChannelPolicy.MAX_UPDATE_LENGTH) },
                    label = { Text("Broadcast update") }, modifier = Modifier.weight(1f), maxLines = 4)
                Button(
                    enabled = !saving && (draft.isNotBlank() || selectedMedia != null ||
                        (mediaKind == "poll" && pollQuestion.isNotBlank())),
                    onClick = {
                        scope.launch {
                            saving = true
                            try {
                                val upload = selectedMedia?.let {
                                    state.client.uploadChannelMedia(channel.id, it)
                                }
                                val chosenKind = upload?.first ?: mediaKind
                                val schedule = scheduledAt.trim().takeIf { it.isNotBlank() }?.let {
                                    java.time.Instant.parse(it).toString()
                                }
                                state.client.publishRichChannelUpdate(
                                    channel.id, draft, chosenKind, upload?.second,
                                    if (chosenKind == "poll") pollQuestion else null,
                                    if (chosenKind == "poll")
                                        pollOptions.lines().filter { it.isNotBlank() }
                                    else emptyList(), schedule
                                )
                                draft = ""; selectedMedia = null; mediaKind = "text"
                                pollQuestion = ""; pollOptions = ""; scheduledAt = ""
                                updates = state.client.channelUpdates(channel.id)
                                error = null
                            } catch (e: Exception) { error = e.message }
                            finally { saving = false }
                        }
                    }
                ) { Text(if (scheduledAt.isBlank()) "Post" else "Schedule") }
            }
        } else if (!following) {
            Button(enabled = role != "pending" && role != "banned" && !saving,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                onClick = {
                    scope.launch {
                        saving = true
                        try {
                            state.client.requestJoinCommunity(channel.id)
                            refresh()
                        } catch (e: Exception) { error = e.message }
                        finally { saving = false }
                    }
                }
            ) { Text(when (role) { "pending" -> "Follow requested"; "banned" -> "Channel restricted"; else -> "Follow channel" }) }

        } else if (role == "follower") {
            TextButton(
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                onClick = {
                    scope.launch {
                        saving = true
                        try {
                            state.client.leaveCommunity(channel.id)
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
                title = { Text("Report channel update") },
                text = {
                    OutlinedTextField(reportReason, { reportReason = it.take(280) },
                        label = { Text("Reason") })
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
                                    state.client.reportChannelUpdate(selected, reportReason)
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
                            Column {
                                Text(report.reason)
                                if (report.reviewed) Text("Reviewed") else Row {
                                    TextButton(onClick = {
                                        scope.launch {
                                            try {
                                                state.client.reviewChannelReport(report.id, false)
                                                reports = state.client.channelReports(channel.id)
                                            } catch (e: Exception) { error = e.message }
                                        }
                                    }) { Text("Dismiss") }
                                    TextButton(onClick = {
                                        scope.launch {
                                            try {
                                                state.client.reviewChannelReport(report.id, true)
                                                reports = state.client.channelReports(channel.id)
                                                updates = state.client.channelUpdates(channel.id)
                                            } catch (e: Exception) { error = e.message }
                                        }
                                    }) { Text("Remove") }
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
                                Text(entry.second)
                                TextButton(onClick = {
                                    scope.launch {
                                        try {
                                            state.client.cancelScheduledChannelUpdate(entry.first)
                                            scheduled = state.client.scheduledChannelUpdates(channel.id)
                                        } catch (e: Exception) { error = e.message }
                                    }
                                }) { Text("Cancel scheduled update") }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showScheduled = false }) { Text("Close") } }
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
