package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.blinkng.shared.notification.BlinkAdminAnnouncementDraft
import com.blinkng.shared.notification.BlinkAnnouncementAudience
import com.blinkng.shared.notification.BlinkAnnouncementDelivery
import com.example.data.supabase.AdminSupabaseService
import com.example.data.supabase.AdminUserSummary
import kotlinx.coroutines.launch

/**
 * Existing authenticated admin RPC is authoritative. This screen never sends
 * unverified phone pushes or pretends local scheduling has reached the server.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlinkAdminAnnouncementManagerScreen(
    service: AdminSupabaseService,
    onBack: () -> Unit,
    onSent: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var delivery by remember { mutableStateOf(BlinkAnnouncementDelivery.IN_APP) }
    var audience by remember { mutableStateOf(BlinkAnnouncementAudience.EVERYONE) }
    var scheduled by remember { mutableStateOf(false) }
    var university by remember { mutableStateOf("") }
    var chosenUser by remember { mutableStateOf<AdminUserSummary?>(null) }
    var userPicker by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<AdminUserSummary>>(emptyList()) }
    var userBusy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var confirmAll by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val draft = BlinkAdminAnnouncementDraft(
        title = title,
        body = body,
        audience = audience,
        delivery = delivery,
        userId = chosenUser?.id,
        university = university,
        sendLater = scheduled,
    )

    BackHandler(enabled = confirm || userPicker) {
        when {
            confirm -> confirm = false
            userPicker -> userPicker = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Announcement Manager", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack, enabled = !sending) { Text("Back") }
        }
        HorizontalDivider()
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Create official BLINK announcement", fontWeight = FontWeight.SemiBold)
            Text(
                "Compose and preview before publishing. Your admin privileges are verified again by Supabase.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                title, { title = it.take(120) },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                body, { body = it.take(1_600) },
                label = { Text("Announcement message") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
            Text("Delivery", fontWeight = FontWeight.SemiBold)
            BlinkAnnouncementDelivery.entries.forEach { mode ->
                FilterChip(
                    selected = delivery == mode,
                    onClick = { delivery = mode },
                    label = { Text(mode.label) },
                )
            }
            Text("Recipients", fontWeight = FontWeight.SemiBold)
            BlinkAnnouncementAudience.entries.forEach { group ->
                FilterChip(
                    selected = audience == group,
                    onClick = { audience = group },
                    label = { Text(group.label) },
                )
            }

            if (audience == BlinkAnnouncementAudience.USER) {
                Text(
                    chosenUser?.let { "Selected: @${it.username} (${it.fullName})" } ?: "No user selected",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { query = ""; userPicker = true }) {
                    Text("Choose user • Search")
                }
            }
            if (audience == BlinkAnnouncementAudience.UNIVERSITY) {
                OutlinedTextField(
                    university, { university = it.take(120) },
                    label = { Text("University name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            FilterChip(
                selected = scheduled,
                onClick = { scheduled = !scheduled },
                label = { Text("Schedule for later (coming soon)") },
            )
            draft.unavailableReason()?.let { why ->
                Text(why, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            draft.validate()?.let { why ->
                Text(why, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = draft.canPublishWithExistingBackend() && !sending,
                onClick = { error = null; confirmAll = ""; confirm = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Preview & confirm delivery")
            }
            Text(
                "Push-only, push + in-app, and scheduled broadcasts are shown for planning but remain disabled until a verified server dispatcher is available.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (userPicker) {
        fun loadUsers() {
            scope.launch {
                userBusy = true
                service.searchUsersV2(query, 20)
                    .onSuccess { users = it }
                    .onFailure { error = it.message ?: "Couldn't search users." }
                userBusy = false
            }
        }
        LaunchedEffect(userPicker) { loadUsers() }
        AlertDialog(
            onDismissRequest = { userPicker = false },
            title = { Text("Select a BLINK user") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        query,
                        { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search name or @username") },
                        singleLine = true,
                    )
                    OutlinedButton(onClick = ::loadUsers, enabled = !userBusy) {
                        Text(if (userBusy) "Searching…" else "Search users")
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(users, key = { it.id }) { user ->
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { chosenUser = user; userPicker = false },
                            ) { Text("@${user.username} • ${user.fullName}") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { userPicker = false }) { Text("Cancel") } },
        )
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { if (!sending) confirm = false },
            title = { Text("Review official announcement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(draft.title.trim(), fontWeight = FontWeight.Bold)
                    Text(draft.body.trim())
                    Text("Delivery: ${draft.delivery.label}")
                    Text(
                        "To: " + when (audience) {
                            BlinkAnnouncementAudience.EVERYONE -> "All BLINK users"
                            BlinkAnnouncementAudience.USER -> "@${chosenUser?.username.orEmpty()}"
                            BlinkAnnouncementAudience.UNIVERSITY -> university.trim()
                        },
                    )
                    if (audience == BlinkAnnouncementAudience.EVERYONE) {
                        OutlinedTextField(
                            confirmAll, { confirmAll = it },
                            label = { Text("Type SEND to confirm broadcast") },
                            singleLine = true,
                        )
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(
                    enabled = draft.canPublishWithExistingBackend() && !sending &&
                        (audience != BlinkAnnouncementAudience.EVERYONE || confirmAll == "SEND"),
                    onClick = {
                        sending = true
                        error = null
                        scope.launch {
                            service.sendAnnouncement(
                                message = draft.formattedMessage(),
                                targetUserId = if (audience == BlinkAnnouncementAudience.USER) chosenUser?.id else null,
                                targetUniversity = if (audience == BlinkAnnouncementAudience.UNIVERSITY) university.trim() else null,
                            ).onSuccess { count ->
                                confirm = false
                                sending = false
                                title = ""
                                body = ""
                                onSent("Official in-app announcement delivered to $count users.")
                            }.onFailure {
                                sending = false
                                error = it.message ?: "Announcement couldn't be delivered."
                            }
                        }
                    },
                ) { Text(if (sending) "Sending…" else "Send now") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }, enabled = !sending) { Text("Cancel") }
            },
        )
    }
}
