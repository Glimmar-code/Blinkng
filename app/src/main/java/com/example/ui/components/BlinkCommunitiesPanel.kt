package com.example.ui.components

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blinkng.shared.BlinkCommunityPolicy
import com.example.data.models.UserProfile
import com.example.data.repository.BlinkCommunity
import com.example.data.repository.BlinkCommunityMember
import com.example.data.repository.BlinkCommunityRepository
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.launch

/** Connected Communities surface; no placeholder data or local membership grants. */
@Composable
fun BlinkCommunitiesPanel(
    searchProfiles: suspend (String, Int, String?, String?) -> List<UserProfile>,
    modifier: Modifier = Modifier
) {
    val backend = remember { BlinkCommunityRepository() }
    val service = remember { SupabaseService() }
    val scope = rememberCoroutineScope()
    val ownerId = service.getCurrentUserId().orEmpty()
    // Never carry a previous account's private communities, roster or draft
    // through a logout/login or Switch Account transition.
    var communities by remember(ownerId) { mutableStateOf<List<BlinkCommunity>>(emptyList()) }
    var loading by remember(ownerId) { mutableStateOf(true) }
    var saving by remember(ownerId) { mutableStateOf(false) }
    var error by remember(ownerId) { mutableStateOf<String?>(null) }
    var success by remember(ownerId) { mutableStateOf<String?>(null) }
    var showCreate by rememberSaveable(ownerId) { mutableStateOf(false) }
    var name by rememberSaveable(ownerId) { mutableStateOf("") }
    var description by rememberSaveable(ownerId) { mutableStateOf("") }
    var university by rememberSaveable(ownerId) { mutableStateOf("") }
    var category by rememberSaveable(ownerId) { mutableStateOf("Academic") }
    var coverUrl by rememberSaveable(ownerId) { mutableStateOf("") }
    var rules by rememberSaveable(ownerId) { mutableStateOf("") }
    var isPrivate by rememberSaveable(ownerId) { mutableStateOf(false) }
    var inviteTo by remember(ownerId) { mutableStateOf<BlinkCommunity?>(null) }
    var managing by remember(ownerId) { mutableStateOf<String?>(null) }
    var roster by remember(ownerId) { mutableStateOf<List<BlinkCommunityMember>>(emptyList()) }

    suspend fun refresh() {
        loading = true
        try {
            communities = backend.discover()
            error = null
        } catch (cause: Exception) {
            error = cause.message ?: "Communities are not currently available."
        } finally { loading = false }
    }
    LaunchedEffect(ownerId) {
        if (ownerId.isNotBlank()) refresh() else loading = false
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Communities", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = { scope.launch { refresh() } }) { Text("Refresh") }
            }
        }
        item {
            Text(
                "Find campus communities, or create a public/private space with rules and invitations.",
                Modifier.padding(horizontal = 14.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(
                onClick = { showCreate = !showCreate },
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                enabled = !saving
            ) { Text(if (showCreate) "Cancel creation" else "＋ Create Community") }
        }
        if (showCreate) {
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(name, { name = it.take(80) }, label = { Text("Community name") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(description, { description = it.take(1500) },
                            label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(university, { university = it.take(150) },
                            label = { Text("University") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(category, { category = it.take(80) },
                            label = { Text("Category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(coverUrl, { coverUrl = it.take(500) },
                            label = { Text("Cover image URL (optional)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(rules, { rules = it.take(4500) },
                            label = { Text("Community rules (one per line)") },
                            modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            FilterChip(selected = !isPrivate, onClick = { isPrivate = false },
                                label = { Text("Public") })
                            FilterChip(selected = isPrivate, onClick = { isPrivate = true },
                                label = { Text("Private") })
                        }
                        Button(
                            enabled = !saving && name.trim().length in 3..80 &&
                                university.trim().length >= 2 && category.trim().length >= 2,
                            onClick = {
                                scope.launch {
                                    saving = true
                                    error = null
                                    try {
                                        val created = backend.create(
                                            name, description, university, category,
                                            if (isPrivate) BlinkCommunityPolicy.Visibility.PRIVATE
                                            else BlinkCommunityPolicy.Visibility.PUBLIC,
                                            rules.lines(), coverUrl.trim().takeIf { it.isNotBlank() }
                                        )
                                        communities = listOf(created) + communities.filterNot { it.id == created.id }
                                        success = "Community created."
                                        showCreate = false
                                        name = ""
                                        description = ""
                                        rules = ""
                                        coverUrl = ""
                                    } catch (cause: Exception) {
                                        error = cause.message ?: "Unable to create community."
                                    } finally { saving = false }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Create Community") }
                    }
                }
            }
        }
        if (loading || saving) item { CircularProgressIndicator(Modifier.padding(14.dp)) }
        if (!error.isNullOrBlank()) item {
            Text(error.orEmpty(), Modifier.padding(horizontal = 14.dp),
                color = MaterialTheme.colorScheme.error)
        }
        if (!success.isNullOrBlank()) item {
            Text(success.orEmpty(), Modifier.padding(horizontal = 14.dp),
                color = MaterialTheme.colorScheme.primary)
        }
        items(communities, key = { it.id }) { community ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(community.name, style = MaterialTheme.typography.titleMedium)
                    Text(community.university + " · " + community.category + " · " +
                        community.visibility.replaceFirstChar(Char::uppercase))
                    if (community.description.isNotBlank()) Text(community.description)
                    community.rules.take(3).forEach { rule ->
                        Text("• " + rule, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (community.ownerId == ownerId) {
                            OutlinedButton(onClick = { inviteTo = community }, enabled = !saving) {
                                Text("Invite students")
                            }
                            TextButton(enabled = !saving, onClick = {
                                if (managing == community.id) {
                                    managing = null
                                } else {
                                    scope.launch {
                                        saving = true
                                        try {
                                            roster = backend.memberRoster(community.id)
                                            managing = community.id
                                            error = null
                                        } catch (cause: Exception) { error = cause.message }
                                        finally { saving = false }
                                    }
                                }
                            }) { Text(if (managing == community.id) "Close management" else "Manage members") }
                        } else {
                            Button(enabled = !saving, onClick = {
                                scope.launch {
                                    saving = true
                                    try {
                                        val result = backend.requestJoin(community.id)
                                        success = if (result == "pending") "Join request sent to " + community.name
                                            else "Joined " + community.name
                                    } catch (cause: Exception) {
                                        error = cause.message
                                    } finally { saving = false }
                                }
                            }) { Text(if (community.visibility == "private") "Request / Accept Invite" else "Join") }
                            TextButton(enabled = !saving, onClick = {
                                scope.launch {
                                    saving = true
                                    try {
                                        backend.leave(community.id)
                                        success = "Left community."
                                        refresh()
                                    } catch (cause: Exception) {
                                        error = cause.message
                                    } finally { saving = false }
                                }
                            }) { Text("Leave") }
                        }
                    }
                    if (community.ownerId == ownerId && managing == community.id) {
                        Text("Membership and requests", style = MaterialTheme.typography.titleSmall)
                        if (roster.isEmpty()) Text("No requests or members to review.")
                        roster.forEach { member ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(member.displayName + "  @" + member.username)
                                Text(member.status + " · " + member.role,
                                    style = MaterialTheme.typography.bodySmall)
                                if (member.id != ownerId) {
                                    val actions = when (member.status) {
                                        "pending", "invited" -> listOf("approve", "ban")
                                        "active" -> listOf(
                                            if (member.role == "moderator") "demote" else "promote",
                                            "remove", "ban"
                                        )
                                        else -> emptyList()
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        actions.forEach { action ->
                                            TextButton(enabled = !saving, onClick = {
                                                scope.launch {
                                                    saving = true
                                                    try {
                                                        backend.moderate(community.id, member.id, action)
                                                        roster = backend.memberRoster(community.id)
                                                        success = "Membership updated."
                                                        error = null
                                                    } catch (cause: Exception) {
                                                        error = cause.message
                                                    } finally { saving = false }
                                                }
                                            }) { Text(action.replaceFirstChar(Char::uppercase)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val inviteTarget = inviteTo
    if (inviteTarget != null) {
        BlinkUserPickerSheet(
            title = "Invite to " + inviteTarget.name,
            onDismiss = { inviteTo = null },
            searchProfiles = searchProfiles,
            onSelectUser = { candidate ->
                inviteTo = null
                scope.launch {
                    saving = true
                    try {
                        backend.invite(inviteTarget.id, candidate.id)
                        success = "Invitation sent to @" + candidate.username
                    } catch (cause: Exception) {
                        error = cause.message
                    } finally { saving = false }
                }
            }
        )
    }
}
