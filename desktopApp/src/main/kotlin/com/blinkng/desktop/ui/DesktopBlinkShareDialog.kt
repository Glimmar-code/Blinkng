package com.blinkng.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.blinkng.desktop.DesktopAppState
import com.blinkng.desktop.data.DesktopConversation
import com.blinkng.desktop.data.DesktopProfile
import com.blinkng.desktop.sharing.DesktopShareLinkManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.net.URI
import java.awt.Desktop

/** Desktop equivalent of BLINK's native Reel share picker; uses real server ACLs. */
@Composable
internal fun DesktopBlinkShareDialog(
    state: DesktopAppState,
    postId: String,
    isReel: Boolean,
    onDismiss: () -> Unit
) {
    var recent by remember(postId) { mutableStateOf<List<DesktopConversation>>(emptyList()) }
    var suggested by remember(postId) { mutableStateOf<List<DesktopProfile>>(emptyList()) }
    var search by remember(postId) { mutableStateOf("") }
    var directory by remember(postId) { mutableStateOf<List<DesktopProfile>>(emptyList()) }
    var directorySearching by remember(postId) { mutableStateOf(false) }
    var directoryError by remember(postId) { mutableStateOf<String?>(null) }
    var busy by remember(postId) { mutableStateOf(false) }
    var error by remember(postId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val link = remember(postId, isReel) { DesktopShareLinkManager.generate(postId, isReel) }
    LaunchedEffect(postId) {
        runCatching { state.client.fetchConversations() }
            .onSuccess { recent = it.filter { convo -> !convo.isGroup && !convo.partnerUsername.isNullOrBlank() } }
            .onFailure { error = "Recent chats unavailable: " + (it.message ?: "network error") }
        runCatching { state.client.fetchOnboardingSuggestions(24) }
            .onSuccess { suggested = it }
    }
    LaunchedEffect(postId, search) {
        directory = emptyList()
        directoryError = null
        val q = search.trim().removePrefix("@")
        if (q.length >= 2) {
            directorySearching = true
            delay(300)
            try { directory = state.client.searchMentionProfiles(q, limit = 60) }
            catch (c: kotlinx.coroutines.CancellationException) { throw c }
            catch (_: Exception) { directoryError = "Directory search unavailable. Try again." }
            finally { directorySearching = false }
        } else directorySearching = false
    }
    val recents = recent.mapNotNull { convo ->
        convo.partnerUsername?.takeIf(String::isNotBlank)?.let { handle -> handle to convo.title }
    }
    val recentNames = recents.map { it.first.lowercase() }.toSet()
    val suggestedHandles = suggested.mapNotNull { profile ->
        profile.username.takeIf {
            it.isNotBlank() && it.lowercase() !in recentNames &&
                !it.equals(state.profile?.username, ignoreCase = true)
        }?.let { it to profile.fullName }
    }
    val matches = (recents + suggestedHandles + directory.map { it.username to it.fullName })
        .filter { it.first.isNotBlank() && !it.first.equals(state.profile?.username, ignoreCase = true) }
        .distinctBy { it.first.lowercase() }
        .filter { (username, name) ->
            search.isBlank() || username.contains(search, ignoreCase = true) ||
                name.contains(search, ignoreCase = true)
        }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (isReel) "Share Reel on BLINK" else "Share on BLINK") },
        text = {
            Column(modifier = Modifier.widthIn(min = 320.dp, max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Send to a BLINK contact or use another app.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = search, onValueChange = { search = it },
                    label = { Text("Search recent chats and people") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                if (directorySearching) LinearProgressIndicator(Modifier.fillMaxWidth())
                directoryError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (matches.isEmpty()) {
                    Text("No matching conversations or suggestions.",
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 270.dp)) {
                        items(matches, key = { it.first }) { (username, display) ->
                            ListItem(
                                headlineContent = { Text(display.ifBlank { username }, fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text("@$username") },
                                modifier = Modifier.clickable(enabled = !busy) {
                                    busy = true
                                    error = null
                                    scope.launch {
                                        runCatching {
                                            val conversationId = state.client
                                                .getOrCreateDirectConversationForShare(username)
                                            state.client.sendMessage(
                                                conversationId = conversationId,
                                                content = link,
                                                partnerUsername = username,
                                                isGroup = false
                                            )
                                        }.onSuccess { onDismiss() }
                                            .onFailure { error = it.message ?: "Could not send this link." }
                                        busy = false
                                    }
                                }
                            )
                        }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        if (!DesktopShareLinkManager.copyToClipboard(postId, isReel)) {
                            error = "Unable to copy the link."
                        }
                    }) { Text("Copy link") }
                    TextButton(onClick = {
                        runCatching {
                            Desktop.getDesktop().browse(
                                URI("https://api.whatsapp.com/send?text=" +
                                    java.net.URLEncoder.encode(link, "UTF-8"))
                            )
                        }.onFailure { error = "Cannot open WhatsApp in a browser." }
                    }) { Text("WhatsApp") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Close") } }
    )
}
