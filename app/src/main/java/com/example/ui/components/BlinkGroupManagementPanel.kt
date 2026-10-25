package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.data.models.UserProfile
import com.example.data.repository.BlinkGroupMember
import com.example.data.repository.BlinkGroupSummary
import com.example.data.repository.ProfessionalRepository
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.launch

/** RLS-backed group membership actions. Group creation remains in GroupsSection. */
@Composable
fun BlinkGroupManagementPanel(
    searchProfiles: suspend (String, Int, String?, String?) -> List<UserProfile>,
    modifier: Modifier = Modifier,
) {
    val repo = remember { ProfessionalRepository() }
    val ownerId = SupabaseService().getCurrentUserId().orEmpty()
    val scope = rememberCoroutineScope()
    var groups by remember(ownerId) { mutableStateOf<List<BlinkGroupSummary>>(emptyList()) }
    var openGroup by remember(ownerId) { mutableStateOf<BlinkGroupSummary?>(null) }
    var members by remember(openGroup?.id) { mutableStateOf<List<BlinkGroupMember>>(emptyList()) }
    var showInvite by remember(openGroup?.id) { mutableStateOf(false) }
    var transferTarget by remember(openGroup?.id) { mutableStateOf<BlinkGroupMember?>(null) }
    var busy by remember(ownerId) { mutableStateOf(false) }
    var error by remember(ownerId) { mutableStateOf<String?>(null) }
    var info by remember(ownerId) { mutableStateOf<String?>(null) }

    suspend fun reloadGroups() {
        busy = true
        try {
            groups = repo.fetchMyGroups()
            error = null
        } catch (cause: Exception) {
            error = cause.message ?: "Could not load groups."
        } finally { busy = false }
    }
    suspend fun reloadMembers(groupId: String) {
        busy = true
        try {
            members = repo.fetchGroupMembers(groupId)
            error = null
        } catch (cause: Exception) {
            error = cause.message ?: "Could not load members."
        } finally { busy = false }
    }

    LaunchedEffect(ownerId) { if (ownerId.isNotBlank()) reloadGroups() }
    LaunchedEffect(openGroup?.id) {
        openGroup?.id?.let { reloadMembers(it) }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Your group conversations", style = MaterialTheme.typography.titleMedium)
            TextButton(enabled = !busy, onClick = { scope.launch { reloadGroups() } }) {
                Text("Refresh")
            }
        }
        if (busy) CircularProgressIndicator()
        if (!error.isNullOrBlank()) Text(
            error.orEmpty(), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
        if (!info.isNullOrBlank()) Text(info.orEmpty(), style = MaterialTheme.typography.bodySmall)
        if (groups.isEmpty() && !busy) {
            Text("Your real group conversations will appear here after creation.",
                style = MaterialTheme.typography.bodyMedium)
        }
        groups.forEach { group ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(group.title, style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = {
                            openGroup = if (openGroup?.id == group.id) null else group
                        }) { Text(if (openGroup?.id == group.id) "Hide members" else "Manage") }
                    }
                    if (openGroup?.id == group.id) {
                        members.forEach { member ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(member.displayName)
                                    Text("@" + member.username +
                                        if (member.isAdmin) " · Admin" else "",
                                        style = MaterialTheme.typography.bodySmall)
                                    if (group.ownerId == ownerId && member.id != ownerId) {
                                        TextButton(enabled = !busy, onClick = {
                                            transferTarget = member
                                        }) { Text("Transfer ownership") }
                                    }
                                }
                                if (group.ownerId == ownerId && member.id != ownerId) {
                                    TextButton(enabled = !busy, onClick = {
                                        scope.launch {
                                            busy = true
                                            try {
                                                check(repo.moderateGroupMember(
                                                    group.id, member.id, "remove"
                                                )) { "Server did not confirm removal." }
                                                info = "Member removed."
                                                members = repo.fetchGroupMembers(group.id)
                                            } catch (cause: Exception) { error = cause.message }
                                            finally { busy = false }
                                        }
                                    }) { Text("Remove") }
                                    TextButton(enabled = !busy, onClick = {
                                        scope.launch {
                                            busy = true
                                            try {
                                                check(repo.moderateGroupMember(
                                                    group.id, member.id,
                                                    if (member.isAdmin) "demote" else "promote"
                                                )) { "Server did not confirm role change." }
                                                info = "Member role updated."
                                                members = repo.fetchGroupMembers(group.id)
                                            } catch (cause: Exception) { error = cause.message }
                                            finally { busy = false }
                                        }
                                    }) { Text(if (member.isAdmin) "Demote" else "Make admin") }
                                }
                            }
                        }
                        if (group.ownerId == ownerId) {
                            OutlinedButton(
                                onClick = { showInvite = true }, enabled = !busy
                            ) { Text("Invite a member") }
                        } else {
                            OutlinedButton(
                                enabled = !busy,
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        try {
                                            check(repo.leaveGroup(group.id)) {
                                                "Server did not confirm leaving the group."
                                            }
                                            openGroup = null
                                            groups = repo.fetchMyGroups()
                                            info = "Left group."
                                        } catch (cause: Exception) {
                                            error = cause.message
                                        } finally { busy = false }
                                    }
                                }
                            ) { Text("Leave group") }
                        }
                    }
                }
            }
        }
    }
    val ownerTransferGroup = openGroup
    val successor = transferTarget
    if (successor != null && ownerTransferGroup != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) transferTarget = null },
            title = { Text("Transfer group ownership?") },
            text = { Text("Make @" + successor.username +
                " the group owner? You will remain an admin, but only the new owner can transfer ownership again.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        try {
                            check(repo.transferGroupOwnership(ownerTransferGroup.id, successor.id)) {
                                "Ownership transfer was not confirmed by the server."
                            }
                            groups = repo.fetchMyGroups()
                            openGroup = groups.firstOrNull { it.id == ownerTransferGroup.id }
                            members = repo.fetchGroupMembers(ownerTransferGroup.id)
                            info = "Group ownership transferred to @" + successor.username
                            error = null
                            transferTarget = null
                        } catch (cause: Exception) {
                            error = cause.message ?: "Ownership transfer failed."
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
    val inviteGroup = openGroup
    if (showInvite && inviteGroup != null) {
        BlinkUserPickerSheet(
            title = "Invite to " + inviteGroup.title,
            onDismiss = { showInvite = false },
            searchProfiles = searchProfiles,
            onSelectUser = { user ->
                showInvite = false
                scope.launch {
                    busy = true
                    try {
                        check(repo.inviteGroupMember(inviteGroup.id, user.id)) {
                            "Server did not confirm the invitation."
                        }
                        info = "Member invited."
                        members = repo.fetchGroupMembers(inviteGroup.id)
                    } catch (cause: Exception) {
                        error = cause.message
                    } finally { busy = false }
                }
            }
        )
    }
}
