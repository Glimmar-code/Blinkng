package com.blinkng.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blinkng.desktop.DesktopAppState
import com.blinkng.desktop.data.DesktopPendingGroupInvite
import com.blinkng.shared.discovery.BlinkReferralSuggestion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Uses the same real-referrer username lookup as Windows signup. No forced membership. */
@Composable
internal fun DesktopGroupInvitationsDialog(
    state: DesktopAppState,
    onDismiss: () -> Unit,
    onMembershipChanged: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<BlinkReferralSuggestion>>(emptyList()) }
    var selected by remember { mutableStateOf<List<BlinkReferralSuggestion>>(emptyList()) }
    var pending by remember { mutableStateOf<List<DesktopPendingGroupInvite>>(emptyList()) }
    var inviteBackend by remember { mutableStateOf("main") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.session?.userId) {
        pending = runCatching {
            inviteBackend = "main"
            state.client.pendingGroupInvitations()
        }.recoverCatching {
            // Testlab's server-authorized invitation RPC predates the main
            // invite-specific endpoint. Neither route grants membership here.
            val staged = state.client.fetchGroupInvitations()
            inviteBackend = "staged"
            staged.map { DesktopPendingGroupInvite(
                groupId = it.conversationId,
                groupTitle = it.title,
                inviterUsername = it.inviterUsername,
            ) }
        }.onFailure { error = "Group invitations require the latest server update." }
            .getOrDefault(emptyList())
    }

    LaunchedEffect(query, state.session?.userId) {
        if (query.trim().removePrefix("@").length < 2) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        suggestions = runCatching { state.client.findReferrers(query) }
            .getOrDefault(emptyList())
            .filter { person -> person.id != state.session?.userId && selected.none { it.id == person.id } }
            .take(10)
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Group invitations") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Members join only after accepting your invitation.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text("Group name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(40) },
                    label = { Text("Search @username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                suggestions.forEach { member ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (selected.none { it.id == member.id } && selected.size < 30) {
                                selected = selected + member
                            }
                            query = ""
                        }.padding(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DesktopReferralAvatar(member.avatarUrl)
                        Spacer(Modifier.width(8.dp))
                        Text("@${member.username}", modifier = Modifier.weight(1f))
                        if (member.isVerified) Icon(Icons.Rounded.Verified,
                            contentDescription = "Verified", modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    if (selected.isEmpty()) "Select 2–30 students to invite."
                    else "Invited: " + selected.joinToString { "@${it.username}" },
                    style = MaterialTheme.typography.labelSmall,
                )
                if (selected.isNotEmpty()) TextButton(onClick = { selected = emptyList() }) {
                    Text("Clear selection")
                }
                Button(
                    enabled = !busy && title.trim().length in 2..80 && selected.size in 2..30,
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            runCatching {
                                state.client.createGroupWithInvitations(title, selected.map { it.id })
                            }.onSuccess {
                                notice = "Group created. ${selected.size} students invited."
                                title = ""
                                query = ""
                                selected = emptyList()
                                onMembershipChanged()
                            }.onFailure {
                                error = it.message ?: "Unable to create invitation group."
                            }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "Creating…" else "Create group & invite") }
                HorizontalDivider()
                Text("Pending invitations", style = MaterialTheme.typography.titleSmall)
                if (pending.isEmpty()) Text("No pending group invitations.")
                pending.forEach { invite ->
                    Text(invite.groupTitle.ifBlank { "Group invitation" } +
                        " · @${invite.inviterUsername}")
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        listOf("Accept" to true, "Decline" to false).forEach { (label, accept) ->
                            TextButton(enabled = !busy, onClick = {
                                busy = true
                                error = null
                                scope.launch {
                                    runCatching {
                                        if (inviteBackend == "staged") {
                                            state.client.respondGroupInvitation(invite.groupId, accept)
                                        } else {
                                            state.client.respondToGroupInvitation(invite.groupId, accept)
                                        }
                                    }.onSuccess { ok ->
                                        if (ok) {
                                            pending = pending.filterNot { it.groupId == invite.groupId }
                                            notice = if (accept) "Group joined." else "Invitation declined."
                                            if (accept) onMembershipChanged()
                                        } else error = "This invitation is no longer available."
                                    }.onFailure { error = "Unable to respond to this invitation." }
                                    busy = false
                                }
                            }) { Text(label) }
                        }
                    }
                }
                notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Close") } },
    )
}
