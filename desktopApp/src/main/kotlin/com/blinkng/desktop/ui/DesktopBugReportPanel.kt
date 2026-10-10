package com.blinkng.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blinkng.desktop.DesktopAppState
import com.blinkng.desktop.data.DesktopRpcActions
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Desktop equivalent of Android's shake-to-report: manual entry in Settings. */
@Composable
fun DesktopBugReportPanel(state: DesktopAppState, admin: Boolean = false) {
    val actions = remember(state.client) { DesktopRpcActions(state.client) }
    val scope = rememberCoroutineScope()
    var description by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var reports by remember { mutableStateOf(JSONArray()) }
    var pending by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    var showReports by remember { mutableStateOf(admin) }

    suspend fun refreshReports() {
        runCatching { if (admin) actions.adminBugReports() else actions.myBugReports() }
            .onSuccess { reports = it; feedback = "" }
            .onFailure { feedback = it.message.orEmpty() }
    }
    LaunchedEffect(admin) { if (admin) refreshReports() }

    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (admin) "Bug Reports • Admin DM" else "Report a bug & earn coins",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (!admin) {
                Text("Help improve BLINK. Verified, useful reports can earn coins after admin review.")
                OutlinedTextField(
                    value = description, onValueChange = { if (it.length <= 2000) description = it },
                    label = { Text("Describe the bug") },
                    placeholder = { Text("What happened, where, and what did you expect?") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                    minLines = 3, maxLines = 6, enabled = !busy
                )
                Button(
                    enabled = !busy && description.trim().length in 20..2000,
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching { actions.submitBugReport(description) }
                                .onSuccess {
                                    feedback = "Sent to BLINK admin. Your report: #${it.optString("id").take(8)}"
                                    description = ""
                                    showReports = true
                                    refreshReports()
                                }
                                .onFailure { feedback = it.message.orEmpty() }
                            busy = false
                        }
                    }
                ) { Text(if (busy) "Sending..." else "Send report to Admin") }
                Text("On Android, you can also shake your phone or attach a screenshot.",
                    style = MaterialTheme.typography.bodySmall)
            }
            Row {
                OutlinedButton(onClick = {
                    showReports = !showReports
                    if (showReports) scope.launch { refreshReports() }
                }) { Text(if (showReports) "Hide reports" else if (admin) "Open bug inbox" else "My reports & rewards") }
                Spacer(Modifier.width(8.dp))
                if (showReports) TextButton(onClick = { scope.launch { refreshReports() } }) { Text("Refresh") }
            }
            if (feedback.isNotBlank()) Text(feedback, color = MaterialTheme.colorScheme.primary)
            if (showReports) {
                if (reports.length() == 0) Text("No reports yet.")
                (0 until minOf(reports.length(), 15)).forEach { index ->
                    val row = reports.optJSONObject(index) ?: JSONObject()
                    val id = row.optString("id")
                    val coins = row.optInt("reward_coins", 0)
                    HorizontalDivider()
                    Text((if (admin) "@${row.optString("username")} • " else "") +
                        row.optString("status").uppercase() +
                        if (coins > 0) " • +$coins coins" else "",
                        fontWeight = FontWeight.Bold)
                    Text(row.optString("description"), maxLines = 5)
                    if (!admin && row.optString("admin_note").isNotBlank())
                        Text("Admin: ${row.optString("admin_note")}")
                    if (admin && coins == 0) {
                        OutlinedTextField(value = note, onValueChange = { if (it.length <= 600) note = it },
                            label = { Text("Note to reporter (sent as a private DM)") },
                            modifier = Modifier.fillMaxWidth(), enabled = !busy)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("reviewing" to 0, "fixed" to 0, "rejected" to 0,
                                "rewarded" to 20, "rewarded" to 50, "rewarded" to 100).forEach { (status, reward) ->
                                OutlinedButton(onClick = {
                                    pending = id to reward
                                }, enabled = !busy, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                    Text(if (reward > 0) "+$reward" else status, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    pending?.let { (reportId, amount) ->
        val targetStatus = if (amount > 0) "rewarded" else "reviewing"
        // Reviewer confirmation is required for every wallet or status change.
        AlertDialog(
            onDismissRequest = { if (!busy) pending = null },
            title = { Text("Confirm bug report review") },
            text = { Text(if (amount > 0) "Award $amount coins to the reporter?" else
                "Mark the report as under review?") },
            confirmButton = {
                Button(onClick = {
                    busy = true
                    scope.launch {
                        actions.reviewBugReport(reportId, targetStatus, amount, note)
                            .let { feedback = "Report updated successfully." }
                        pending = null
                        note = ""
                        refreshReports()
                        busy = false
                    }
                }, enabled = !busy) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { pending = null }, enabled = !busy) { Text("Cancel") } }
        )
    }
}
