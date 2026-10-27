package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.repository.BlinkContactsDiscoveryClient
import com.example.data.models.UserProfile
import kotlinx.coroutines.launch

/** Contact read starts only from the explicit Find My Friends action. */
@Composable
fun BlinkContactDiscoveryCard(
    accountId: String,
    onProfile: (String) -> Unit = {},
    onFollow: (UserProfile) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(accountId) { BlinkContactsDiscoveryClient() }
    var settings by remember(accountId) { mutableStateOf(BlinkContactsDiscoveryClient.Consent()) }
    var people by remember(accountId) { mutableStateOf<List<UserProfile>>(emptyList()) }
    var working by remember(accountId) { mutableStateOf(false) }
    var problem by remember(accountId) { mutableStateOf<String?>(null) }
    var confirmDelete by remember(accountId) { mutableStateOf(false) }

    fun update(value: BlinkContactsDiscoveryClient.Consent) {
        scope.launch {
            working = true
            runCatching { client.set(value) }.onSuccess {
                settings = it
                problem = null
                if (!it.syncEnabled) people = emptyList()
            }.onFailure { problem = it.message ?: "Could not save your choices." }
            working = false
        }
    }

    fun sync() {
        scope.launch {
            working = true
            runCatching { client.syncFromDevice(context) }.onSuccess { people = it; problem = null }
                .onFailure { problem = it.message ?: "Contact sync unavailable." }
            working = false
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) sync() else problem = "Permission denied. BLINK works without contact access."
    }
    LaunchedEffect(accountId) {
        if (accountId.isBlank()) return@LaunchedEffect
        runCatching { client.get() }.onSuccess { settings = it.consent; people = it.people }
            .onFailure { problem = "Contact discovery is currently unavailable." }
    }

    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("People from your contacts", style = MaterialTheme.typography.titleMedium)
            Text("Optional: BLINK matches phone numbers with consent. No call history or contact names are collected.",
                style = MaterialTheme.typography.bodySmall)
            Text("Turn on contact matching, then tap Find my friends and allow Contacts access. Only BLINK members who verified their number and opted in can appear.",
                style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = settings.syncEnabled, enabled = !working, onCheckedChange = {
                    update(settings.copy(syncEnabled = it))
                })
                Text("Sync my phone contacts", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = settings.discoverable, enabled = !working, onCheckedChange = {
                    update(settings.copy(discoverable = it))
                })
                Text("Let my contacts find me", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = settings.joinAlerts, enabled = !working && settings.syncEnabled, onCheckedChange = {
                    update(settings.copy(joinAlerts = it))
                })
                Text("Notify me when contacts join", style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                        PackageManager.PERMISSION_GRANTED) sync()
                    else permission.launch(Manifest.permission.READ_CONTACTS)
                }, enabled = settings.syncEnabled && !working) { Text("Find my friends") }
                TextButton(onClick = { confirmDelete = true }, enabled = !working) { Text("Delete synced contacts") }
            }
            problem?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            if (people.isNotEmpty()) {
                Text(people.size.toString() + " friends on BLINK", style = MaterialTheme.typography.titleSmall)
                people.take(8).forEach { person ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        TextButton(onClick = { onProfile(person.username) }, modifier = Modifier.weight(1f)) {
                            Text(person.fullName.ifBlank { person.username }, maxLines = 1)
                        }
                        TextButton(onClick = { onFollow(person) }) { Text("Follow") }
                    }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete synced contacts?") },
        text = { Text("BLINK will remove your saved matching data and disable contact discovery.") },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                scope.launch {
                    working = true
                    runCatching { client.delete() }.onSuccess {
                        settings = BlinkContactsDiscoveryClient.Consent()
                        people = emptyList()
                        problem = null
                    }.onFailure { problem = it.message }
                    working = false
                }
            }) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
    )
}
