package com.example.ui.screens

import androidx.compose.foundation.layout.*
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.data.supabase.BlinkContestMediaUpload
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
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
    val mediaUpload = remember { BlinkContestMediaUpload() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var contests by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var entries by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var insights by remember { mutableStateOf<JSONObject?>(null) }
    var voteReviewNote by remember { mutableStateOf("") }
    var paymentReference by remember { mutableStateOf("") }
    var paymentNote by remember { mutableStateOf("") }
    var selectedContest by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var pendingEntryDecision by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingContestDecision by remember { mutableStateOf<Pair<String, String>?>(null) }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var rules by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("General") }
    var university by remember { mutableStateOf("") }
    var cover by remember { mutableStateOf("") }
    var coverChoice by remember { mutableStateOf<Uri?>(null) }
    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { coverChoice = it }
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
        insights = null
        selectedContest?.let { id ->
            api.adminEntries(id).onSuccess { entries = it.contestObjects() }
                .onFailure { message = it.message ?: "Cannot load applications." }
            api.adminInsights(id).onSuccess { insights = it }
                .onFailure { message = it.message ?: "Cannot load contest insights." }
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
        item {
            Text("Contest cover", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = {
                coverPicker.launch(PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text(if (coverChoice == null) "Choose cover image" else "Change cover image") }
            coverChoice?.let { uri ->
                AsyncImage(model = uri, contentDescription = "Selected contest cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(150.dp))
                TextButton(onClick = { coverChoice = null }) { Text("Remove") }
            }
            OutlinedTextField(cover, { cover = it },
                label = { Text("Or existing HTTPS banner URL") },
                modifier = Modifier.fillMaxWidth(), enabled = coverChoice == null)
        }
        item { BlinkContestDateTimeField("Applications open", registrationStart, { registrationStart = it }) }
        item { BlinkContestDateTimeField("Applications close", registrationEnd, { registrationEnd = it }) }
        item { BlinkContestDateTimeField("Voting opens", votingStart, { votingStart = it }) }
        item { BlinkContestDateTimeField("Voting closes", votingEnd, { votingEnd = it }) }
        item { BlinkContestDateTimeField("Results announcement", resultsDate, { resultsDate = it }) }
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
                    runCatching {
                        val resolvedCover = coverChoice?.let {
                            mediaUpload.upload(context, it).getOrThrow()
                        } ?: cover
                        data.put("banner_url", resolvedCover.trim())
                        api.adminSave(data).getOrThrow()
                    }.onSuccess {
                        message = "Contest draft created."
                        coverChoice = null
                        revision++
                    }.onFailure { message = it.message ?: "Unable to save draft." }
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
                                pendingContestDecision = contest.optString("id") to "finalize"
                            }) { Text("Finalize") }
                            TextButton(enabled = !busy, onClick = {
                                pendingContestDecision = contest.optString("id") to "cancel"
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
        if (selectedContest != null) item {
            val stats = insights
            Text("Share and voting analytics",style=MaterialTheme.typography.titleLarge)
            if (stats == null) {
                Text("Loading contest metrics…")
            } else {
                Text("Recorded shares: " + stats.optInt("shares") +
                    " · Unique sharers: " + stats.optInt("unique_sharers"))
                Text("Counted votes: " + stats.optInt("counted_votes") +
                    " · Under review: " + stats.optInt("review_votes") +
                    " · Invalidated: " + stats.optInt("rejected_votes"))
                val total = stats.optInt("total_votes").coerceAtLeast(1)
                LinearProgressIndicator(
                    progress = { stats.optInt("counted_votes").toFloat() / total },
                    modifier = Modifier.fillMaxWidth(), color = Color(0xFF1685FF)
                )
                Text("Valid votes out of all received votes",
                    style = MaterialTheme.typography.bodySmall)
                val channels = stats.optJSONObject("share_by_channel")
                if (channels != null) {
                    val names = channels.keys().asSequence().toList().sorted()
                    names.forEach { name ->
                        Text(name.replaceFirstChar { it.uppercase() } + ": " +
                            channels.optInt(name) + " recorded shares",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("Sharing records user-initiated actions only, not website views.",
                    style=MaterialTheme.typography.bodySmall)
            }
        }
        if (selectedContest != null) item {
            val queue = insights?.optJSONArray("review_queue")?.contestObjects().orEmpty()
            Text("Vote integrity review",style=MaterialTheme.typography.titleLarge)
            if (queue.isEmpty()) Text("No suspicious votes are pending.")
            OutlinedTextField(value=voteReviewNote, onValueChange={voteReviewNote=it.take(300)},
                label={Text("Review reason (at least 8 characters)")},
                modifier=Modifier.fillMaxWidth())
        }
        items(insights?.optJSONArray("review_queue")?.contestObjects().orEmpty(),
            key = { it.optString("id") }) { vote ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Vote: " + vote.optString("id").take(8) + " · " +
                        vote.optString("reason"),style=MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        listOf("approve","invalidate").forEach { action ->
                            TextButton(enabled=!busy && voteReviewNote.trim().length>=8,
                                onClick={
                                    busy=true
                                    scope.launch {
                                        api.adminVoteReview(vote.optString("id"),action,voteReviewNote)
                                            .onSuccess { message="Vote reviewed.";revision++ }
                                            .onFailure { message=it.message ?: "Vote review failed." }
                                        busy=false
                                    }
                                }) { Text(action.replaceFirstChar { it.uppercase() }) }
                        }
                    }
                }
            }
        }
        if (selectedContest != null) item {
            Text("Prize payment ledger",style=MaterialTheme.typography.titleLarge)
            Text("Record confirmed external payments only. This does not send funds.",
                style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(value=paymentReference,onValueChange={paymentReference=it.take(150)},
                label={Text("External payment receipt/reference")},
                modifier=Modifier.fillMaxWidth())
            OutlinedTextField(value=paymentNote,onValueChange={paymentNote=it.take(400)},
                label={Text("Admin payment note (optional)")},
                modifier=Modifier.fillMaxWidth())
        }
        items(insights?.optJSONArray("prizes")?.contestObjects().orEmpty(),
            key = { it.optString("id") }) { prize ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("#" + prize.optInt("place") + " @" + prize.optString("username") +
                        " · ₦" + prize.optInt("amount_ngn") + " · " +
                        prize.optString("payment_status"))
                    Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                        listOf("processing","failed","paid").forEach { action ->
                            TextButton(
                                enabled=!busy && prize.optString("payment_status") != "paid" &&
                                    (action != "paid" || paymentReference.trim().length>=6),
                                onClick={
                                    busy=true
                                    scope.launch {
                                        api.adminPrize(prize.optString("id"),action,paymentReference,paymentNote)
                                            .onSuccess { message="Prize status recorded, not transferred.";revision++ }
                                            .onFailure { message=it.message ?: "Unable to save prize status." }
                                        busy=false
                                    }
                                }) { Text(action.replaceFirstChar { it.uppercase() }) }
                        }
                    }
                }
            }
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
                                if (action != "approve") {
                                    pendingEntryDecision = entry.optString("id") to action
                                } else {
                                    busy = true
                                    scope.launch {
                                        api.adminReview(entry.optString("id"), action)
                                            .onSuccess { message = "Entry approved."; revision++ }
                                            .onFailure { message = it.message ?: "Review failed." }
                                        busy = false
                                    }
                                }
                            }) { Text(action.replaceFirstChar { it.uppercase() }) }
                        }
                    }
                }
            }
        }
    }
    pendingEntryDecision?.let { (entryId, action) ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingEntryDecision = null },
            title = { Text(if (action == "reject") "Reject application?" else "Disqualify contestant?") },
            text = { Text("This changes the contestant's eligibility. Confirm the decision before continuing.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        api.adminReview(entryId, action)
                            .onSuccess { message = "Application updated."; revision++ }
                            .onFailure { message = it.message ?: "Review failed." }
                        busy = false
                        pendingEntryDecision = null
                    }
                }) { Text("Confirm " + action) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { pendingEntryDecision = null }) { Text("Go back") }
            }
        )
    }
    pendingContestDecision?.let { (contestId, action) ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingContestDecision = null },
            title = { Text(if (action == "finalize") "Finalize contest results?" else "Cancel contest?") },
            text = { Text(if (action == "finalize")
                "Check the voting deadline and pending vote reviews before publishing final winners."
                else "Cancelling will end applications and voting for this contest.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val result = if (action == "finalize") api.adminFinalize(contestId)
                        else api.adminTransition(contestId, "cancel")
                        result.onSuccess { message = "Contest updated."; revision++ }
                            .onFailure { message = it.message ?: "Contest action failed." }
                        busy = false
                        pendingContestDecision = null
                    }
                }) { Text(if (action == "finalize") "Finalize" else "Cancel contest") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { pendingContestDecision = null }) { Text("Go back") }
            }
        )
    }
}

/** Device-local picker writes an explicit time zone offset; manual ISO editing stays available. */
@Composable
private fun BlinkContestDateTimeField(
    title: String, value: String, onValueChange: (String) -> Unit
) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value, onValueChange,
            label = { Text(title) },
            modifier = Modifier.weight(1f), singleLine = true,
            placeholder = { Text("ISO date + time") }
        )
        OutlinedButton(onClick = {
            val now = Calendar.getInstance()
            DatePickerDialog(
                context, { _, year, month, day ->
                    TimePickerDialog(context, { _, hour, minute ->
                        val date = LocalDateTime.of(year, month + 1, day, hour, minute)
                        val zoneDate = date.atZone(ZoneId.systemDefault())
                        onValueChange(zoneDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
                }, now.get(Calendar.YEAR), now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH)
            ).show()
        }) { Text("Pick") }
    }
}
