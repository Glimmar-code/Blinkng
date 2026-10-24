package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.shared.discovery.BlinkReferralSuggestion
import com.example.data.repository.FollowStateStore
import com.example.data.supabase.BlinkReferralClient
import kotlinx.coroutines.launch

/** Only shown immediately after campus registration. Never follows anyone without a tap. */
@Composable
internal fun BlinkNewStudentSuggestionsDialog(onDismiss: () -> Unit) {
    var suggestions by remember { mutableStateOf<List<BlinkReferralSuggestion>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var followingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var busyId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        suggestions = runCatching { BlinkReferralClient.recommendations() }
            .onFailure { failed = true }
            .getOrDefault(emptyList())
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discover students on BLINK") },
        text = {
            Column(
                Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Welcome! Follow students to start building your campus network. " +
                        "Some members earned extra discovery through referrals.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) Text(
                    "Recommendations are unavailable right now. You can find students in Search.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!loading && !failed && suggestions.isEmpty()) {
                    Text("Explore Search to discover students from your campus.",
                        style = MaterialTheme.typography.bodySmall)
                }
                suggestions.take(10).forEach { person ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(person.fullName.ifBlank { person.username },
                                style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text("@${person.username}", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(
                            onClick = {
                                busyId = person.id
                                scope.launch {
                                    val success = runCatching {
                                        FollowStateStore.setFollowing(person.id, true)
                                    }.getOrDefault(false)
                                    if (success) followingIds = followingIds + person.id
                                    busyId = null
                                }
                            },
                            enabled = busyId == null && person.id !in followingIds,
                        ) {
                            Text(
                                if (person.id in followingIds) "Following" else "Follow",
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Explore BLINK") } },
    )
}
