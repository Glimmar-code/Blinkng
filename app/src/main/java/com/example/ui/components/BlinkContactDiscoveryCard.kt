package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import coil.compose.AsyncImage
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
    var serviceReady by remember(accountId) { mutableStateOf(false) }
    var mutualMatchingReady by remember(accountId) { mutableStateOf(false) }
    var syncedThisSession by remember(accountId) { mutableStateOf(false) }
    var problem by remember(accountId) { mutableStateOf<String?>(null) }
    var confirmDelete by remember(accountId) { mutableStateOf(false) }

    fun update(value: BlinkContactsDiscoveryClient.Consent) {
        scope.launch {
            working = true
            runCatching { client.set(value) }.onSuccess {
                settings = it
                problem = null
                if (!it.syncEnabled) {
                    people = emptyList()
                    syncedThisSession = false
                }
            }.onFailure { problem = it.message ?: "Could not save your choices." }
            working = false
        }
    }

    fun sync() {
        scope.launch {
            working = true
            runCatching { client.syncFromDevice(context) }.onSuccess {
                people = it
                syncedThisSession = true
                problem = null
                runCatching { client.get() }.onSuccess { snapshot ->
                    settings = snapshot.consent
                    people = snapshot.people
                }
            }
                .onFailure { problem = it.message ?: "Contact sync unavailable." }
            working = false
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) sync() else problem = "Permission denied. BLINK works without contact access."
    }
    suspend fun refresh() {
        working = true
        runCatching { client.get() }.onSuccess {
            settings = it.consent
            people = it.people
            serviceReady = true
            mutualMatchingReady = it.mutualMatchingReady
            problem = null
        }.onFailure {
            serviceReady = false
            mutualMatchingReady = false
            problem = it.message ?: "Contact discovery is currently unavailable."
        }
        working = false
    }
    LaunchedEffect(accountId) {
        if (accountId.isBlank()) return@LaunchedEffect
        refresh()
    }

    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Find your people", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (working) CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
            Text("Optional: BLINK matches phone numbers with consent. No call history or contact names are collected.",
                style = MaterialTheme.typography.bodySmall)
            Text("Choose Sync my phone contacts, then tap Find my friends to grant permission. You can switch syncing off or delete your matching data anytime.",
                style = MaterialTheme.typography.bodySmall)
            Text(
                "No SMS verification is required to sync your own contacts. " +
                    "A possible match appears only when both members explicitly opt in " +
                    "and have each other's number saved. No SMS or ownership verification is used.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                if (settings.lastSyncedAt.isBlank()) "Not synced yet"
                else "Last synced: " + settings.lastSyncedAt.take(10),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = settings.syncEnabled, enabled = serviceReady && !working, onCheckedChange = {
                    update(settings.copy(syncEnabled = it))
                })
                Text("Sync my phone contacts", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = settings.discoverable,
                    enabled = mutualMatchingReady && serviceReady && !working && settings.syncEnabled,
                    onCheckedChange = {
                    update(settings.copy(discoverable = it))
                })
                Text(if (mutualMatchingReady) "Let mutual contacts find me" else "Matching service not ready",
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = settings.joinAlerts, enabled = serviceReady && !working && settings.syncEnabled, onCheckedChange = {
                    update(settings.copy(joinAlerts = it))
                })
                Text("Notify me when contacts join", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                        PackageManager.PERMISSION_GRANTED) sync()
                    else permission.launch(Manifest.permission.READ_CONTACTS)
                }, enabled = serviceReady && settings.syncEnabled && !working) { Text(if (settings.lastSyncedAt.isBlank()) "Find my friends" else "Sync contacts again") }
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth(),
                    enabled = serviceReady && !working) { Text("Delete synced contacts") }
            }
            problem?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            if (!serviceReady && !working && accountId.isNotBlank()) {
                TextButton(onClick = { scope.launch { refresh() } }) { Text("Retry contact discovery connection") }
            }
            if (syncedThisSession && people.isEmpty()) {
                Text("No eligible matches yet. Check back when more contacts join.",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (people.isNotEmpty()) {
                HorizontalDivider()
                Text(people.size.toString() + " possible BLINK connections", style = MaterialTheme.typography.titleSmall)
                Text("Matches are suggestions, not verified phone ownership.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                people.take(12).forEach { person ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(modifier = Modifier.size(42.dp).clip(CircleShape),
                            shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                            if (person.avatarUrl.isNotBlank()) {
                                AsyncImage(model = person.avatarUrl, contentDescription = "Profile photo",
                                    modifier = Modifier.size(42.dp), contentScale = ContentScale.Crop)
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(person.fullName.ifBlank { person.username }.take(1).uppercase())
                                }
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(person.fullName.ifBlank { person.username }, maxLines = 1,
                                style = MaterialTheme.typography.bodyMedium)
                            Text("@" + person.username, maxLines = 1,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onProfile(person.username) }) { Text("View") }
                        OutlinedButton(onClick = { onFollow(person) }) { Text("Follow") }
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
