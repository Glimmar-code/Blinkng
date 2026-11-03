package com.blinkng.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.blinkng.desktop.DesktopAppState
import com.blinkng.shared.discovery.BlinkReferralPolicy
import com.blinkng.shared.discovery.BlinkReferralSuggestion
import kotlinx.coroutines.delay

@Composable
internal fun DesktopReferrerPicker(
    state: DesktopAppState,
    selected: BlinkReferralSuggestion?,
    onSelected: (BlinkReferralSuggestion?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var offset by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<BlinkReferralSuggestion>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }

    LaunchedEffect(query, offset, selected?.id) {
        val clean = BlinkReferralPolicy.normalizedQuery(query)
        if (clean.isBlank() || selected != null) {
            results = emptyList()
            loading = false
            error = false
            return@LaunchedEffect
        }
        loading = true
        error = false
        delay(260)
        runCatching { state.client.findReferrers(clean, offset) }
            .onSuccess { page ->
                results = if (offset == 0) page else (results + page).distinctBy { it.id }
                more = page.size == 10 && offset < 500
            }
            .onFailure { error = true; more = false }
        loading = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Referred by (optional)", style = MaterialTheme.typography.labelMedium)
        if (selected != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(BlinkReferralPolicy.selectionLabel(selected), modifier = Modifier.weight(1f))
                TextButton(onClick = { query = ""; offset = 0; onSelected(null) }) { Text("Change") }
            }
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(40); offset = 0; results = emptyList() },
                label = { Text("Search name or @username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (loading && results.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error) Text("Can't load referrers. You can skip this field.",
                color = MaterialTheme.colorScheme.error)
            if (query.isNotBlank() && !loading && !error && results.isEmpty())
                Text("No matching users found.")
            results.forEach { member ->
                OutlinedButton(
                    onClick = { onSelected(member); query = ""; results = emptyList() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    DesktopReferralAvatar(member.avatarUrl)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(BlinkReferralPolicy.selectionLabel(member), maxLines = 1)
                            if (member.isVerified) Icon(Icons.Rounded.Verified,
                                contentDescription = "Verified account", modifier = Modifier.size(15.dp))
                        }
                        member.university?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                    }
                    Text("Select")
                }
            }
            if (more) TextButton(
                onClick = { offset += 10 },
                enabled = !loading,
            ) { Text("See more people") }
        }
    }
}
