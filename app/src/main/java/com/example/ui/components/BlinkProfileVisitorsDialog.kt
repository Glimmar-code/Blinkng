package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.repository.FollowStateStore
import com.example.notification.BlinkProfileVisitPreferences
import com.example.notification.BlinkProfileVisitorClient
import com.example.notification.BlinkProfileVisitorSnapshot
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos

/**
 * Owner-only UI. The backend verifies the signed-in owner and never exposes
 * visitors who declined participation or blocked the profile.
 */
@Composable
fun BlinkProfileVisitorsDialog(
    accountId: String,
    onDismiss: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onReadConfirmed: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val following by FollowStateStore.followingIds.collectAsState()
    var settings by remember(accountId) { mutableStateOf<BlinkProfileVisitPreferences?>(null) }
    var snapshot by remember(accountId) { mutableStateOf<BlinkProfileVisitorSnapshot?>(null) }
    var loading by remember(accountId) { mutableStateOf(true) }
    var saving by remember(accountId) { mutableStateOf(false) }
    var followingBusy by remember(accountId) { mutableStateOf<String?>(null) }
    var error by remember(accountId) { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        loading = true
        error = null
        runCatching {
            settings = BlinkProfileVisitorClient.settings()
            snapshot = BlinkProfileVisitorClient.list()
        }.onFailure { error = it.message ?: "Could not load profile visitors." }
        loading = false
    }

    // Only mark read after the list has successfully loaded and the dialog has
    // actually rendered. A failed read leaves the eye badge unchanged.
    LaunchedEffect(accountId) {
        refresh()
        val result = snapshot
        if (error == null && result?.enabled == true) {
            withFrameNanos { }
            runCatching { BlinkProfileVisitorClient.acknowledge(result.seenThrough) }
                .onSuccess { ok ->
                    if (ok) onReadConfirmed(BlinkProfileVisitorClient.newCount())
                }
                .onFailure { error = "Visits loaded, but their unread count could not be updated." }
        }
        FollowStateStore.refresh()
    }

    fun changePreferences(participate: Boolean, notifyVisits: Boolean) {
        if (saving) return
        saving = true
        error = null
        scope.launch {
            runCatching {
                val updated = BlinkProfileVisitorClient.saveSettings(participate, notifyVisits)
                val fresh = BlinkProfileVisitorClient.list()
                updated to fresh
            }.onSuccess { (prefs, visits) ->
                settings = prefs
                snapshot = visits
                if (!prefs.participate) onReadConfirmed(0)
            }.onFailure { error = it.message ?: "Could not save privacy settings." }
            saving = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().heightIn(max = 610.dp),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Profile Visitors",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Text(
                    "Your visitor history is private. To see names, you must agree to appear in other people's visitor lists too.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show my visits and see visitors", fontWeight = FontWeight.SemiBold)
                            Text("Off means private browsing. Switching off deletes identifiable visit history.",
                                style = MaterialTheme.typography.labelSmall)
                        }
                        Switch(
                            checked = settings?.participate == true,
                            onCheckedChange = { changePreferences(it, settings?.notifyVisits == true) },
                            enabled = settings != null && !saving,
                        )
                    }
                    if (settings?.participate == true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Notify me of profile visits", modifier = Modifier.weight(1f))
                            Switch(
                                checked = settings?.notifyVisits == true,
                                onCheckedChange = { changePreferences(true, it) },
                                enabled = !saving,
                            )
                        }
                        HorizontalDivider()
                        Text("Recent visitors", style = MaterialTheme.typography.titleMedium)
                        val visitors = snapshot?.visitors.orEmpty()
                        if (visitors.isEmpty()) {
                            Text("No identifiable visits yet.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f, fill = false),
                                contentPadding = PaddingValues(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(visitors, key = { it.id }) { v ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(shape = CircleShape, modifier = Modifier.size(42.dp)) {
                                            AsyncImage(model = v.avatarUrl.takeIf(String::isNotBlank),
                                                contentDescription = null, contentScale = ContentScale.Crop)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(v.name, maxLines = 1, fontWeight = FontWeight.SemiBold)
                                            Text("@${v.username}", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                                            Text(v.lastVisitedAt.replace('T', ' ').take(16) + " UTC",
                                                style = MaterialTheme.typography.labelSmall)
                                        }
                                        if (v.id in following) {
                                            OutlinedButton(onClick = { onOpenProfile(v.id) }) { Text("Following") }
                                        } else {
                                            Button(
                                                enabled = followingBusy == null,
                                                onClick = {
                                                    followingBusy = v.id
                                                    scope.launch {
                                                        val ok = runCatching {
                                                            FollowStateStore.setFollowing(v.id, true)
                                                        }.getOrDefault(false)
                                                        if (!ok) error = "Could not follow @${v.username}."
                                                        followingBusy = null
                                                    }
                                                },
                                            ) {
                                                if (followingBusy == v.id) CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                                                ) else Text("Follow")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (settings != null) {
                        Text("Private browsing is on. Enable participation to start recording reciprocal profile visits.",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { scope.launch { refresh() } }) { Text("Retry") }
                }
            }
        }
    }
}
