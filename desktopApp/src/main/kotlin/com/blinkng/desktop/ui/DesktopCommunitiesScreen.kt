package com.blinkng.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blinkng.desktop.DesktopAppState
import com.blinkng.desktop.data.DesktopCommunity
import com.blinkng.desktop.data.DesktopProfile
import com.blinkng.desktop.data.DesktopProfileMembership
import com.blinkng.shared.BlinkCommunityPolicy
import com.blinkng.shared.BlinkUserSuggestionPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Windows equivalent of the Android real-data Community workspace. */
@Composable
fun DesktopCommunitiesScreen(state: DesktopAppState) {
    val scope = rememberCoroutineScope()
    val ownerId = state.session?.userId.orEmpty()
    var communities by remember(ownerId) { mutableStateOf<List<DesktopCommunity>>(emptyList()) }
    var busy by remember(ownerId) { mutableStateOf(false) }
    var error by remember(ownerId) { mutableStateOf<String?>(null) }
    var message by remember(ownerId) { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var university by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Academic") }
    var rules by remember { mutableStateOf("") }
    var coverUrl by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    var inviteTo by remember { mutableStateOf<DesktopCommunity?>(null) }
    var manageCommunityId by remember { mutableStateOf<String?>(null) }
    var memberRoster by remember { mutableStateOf<List<DesktopProfileMembership>>(emptyList()) }
    var inviteQuery by remember { mutableStateOf("") }
    var inviteResults by remember(inviteQuery) { mutableStateOf<List<DesktopProfile>>(emptyList()) }

    suspend fun reload() {
        busy = true
        try {
            communities = state.client.fetchCommunities()
            error = null
        } catch (cause: Exception) {
            error = cause.message ?: "Communities are not currently available."
        } finally { busy = false }
    }
    LaunchedEffect(ownerId) {
        if (ownerId.isNotBlank()) reload()
    }
    LaunchedEffect(inviteTo?.id, inviteQuery) {
        if (inviteTo == null || inviteQuery.isBlank()) return@LaunchedEffect
        delay(220)
        try {
            inviteResults = state.client.searchMentionProfiles(inviteQuery, 60)
                .filterNot { it.id == ownerId }
        } catch (cause: Exception) { error = cause.message }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("BLINK Communities", style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = { scope.launch { reload() } }) { Text("Refresh") }
            }
            Text("Create campus spaces with real accounts, rules, visibility and invitations.")
            OutlinedButton(onClick = { showCreate = !showCreate }, enabled = !busy) {
                Text(if (showCreate) "Cancel" else "＋ Create Community")
            }
        }
        if (showCreate) {
            item {
                Card {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(name, { name = it.take(80) }, label = { Text("Community name") })
                        OutlinedTextField(description, { description = it.take(1500) },
                            label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(university, { university = it.take(150) },
                            label = { Text("University") })
                        OutlinedTextField(category, { category = it.take(80) }, label = { Text("Category") })
                        OutlinedTextField(coverUrl, { coverUrl = it.take(500) },
                            label = { Text("Cover image URL (optional)") })
                        OutlinedTextField(rules, { rules = it.take(4500) },
                            label = { Text("Rules — one per line") }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !isPrivate, onClick = { isPrivate = false },
                                label = { Text("Public") })
                            FilterChip(selected = isPrivate, onClick = { isPrivate = true },
                                label = { Text("Private") })
                        }
                        Button(
                            enabled = !busy && name.trim().length in 3..80 &&
                                university.trim().length >= 2 && category.trim().length >= 2,
                            onClick = {
                                scope.launch {
                                    busy = true
                                    try {
                                        val created = state.client.createCommunity(
                                            name, description, university, category,
                                            if (isPrivate) BlinkCommunityPolicy.Visibility.PRIVATE
                                            else BlinkCommunityPolicy.Visibility.PUBLIC,
                                            rules.lines(), coverUrl.trim().takeIf { it.isNotBlank() }
                                        )
                                        communities = listOf(created) + communities.filterNot { it.id == created.id }
                                        message = "Community created."
                                        error = null
                                        showCreate = false
                                        name = ""
                                        rules = ""
                                    } catch (cause: Exception) { error = cause.message }
                                    finally { busy = false }
                                }
                            }
                        ) { Text("Create Community") }
                    }
                }
            }
        }
        if (busy) item { CircularProgressIndicator() }
        if (!error.isNullOrBlank()) item {
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
        }
        if (!message.isNullOrBlank()) item {
            Text(message.orEmpty(), color = MaterialTheme.colorScheme.primary)
        }
        items(communities, key = { it.id }) { community ->
            Card {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(community.name, style = MaterialTheme.typography.titleLarge)
                    Text(community.university + " · " + community.category + " · " + community.visibility)
                    if (community.description.isNotBlank()) Text(community.description)
                    community.rules.take(4).forEach { Text("• " + it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (community.ownerId == ownerId) {
                            OutlinedButton(onClick = {
                                inviteTo = community
                                inviteQuery = ""
                            }) { Text("Invite members") }
                            TextButton(enabled = !busy, onClick = {
                                if (manageCommunityId == community.id) {
                                    manageCommunityId = null
                                } else {
                                    scope.launch {
                                        busy = true
                                        try {
                                            memberRoster = state.client.communityMemberRoster(community.id)
                                            manageCommunityId = community.id
                                            error = null
                                        } catch (cause: Exception) { error = cause.message }
                                        finally { busy = false }
                                    }
                                }
                            }) { Text(if (manageCommunityId == community.id) "Close" else "Manage members") }
                        } else {
                            Button(enabled = !busy, onClick = {
                                scope.launch {
                                    busy = true
                                    try {
                                        val result = state.client.requestJoinCommunity(community.id)
                                        message = if (result == "pending") "Join request sent." else "Joined " + community.name
                                        error = null
                                    } catch (cause: Exception) { error = cause.message }
                                    finally { busy = false }
                                }
                            }) { Text(if (community.visibility == "private") "Request / Accept Invite" else "Join") }
                            TextButton(enabled = !busy, onClick = {
                                scope.launch {
                                    busy = true
                                    try {
                                        state.client.leaveCommunity(community.id)
                                        message = "Left community."
                                        reload()
                                    } catch (cause: Exception) { error = cause.message }
                                    finally { busy = false }
                                }
                            }) { Text("Leave") }
                        }
                    }
                    if (community.ownerId == ownerId && manageCommunityId == community.id) {
                        Text("Member requests & moderation", style = MaterialTheme.typography.titleMedium)
                        if (memberRoster.isEmpty()) Text("No membership requests yet.")
                        memberRoster.forEach { person ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(person.displayName + "  @" + person.username)
                                    Text(person.status + " · " + person.role,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                if (person.id != ownerId) {
                                    val actions = when (person.status) {
                                        "pending", "invited" -> listOf("approve", "ban")
                                        "active" -> listOf(
                                            if (person.role == "moderator") "demote" else "promote",
                                            "remove", "ban"
                                        )
                                        else -> emptyList()
                                    }
                                    actions.forEach { action ->
                                        TextButton(enabled = !busy, onClick = {
                                            scope.launch {
                                                busy = true
                                                try {
                                                    state.client.moderateCommunity(
                                                        community.id, person.id, action
                                                    )
                                                    memberRoster = state.client.communityMemberRoster(
                                                        community.id
                                                    )
                                                    message = "Membership updated."
                                                    error = null
                                                } catch (cause: Exception) { error = cause.message }
                                                finally { busy = false }
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
        inviteTo?.let { target ->
            item {
                Card {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Invite to " + target.name)
                        OutlinedTextField(
                            inviteQuery, { inviteQuery = it.take(50) },
                            label = { Text("Search username or name") }
                        )
                        val ranked = BlinkUserSuggestionPolicy.rank(
                            inviteResults, inviteQuery,
                            username = { it.username }, displayName = { it.fullName }
                        )
                        ranked.take(10).forEach { person ->
                            TextButton(onClick = {
                                scope.launch {
                                    busy = true
                                    try {
                                        state.client.inviteToCommunity(target.id, person.id)
                                        message = "Invited @" + person.username
                                        inviteTo = null
                                        error = null
                                    } catch (cause: Exception) { error = cause.message }
                                    finally { busy = false }
                                }
                            }) { Text(person.fullName + "  @" + person.username) }
                        }
                        TextButton(onClick = { inviteTo = null }) { Text("Close invitations") }
                    }
                }
            }
        }
    }
}
