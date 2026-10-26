package com.blinkng.desktop.ui

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

    suspend fun refresh() {
        loading = true
        try {
            role = state.client.channelRole(channel.id)
            followers = state.client.channelFollowerCount(channel.id)
            updates = state.client.channelUpdates(channel.id)
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Unable to load channel."
        } finally { loading = false }
    }
    LaunchedEffect(channel.id) { refresh() }
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
                Text("$followers followers", style = MaterialTheme.typography.labelMedium)
            }
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
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(channel.name, fontWeight = FontWeight.Bold)
                        Text(update.body, style = MaterialTheme.typography.bodyLarge)
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
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
        HorizontalDivider()
        if (admin) {
            Row(Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(draft, { draft = it.take(BlinkChannelPolicy.MAX_UPDATE_LENGTH) },
                    label = { Text("Broadcast update") }, modifier = Modifier.weight(1f), maxLines = 4)
                Button(
                    enabled = draft.isNotBlank() && !saving,
                    onClick = {
                        scope.launch {
                            saving = true
                            try {
                                state.client.publishChannelUpdate(channel.id, draft)
                                draft = ""
                                updates = state.client.channelUpdates(channel.id)
                                error = null
                            } catch (e: Exception) { error = e.message }
                            finally { saving = false }
                        }
                    }
                ) { Text("Post") }
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
    }
}
