package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.supabase.BlinkContestService
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dashboard entry is capability-gated. Every mutation is independently checked
 * by private.require_blink_admin() in Supabase, never by a device-side role flag.
 */
@Composable
fun BlinkContestAdminScreen(onBack: () -> Unit) {
    val api = remember { BlinkContestService() }
    val scope = rememberCoroutineScope()
    var contests by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var entries by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedContest by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var rules by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("General") }
    var university by remember { mutableStateOf("") }
    var cover by remember { mutableStateOf("") }
    var registrationStart by remember { mutableStateOf("") }
    var registrationEnd by remember { mutableStateOf("") }
    var votingStart by remember { mutableStateOf("") }
    var votingEnd by remember { mutableStateOf("") }
    var resultsDate by remember { mutableStateOf("") }
    var prizeOne by remember { mutableStateOf("0") }
    var prizeTwo by remember { mutableStateOf("0") }
    var prizeThree by remember { mutableStateOf("0") }
    var autoApprove by remember { mutableStateOf(false) }

    LaunchedEffect(revision) {
        api.adminContests().onSuccess { contests = it.contestObjects() }
            .onFailure { message = it.message ?: "Cannot load contests." }
    }
    LaunchedEffect(selectedContest, revision) {
        entries = emptyList()
        selectedContest?.let { id ->
            api.adminEntries(id).onSuccess { entries = it.contestObjects() }
                .onFailure { message = it.message ?: "Cannot load applications." }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Contest manager", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onBack) { Text("Close") }
            }
            Text("Applications and voting are always free. Dates use ISO format with a time zone (e.g. 2026-11-01T09:00:00+01:00).")
            if (message.isNotBlank()) {
                Text(message, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { message = "" }) { Text("Dismiss") }
            }
            Text("Create contest draft", style = MaterialTheme.typography.titleMedium)
        }
        item { OutlinedTextField(title, { title = it.take(100) }, label = { Text("Contest title") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(description, { description = it.take(2000) }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
        item { OutlinedTextField(rules, { rules = it.take(4000) }, label = { Text("Competition rules") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
        item { OutlinedTextField(category, { category = it.take(60) }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(university, { university = it.take(120) }, label = { Text("University (optional, blank = all)") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(cover, { cover = it }, label = { Text("Banner HTTPS URL (optional)") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(registrationStart, { registrationStart = it }, label = { Text("Applications open (ISO date/time)") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(registrationEnd, { registrationEnd = it }, label = { Text("Applications close (ISO date/time)") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(votingStart, { votingStart = it }, label = { Text("Voting opens (ISO date/time)") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(votingEnd, { votingEnd = it }, label = { Text("Voting closes (ISO date/time)") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(resultsDate, { resultsDate = it }, label = { Text("Results announcement (ISO date/time)") }, modifier = Modifier.fillMaxWidth()) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(prizeOne, { prizeOne = it }, label = { Text("1st ₦") }, modifier = Modifier.weight(1f))
                OutlinedTextField(prizeTwo, { prizeTwo = it }, label = { Text("2nd ₦") }, modifier = Modifier.weight(1f))
                OutlinedTextField(prizeThree, { prizeThree = it }, label = { Text("3rd ₦") }, modifier = Modifier.weight(1f))
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = autoApprove, onCheckedChange = { autoApprove = it })
                Text("Automatically approve valid applications")
            }
            Button(enabled = !busy && title.trim().length in 4..100, onClick = {
                busy = true
                message = ""
                val data = JSONObject()
                    .put("title", title.trim())
                    .put("description", description.trim())
                    .put("rules", rules.trim())
                    .put("category", category.trim().ifBlank { "General" })
                    .put("university", university.trim())
                    .put("banner_url", cover.trim())
                    .put("registration_opens_at", registrationStart.trim())
                    .put("registration_closes_at", registrationEnd.trim())
                    .put("voting_opens_at", votingStart.trim())
                    .put("voting_closes_at", votingEnd.trim())
                    .put("results_at", resultsDate.trim())
                    .put("prize_first", prizeOne.toIntOrNull() ?: -1)
                    .put("prize_second", prizeTwo.toIntOrNull() ?: -1)
                    .put("prize_third", prizeThree.toIntOrNull() ?: -1)
                    .put("auto_approve", autoApprove)
                scope.launch {
                    api.adminSave(data).onSuccess { message = "Contest draft created."; revision++ }
                        .onFailure { message = it.message ?: "Unable to save draft." }
                    busy = false
                }
            }) { Text("Save contest draft") }
        }
        item {
            HorizontalDivider()
            Text("Manage contests", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 10.dp))
        }
        items(contests, key = { it.optString("id") }) { contest ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(contest.optString("title"), fontWeight = FontWeight.Bold)
                    Text(contest.optString("status"))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { selectedContest = contest.optString("id") }) { Text("Entries") }
                        val action = when (contest.optString("status")) {
                            "draft" -> "publish"
                            "published" -> "pause"
                            "paused" -> "resume"
                            else -> ""
                        }
                        if (action.isNotBlank()) TextButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                api.adminTransition(contest.optString("id"), action)
                                    .onSuccess { message = "Contest updated."; revision++ }
                                    .onFailure { message = it.message ?: "Action failed." }
                                busy = false
                            }
                        }) { Text(action.replaceFirstChar { it.uppercase() }) }
                        if (contest.optString("status") in listOf("published", "paused")) {
                            TextButton(enabled = !busy, onClick = {
                                busy = true
                                scope.launch {
                                    api.adminTransition(contest.optString("id"), "complete")
                                        .onSuccess { message = "Results finalized."; revision++ }
                                        .onFailure { message = it.message ?: "Finalize failed." }
                                    busy = false
                                }
                            }) { Text("Finalize") }
                            TextButton(enabled = !busy, onClick = {
                                busy = true
                                scope.launch {
                                    api.adminTransition(contest.optString("id"), "cancel")
                                        .onSuccess { message = "Contest cancelled."; revision++ }
                                        .onFailure { message = it.message ?: "Cancel failed." }
                                    busy = false
                                }
                            }) { Text("Cancel") }
                        }
                    }
                }
            }
        }
        if (selectedContest != null) item {
            HorizontalDivider()
            Text("Contest applications", style = MaterialTheme.typography.titleLarge)
        }
        items(entries, key = { it.optString("id") }) { entry ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("@" + entry.optString("username") + " · " + entry.optString("status"),
                        fontWeight = FontWeight.SemiBold)
                    Text(entry.optString("statement"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("approve", "reject", "disqualify").forEach { action ->
                            TextButton(enabled = !busy, onClick = {
                                busy = true
                                scope.launch {
                                    api.adminReview(entry.optString("id"), action)
                                        .onSuccess { message = "Entry reviewed."; revision++ }
                                        .onFailure { message = it.message ?: "Review failed." }
                                    busy = false
                                }
                            }) { Text(action.replaceFirstChar { it.uppercase() }) }
                        }
                    }
                }
            }
        }
    }
}
