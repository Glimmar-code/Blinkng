package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.data.repository.BlinkContactsDiscoveryClient
import kotlinx.coroutines.launch

/**
 * Nonblocking opt-in invitation after successful sign-in or signup.
 * Never reads the address book merely because an account is authenticated.
 * Returning users who opted in may sync again using existing Android permission.
 */
@Composable
fun BlinkContactSyncLoginPrompt(accountId: String, safeToShow: Boolean) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val client = remember(accountId) { BlinkContactsDiscoveryClient() }
    val prefs = remember { context.getSharedPreferences("blink_contact_sync_prompt_v1", Context.MODE_PRIVATE) }
    var visible by remember(accountId) { mutableStateOf(false) }
    var busy by remember(accountId) { mutableStateOf(false) }
    var issue by remember(accountId) { mutableStateOf<String?>(null) }
    var checkedAccount by remember(accountId) { mutableStateOf(false) }
    var allowMutualDiscovery by remember(accountId) { mutableStateOf(false) }

    fun deferPrompt() {
        prefs.edit().putLong("remind_" + accountId, System.currentTimeMillis()).apply()
        visible = false
        issue = null
    }

    fun syncWithPermission() {
        scope.launch {
            busy = true
            runCatching {
                val existing = client.get().consent
                if (!existing.syncEnabled || !existing.joinAlerts || (allowMutualDiscovery && !existing.discoverable)) {
                    client.set(existing.copy(syncEnabled = true, joinAlerts = true,
                        discoverable = allowMutualDiscovery || existing.discoverable))
                }
                client.syncFromDevice(context)
            }.onSuccess {
                prefs.edit().putBoolean("enabled_" + accountId, true).apply()
                visible = false
                issue = null
            }.onFailure {
                issue = it.message ?: "Unable to sync your contacts. Please try again."
            }
            busy = false
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) syncWithPermission()
        else {
            issue = "Contacts access is optional. You can enable it later in Connect."
            deferPrompt()
        }
    }

    LaunchedEffect(accountId, safeToShow) {
        if (accountId.isBlank() || !safeToShow || checkedAccount) return@LaunchedEffect
        checkedAccount = true
        // Fail closed when the contact matching Edge Function or tables are not
        // deployed. Do not ask users for contact access if we cannot sync.
        val snapshot = runCatching { client.get() }.getOrNull()
            ?: return@LaunchedEffect
        if (!snapshot.mutualMatchingReady) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        val optedIn = snapshot.consent.syncEnabled
        if (optedIn && granted) {
            prefs.edit().putBoolean("enabled_" + accountId, true).apply()
            // Consent was expressly given on an earlier visit. Retry silently;
            // server cooldowns and temporary failures do not interrupt login.
            runCatching { client.syncFromDevice(context) }
        } else {
            val lastDismissed = prefs.getLong("remind_" + accountId, 0L)
            if (lastDismissed == 0L ||
                System.currentTimeMillis() - lastDismissed >= 14L * 24L * 60L * 60L * 1000L) {
                visible = true
            }
        }
    }
    if (visible && safeToShow) {
        AlertDialog(
            onDismissRequest = { if (!busy) deferPrompt() },
            title = { Text("Find your contacts on BLINK") },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(
                        "Choose to sync contacts and receive possible-contact suggestions and " +
                            "joined alerts. BLINK asks permission first. Names and call history " +
                            "aren't uploaded. A phone match isn't proof of identity."
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = allowMutualDiscovery,
                            enabled = !busy, onCheckedChange = { allowMutualDiscovery = it })
                        Text("Let mutual contacts find my BLINK profile (optional)")
                    }
                    if (busy) CircularProgressIndicator()
                    issue?.let { Text(it) }
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        issue = null
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                            PackageManager.PERMISSION_GRANTED) syncWithPermission()
                        else permission.launch(Manifest.permission.READ_CONTACTS)
                    }
                ) { Text("Sync contacts and notify me") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { deferPrompt() }) { Text("Not now") }
            }
        )
    }
}
