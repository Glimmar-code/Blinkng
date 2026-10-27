package com.blinkng.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.desktop.data.DesktopSupabaseClient
import com.blinkng.shared.ProfileVisitSettings
import com.blinkng.shared.ProfileVisitHistory
import kotlinx.coroutines.launch

/** Desktop equivalent of Android's owner-only, mutual-consent visitor history. */
@Composable
internal fun DesktopProfileVisitorsDialog(
    client: DesktopSupabaseClient,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<ProfileVisitSettings?>(null) }
    var history by remember { mutableStateOf<ProfileVisitHistory?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var key by remember { mutableIntStateOf(0) }
    var confirmDisable by remember { mutableStateOf(false) }

    LaunchedEffect(key) {
        loading = true
        error = null
        history = null
        val first = runCatching { client.getMyProfileVisitSettings() }
        if (first.isFailure) {
            settings = null
            error = "Profile visitor history is not available yet. No privacy setting was changed."
        } else {
            settings = first.getOrNull()
            if (settings?.participate == true) {
                runCatching { client.listMyProfileVisitors() }
                    .onSuccess { history = it }
                    .onFailure { error = "Couldn't load visitor history." }
            }
        }
        loading = false
    }
    fun save(enabled: Boolean, notify: Boolean) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            runCatching { client.setMyProfileVisitSettings(enabled, notify) }
                .onSuccess { settings = it; key++ }
                .onFailure { error = "Couldn't save visitor privacy settings." }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Profile visitors") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Visitor identities appear only when both accounts choose to participate. Blocked accounts never appear.",
                    style = MaterialTheme.typography.bodySmall)
                if (loading) CircularProgressIndicator(modifier = Modifier.size(28.dp))
                if (!loading) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    when (settings) {
                        null -> OutlinedButton(onClick = { key++ }) { Text("Retry") }
                        else -> {
                            if (settings?.participate == false) {
                                Text("Visitor history is off.")
                                Button(onClick = { save(true, false) }, enabled = !busy) {
                                    Text(if (busy) "Saving…" else "Enable visitor history")
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Visitor alerts")
                                        Text("Notify me of eligible visits.", fontSize = 11.sp)
                                    }
                                    Switch(
                                        checked = settings?.notifyVisits == true,
                                        enabled = !busy,
                                        onCheckedChange = { save(true, it) }
                                    )
                                }
                                val h = history
                                when {
                                    h == null && error == null -> Text("No history available.")
                                    h?.enabled == false -> Text("Visitor history is unavailable.")
                                    h?.visitors?.isEmpty() == true ->
                                        Text("No eligible visits yet. Both accounts must opt in.")
                                    h != null -> {
                                        Text("${h.visitors.size} recent visitors", fontSize = 13.sp)
                                        h.visitors.forEach { visitor ->
                                            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                                Text(visitor.fullName.ifBlank { visitor.username })
                                                Text("@${visitor.username} · ${visitor.visitedAt.take(10)}",
                                                    style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    TextButton(onClick = { key++ }, enabled = !busy) { Text("Refresh") }
                                    TextButton(onClick = { confirmDisable = true }, enabled = !busy) {
                                        Text("Turn off history", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Done") } }
    )
    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text("Erase visitor history?") },
            text = { Text("Turning off permanently deletes your recorded visitor history and your appearances in other profiles' histories. Re-enabling won't restore deleted visits.") },
            confirmButton = {
                TextButton(onClick = { confirmDisable = false; save(false, false) }, enabled = !busy) {
                    Text("Turn off and erase")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisable = false }) { Text("Cancel") }
            }
        )
    }
}
