package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.supabase.AdminLiveContentRow
import com.example.data.supabase.AdminSupabaseService
import kotlinx.coroutines.launch
import org.json.JSONObject

private val liveKeys = listOf("announcement", "promotion", "display", "feature_visibility")
private fun liveLabel(key: String) = when (key) {
    "announcement" -> "Announcement"
    "promotion" -> "Promotion"
    "display" -> "Display"
    else -> "Visibility"
}

/** No database dashboard needed: preview, publish and pause from BLINK Admin.
 * Only the owner can publish; Supabase revalidates every write.
 */
@Composable
fun BlinkAdminLiveContentManagerScreen(
    service: AdminSupabaseService,
    isOwner: Boolean,
    onBack: () -> Unit,
) {
    var showCampaignManager by remember { mutableStateOf(false) }
    if (showCampaignManager) {
        BlinkAdminCampaignManagerScreen(
            service = service,
            isOwner = isOwner,
            onBack = { showCampaignManager = false }
        )
        return
    }
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<AdminLiveContentRow>>(emptyList()) }
    var selectedKey by remember { mutableStateOf("announcement") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(false) }
    var showAnnouncements by remember { mutableStateOf(true) }
    var showPromotions by remember { mutableStateOf(true) }
    var homePromo by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var confirmed by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val active = rows.firstOrNull { it.key == selectedKey }

    fun select(key: String, content: AdminLiveContentRow?) {
        selectedKey = key
        val value = content?.value ?: JSONObject()
        title = value.optString("title")
        body = value.optString("message")
        enabled = content?.enabled ?: false
        showAnnouncements = value.optBoolean("show_announcements", true)
        showPromotions = value.optBoolean("show_promotions", true)
        homePromo = value.optBoolean("home_promo_enabled", true)
        message = null
        error = null
    }

    fun reload() {
        scope.launch {
            loading = true
            service.fetchLiveContent()
                .onSuccess {
                    rows = it
                    select(selectedKey, it.firstOrNull { row -> row.key == selectedKey })
                }
                .onFailure { error = it.message ?: "Unable to load live content." }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }
    BackHandler(enabled = confirmed) { if (!working) confirmed = false }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Live Content", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = { showCampaignManager = true }) { Text("Campaigns") }
            TextButton(onClick = onBack, enabled = !working) { Text("Back") }
        }
        Text(
            "Updates reach installed BLINK clients automatically. This is separate from one-time push announcements.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (!isOwner) Text("View only — BLINK owner approval required to publish or pause.", color = MaterialTheme.colorScheme.error)
        if (loading) CircularProgressIndicator()
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            liveKeys.forEach { key ->
                FilterChip(
                    selected = selectedKey == key,
                    onClick = { select(key, rows.firstOrNull { it.key == key }) },
                    label = { Text(liveLabel(key), style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        if (!loading && active == null) {
            Text("Configuration has not been deployed. No changes can be published.", color = MaterialTheme.colorScheme.error)
        } else if (active != null) {
            val isMessage = selectedKey in listOf("announcement", "promotion")
            if (isMessage) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(80) },
                    label = { Text("Title (80 characters)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = isOwner && !working
                )
                OutlinedTextField(
                    value = body, onValueChange = { body = it.take(300) },
                    label = { Text("Message (300 characters)") },
                    modifier = Modifier.fillMaxWidth(), minLines = 3, enabled = isOwner && !working
                )
            } else if (selectedKey == "display") {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Show announcements")
                    Switch(showAnnouncements, onCheckedChange = { showAnnouncements = it }, enabled = isOwner && !working)
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Show promotions")
                    Switch(showPromotions, onCheckedChange = { showPromotions = it }, enabled = isOwner && !working)
                }
            } else {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Home promotion visible")
                    Switch(homePromo, onCheckedChange = { homePromo = it }, enabled = isOwner && !working)
                }
            }

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(if (enabled) "Published / enabled" else "Paused / disabled")
                Switch(enabled, onCheckedChange = { enabled = it }, enabled = isOwner && !working)
            }

            Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Preview — " + liveLabel(selectedKey), fontWeight = FontWeight.Bold)
                    if (isMessage) {
                        Text(title.ifBlank { "Title appears here" })
                        Text(body.ifBlank { "Message appears here" })
                    } else {
                        Text(if (selectedKey == "display")
                            "Announcements: ${if (showAnnouncements) "shown" else "hidden"} • Promotions: ${if (showPromotions) "shown" else "hidden"}"
                            else "Home promotion: ${if (homePromo) "shown" else "hidden"}")
                    }
                    Text(if (enabled) "Will be visible to clients" else "Will not be visible to clients",
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val valid = !isMessage || !enabled || (title.isNotBlank() && body.isNotBlank())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { confirmed = true },
                    enabled = isOwner && !working && valid,
                ) { Text(if (enabled) "Review & Publish" else "Review & Pause") }
                OutlinedButton(onClick = ::reload, enabled = !working) { Text("Reload") }
            }
            Text("Changes are audited. Only supported presentation settings can update live; payments and permissions cannot.",
                style = MaterialTheme.typography.labelSmall)
        }
    }

    if (confirmed && active != null) {
        AlertDialog(
            onDismissRequest = { if (!working) confirmed = false },
            title = { Text(if (enabled) "Publish live changes?" else "Pause this live content?") },
            text = {
                Text("${liveLabel(selectedKey)} • Revision ${active.revision}. " +
                    "Saved settings will appear on supported BLINK clients after their next refresh.")
            },
            confirmButton = {
                Button(enabled = !working, onClick = {
                    working = true
                    error = null
                    val keyToSave = selectedKey
                    val value = when (keyToSave) {
                        "announcement", "promotion" -> JSONObject()
                            .put("title", title.trim()).put("message", body.trim())
                        "display" -> JSONObject()
                            .put("show_announcements", showAnnouncements)
                            .put("show_promotions", showPromotions)
                        else -> JSONObject().put("home_promo_enabled", homePromo)
                    }
                    scope.launch {
                        service.publishLiveContent(keyToSave, value, enabled, active.revision)
                            .onSuccess { saved ->
                                rows = rows.map { if (it.key == saved.key) saved else it }
                                confirmed = false
                                message = "Saved securely. Clients will refresh automatically."
                            }.onFailure { error = it.message ?: "Publishing failed. Reload and retry."; confirmed = false }
                        working = false
                    }
                }) { Text(if (enabled) "Publish" else "Pause") }
            },
            dismissButton = { TextButton(onClick = { confirmed = false }, enabled = !working) { Text("Cancel") } },
        )
    }
}
