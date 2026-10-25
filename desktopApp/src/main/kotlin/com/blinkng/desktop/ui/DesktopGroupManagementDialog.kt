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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.blinkng.desktop.DesktopAppState
import com.blinkng.desktop.data.DesktopConversation
import com.blinkng.desktop.data.DesktopGroupMember
import com.blinkng.desktop.data.DesktopProfile
import com.blinkng.shared.BlinkUserSuggestionPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Authenticated, live-data group management; existing messages stay intact. */
@Composable
fun DesktopGroupManagementDialog(
    state: DesktopAppState,
    conversation: DesktopConversation,
    onDismiss: () -> Unit,
    onLeft: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val myId = state.session?.userId.orEmpty()
    var ownerId by remember(conversation.id) { mutableStateOf("") }
    var members by remember(conversation.id) { mutableStateOf<List<DesktopGroupMember>>(emptyList()) }
    var inviteQuery by remember(conversation.id) { mutableStateOf("") }
    var searching by remember(conversation.id, inviteQuery) { mutableStateOf(false) }
    var matches by remember(conversation.id, inviteQuery) { mutableStateOf<List<DesktopProfile>>(emptyList()) }
    var inviting by remember(conversation.id) { mutableStateOf(false) }
    var busy by remember(conversation.id) { mutableStateOf(false) }
    var transferTarget by remember(conversation.id) { mutableStateOf<DesktopGroupMember?>(null) }
    var error by remember(conversation.id) { mutableStateOf<String?>(null) }
    var info by remember(conversation.id) { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        busy = true
        try {
            ownerId = state.client.groupOwner(conversation.id).orEmpty()
            members = state.client.groupMembers(conversation.id)
            error = null
        } catch (cause: Exception) {
            error = cause.message ?: "Cannot load group membership."
        } finally { busy = false }
    }
    LaunchedEffect(conversation.id, myId) { refresh() }
    LaunchedEffect(inviteQuery, inviting, conversation.id) {
        if (!inviting || inviteQuery.trim().isBlank()) return@LaunchedEffect
        delay(220)
        searching = true
        try {
            matches = state.client.searchMentionProfiles(inviteQuery.trim(), 30)
                .filterNot { p -> p.id == myId || members.any { it.id == p.id } }
        } catch (cause: Exception) { error = cause.message }
        finally { searching = false }
    }
    val amAdmin = members.any { it.id == myId && it.isAdmin }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(conversation.title + " · Members") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy || searching) CircularProgressIndicator()
                if (!error.isNullOrBlank()) Text(
                    error.orEmpty(), color = MaterialTheme.colorScheme.error
                )
                if (!info.isNullOrBlank()) Text(info.orEmpty())
                if (amAdmin) {
                    TextButton(onClick = { inviting = !inviting }, enabled = !busy) {
                        Text(if (inviting) "Close invitations" else "＋ Invite student")
                    }
                }
                if (inviting && amAdmin) {
                    OutlinedTextField(inviteQuery, { inviteQuery = it.take(50) },
                        label = { Text("Search username") })
                    BlinkUserSuggestionPolicy.rank(
                        matches, inviteQuery,
                        username = { it.username }, displayName = { it.fullName }
                    ).take(10).forEach { person ->
                        TextButton(enabled = !busy, onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    state.client.inviteGroupMember(conversation.id, person.id)
                                    info = "Invited @" + person.username
                                    inviting = false
                                    refresh()
                                } catch (cause: Exception) { error = cause.message }
                                finally { busy = false }
                            }
                        }) { Text(person.fullName.ifBlank { person.username } + " @" + person.username) }
                    }
                }
                LazyColumn(modifier = Modifier.heightIn(max = 290.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(members, key = { it.id }) { member ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(member.displayName + "  @" + member.username)
                            Text(if (member.isAdmin) "Group admin" else "Member",
                                style = MaterialTheme.typography.bodySmall)
                            if (myId == ownerId && member.id != myId) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("remove",
                                        if (member.isAdmin) "demote" else "promote")
                                        .forEach { action ->
                                            TextButton(enabled = !busy, onClick = {
                                                scope.launch {
                                                    busy = true
                                                    try {
                                                        state.client.moderateGroupMember(
                                                            conversation.id, member.id, action
                                                        )
                                                        info = "Member updated."
                                                        refresh()
                                                    } catch (cause: Exception) { error = cause.message }
                                                    finally { busy = false }
                                                }
                                            }) { Text(action.replaceFirstChar(Char::uppercase)) }
                                        }
                                }
                                TextButton(enabled = !busy, onClick = {
                                    transferTarget = member
                                }) { Text("Transfer ownership") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (ownerId.isNotBlank() && ownerId != myId) {
                Button(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        try {
                            state.client.leaveGroup(conversation.id)
                            onLeft()
                        } catch (cause: Exception) { error = cause.message }
                        finally { busy = false }
                    }
                }) { Text("Leave group") }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Close") }
        }
    )
    val successor = transferTarget
    if (successor != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) transferTarget = null },
            title = { Text("Transfer group ownership?") },
            text = { Text("Make @" + successor.username +
                " the group owner? Only the new owner can transfer ownership again.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        try {
                            check(state.client.transferGroupOwnership(
                                conversation.id, successor.id
                            )) { "Ownership transfer was not confirmed by Supabase." }
                            transferTarget = null
                            info = "Ownership transferred to @" + successor.username
                            refresh()
                        } catch (cause: Exception) {
                            error = cause.message ?: "Unable to transfer group."
                        } finally { busy = false }
                    }
                }) { Text("Transfer") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { transferTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
