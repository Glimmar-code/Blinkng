package com.blinkng.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blinkng.desktop.DesktopAppState
import com.blinkng.shared.discovery.BlinkReferralSuggestion
import kotlinx.coroutines.launch

/** Immediately after successful desktop sign-up, choose whom to follow (never auto-follow). */
@Composable
internal fun DesktopNewStudentSuggestionsDialog(
    state: DesktopAppState,
    onDismiss: () -> Unit,
) {
    var suggestions by remember { mutableStateOf<List<BlinkReferralSuggestion>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var followedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var busyId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.session?.userId) {
        suggestions = runCatching { state.client.referralRecommendations() }
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
                    "Follow students to start your campus network. " +
                        "Some members earned extra discovery through referrals.",
                )
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) Text("Recommendations aren't available yet. Try Search.",
                    color = MaterialTheme.colorScheme.error)
                if (!loading && !failed && suggestions.isEmpty()) Text("Find students from your campus in Search.")
                suggestions.take(10).forEach { person ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Person, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(person.fullName.ifBlank { person.username }, maxLines = 1)
                                if (person.isVerified) Icon(Icons.Rounded.Verified,
                                    contentDescription = "Verified", modifier = Modifier.size(16.dp))
                            }
                            Text("@${person.username} · ${person.university.orEmpty()}",
                                style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        TextButton(
                            enabled = busyId == null && person.id !in followedIds,
                            onClick = {
                                busyId = person.id
                                scope.launch {
                                    runCatching { state.client.followBack(person.id) }
                                        .onSuccess { followedIds = followedIds + person.id }
                                    busyId = null
                                }
                            },
                        ) { Text(if (person.id in followedIds) "Following" else "Follow") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Explore BLINK") } },
    )
}
