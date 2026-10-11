package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.supabase.BlinkBugReport
import com.example.data.supabase.BlinkBugReportService
import kotlinx.coroutines.launch

/** Independent inbox, visible only after the admin capability gate in AdminControlCenter. */
@Composable
fun BlinkAdminBugInbox(onDismiss: () -> Unit) {
    val service = remember { BlinkBugReportService() }
    val scope = rememberCoroutineScope()
    var reports by remember { mutableStateOf<List<BlinkBugReport>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<BlinkBugReport?>(null) }
    var note by remember { mutableStateOf("") }
    var proposedAction by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var screenshot by remember { mutableStateOf<Bitmap?>(null) }
    var screenshotError by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        service.adminReports().onSuccess { reports = it; error = null }
            .onFailure { error = it.message ?: "Cannot load bug reports." }
        loading = false
    }
    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(selected?.id) {
        screenshot = null
        screenshotError = null
        val path = selected?.screenshotPath?.takeIf(String::isNotBlank) ?: return@LaunchedEffect
        service.screenshot(path).onSuccess { bytes ->
            screenshot = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.onFailure { screenshotError = it.message ?: "Cannot open screenshot." }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Card(Modifier.fillMaxWidth().fillMaxHeight(.85f)) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bug Reports • Admin DM", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("Close") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (loading) {
                    CircularProgressIndicator()
                } else if (reports.isEmpty()) {
                    Text("No bug reports yet.")
                } else {
                    TextButton(onClick = { scope.launch { reload() } }) { Text("Refresh") }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(reports, key = { it.id }) { report ->
                            Card(onClick = { selected = report; note = report.note },
                                modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("@${report.username} • ${report.status.uppercase()}",
                                        fontWeight = FontWeight.Bold)
                                    Text(report.surface, style = MaterialTheme.typography.labelSmall)
                                    Text(report.description, maxLines = 3)
                                    if (report.rewardCoins > 0) {
                                        Text("Rewarded ${report.rewardCoins} coins",
                                            color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { report ->
        AlertDialog(
            onDismissRequest = { if (!busy) selected = null },
            title = { Text("Report #${report.id.take(8)} • @${report.username}") },
            text = {
                Column(
                    Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(report.description)
                    Text("Surface: ${report.surface} • ${report.createdAt.take(16)}")
                    if (screenshot != null) {
                        Image(screenshot!!.asImageBitmap(), contentDescription = "Private bug screenshot",
                            modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp))
                    }
                    screenshotError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedTextField(value = note, onValueChange = { if (it.length < 600) note = it },
                        label = { Text("Admin response / review notes") }, enabled = !busy)
                    if (report.rewardCoins == 0) {
                        Text("Review or award verified bug reports:",
                            style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(20, 50, 100).forEach { coins ->
                                OutlinedButton(onClick = { proposedAction = "rewarded" to coins },
                                    enabled = !busy, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                    Text("+$coins", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        Row {
                            TextButton(onClick = { proposedAction = "reviewing" to 0 }, enabled = !busy) { Text("Reviewing") }
                            TextButton(onClick = { proposedAction = "fixed" to 0 }, enabled = !busy) { Text("Fixed") }
                            TextButton(onClick = { proposedAction = "rejected" to 0 }, enabled = !busy) { Text("Reject") }
                        }
                    } else {
                        Text("Already rewarded • ${report.rewardCoins} coins. Duplicate rewards are blocked.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selected = null }, enabled = !busy) { Text("Back") }
            }
        )
    }

    proposedAction?.let { (action, coins) ->
        AlertDialog(
            onDismissRequest = { if (!busy) proposedAction = null },
            title = { Text("Confirm report review") },
            text = { Text(if (coins > 0)
                "Award $coins Blink Coins to this reporter? This is permanent and cannot be repeated."
            else "Mark this report as ${action.uppercase()}? Your note will be visible to the reporter.") },
            confirmButton = {
                Button(enabled = !busy, onClick = {
                    val id = selected?.id ?: return@Button
                    busy = true
                    scope.launch {
                        service.review(id, action, coins, note)
                            .onSuccess {
                                proposedAction = null
                                selected = null
                                reload()
                            }
                            .onFailure { error = it.message ?: "Admin review failed."; proposedAction = null }
                        busy = false
                    }
                }) { Text(if (busy) "Saving..." else "Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { proposedAction = null }, enabled = !busy) { Text("Cancel") }
            }
        )
    }
}
