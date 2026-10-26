package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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

    suspend fun refresh() {
        loading = true
        try {
            role = backend.myChannelRole(channel.id)
            followers = backend.channelFollowerCount(channel.id)
            updates = backend.channelUpdates(channel.id)
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Unable to load this channel."
        } finally {
            loading = false
        }
    }

    BackHandler(onBack = onBack)
    LaunchedEffect(channel.id) { refresh() }
    val admin = role == "owner" || role == "moderator"
    val following = admin || role == "follower"

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                Text(channel.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, maxLines = 1)
                Text("$followers followers", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = { scope.launch { refresh() } }, enabled = !loading && !saving) {
                Text("Refresh")
            }
        }
        HorizontalDivider()
        LazyColumn(
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
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(channel.name, style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold)
                        Text(update.body, style = MaterialTheme.typography.bodyLarge)
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
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
        HorizontalDivider()
        if (admin) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it.take(BlinkChannelPolicy.MAX_UPDATE_LENGTH) },
                    label = { Text("Broadcast an update") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4
                )
                Button(
                    enabled = !saving && draft.isNotBlank(),
                    onClick = {
                        scope.launch {
                            saving = true
                            try {
                                backend.publishChannelUpdate(channel.id, draft)
                                draft = ""
                                updates = backend.channelUpdates(channel.id)
                                error = null
                            } catch (e: Exception) { error = e.message }
                            finally { saving = false }
                        }
                    }
                ) { Text("Post") }
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
    }
}
