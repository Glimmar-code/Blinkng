package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.data.supabase.BlinkContestMediaUpload
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.supabase.BlinkContestService
import com.blinkng.shared.BlinkContestDraftValidator
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
    var appeals by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var cleanupPreview by remember { mutableStateOf<JSONObject?>(null) }
    var cleanupAuditBusy by remember { mutableStateOf(false) }
    var appealReviewReason by remember { mutableStateOf("") }
    var appealReviewError by remember { mutableStateOf<String?>(null) }
    var insights by remember { mutableStateOf<JSONObject?>(null) }
    var voteReviewNote by remember { mutableStateOf("") }
    var paymentReference by remember { mutableStateOf("") }
    var paymentNote by remember { mutableStateOf("") }
    var selectedContest by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    var contestsLoading by remember { mutableStateOf(true) }
    var entriesLoading by remember { mutableStateOf(false) }
    var entriesError by remember { mutableStateOf<String?>(null) }
    var insightsError by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var pendingEntryDecision by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingEntryReason by remember { mutableStateOf("") }
    var pendingEntryError by remember { mutableStateOf<String?>(null) }
    var reviewHistoryEntry by remember { mutableStateOf<String?>(null) }
    var reviewHistoryName by remember { mutableStateOf("") }
    var reviewHistoryRows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var reviewHistoryTotal by remember { mutableIntStateOf(0) }
    var reviewHistoryBusy by remember { mutableStateOf(false) }
    var reviewHistoryError by remember { mutableStateOf<String?>(null) }
    var previewEntryMedia by remember { mutableStateOf<String?>(null) }
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
    var maxEntries by remember { mutableStateOf("") }
    var editingContestId by remember { mutableStateOf<String?>(null) }
    var applicantFilter by remember { mutableStateOf("All") }
    val listState = rememberLazyListState()
    val resetDraft: () -> Unit = {
        editingContestId = null
        title = ""; description = ""; rules = ""; category = "General"
        university = ""; cover = ""; coverChoice = null
        registrationStart = ""; registrationEnd = ""; votingStart = ""
        votingEnd = ""; resultsDate = ""
        prizeOne = "0"; prizeTwo = "0"; prizeThree = "0"
        maxEntries = ""; autoApprove = false
    }
    val draftError = BlinkContestDraftValidator.error(
        title, listOf(registrationStart, registrationEnd, votingStart, votingEnd, resultsDate),
        listOf(prizeOne, prizeTwo, prizeThree), maxEntries, cover, coverChoice != null
    )

    LaunchedEffect(message) {
        if (message.isNotBlank()) snackbarHostState.showSnackbar(message)
    }
    LaunchedEffect(revision) {
        contestsLoading = true
        api.adminContests().onSuccess { contests = it.contestObjects() }
            .onFailure { message = it.message ?: "Cannot load contests." }
        contestsLoading = false
    }
    LaunchedEffect(selectedContest) {
        reviewHistoryEntry = null
        previewEntryMedia = null
        applicantFilter = "All"
        appealReviewReason = ""
        appealReviewError = null
        voteReviewNote = ""
        paymentReference = ""
        paymentNote = ""
    }
    LaunchedEffect(reviewHistoryEntry, revision) {
        reviewHistoryRows = emptyList()
        reviewHistoryTotal = 0
        reviewHistoryError = null
        val entryId = reviewHistoryEntry
        if (entryId != null) {
            reviewHistoryBusy = true
            api.adminEntryHistory(entryId)
                .onSuccess { record ->
                    reviewHistoryRows = record.optJSONArray("history")?.contestObjects().orEmpty()
                    reviewHistoryTotal = record.optInt("total")
                }
                .onFailure { reviewHistoryError = it.message ?: "Unable to load moderation history." }
            reviewHistoryBusy = false
        }
    }
    LaunchedEffect(selectedContest, revision) {
        val id = selectedContest
        if (id == null) {
            entries = emptyList()
            appeals = emptyList()
            insights = null
            entriesError = null
            insightsError = null
        } else {
            // Keep successful data on refresh, but never display another contest's.
            entriesLoading = true
            entriesError = null
            insightsError = null
            api.adminAppeals(id)
                .onSuccess { appeals = it.contestObjects(); appealReviewError = null }
                .onFailure { appealReviewError = it.message ?: "Cannot load appeals." }
            api.adminEntries(id).onSuccess { entries = it.contestObjects() }
                .onFailure { entriesError = it.message ?: "Cannot load applications." }
            entriesLoading = false
            api.adminInsights(id).onSuccess { insights = it }
                .onFailure { insightsError = it.message ?: "Cannot load contest insights." }
        }
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp),
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
            Text(if (editingContestId == null) "Create contest draft" else "Edit contest draft",
                style = MaterialTheme.typography.titleMedium)
            if (editingContestId != null) {
                TextButton(enabled = !busy, onClick = resetDraft) { Text("Cancel editing") }
            }
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
            OutlinedTextField(maxEntries, { maxEntries = it.take(6) },
                label = { Text("Maximum participants (optional)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Leave blank for unlimited entries; minimum 2, maximum 100,000.",
                style = MaterialTheme.typography.bodySmall)
        }
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
            if (autoApprove) {
                Text("Auto-approved applications become visible without human moderation. Enable only when submissions are reviewed through other safeguards.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("All applications and votes remain free.")
            }
            if (draftError != null) {
                Text(draftError, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            Button(enabled = !busy && draftError == null, onClick = {
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
                    .put("max_entries", maxEntries.toIntOrNull() ?: JSONObject.NULL)
                    .put("auto_approve", autoApprove)
                scope.launch {
                    runCatching {
                        val resolvedCover = coverChoice?.let {
                            mediaUpload.upload(context, it).getOrThrow()
                        } ?: cover
                        data.put("banner_url", resolvedCover.trim())
                        api.adminSave(data, editingContestId).getOrThrow()
                    }.onSuccess {
                        message = if (editingContestId == null) "Contest draft created."
                            else "Contest draft updated."
                        resetDraft()
                        revision++
                    }.onFailure { message = it.message ?: "Unable to save draft." }
                    busy = false
                }
            }) { Text(if (editingContestId == null) "Save contest draft" else "Save draft changes") }
        }
        item {
            HorizontalDivider()
            Text("Manage contests", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 10.dp))
            if (contestsLoading && contests.isEmpty()) CircularProgressIndicator()
            if (!contestsLoading && contests.isEmpty()) Text("No contests available. Create a draft above.")
            TextButton(enabled = !contestsLoading, onClick = { revision++ }) { Text("Refresh manager") }
        }
        items(contests, key = { it.optString("id") }) { contest ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(contest.optString("title"), fontWeight = FontWeight.Bold)
                    Text(contest.optString("status"))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { selectedContest = contest.optString("id") }) { Text("Entries") }
                        if (contest.optString("status") == "draft") {
                            TextButton(enabled = !busy, onClick = {
                                editingContestId = contest.optString("id")
                                title = contest.optString("title")
                                description = contest.optString("description")
                                rules = contest.optString("rules")
                                category = contest.optString("category")
                                university = contest.optString("university").takeUnless { it == "null" }.orEmpty()
                                cover = contest.optString("banner_url").takeUnless { it == "null" }.orEmpty()
                                coverChoice = null
                                registrationStart = contest.optString("registration_opens_at")
                                registrationEnd = contest.optString("registration_closes_at")
                                votingStart = contest.optString("voting_opens_at")
                                votingEnd = contest.optString("voting_closes_at")
                                resultsDate = contest.optString("results_at")
                                prizeOne = contest.optInt("prize_first").toString()
                                prizeTwo = contest.optInt("prize_second").toString()
                                prizeThree = contest.optInt("prize_third").toString()
                                maxEntries = if (contest.isNull("max_entries")) ""
                                    else contest.optInt("max_entries").toString()
                                autoApprove = contest.optBoolean("auto_approve")
                                scope.launch { listState.animateScrollToItem(0) }
                            }) { Text("Edit") }
                        }
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
                    }
                    // Separate critical actions from the regular actions so a narrow
                    // Android screen never has four buttons competing in one row.
                    if (contest.optString("status") in listOf("published", "paused")) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
        item {
            HorizontalDivider()
            Text("Private media maintenance", style = MaterialTheme.typography.titleLarge)
            Text("Safe audit only. Shows uploads at least 72 hours old that have no application reference. This screen does not delete files.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled = !cleanupAuditBusy, onClick = {
                cleanupAuditBusy = true
                scope.launch {
                    api.adminOrphanMedia()
                        .onSuccess { cleanupPreview = it }
                        .onFailure { message = it.message ?: "Cannot inspect private uploads." }
                    cleanupAuditBusy = false
                }
            }) { Text(if (cleanupAuditBusy) "Checking…" else "Inspect unreferenced media") }
            cleanupPreview?.let { report ->
                val candidates = report.optJSONArray("objects")?.contestObjects().orEmpty()
                Text(candidates.size.toString() + " candidate upload(s) in first 50 examined")
                candidates.forEach { candidate ->
                    Text(candidate.optString("path") + " · " +
                        candidate.optString("created_at").take(10),
                        style = MaterialTheme.typography.bodySmall)
                }
                Text("Deletion remains disabled until storage-safe cleanup is staged and verified.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        if (selectedContest != null) item {
            HorizontalDivider()
            val activeContest = contests.firstOrNull { it.optString("id") == selectedContest }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Managing: " + (activeContest?.optString("title") ?: "Selected contest"),
                    modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                TextButton(onClick = { selectedContest = null }) { Text("Close entries") }
            }
            Text("Contestant appeals", style = MaterialTheme.typography.titleLarge)
            Text("A single appeal is allowed for a rejected or disqualified entry. Accepting reinstates the applicant through audited moderation; denying preserves the decision.",
                style = MaterialTheme.typography.bodySmall)
            appealReviewError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { revision++ }) { Text("Retry appeals") }
            }
            if (appeals.isEmpty() && appealReviewError == null && !entriesLoading) {
                Text("No pending appeals.", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = appealReviewReason,
                onValueChange = { appealReviewReason = it.take(300) },
                label = { Text("Reason for appeal decision (8–300 characters)") },
                minLines = 2, modifier = Modifier.fillMaxWidth()
            )
        }
        items(appeals, key = { "appeal-" + it.optString("id") }) { appeal ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("@" + appeal.optString("username") + " · Pending appeal",
                        fontWeight = FontWeight.SemiBold)
                    Text(appeal.optString("statement"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("accept", "deny").forEach { action ->
                            TextButton(
                                enabled = !busy && appealReviewReason.trim().length in 8..300,
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        api.adminDecideAppeal(appeal.optString("id"), action,
                                            appealReviewReason)
                                            .onSuccess {
                                                message = "Appeal " + it.optString("status") +
                                                    "; contestant notified."
                                                appealReviewReason = ""
                                                revision++
                                            }.onFailure {
                                                message = it.message ?: "Appeal review failed."
                                            }
                                        busy = false
                                    }
                                }
                            ) { Text(action.replaceFirstChar { it.uppercase() }) }
                        }
                    }
                }
            }
        }
        if (selectedContest != null) item {
            HorizontalDivider()
            Text("Contest applications", style = MaterialTheme.typography.titleLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("All", "Pending", "Approved", "Rejected", "Disqualified")) { status ->
                    FilterChip(selected = applicantFilter == status,
                        onClick = { applicantFilter = status },
                        label = { Text(status) })
                }
            }
            val visibleCount = entries.count {
                applicantFilter == "All" || it.optString("status")
                    .equals(applicantFilter, ignoreCase = true)
            }
            Text("$visibleCount matching applications",
                style = MaterialTheme.typography.bodySmall)
            if (entriesLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            entriesError?.let { failure ->
                Text(failure, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { revision++ }) { Text("Retry applications") }
            }
        }
        if (selectedContest != null) item {
            val stats = insights
            Text("Share and voting analytics",style=MaterialTheme.typography.titleLarge)
            insightsError?.let { failure ->
                Text(failure, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { revision++ }) { Text("Retry analytics") }
            }
            if (stats == null) {
                if (insightsError == null) Text("Loading contest metrics…")
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
        items(entries.filter {
            applicantFilter == "All" ||
                it.optString("status").equals(applicantFilter, ignoreCase = true)
        }, key = { it.optString("id") }) { entry ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("@" + entry.optString("username") + " · " + entry.optString("status"),
                        fontWeight = FontWeight.SemiBold)
                    Text(entry.optString("statement"))
                    TextButton(enabled = !busy, onClick = {
                        reviewHistoryName = entry.optString("username")
                        reviewHistoryEntry = entry.optString("id")
                    }) { Text("Review history") }
                    val media = entry.optString("media_url")
                    if (media.startsWith("https://")) {
                        BlinkContestApplicantMediaCard(media) {
                            scope.launch {
                                api.adminMediaPreview(media)
                                    .onSuccess { previewEntryMedia = it }
                                    .onFailure { message = it.message ?: "Unable to preview private media." }
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf("approve", "reject", "disqualify")
                            .filter { it != entry.optString("status") && !(it == "approve" && entry.optString("status") == "approved") }
                            .forEach { action ->
                            TextButton(enabled = !busy, onClick = {
                                if (action != "approve") {
                                    pendingEntryReason = ""
                                    pendingEntryError = null
                                    pendingEntryDecision = entry.optString("id") to action
                                } else {
                                    busy = true
                                    scope.launch {
                                        api.adminReview(entry.optString("id"), action,
                                            "Application meets contest eligibility rules")
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
    SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
    reviewHistoryEntry?.let { entryId ->
        BlinkContestReviewHistoryDialog(
            username = reviewHistoryName,
            history = reviewHistoryRows,
            total = reviewHistoryTotal,
            busy = reviewHistoryBusy,
            error = reviewHistoryError,
            onDismiss = { reviewHistoryEntry = null },
            onRetry = {
                scope.launch {
                    reviewHistoryBusy = true
                    api.adminEntryHistory(entryId)
                        .onSuccess { response ->
                            reviewHistoryRows = response.optJSONArray("history")
                                ?.contestObjects().orEmpty()
                            reviewHistoryTotal = response.optInt("total")
                            reviewHistoryError = null
                        }.onFailure {
                            reviewHistoryError = it.message ?: "Unable to refresh history."
                        }
                    reviewHistoryBusy = false
                }
            }
        )
    }
    previewEntryMedia?.let { media ->
        AlertDialog(
            onDismissRequest = { previewEntryMedia = null },
            title = { Text("Application media") },
            text = {
                when (contestMediaExtension(media)) {
                    "jpg", "jpeg", "png", "webp" -> AsyncImage(
                        model = media,
                        contentDescription = "Contest application photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(280.dp)
                    )
                    "mp4" -> BlinkContestVideoPreview(
                        media, Modifier.fillMaxWidth().height(280.dp)
                    )
                    else -> Text("This media format cannot be previewed.")
                }
            },
            confirmButton = {
                TextButton(onClick = { previewEntryMedia = null }) { Text("Close") }
            }
        )
    }
    pendingEntryDecision?.let { (entryId, action) ->
        AlertDialog(
            onDismissRequest = {
                if (!busy) { pendingEntryDecision = null; pendingEntryReason = "" }
            },
            title = { Text(if (action == "reject") "Reject application?" else "Disqualify contestant?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("State a specific reason. The contestant will receive it in a notification, and the decision will be audited.")
                    OutlinedTextField(
                        value = pendingEntryReason,
                        onValueChange = { pendingEntryReason = it.take(300) },
                        label = { Text("Reason (8–300 characters)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    pendingEntryError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && pendingEntryReason.trim().length in 8..300, onClick = {
                    busy = true
                    scope.launch {
                        api.adminReview(entryId, action, pendingEntryReason)
                            .onSuccess {
                                message = "Application updated."
                                pendingEntryDecision = null
                                pendingEntryReason = ""
                                pendingEntryError = null
                                revision++
                            }
                            .onFailure {
                                pendingEntryError = it.message ?: "Review failed. Try again."
                                message = pendingEntryError.orEmpty()
                            }
                        busy = false
                    }
                }) { Text("Confirm " + action) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = {
                    pendingEntryDecision = null
                    pendingEntryReason = ""
                }) { Text("Go back") }
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

/** Reusable audited moderation timeline, captured by emulator screenshot tests. */
@Composable
internal fun BlinkContestReviewHistoryDialog(
    username: String,
    history: List<JSONObject>,
    total: Int,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review history · @" + username) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (busy) CircularProgressIndicator()
                error?.let { fault ->
                    Text(fault, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
                if (!busy && error == null) {
                    Text("$total recorded decision(s) · displaying the latest 30",
                        style = MaterialTheme.typography.bodySmall)
                    if (history.isEmpty()) Text("No moderation decisions recorded yet.")
                    history.forEach { audit ->
                        HorizontalDivider()
                        Text(
                            audit.optString("previous_status").replaceFirstChar { it.uppercase() } +
                                " → " + audit.optString("next_status").replaceFirstChar { it.uppercase() },
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(audit.optString("reason"))
                        Text("By @" + audit.optString("actor_username") +
                            " · " + audit.optString("created_at").replace('T',' ').take(19) + " UTC",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/** Readable application-media preview, shared by live admin UI and emulator visual tests. */
@Composable
internal fun BlinkContestApplicantMediaCard(media: String, onPreview: () -> Unit) {
    val isVideo = contestMediaExtension(media) == "mp4"
    val isPrivate = media.contains("/functions/v1/blink-contest-private-media/")
    Surface(
        onClick = onPreview,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(13.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isVideo) {
                Surface(
                    color = Color(0xFF19293A), shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(76.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayCircleOutline,
                            contentDescription = "Video application",
                            tint = Color(0xFFB1D6FF), modifier = Modifier.size(32.dp))
                    }
                }
            } else if (isPrivate) {
                Box(
                    modifier = Modifier.size(76.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF19293A)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Private", color = Color(0xFFB1D6FF),
                        style = MaterialTheme.typography.labelMedium)
                }
            } else {
                var imageLoaded by remember(media) { mutableStateOf(false) }
                var imageFailed by remember(media) { mutableStateOf(false) }
                Box(
                    modifier = Modifier.size(76.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF27303C)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = media,
                        contentDescription = if (imageLoaded) "Loaded application photo"
                            else "Submitted application photo",
                        contentScale = ContentScale.Crop,
                        onSuccess = { imageLoaded = true; imageFailed = false },
                        onError = { imageLoaded = false; imageFailed = true },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (imageFailed) {
                        Text("Photo unavailable", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(5.dp))
                    } else if (!imageLoaded) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp)
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(if (isVideo) "View application video" else "View application photo",
                    fontWeight = FontWeight.SemiBold)
                Text(if (isPrivate) "Private until approved · Tap for secure preview"
                    else "Inspect before approving",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
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
