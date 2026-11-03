package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.blinkng.shared.ProfileVisitHistory
import com.blinkng.shared.ProfileVisitSettings
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.launch

/** No guessed visitor identities and no public/profile-owner data fallback.
 * All reads and writes are authenticated, server-authorized and opt-in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileVisitorsSheet(
    service: SupabaseService,
    onDismiss: () -> Unit,
    onOpenProfile: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<ProfileVisitSettings?>(null) }
    var history by remember { mutableStateOf<ProfileVisitHistory?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var newVisitors by remember { mutableIntStateOf(0) }
    var confirmDisable by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        loading = true
        message = null
        history = null
        val preference = service.getMyProfileVisitSettings()
        if (preference.isFailure) {
            settings = null
            message = "Profile visitor history is not available yet. Your privacy settings were not changed."
        } else {
            settings = preference.getOrNull()
            if (settings?.participate == true) {
                newVisitors = service.countMyNewProfileVisitors()
                val result = service.listMyProfileVisitors()
                if (result.isSuccess) {
                    history = result.getOrNull()
                    history?.seenThrough?.takeIf { it.isNotBlank() }?.let {
                        service.acknowledgeMyProfileVisitorsSeen(it)
                    }
                } else {
                    message = "Couldn't load visitor history. Please retry."
                }
            }
        }
        loading = false
    }

    fun changeSettings(participate: Boolean, notify: Boolean) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            val result = service.setMyProfileVisitSettings(participate, notify)
            if (result.isSuccess) {
                settings = result.getOrNull()
                refreshKey++
            } else {
                message = "Couldn't save this privacy preference. Please retry."
            }
            busy = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Profile visitors", style = MaterialTheme.typography.titleLarge)
            Text(
                "Only people who opt in can appear in visitor history, and only when you also opt in. Blocked users never appear.",
                style = MaterialTheme.typography.bodySmall
            )
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(30.dp))
            } else {
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                if (settings == null) {
                    OutlinedButton(onClick = { refreshKey++ }, enabled = !busy) { Text("Retry") }
                } else if (settings?.participate == false) {
                    Text("Visitor history is off. BLINK won't record identifiable profile visits for this account.")
                    Button(onClick = { changeSettings(true, false) }, enabled = !busy) {
                        Text(if (busy) "Saving…" else "Enable visitor history")
                    }
                } else {
                    if (newVisitors > 0) {
                        Text("$newVisitors new visitor(s) since your last check.",
                            style = MaterialTheme.typography.labelLarge)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Visitor notifications", style = MaterialTheme.typography.titleSmall)
                            Text("Send an alert when an eligible visit is recorded.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = settings?.notifyVisits == true,
                            enabled = !busy,
                            onCheckedChange = { changeSettings(true, it) }
                        )
                    }
                    val current = history
                    when {
                        current == null && message == null -> Text("No history available.")
                        current?.enabled == false -> Text("Visitor history is unavailable for this account.")
                        current?.visitors?.isEmpty() == true ->
                            Text("No eligible profile visits yet. Both people must opt in.")
                        current != null -> {
                            Text("${current.visitors.size} recent visitor(s)",
                                style = MaterialTheme.typography.titleSmall)
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 330.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                items(current.visitors, key = { it.id }) { visitor ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = visitor.avatarUrl,
                                            contentDescription = "Profile picture of ${visitor.fullName}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(42.dp).clip(CircleShape)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(visitor.fullName, maxLines = 1)
                                            Text("@${visitor.username} · ${visitor.visitedAt.take(10)}",
                                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        TextButton(onClick = { onDismiss(); onOpenProfile(visitor.username) }) {
                                            Text("View")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { refreshKey++ }, enabled = !busy) { Text("Refresh") }
                        TextButton(onClick = { confirmDisable = true }, enabled = !busy) {
                            Text("Turn off history", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text("Turn off visitor history?") },
            text = { Text(
                "This permanently erases your identifiable visitor history and removes your visits from other accounts' history. You can enable it again later, but deleted visits won't return."
            ) },
            confirmButton = {
                TextButton(onClick = { confirmDisable = false; changeSettings(false, false) },
                    enabled = !busy) { Text("Turn off and erase") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisable = false }) { Text("Cancel") }
            }
        )
    }
}
