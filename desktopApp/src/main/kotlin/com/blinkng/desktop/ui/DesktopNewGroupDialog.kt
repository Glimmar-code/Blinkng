package com.blinkng.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.blinkng.desktop.DesktopAppState
import com.blinkng.desktop.data.DesktopProfile
import com.blinkng.shared.BlinkGroupCreationPolicy
import com.blinkng.shared.BlinkUserSuggestionPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Same group-create backend and limits as Android, using Windows-friendly dialogs. */
@Composable
fun DesktopNewGroupDialog(
    state: DesktopAppState,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit
) {
    val owner = state.session?.userId.orEmpty()
    var step by remember(owner) { mutableIntStateOf(0) }
    var query by remember(owner) { mutableStateOf("") }
    var groupName by remember(owner) { mutableStateOf("") }
    var matches by remember(owner, query) { mutableStateOf<List<DesktopProfile>>(emptyList()) }
    var selected by remember(owner) { mutableStateOf<List<DesktopProfile>>(emptyList()) }
    var pageVisible by remember(owner, query) { mutableIntStateOf(10) }
    var hasMoreServer by remember(owner, query) { mutableStateOf(false) }
    var loading by remember(owner, query) { mutableStateOf(false) }
    var saving by remember(owner) { mutableStateOf(false) }
    var error by remember(owner) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(owner, query) {
        if (owner.isBlank() || query.trim().isBlank()) return@LaunchedEffect
        delay(220)
        loading = true
        try {
            val first = state.client.searchMentionProfiles(query, 60)
                .filterNot { it.id == owner }
            matches = first.distinctBy { it.id }
            hasMoreServer = first.size >= 60
            error = null
        } catch (cause: Exception) {
            matches = emptyList()
            hasMoreServer = false
            error = cause.message ?: "Unable to search BLINK accounts."
        } finally { loading = false }
    }
    val ranked = BlinkUserSuggestionPolicy.rank(
        matches, query,
        username = { it.username },
        displayName = { it.fullName }
    )

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (step == 0) "New Group • Select Members" else "New Group • Details") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(if (step == 0) "Choose 2–49 students to join your private group."
                    else "Name the group before creating it.",
                    style = MaterialTheme.typography.bodyMedium)
                if (selected.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(selected, key = { it.id }) { person ->
                            TextButton(
                                enabled = !saving,
                                onClick = { selected = selected.filterNot { it.id == person.id } }
                            ) { Text("@" + person.username + " ×") }
                        }
                    }
                }
                if (step == 0) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(50) },
                        label = { Text("Search username or name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(ranked.take(pageVisible), key = { it.id }) { person ->
                            val chosen = selected.any { it.id == person.id }
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (chosen) selected = selected.filterNot { it.id == person.id }
                                    else if (selected.size < 49) selected = selected + person
                                }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                if (person.avatarUrl?.isNotBlank() == true) {
                                    AsyncImage(
                                        model = person.avatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(30.dp).clip(CircleShape)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(person.fullName.ifBlank { person.username })
                                    Text("@" + person.username,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                if (chosen) Text("✓")
                            }
                        }
                        if (ranked.size > pageVisible || hasMoreServer) {
                            item(key = "group_more_users") {
                                TextButton(
                                    enabled = !loading,
                                    onClick = {
                                        if (ranked.size > pageVisible) {
                                            pageVisible += BlinkUserSuggestionPolicy.DEFAULT_PAGE_SIZE
                                        } else {
                                            val last = matches.lastOrNull()
                                            if (last != null) scope.launch {
                                                loading = true
                                                try {
                                                    val next = state.client.searchMentionProfiles(
                                                        query, 60, last.username, last.id
                                                    ).filterNot { it.id == owner }
                                                    matches = (matches + next).distinctBy { it.id }
                                                    hasMoreServer = next.size >= 60
                                                    pageVisible += BlinkUserSuggestionPolicy.DEFAULT_PAGE_SIZE
                                                } catch (cause: Exception) {
                                                    error = cause.message
                                                } finally { loading = false }
                                            }
                                        }
                                    }
                                ) { Text("See more") }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it.take(80) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Group name") }
                    )
                }
                if (loading || saving) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                if (!error.isNullOrBlank()) Text(
                    error.orEmpty(), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && if (step == 0) selected.size in 2..49
                    else groupName.trim().length in 2..80 && selected.size in 2..49,
                onClick = {
                    if (step == 0) step = 1
                    else scope.launch {
                        saving = true
                        error = null
                        try {
                            val members = BlinkGroupCreationPolicy.normalizedMembers(
                                owner, selected.map { it.id }
                            )
                            val id = state.client.createGroupConversation(
                                BlinkGroupCreationPolicy.normalizedTitle(groupName), members
                            )
                            onCreated(id)
                        } catch (cause: Exception) {
                            error = cause.message ?: "Could not create this group."
                        } finally { saving = false }
                    }
                }
            ) { Text(if (step == 0) "Next" else "Create Group") }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = {
                if (step == 1) step = 0 else onDismiss()
            }) { Text(if (step == 1) "Back" else "Cancel") }
        }
    )
}
