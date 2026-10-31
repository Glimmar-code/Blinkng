package com.blinkng.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blinkng.desktop.DesktopAppState
import com.blinkng.desktop.data.DesktopRpcActions
import com.blinkng.shared.BlinkContestPolicy
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private fun JSONArray.rows(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
private fun prize(v: Int) = "₦" + "%,d".format(v)
private fun dt(v: String) = v.replace('T', ' ').take(16)

@Composable
fun BlinkDesktopContestScreen(state: DesktopAppState) {
    val api = remember(state.client) { DesktopRpcActions(state.client) }
    val scope = rememberCoroutineScope()
    var contests by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf("Ongoing") }
    var statement by remember { mutableStateOf("") }
    var mediaUrl by remember { mutableStateOf("") }
    var entryFile by remember(selected) { mutableStateOf<java.io.File?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var ownCandidate by remember { mutableStateOf<JSONObject?>(null) }
    var sharingCandidate by remember { mutableStateOf<JSONObject?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchOffset by remember { mutableIntStateOf(0) }
    var searchResults by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var searchTotal by remember { mutableIntStateOf(0) }
    var searchBusy by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf("") }

    LaunchedEffect(selected) {
        searchOpen = false
        searchQuery = ""
        searchOffset = 0
        searchResults = emptyList()
        searchError = ""
    }
    LaunchedEffect(selected, searchOpen, searchQuery, searchOffset, revision) {
        val id = selected
        if (!searchOpen || id == null || searchQuery.trim().length == 1) {
            searchResults = emptyList()
            searchTotal = 0
            searchBusy = false
        } else {
            searchBusy = true
            searchError = ""
            delay(300)
            runCatching { api.contestSearchCandidates(id, searchQuery, searchOffset) }
                .onSuccess {
                    searchResults = it.optJSONArray("entries")?.rows().orEmpty()
                    searchTotal = it.optInt("total")
                }.onFailure {
                    searchResults = emptyList()
                    searchError = it.message ?: "Cannot search contestants."
                }
            searchBusy = false
        }
    }
    LaunchedEffect(selected, detail?.optString("my_entry_id"), revision) {
        ownCandidate = null
        val contest = selected
        val mine = detail?.optString("my_entry_id")?.takeUnless { it == "null" }.orEmpty()
        if (contest != null && mine.isNotBlank() && detail?.optString("my_entry_status") == "approved") {
            runCatching { api.contestCandidate(contest, mine) }.onSuccess { ownCandidate = it }
        }
    }
    LaunchedEffect(revision) {
        runCatching { api.contestList() }.onSuccess { contests = it.rows() }
            .onFailure { message = it.message.orEmpty() }
    }
    LaunchedEffect(selected, revision) {
        detail = null
        detailError = null
        selected?.let { id ->
            runCatching { api.contestDetail(id) }.onSuccess { detail = it }
                .onFailure { detailError = it.message ?: "Unable to load this contest." }
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected != null) TextButton(onClick = { selected = null }) { Text("Back") }
            Text("Contest", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { revision++ }) { Text("Refresh") }
        }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        if (selected == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("Ongoing", "Upcoming", "Completed").forEach { t ->
                    FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t) })
                }
                TextButton(onClick = { state.selectedRoute = "people" }) { Text("People discovery") }
            }
            val rows = contests.filter { BlinkContestPolicy.section(it.optString("status")) == tab }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (rows.isEmpty()) item { Text("No contests in this section yet.") }
                items(rows, key = { it.optString("id") }) { c ->
                    Surface(
                        color = Color(0xFF0B1626), shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().clickable {
                            selected = c.optString("id"); statement = ""; mediaUrl = ""; message = ""
                        }
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            val banner = c.optString("banner_url")
                            if (banner.startsWith("https://")) AsyncImage(
                                model = banner, contentDescription = "Contest cover",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(105.dp).clip(RoundedCornerShape(12.dp))
                            )
                            Column(Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("BLINK CONTEST  ·  FREE ENTRY / FREE VOTING",
                                    color = Color(0xFF69B9FF), fontSize = 10.sp)
                                Text(c.optString("title"), color = Color.White,
                                    style = MaterialTheme.typography.titleLarge)
                                Text(c.optString("description"), color = Color(0xFFCDD9EB), maxLines = 2)
                                Text("🥇 " + prize(c.optInt("prize_first")) +
                                    "    🥈 " + prize(c.optInt("prize_second")) +
                                    "    🥉 " + prize(c.optInt("prize_third")),
                                    color = Color.White, fontWeight = FontWeight.Bold)
                                Text(c.optInt("contestants").toString() +
                                    " contestants · View contest →",
                                    color = Color(0xFF8BCBFF), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        } else {
            val d = detail
            if (d == null) {
                if (detailError != null) {
                    Text(detailError.orEmpty(), color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { revision++ }) { Text("Retry contest") }
                } else CircularProgressIndicator()
            } else {
                val c = d.optJSONObject("contest") ?: JSONObject()
                val status = c.optString("status")
                val own = d.optString("my_entry_id", "").takeUnless { it == "null" }.orEmpty()
                val voted = d.optString("my_vote_entry_id", "").takeUnless { it == "null" }.orEmpty()
                val leaders = d.optJSONArray("leaderboard")?.rows().orEmpty()
                val winners = d.optJSONArray("winners")?.rows().orEmpty()
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text(c.optString("title"), style = MaterialTheme.typography.headlineSmall)
                        Text(BlinkContestPolicy.statusLabel(status))
                        if (status == "cancelled") {
                            Text(
                                "This contest was cancelled after publication. Applications and voting are closed; no prizes will be awarded.",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(c.optString("description"))
                        Text("1st " + prize(c.optInt("prize_first")) +
                            " · 2nd " + prize(c.optInt("prize_second")) +
                            " · 3rd " + prize(c.optInt("prize_third")), fontWeight = FontWeight.Bold)
                        Text("Voting closes: " + dt(c.optString("voting_closes_at")))
                        if (c.optString("rules").isNotBlank()) Text("Rules: " + c.optString("rules"))
                        if (status == "completed") {
                            Text("Official winners",style=MaterialTheme.typography.titleLarge)
                            if (winners.isEmpty()) Text("No prize winners were recorded.")
                            winners.forEach { winner ->
                                Surface(color = Color(0xFF0B1626),
                                    shape = RoundedCornerShape(12.dp)) {
                                    Row(Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text(when (winner.optInt("place")) {
                                            1 -> "🥇"; 2 -> "🥈"; else -> "🥉" }, fontSize = 25.sp)
                                        DesktopContestAvatar(winner.optString("avatar_url"),
                                            winner.optString("username"), 42.dp)
                                        Column {
                                            Text("@" + winner.optString("username"),
                                                color = Color.White, fontWeight = FontWeight.Bold)
                                            Text(prize(winner.optInt("prize_amount")),
                                                color = Color(0xFF8BCBFF))
                                            Text("Payment: " + winner.optString("payment_status") +
                                                " (admin-recorded)", color = Color(0xFFB7C7DA),
                                                fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                        Text("Your rank: " + if (d.isNull("my_rank")) "Not ranked yet"
                            else "#" + d.optInt("my_rank") +
                                DesktopContestRankMovement.label(d,"my_rank_delta"))
                        if (own.isNotBlank()) {
                            val current = d.optString("my_entry_status")
                            Text("Your application: " + current)
                            if (current == "rejected" || current == "disqualified") {
                                Text("Check Notifications for the moderator's reason.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (voted.isNotBlank()) Text("You've already used your free vote.")
                    }
                    if (status == "registration" && own.isBlank()) item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(statement, { statement = it.take(500) },
                                label = { Text("Why should people vote for you?") },
                                modifier = Modifier.fillMaxWidth())
                            Text(
                                if (mediaUrl.isBlank()) "No private entry media uploaded yet."
                                else "Private photo or video uploaded and ready to submit.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text("Contestant media stays private until approved. Choose a file and upload it before applying.", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(enabled = !busy, onClick = {
                                scope.launch {
                                    entryFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        var picked: java.io.File? = null
                                        javax.swing.SwingUtilities.invokeAndWait {
                                            val chooser = javax.swing.JFileChooser().apply {
                                                fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Contest photo or video", "jpg", "jpeg", "png", "webp", "mp4")
                                                isAcceptAllFileFilterUsed = false
                                            }
                                            if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) picked = chooser.selectedFile
                                        }
                                        picked
                                    }
                                }
                            }) { Text("Choose photo or video") }
                            entryFile?.let { file ->
                                Text(file.name)
                                OutlinedButton(enabled = !busy, onClick = {
                                    busy = true
                                    scope.launch {
                                        try {
                                            mediaUrl = api.uploadContestMedia(file)
                                            message = "Private media uploaded. Submit your application to finish."
                                            entryFile = null
                                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                        catch (failure: Exception) { message = failure.message ?: "Media upload failed. Try again." }
                                        finally { busy = false }
                                    }
                                }) { Text("Upload public preview") }
                            }
                            Button(enabled = !busy && BlinkContestPolicy.canApply(statement, false, mediaUrl), onClick = {
                                busy = true
                                scope.launch {
                                    runCatching { api.contestApply(c.optString("id"), statement, mediaUrl) }
                                        .onSuccess { message = "Application submitted."; revision++ }
                                        .onFailure { message = it.message.orEmpty() }
                                    busy = false
                                }
                            }) { Text("Apply for free") }
                        }
                    }
                    if (ownCandidate != null && leaders.none { it.optString("id") == own }) item {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Your position: #" + ownCandidate!!.optInt("rank"),
                                    modifier = Modifier.weight(1f))
                                OutlinedButton(onClick = { sharingCandidate = ownCandidate }) {
                                    Text("Share my flyer")
                                }
                            }
                        }
                    }
                    if (leaders.isNotEmpty()) item {
                        Text("Top three leaders", style = MaterialTheme.typography.titleLarge)
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Bottom) {
                            listOf(1, 0, 2).forEach { index ->
                                val entry = leaders.getOrNull(index)
                                if (entry == null) Spacer(Modifier.weight(1f))
                                else {
                                    Column(
                                        Modifier.weight(1f).height(
                                            if (index == 0) 140.dp else 165.dp
                                        ).clip(RoundedCornerShape(14.dp))
                                            .background(Color(0xFF102843)).padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        Text(when(entry.optInt("rank")) {
                                            1 -> "🥇"; 2 -> "🥈"; else -> "🥉" })
                                        DesktopContestAvatar(entry.optString("avatar_url"),
                                            entry.optString("username"), 48.dp)
                                        Text("@" + entry.optString("username"),
                                            color = Color.White, maxLines = 1)
                                        Text(entry.optInt("votes").toString() + " votes" + DesktopContestRankMovement.label(entry),
                                            color = Color(0xFF9ACFFF), fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        OutlinedButton(onClick = { searchOpen = !searchOpen },
                            modifier = Modifier.fillMaxWidth()) {
                            Text(if (searchOpen) "Hide contestant directory"
                                else "Find contestants beyond the Top 20")
                        }
                    }
                    if (searchOpen) {
                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it.take(60); searchOffset = 0 },
                                label = { Text("Search name or username") },
                                singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                            Text("Leave blank to browse all approved contestants.",
                                style = MaterialTheme.typography.bodySmall)
                            if (searchQuery.trim().length == 1) {
                                Text("Enter at least two characters, or clear to browse.",
                                    color = MaterialTheme.colorScheme.error)
                            } else if (searchBusy) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            } else {
                                Text("$searchTotal approved results")
                            }
                            if (searchError.isNotEmpty()) {
                                Text(searchError, color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { revision++ }) { Text("Retry search") }
                            }
                        }
                        items(searchResults, key = { "directory-" + it.optString("id") }) { candidate ->
                            ElevatedCard {
                                Row(Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text("#" + candidate.optInt("rank"),
                                        fontWeight = FontWeight.Bold)
                                    DesktopContestAvatar(candidate.optString("avatar_url"),
                                        candidate.optString("username"), 42.dp)
                                    Column(Modifier.weight(1f)) {
                                        Text("@" + candidate.optString("username"),
                                            fontWeight = FontWeight.SemiBold)
                                        Text(candidate.optInt("votes").toString() + " votes",
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            runCatching { api.contestCandidate(c.optString("id"), candidate.optString("id")) }
                                                .onSuccess { sharingCandidate = it }
                                                .onFailure { message = it.message.orEmpty() }
                                        }
                                    }) { Text("Share") }
                                    Button(enabled = !busy && BlinkContestPolicy.canVote(
                                        status, voted, own, candidate.optString("id")),
                                        onClick = {
                                            busy = true
                                            scope.launch {
                                                runCatching { api.contestVote(c.optString("id"), candidate.optString("id")) }
                                                    .onSuccess {
                                                        message = if (it.optString("vote_status") == "under_review")
                                                            "Free vote received; pending integrity review."
                                                        else "Your free vote was counted."
                                                        revision++
                                                    }
                                                    .onFailure { message = it.message.orEmpty() }
                                                busy = false
                                            }
                                        }) { Text("Vote") }
                                }
                            }
                        }
                        if (!searchBusy && searchError.isEmpty() && searchResults.isEmpty() &&
                            searchQuery.trim().length != 1) item {
                            Text("No approved contestants found.")
                        }
                        if (searchTotal > 20) item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(enabled = !searchBusy && searchOffset > 0,
                                    onClick = { searchOffset = (searchOffset - 20).coerceAtLeast(0) }) {
                                    Text("Previous")
                                }
                                Text("Page " + (searchOffset / 20 + 1),
                                    modifier = Modifier.weight(1f))
                                OutlinedButton(
                                    enabled = !searchBusy && searchOffset + 20 < searchTotal &&
                                        searchOffset < 10000,
                                    onClick = { searchOffset += 20 }) { Text("Next") }
                            }
                        }
                    }
                    item { Text("Top 20 · " + d.optInt("total_ranked") + " ranked contestants",
                        style = MaterialTheme.typography.titleLarge) }
                    if (leaders.isEmpty()) item { Text("No approved contestants yet.") }
                    items(leaders, key = { it.optString("id") }) { entry ->
                        ElevatedCard {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("#" + entry.optInt("rank"), fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(10.dp))
                                DesktopContestAvatar(entry.optString("avatar_url"),
                                    entry.optString("username"), 42.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("@" + entry.optString("username"),
                                    modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Text(entry.optInt("votes").toString() + " votes")
                                Spacer(Modifier.width(16.dp))
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        runCatching { api.contestCandidate(c.optString("id"), entry.optString("id")) }
                                            .onSuccess { sharingCandidate = it }
                                            .onFailure { message = it.message.orEmpty() }
                                    }
                                }) { Text("Share") }
                                Spacer(Modifier.width(10.dp))
                                Button(enabled = !busy && BlinkContestPolicy.canVote(
                                    status, voted, own, entry.optString("id")), onClick = {
                                    busy = true
                                    scope.launch {
                                        runCatching { api.contestVote(c.optString("id"), entry.optString("id")) }
                                            .onSuccess {
                                                message = if (it.optString("vote_status") == "under_review")
                                                    "Free vote received; pending integrity review."
                                                else "Your free vote was counted."
                                                revision++
                                            }
                                            .onFailure { message = it.message.orEmpty() }
                                        busy = false
                                    }
                                }) { Text("Vote") }
                            }
                            Text(entry.optString("statement"), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                }
            }
        }
    }
    sharingCandidate?.let { candidate ->
        BlinkDesktopContestShareDialog(
            candidate, onDismiss = { sharingCandidate = null },
            onShareRecorded = { channel ->
                scope.launch {
                    runCatching {
                        api.contestRecordShare(candidate.optString("contest_id"),
                            candidate.optString("id"), channel)
                    }
                }
            }
        )
    }
}

@Composable
fun BlinkDesktopContestAdminScreen(state: DesktopAppState) {
    val api = remember(state.client) { DesktopRpcActions(state.client) }
    val scope = rememberCoroutineScope()
    var contests by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var entries by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var inspectId by remember { mutableStateOf<String?>(null) }
    var insights by remember { mutableStateOf<JSONObject?>(null) }
    var reviewNote by remember { mutableStateOf("") }
    var applicationReviewReason by remember { mutableStateOf("") }
    var historyEntry by remember { mutableStateOf<String?>(null) }
    var historyUsername by remember { mutableStateOf("") }
    var historyRows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var historyTotal by remember { mutableIntStateOf(0) }
    var historyBusy by remember { mutableStateOf(false) }
    var historyError by remember { mutableStateOf("") }
    var paymentReference by remember { mutableStateOf("") }
    var paymentNote by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var rules by remember { mutableStateOf("") }
    var regStart by remember { mutableStateOf("") }
    var regEnd by remember { mutableStateOf("") }
    var voteStart by remember { mutableStateOf("") }
    var voteEnd by remember { mutableStateOf("") }
    var results by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("0") }
    var second by remember { mutableStateOf("0") }
    var third by remember { mutableStateOf("0") }

    LaunchedEffect(revision) {
        runCatching { api.adminContestList() }.onSuccess { contests = it.rows() }
            .onFailure { message = it.message.orEmpty() }
    }
    LaunchedEffect(inspectId) { historyEntry = null }
    LaunchedEffect(historyEntry, revision) {
        historyRows = emptyList()
        historyTotal = 0
        historyError = ""
        val id = historyEntry
        if (id != null) {
            historyBusy = true
            runCatching { api.adminContestEntryHistory(id) }
                .onSuccess {
                    historyRows = it.optJSONArray("history")?.rows().orEmpty()
                    historyTotal = it.optInt("total")
                }
                .onFailure { historyError = it.message ?: "Review history unavailable." }
            historyBusy = false
        }
    }
    LaunchedEffect(inspectId, revision) {
        entries = emptyList()
        insights = null
        inspectId?.let { id ->
            runCatching { api.adminContestEntries(id) }.onSuccess { entries = it.rows() }
                .onFailure { message = it.message.orEmpty() }
            runCatching { api.adminContestInsights(id) }.onSuccess { insights = it }
                .onFailure { message = it.message.orEmpty() }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Contest administration", style = MaterialTheme.typography.headlineMedium)
        Text("Free entry and free voting. Enter ISO dates with timezone, e.g. 2026-11-01T09:00:00+01:00")
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        Text("Create a contest", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(rules, { rules = it }, label = { Text("Rules and eligibility") }, modifier = Modifier.fillMaxWidth())
        listOf(
            "Applications open" to regStart, "Applications close" to regEnd,
            "Voting opens" to voteStart, "Voting closes" to voteEnd, "Results announced" to results
        ).forEach { (label, value) ->
            OutlinedTextField(value, {
                when(label) {
                    "Applications open" -> regStart = it
                    "Applications close" -> regEnd = it
                    "Voting opens" -> voteStart = it
                    "Voting closes" -> voteEnd = it
                    else -> results = it
                }
            }, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(first, { first = it }, label = { Text("1st prize ₦") }, modifier = Modifier.weight(1f))
            OutlinedTextField(second, { second = it }, label = { Text("2nd prize ₦") }, modifier = Modifier.weight(1f))
            OutlinedTextField(third, { third = it }, label = { Text("3rd prize ₦") }, modifier = Modifier.weight(1f))
        }
        Button(enabled = !busy && title.length >= 4, onClick = {
            busy = true
            val data = JSONObject().put("title", title).put("description", description)
                .put("rules", rules).put("category", "General")
                .put("registration_opens_at", regStart).put("registration_closes_at", regEnd)
                .put("voting_opens_at", voteStart).put("voting_closes_at", voteEnd)
                .put("results_at", results).put("prize_first", first.toIntOrNull() ?: 0)
                .put("prize_second", second.toIntOrNull() ?: 0)
                .put("prize_third", third.toIntOrNull() ?: 0)
                .put("auto_approve", false)
            scope.launch {
                runCatching { api.adminContestSave(data) }
                    .onSuccess { message = "Contest draft saved."; revision++ }
                    .onFailure { message = it.message.orEmpty() }
                busy = false
            }
        }) { Text("Save draft") }
        HorizontalDivider()
        Text("Manage contests", style = MaterialTheme.typography.titleLarge)
        contests.forEach { contest ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(contest.optString("title") + " · " + contest.optString("status"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { inspectId = contest.optString("id") }) { Text("Applications") }
                        val action = when(contest.optString("status")) {
                            "draft" -> "publish"
                            "published" -> "pause"
                            "paused" -> "resume"
                            else -> ""
                        }
                        if (action.isNotBlank()) TextButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                runCatching { api.adminContestAction(contest.optString("id"), action) }
                                    .onSuccess { message = "Contest updated."; revision++ }
                                    .onFailure { message = it.message.orEmpty() }
                                busy = false
                            }
                        }) { Text(action.replaceFirstChar { it.uppercase() }) }
                        if (contest.optString("status") in listOf("published", "paused")) {
                            TextButton(enabled = !busy, onClick = {
                                busy = true
                                scope.launch {
                                    runCatching { api.adminContestFinalize(contest.optString("id")) }
                                        .onSuccess { message = "Final results confirmed."; revision++ }
                                        .onFailure { message = it.message.orEmpty() }
                                    busy = false
                                }
                            }) { Text("Finalize") }
                        }
                    }
                }
            }
        }
        if (inspectId != null) {
            Text("Growth and fraud monitoring",style=MaterialTheme.typography.titleLarge)
            val stat = insights
            if (stat != null) {
                Text("Shares: " + stat.optInt("shares") + " · Unique sharers: " +
                    stat.optInt("unique_sharers"))
                Text("Counted votes: " + stat.optInt("counted_votes") +
                    " · Under review: " + stat.optInt("review_votes") +
                    " · Invalidated: " + stat.optInt("rejected_votes"))
            }
            Text("Share totals represent authenticated share actions, not page visits.",
                style=MaterialTheme.typography.bodySmall)
            Text("Vote integrity review",style=MaterialTheme.typography.titleMedium)
            OutlinedTextField(reviewNote,{reviewNote=it.take(300)},
                label={Text("Reason for approving/invalidating (8+ characters)")},
                modifier=Modifier.fillMaxWidth())
            insights?.optJSONArray("review_queue")?.rows().orEmpty().forEach { vote ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Vote " + vote.optString("id").take(8) + " · " +
                            vote.optString("reason"))
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            listOf("approve","invalidate").forEach { action ->
                                TextButton(enabled=!busy && reviewNote.trim().length>=8,onClick={
                                    busy=true
                                    scope.launch {
                                        runCatching {
                                            api.adminContestVoteReview(vote.optString("id"),action,reviewNote)
                                        }.onSuccess { message="Vote reviewed.";revision++ }
                                            .onFailure { message=it.message.orEmpty() }
                                        busy=false
                                    }
                                }) { Text(action) }
                            }
                        }
                    }
                }
            }
            Text("Prize payment ledger",style=MaterialTheme.typography.titleLarge)
            Text("Manual bookkeeping only; these controls do not transfer funds.",
                style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(paymentReference,{paymentReference=it.take(150)},
                label={Text("External receipt / transfer reference")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(paymentNote,{paymentNote=it.take(400)},
                label={Text("Admin note")},modifier=Modifier.fillMaxWidth())
            insights?.optJSONArray("prizes")?.rows().orEmpty().forEach { row ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("#" + row.optInt("place") + " @" + row.optString("username") +
                            " · " + prize(row.optInt("amount_ngn")) +
                            " · " + row.optString("payment_status"))
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            listOf("processing","failed","paid").forEach { action ->
                                TextButton(enabled=!busy && row.optString("payment_status")!="paid" &&
                                    (action!="paid" || paymentReference.trim().length>=6),onClick={
                                    busy=true
                                    scope.launch {
                                        runCatching { api.adminContestPrize(row.optString("id"),
                                            action,paymentReference,paymentNote) }
                                            .onSuccess { message="Prize record saved, not transferred.";revision++ }
                                            .onFailure { message=it.message.orEmpty() }
                                        busy=false
                                    }
                                }) { Text(action) }
                            }
                        }
                    }
                }
            }
            Text("Contest applications", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = applicationReviewReason,
                onValueChange = { applicationReviewReason = it.take(300) },
                label = { Text("Reason for rejection/disqualification (8–300 characters)") },
                modifier = Modifier.fillMaxWidth(), minLines = 2
            )
            Text("The applicant receives this reason in a notification; every moderation decision is audited.",
                style = MaterialTheme.typography.bodySmall)
            entries.forEach { entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("@" + entry.optString("username") + " · " + entry.optString("status"),
                        modifier = Modifier.weight(1f))
                    listOf("approve", "reject", "disqualify")
                        .filter { action ->
                            val targetStatus = when (action) {
                                "approve" -> "approved"
                                "reject" -> "rejected"
                                else -> "disqualified"
                            }
                            entry.optString("status") != targetStatus
                        }.forEach { action ->
                        TextButton(enabled = !busy &&
                            (action == "approve" || applicationReviewReason.trim().length in 8..300),
                            onClick = {
                                busy = true
                                val reason = if (action == "approve")
                                    "Application meets contest eligibility rules"
                                else applicationReviewReason.trim()
                                scope.launch {
                                    runCatching {
                                        api.adminContestReview(entry.optString("id"), action, reason)
                                    }.onSuccess {
                                        message = "Entry reviewed; applicant notified."
                                        applicationReviewReason = ""
                                        revision++
                                    }.onFailure { message = it.message.orEmpty() }
                                    busy = false
                                }
                            }) { Text(action.replaceFirstChar { it.uppercase() }) }
                    }
                }
                Text(entry.optString("statement"))
                TextButton(enabled = !busy, onClick = {
                    historyUsername = entry.optString("username")
                    historyEntry = entry.optString("id")
                }) { Text("Review history") }
            }
        }
    }
    historyEntry?.let {
        AlertDialog(
            onDismissRequest = { historyEntry = null },
            title = { Text("Review history · @" + historyUsername) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (historyBusy) CircularProgressIndicator()
                    if (historyError.isNotEmpty()) Text(historyError,
                        color = MaterialTheme.colorScheme.error)
                    if (!historyBusy && historyError.isEmpty()) {
                        Text("$historyTotal recorded decisions · newest 30 shown",
                            style = MaterialTheme.typography.bodySmall)
                        if (historyRows.isEmpty()) Text("No moderation decisions recorded.")
                        historyRows.forEach { audit ->
                            HorizontalDivider()
                            Text(audit.optString("previous_status").replaceFirstChar { it.uppercase() } +
                                " → " + audit.optString("next_status").replaceFirstChar { it.uppercase() },
                                fontWeight = FontWeight.SemiBold)
                            Text(audit.optString("reason"))
                            Text("By @" + audit.optString("actor_username") + " · " +
                                audit.optString("created_at").replace('T',' ').take(19) + " UTC",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { historyEntry = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun DesktopContestAvatar(url: String, username: String, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Color(0xFF28415D)),
        contentAlignment = Alignment.Center) {
        if (url.startsWith("https://")) AsyncImage(
            model = url, contentDescription = "Contestant @" + username,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
        ) else Text(username.take(1).uppercase().ifBlank { "B" }, color = Color.White,
            fontWeight = FontWeight.Bold)
    }
}

private object DesktopContestRankMovement {
    fun label(entry: JSONObject, field: String = "rank_delta"): String {
        if (!entry.has(field) || entry.isNull(field)) return ""
        val delta = entry.optInt(field)
        return when {
            delta > 0 -> " ↑" + delta
            delta < 0 -> " ↓" + (-delta)
            else -> " —"
        }
    }
}
