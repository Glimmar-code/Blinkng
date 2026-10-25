package com.blinkng.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.blinkng.desktop.data.DesktopRpcActions
import com.blinkng.shared.notification.BlinkAdminAnnouncementDraft
import com.blinkng.shared.notification.BlinkAnnouncementAudience
import com.blinkng.shared.notification.BlinkAnnouncementDelivery
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Windows desktop companion to Android's authenticated Announcement Manager. */
@Composable
fun DesktopAdminAnnouncementManager(
    actions: DesktopRpcActions,
    onDismiss: () -> Unit,
    onSent: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var delivery by remember { mutableStateOf(BlinkAnnouncementDelivery.IN_APP) }
    var audience by remember { mutableStateOf(BlinkAnnouncementAudience.EVERYONE) }
    var scheduled by remember { mutableStateOf(false) }
    var university by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf<JSONObject?>(null) }
    var selectingUser by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf(JSONArray()) }
    var searching by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var confirmEveryone by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val draft = BlinkAdminAnnouncementDraft(
        title = title,
        body = message,
        delivery = delivery,
        audience = audience,
        userId = selectedUser?.optString("id"),
        university = university,
        sendLater = scheduled,
    )

    Dialog(onCloseRequest = { if (!sending) onDismiss() }, title = "BLINK — Announcement Manager") {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp)
                    .verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Admin Announcement Manager", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Server-authorized official messages. Preview before publishing.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(title, { title = it.take(120) }, modifier = Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true)
                OutlinedTextField(message, { message = it.take(1_600) }, modifier = Modifier.fillMaxWidth(), label = { Text("Message") }, minLines = 4)
                Text("Delivery", fontWeight = FontWeight.SemiBold)
                BlinkAnnouncementDelivery.entries.forEach { mode ->
                    FilterChip(selected = delivery == mode, onClick = { delivery = mode }, label = { Text(mode.label) })
                }
                Text("Audience", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlinkAnnouncementAudience.entries.forEach { group ->
                        FilterChip(selected = audience == group, onClick = { audience = group }, label = { Text(group.label) })
                    }
                }
                if (audience == BlinkAnnouncementAudience.USER) {
                    Text(
                        "Selected user: " + (selectedUser?.optString("username")?.let { "@$it" } ?: "None"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = { query = ""; selectingUser = true }) { Text("Search BLINK users") }
                }
                if (audience == BlinkAnnouncementAudience.UNIVERSITY) {
                    OutlinedTextField(
                        university, { university = it.take(120) }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("University name") }, singleLine = true,
                    )
                }
                FilterChip(
                    selected = scheduled, onClick = { scheduled = !scheduled },
                    label = { Text("Schedule later (requires server activation)") },
                )
                draft.unavailableReason()?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                draft.validate()?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { confirmEveryone = ""; confirm = true },
                        enabled = draft.canPublishWithExistingBackend() && !sending,
                    ) { Text("Preview & confirm") }
                    OutlinedButton(onClick = onDismiss, enabled = !sending) { Text("Close") }
                }
                Text(
                    "Phone push, combined delivery, and scheduled announcements are not activated yet. Choosing them never silently sends through a different channel.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (selectingUser) {
        fun search() {
            scope.launch {
                searching = true
                runCatching { actions.adminSearchAnnouncementUsers(query) }
                    .onSuccess { matches = it; error = null }
                    .onFailure { error = it.message ?: "Could not search users." }
                searching = false
            }
        }
        LaunchedEffect(selectingUser) { search() }
        AlertDialog(
            onDismissRequest = { selectingUser = false },
            title = { Text("Select recipient") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(query, { query = it }, label = { Text("Name or @username") }, singleLine = true)
                    OutlinedButton(onClick = ::search, enabled = !searching) {
                        Text(if (searching) "Searching…" else "Find people")
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                        items((0 until matches.length()).toList()) { index ->
                            val user = matches.optJSONObject(index) ?: return@items
                            TextButton(
                                onClick = { selectedUser = user; selectingUser = false },
                            ) { Text("@${user.optString("username")} • ${user.optString("full_name")}") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectingUser = false }) { Text("Cancel") } },
        )
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { if (!sending) confirm = false },
            title = { Text("Confirm official announcement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(message)
                    Text("Channel: ${delivery.label}")
                    Text("Audience: ${audience.label}")
                    if (audience == BlinkAnnouncementAudience.EVERYONE) {
                        OutlinedTextField(
                            confirmEveryone, { confirmEveryone = it },
                            label = { Text("Type SEND to confirm all users") }, singleLine = true,
                        )
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(
                    enabled = draft.canPublishWithExistingBackend() && !sending &&
                        (audience != BlinkAnnouncementAudience.EVERYONE || confirmEveryone == "SEND"),
                    onClick = {
                        sending = true
                        error = null
                        scope.launch {
                            runCatching {
                                actions.adminSendInAppAnnouncement(
                                    message = draft.formattedMessage(),
                                    userId = if (audience == BlinkAnnouncementAudience.USER) selectedUser?.optString("id") else null,
                                    university = if (audience == BlinkAnnouncementAudience.UNIVERSITY) university.trim() else null,
                                )
                            }.onSuccess { delivered ->
                                sending = false
                                confirm = false
                                onSent("Official in-app announcement delivered to $delivered users.")
                            }.onFailure {
                                sending = false
                                error = it.message ?: "Announcement could not be sent."
                            }
                        }
                    },
                ) { Text(if (sending) "Sending…" else "Send now") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }, enabled = !sending) { Text("Cancel") } },
        )
    }
}
