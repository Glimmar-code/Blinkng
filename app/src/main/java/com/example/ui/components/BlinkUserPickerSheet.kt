package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.blinkng.shared.BlinkUserSuggestionPolicy
import com.example.data.models.UserProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Universal authenticated account picker. All candidates must come from
 * Supabase's server-filtered profile search (block/privacy restrictions).
 * Supports direct messaging and group/community member selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlinkUserPickerSheet(
    title: String,
    onDismiss: () -> Unit,
    searchProfiles: suspend (query: String, limit: Int, afterUsername: String?, afterId: String?) -> List<UserProfile>,
    onSelectUser: (UserProfile) -> Unit,
    multiSelect: Boolean = false,
    minSelection: Int = 2,
    maxSelection: Int = 49,
    onConfirmSelection: (List<UserProfile>) -> Unit = {},
    onCreateGroup: (() -> Unit)? = null,
    initiallySelected: List<UserProfile> = emptyList(),
) {
    var query by rememberSaveable { mutableStateOf("") }
    var users by remember(query) { mutableStateOf<List<UserProfile>>(emptyList()) }
    var pageVisible by remember(query) { mutableIntStateOf(10) }
    var hasMoreServer by remember(query) { mutableStateOf(false) }
    var loading by remember(query) { mutableStateOf(false) }
    var selected by remember { mutableStateOf(initiallySelected.distinctBy { it.id }) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(query) {
        val clean = query.trim().removePrefix("@")
        if (clean.isBlank()) return@LaunchedEffect
        delay(240)
        loading = true
        try {
            val fetched = searchProfiles(clean, 60, null, null)
            users = fetched.filter { it.username.isNotBlank() }
            hasMoreServer = fetched.size >= 60
        } finally {
            loading = false
        }
    }

    val ranked = remember(users, query) {
        BlinkUserSuggestionPolicy.rank(
            users, query,
            username = { it.username },
            displayName = { it.fullName }
        )
    }
    val visible = ranked.take(pageVisible)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (!multiSelect && onCreateGroup != null) {
                OutlinedButton(
                    onClick = onCreateGroup,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("＋ New Group") }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(50) },
                label = { Text("Search username or name") },
                placeholder = { Text("Start typing a name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (multiSelect && selected.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(selected, key = { it.id }) { person ->
                        InputChip(
                            selected = true,
                            onClick = { selected = selected.filterNot { it.id == person.id } },
                            label = { Text("@${person.username}") },
                            trailingIcon = { Text("×") }
                        )
                    }
                }
            }
            when {
                query.isBlank() -> Text(
                    "Type a username to find real BLINK accounts.",
                    style = MaterialTheme.typography.bodySmall
                )
                loading && users.isEmpty() -> CircularProgressIndicator()
                !loading && users.isEmpty() -> Text(
                    "No matching accounts available.",
                    style = MaterialTheme.typography.bodySmall
                )
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(visible, key = { it.id }) { person ->
                            val checked = selected.any { it.id == person.id }
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (!multiSelect) onSelectUser(person)
                                        else if (checked) selected = selected.filterNot { it.id == person.id }
                                        else if (selected.size < maxSelection) selected = selected + person
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (person.avatarUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = person.avatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp).clip(CircleShape)
                                    )
                                } else {
                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(36.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(person.fullName.ifBlank { person.username }, fontWeight = FontWeight.SemiBold)
                                    Text("@${person.username}", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (multiSelect && checked) Icon(Icons.Default.Check, contentDescription = "Selected")
                            }
                        }
                    }
                    if (ranked.size > pageVisible || hasMoreServer) {
                        TextButton(
                            onClick = {
                                if (ranked.size > pageVisible) {
                                    pageVisible += BlinkUserSuggestionPolicy.DEFAULT_PAGE_SIZE
                                } else {
                                    val last = users.lastOrNull()
                                    if (last != null && !loading) {
                                        scope.launch {
                                            loading = true
                                            try {
                                                val next = searchProfiles(query, 60, last.username, last.id)
                                                users = (users + next).distinctBy { it.id }
                                                hasMoreServer = next.size >= 60
                                                pageVisible += BlinkUserSuggestionPolicy.DEFAULT_PAGE_SIZE
                                            } finally { loading = false }
                                        }
                                    }
                                }
                            },
                            enabled = !loading,
                        ) { Text(if (loading) "Searching…" else "See more") }
                    }
                }
            }
            if (multiSelect) {
                Button(
                    enabled = selected.size >= minSelection,
                    onClick = { onConfirmSelection(selected) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Continue (${selected.size} selected)") }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
