package com.blinkng.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.blinkng.desktop.DesktopAppState
import com.blinkng.desktop.data.DesktopGroupInvitation
import kotlinx.coroutines.launch

/**
 * Server-authorized group invitations. No pending invite grants chat access;
 * only an explicit recipient acceptance can create active membership.
 */
@Composable
fun DesktopGroupInvitationsDialog(
    state: DesktopAppState,
    onDismiss: () -> Unit,
    onMembershipChanged: () -> Unit
) {
    val ownerId = state.session?.userId.orEmpty()
    val scope = rememberCoroutineScope()
    var invitations by remember(ownerId) {
        mutableStateOf<List<DesktopGroupInvitation>>(emptyList())
    }
    var busy by remember(ownerId) { mutableStateOf(false) }
    var error by remember(ownerId) { mutableStateOf<String?>(null) }
    var info by remember(ownerId) { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        if (ownerId.isBlank()) return
        busy = true
        try {
            val rows = state.client.fetchGroupInvitations()
            if (state.session?.userId == ownerId) {
                invitations = rows
                error = null
            }
        } catch (cause: Exception) {
            if (state.session?.userId == ownerId) {
                error = cause.message ?: "Unable to load invitations."
            }
        } finally { busy = false }
    }

    LaunchedEffect(ownerId) { refresh() }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Group invitations") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (busy) CircularProgressIndicator()
                if (!error.isNullOrBlank()) Text(
                    error.orEmpty(), color = MaterialTheme.colorScheme.error
                )
                if (!info.isNullOrBlank()) Text(info.orEmpty())
                if (invitations.isEmpty() && !busy && error == null) {
                    Text("No pending group invitations.")
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(invitations, key = { it.conversationId }) { invite ->
                        Column(modifier = Modifier.fillMaxWidth().padding(6.dp)) {
                            Text(invite.title, style = MaterialTheme.typography.titleSmall)
                            Text("Invited by @" + invite.inviterUsername,
                                style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(true to "Accept", false to "Decline")
                                    .forEach { (accept, label) ->
                                        TextButton(enabled = !busy, onClick = {
                                            scope.launch {
                                                busy = true
                                                try {
                                                    check(state.client.respondGroupInvitation(
                                                        invite.conversationId, accept
                                                    )) { "Server did not confirm your response." }
                                                    if (state.session?.userId == ownerId) {
                                                        info = if (accept) "Group invitation accepted."
                                                            else "Invitation declined."
                                                        refresh()
                                                        if (accept) onMembershipChanged()
                                                    }
                                                } catch (cause: Exception) {
                                                    if (state.session?.userId == ownerId)
                                                        error = cause.message ?: "Unable to respond."
                                                } finally { busy = false }
                                            }
                                        }) { Text(label) }
                                    }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Close") }
        }
    )
}
