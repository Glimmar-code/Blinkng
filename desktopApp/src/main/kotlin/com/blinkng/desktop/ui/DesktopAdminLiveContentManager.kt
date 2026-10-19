package com.blinkng.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.window.Dialog
import com.blinkng.desktop.data.DesktopRpcActions
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Feature parity with Android's server-authorized, audited Live Content Manager. */
@Composable
fun DesktopAdminLiveContentManager(
    actions: DesktopRpcActions,
    isOwner: Boolean,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val keys = listOf("announcement", "promotion", "display", "feature_visibility")
    var items by remember { mutableStateOf(JSONArray()) }
    var selected by remember { mutableStateOf("announcement") }
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(false) }
    var showAnnouncements by remember { mutableStateOf(true) }
    var showPromotions by remember { mutableStateOf(true) }
    var homePromo by remember { mutableStateOf(true) }
    var campaignFlags by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    fun rowFor(key: String): JSONObject? {
        for (i in 0 until items.length()) {
            val row = items.optJSONObject(i) ?: continue
            if (row.optString("key") == key) return row
        }
        return null
    }
    fun select(key: String) {
        selected = key
        val row = rowFor(key)
        val value = row?.optJSONObject("value") ?: JSONObject()
        title = value.optString("title")
        message = value.optString("message")
        enabled = row?.optBoolean("enabled", false) ?: false
        showAnnouncements = value.optBoolean("show_announcements", true)
        showPromotions = value.optBoolean("show_promotions", true)
        homePromo = value.optBoolean("home_promo_enabled", true)
        campaignFlags = com.blinkng.shared.live.BlinkLiveCampaignPolicy.placements.associateWith {
            value.optBoolean(it + "_campaign_enabled", true)
        }
        info = null
        error = null
    }
    fun refresh() {
        scope.launch {
            loading = true
            runCatching { actions.adminReadLiveConfig() }
                .onSuccess { items = it; select(selected) }
                .onFailure { error = it.message ?: "Cannot fetch live content." }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }
    val active = rowFor(selected)
    val isMessage = selected in listOf("announcement", "promotion")

    Dialog(onCloseRequest = { if (!saving) onDismiss() }, title = "BLINK — Live Content") {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp)
                    .verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Live Content Manager", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Changes are published server-side and appear on Android/Windows after their next refresh.",
                    style = MaterialTheme.typography.bodySmall)
                if (!isOwner) Text("Read only. Publishing requires the BLINK owner.",
                    color = MaterialTheme.colorScheme.error)
                if (loading) Text("Loading current content…")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    keys.forEach { k ->
                        FilterChip(selected = selected == k, onClick = { select(k) },
                            label = { Text(if (k == "feature_visibility") "Visibility" else k.replaceFirstChar(Char::uppercase)) })
                    }
                }
                if (active == null && !loading) {
                    Text("Config isn't deployed yet. No changes can be published.",
                        color = MaterialTheme.colorScheme.error)
                }
                if (active != null) {
                    if (isMessage) {
                        OutlinedTextField(title, { title = it.take(80) }, label = { Text("Title") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = isOwner && !saving)
                        OutlinedTextField(message, { message = it.take(300) }, label = { Text("Message") },
                            modifier = Modifier.fillMaxWidth(), minLines = 3, enabled = isOwner && !saving)
                    } else if (selected == "display") {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Show announcements")
                            Switch(showAnnouncements, onCheckedChange = { showAnnouncements = it }, enabled = isOwner && !saving)
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Show promotions")
                            Switch(showPromotions, onCheckedChange = { showPromotions = it }, enabled = isOwner && !saving)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Home promotion enabled")
                            Switch(homePromo, onCheckedChange = { homePromo = it }, enabled = isOwner && !saving)
                        }
                        com.blinkng.shared.live.BlinkLiveCampaignPolicy.placements.sorted().forEach { page ->
                            Row(horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()) {
                                Text(page.replaceFirstChar(Char::uppercase) + " campaign visible")
                                Switch(campaignFlags[page] != false,
                                    onCheckedChange = { campaignFlags = campaignFlags + (page to it) },
                                    enabled = isOwner && !saving)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(if (enabled) "Published" else "Paused")
                        Switch(enabled, onCheckedChange = { enabled = it }, enabled = isOwner && !saving)
                    }
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Preview", fontWeight = FontWeight.Bold)
                            if (isMessage) {
                                Text(title.ifBlank { "Title" })
                                Text(message.ifBlank { "Message" })
                            } else {
                                Text(if (selected == "display")
                                    "Announcements: ${if (showAnnouncements) "On" else "Off"} • Promotions: ${if (showPromotions) "On" else "Off"}"
                                    else "Home promotion: ${if (homePromo) "On" else "Off"}")
                            }
                            Text(if (enabled) "Will be visible" else "Hidden from clients")
                        }
                    }
                    info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { confirm = true },
                            enabled = isOwner && !saving && (!isMessage || !enabled ||
                                (title.isNotBlank() && message.isNotBlank()))
                        ) { Text(if (enabled) "Review & Publish" else "Review & Pause") }
                        OutlinedButton(onClick = ::refresh, enabled = !saving) { Text("Reload") }
                        OutlinedButton(onClick = onDismiss, enabled = !saving) { Text("Close") }
                    }
                }
            }
        }
    }

    if (confirm && active != null) {
        AlertDialog(
            onDismissRequest = { if (!saving) confirm = false },
            title = { Text(if (enabled) "Publish change?" else "Pause content?") },
            text = { Text("Change to ${selected} will apply automatically to compatible clients. Revision: ${active.optLong("revision")}.") },
            confirmButton = {
                Button(enabled = !saving, onClick = {
                    saving = true
                    error = null
                    val value = when (selected) {
                        "announcement", "promotion" -> JSONObject().put("title", title.trim()).put("message", message.trim())
                        "display" -> JSONObject().put("show_announcements", showAnnouncements).put("show_promotions", showPromotions)
                        else -> JSONObject().put("home_promo_enabled", homePromo).apply {
                            com.blinkng.shared.live.BlinkLiveCampaignPolicy.placements.forEach { page ->
                                put(page + "_campaign_enabled", campaignFlags[page] != false)
                            }
                        }
                    }
                    scope.launch {
                        runCatching { actions.adminPublishLiveConfig(
                            selected, value, enabled, active.getLong("revision")
                        ) }.onSuccess {
                            confirm = false
                            info = "Published successfully."
                            refresh()
                        }.onFailure {
                            error = it.message ?: "Publish failed. Reload the record and try again."
                            confirm = false
                        }
                        saving = false
                    }
                }) { Text(if (enabled) "Publish" else "Pause") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }, enabled = !saving) { Text("Cancel") } }
        )
    }
}
