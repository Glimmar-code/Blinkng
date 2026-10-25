package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blinkng.shared.discovery.BlinkReferralSuggestion
import com.example.data.supabase.BlinkGroupInvitationClient
import com.example.data.supabase.BlinkPendingGroupInvite
import kotlinx.coroutines.launch

/**
 * The only Android entry point for consent-based group invitations.
 * Invitees join ONLY when they explicitly accept the request.
 */
@Composable
internal fun BlinkGroupInvitationsDialog(onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<List<BlinkReferralSuggestion>>(emptyList()) }
    var pending by remember { mutableStateOf<List<BlinkPendingGroupInvite>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { BlinkGroupInvitationClient.pendingInvitations() }
            .onSuccess { pending = it }
            .onFailure { error = "Group invitations aren't available yet. Please try again." }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Group invitations") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Create a private invitation. Students can accept or decline before joining.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text("Group name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                BlinkMemberInviteSelector(
                    purpose = "a group",
                    selected = selected,
                    onSelected = { selected = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (selected.isNotEmpty()) {
                    TextButton(onClick = { selected = emptyList() }, enabled = !busy) {
                        Text("Clear selected students")
                    }
                }
                Text("Choose at least two students. Nobody will join until they accept.",
                    style = MaterialTheme.typography.labelSmall)
                Button(
                    enabled = !busy && title.trim().length in 2..80 && selected.size in 2..30,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        busy = true
                        error = null
                        notice = null
                        scope.launch {
                            runCatching {
                                BlinkGroupInvitationClient.createGroup(title, selected.map { it.id })
                            }.onSuccess {
                                notice = "Group created. ${selected.size} invitations are awaiting acceptance."
                                selected = emptyList()
                                title = ""
                            }.onFailure {
                                error = it.message ?: "Couldn't create the group."
                            }
                            busy = false
                        }
                    },
                ) {
                    Text(if (busy) "Submitting…" else "Create group & invite students")
                }

                HorizontalDivider()
                Text("Your pending invitations", style = MaterialTheme.typography.titleSmall)
                if (pending.isEmpty()) Text("No pending invitations.", style = MaterialTheme.typography.labelSmall)
                pending.forEach { invite ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(invite.groupTitle.ifBlank { "Group invitation" },
                            style = MaterialTheme.typography.bodyMedium)
                        Text("Invited by @${invite.inviterUsername}",
                            style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                busy = true
                                scope.launch {
                                    runCatching { BlinkGroupInvitationClient.respond(invite.groupId, true) }
                                        .onSuccess { ok ->
                                            if (ok) {
                                                pending = pending.filterNot { it.groupId == invite.groupId }
                                                notice = "Invitation accepted. Open Messages to see the group."
                                            } else error = "This invitation is no longer available."
                                        }.onFailure { error = "Unable to accept this invitation." }
                                    busy = false
                                }
                            }, enabled = !busy) { Text("Accept") }
                            TextButton(onClick = {
                                busy = true
                                scope.launch {
                                    runCatching { BlinkGroupInvitationClient.respond(invite.groupId, false) }
                                        .onSuccess { ok ->
                                            if (ok) pending = pending.filterNot { it.groupId == invite.groupId }
                                            else error = "This invitation is no longer available."
                                        }.onFailure { error = "Unable to decline this invitation." }
                                    busy = false
                                }
                            }, enabled = !busy) { Text("Decline") }
                        }
                    }
                }
                notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Close") }
        },
    )
}
