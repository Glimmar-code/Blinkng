package com.blinkng.desktop.ui

import androidx.compose.foundation.clickable
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
    var tab by remember { mutableStateOf("Ongoing") }
    var statement by remember { mutableStateOf("") }
    var mediaUrl by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }

    LaunchedEffect(revision) {
        runCatching { api.contestList() }.onSuccess { contests = it.rows() }
            .onFailure { message = it.message.orEmpty() }
    }
    LaunchedEffect(selected, revision) {
        detail = null
        selected?.let { id ->
            runCatching { api.contestDetail(id) }.onSuccess { detail = it }
                .onFailure { message = it.message.orEmpty() }
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
                listOf("Ongoing", "Upcoming", "Winners").forEach { t ->
                    FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t) })
                }
                TextButton(onClick = { state.selectedRoute = "people" }) { Text("People discovery") }
            }
            val rows = contests.filter { BlinkContestPolicy.section(it.optString("status")) == tab }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (rows.isEmpty()) item { Text("No contests in this section yet.") }
                items(rows, key = { it.optString("id") }) { c ->
                    ElevatedCard(Modifier.fillMaxWidth().clickable {
                        selected = c.optString("id"); statement = ""; mediaUrl = ""; message = ""
                    }) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(c.optString("title"), style = MaterialTheme.typography.titleLarge)
                            Text(BlinkContestPolicy.statusLabel(c.optString("status")))
                            Text(c.optString("description"), maxLines = 2)
                            Text("1st " + prize(c.optInt("prize_first")) +
                                " · 2nd " + prize(c.optInt("prize_second")) +
                                " · 3rd " + prize(c.optInt("prize_third")), fontWeight = FontWeight.Bold)
                            Text(c.optInt("contestants").toString() + " contestants · Free entry · Free voting")
                        }
                    }
                }
            }
        } else {
            val d = detail
            if (d == null) CircularProgressIndicator() else {
                val c = d.optJSONObject("contest") ?: JSONObject()
                val status = c.optString("status")
                val own = d.optString("my_entry_id", "").takeUnless { it == "null" }.orEmpty()
                val voted = d.optString("my_vote_entry_id", "").takeUnless { it == "null" }.orEmpty()
                val leaders = d.optJSONArray("leaderboard")?.rows().orEmpty()
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text(c.optString("title"), style = MaterialTheme.typography.headlineSmall)
                        Text(BlinkContestPolicy.statusLabel(status))
                        Text(c.optString("description"))
                        Text("1st " + prize(c.optInt("prize_first")) +
                            " · 2nd " + prize(c.optInt("prize_second")) +
                            " · 3rd " + prize(c.optInt("prize_third")), fontWeight = FontWeight.Bold)
                        Text("Voting closes: " + dt(c.optString("voting_closes_at")))
                        if (c.optString("rules").isNotBlank()) Text("Rules: " + c.optString("rules"))
                        Text("Your rank: " + if (d.isNull("my_rank")) "Not ranked yet"
                            else "#" + d.optInt("my_rank"))
                        if (own.isNotBlank()) Text("Your application: " + d.optString("my_entry_status"))
                        if (voted.isNotBlank()) Text("You've already used your free vote.")
                    }
                    if (status == "registration" && own.isBlank()) item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(statement, { statement = it.take(500) },
                                label = { Text("Why should people vote for you?") },
                                modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(mediaUrl, { mediaUrl = it },
                                label = { Text("Optional entry photo/Reel URL") },
                                modifier = Modifier.fillMaxWidth())
                            Button(enabled = !busy && BlinkContestPolicy.validStatement(statement), onClick = {
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
                    item { Text("Top 20 · " + d.optInt("total_ranked") + " ranked contestants",
                        style = MaterialTheme.typography.titleLarge) }
                    if (leaders.isEmpty()) item { Text("No approved contestants yet.") }
                    items(leaders, key = { it.optString("id") }) { entry ->
                        ElevatedCard {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("#" + entry.optInt("rank") + "  @" + entry.optString("username"),
                                    modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Text(entry.optInt("votes").toString() + " votes")
                                Spacer(Modifier.width(16.dp))
                                Button(enabled = !busy && BlinkContestPolicy.canVote(
                                    status, voted, own, entry.optString("id")), onClick = {
                                    busy = true
                                    scope.launch {
                                        runCatching { api.contestVote(c.optString("id"), entry.optString("id")) }
                                            .onSuccess { message = "Vote recorded."; revision++ }
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
}

@Composable
fun BlinkDesktopContestAdminScreen(state: DesktopAppState) {
    val api = remember(state.client) { DesktopRpcActions(state.client) }
    val scope = rememberCoroutineScope()
    var contests by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var entries by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var inspectId by remember { mutableStateOf<String?>(null) }
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
    LaunchedEffect(inspectId, revision) {
        entries = emptyList()
        inspectId?.let { id ->
            runCatching { api.adminContestEntries(id) }.onSuccess { entries = it.rows() }
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
                                    runCatching { api.adminContestAction(contest.optString("id"), "complete") }
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
            Text("Contest applications", style = MaterialTheme.typography.titleLarge)
            entries.forEach { entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("@" + entry.optString("username") + " · " + entry.optString("status"),
                        modifier = Modifier.weight(1f))
                    listOf("approve", "reject", "disqualify").forEach { action ->
                        TextButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                runCatching { api.adminContestReview(entry.optString("id"), action) }
                                    .onSuccess { message = "Entry reviewed."; revision++ }
                                    .onFailure { message = it.message.orEmpty() }
                                busy = false
                            }
                        }) { Text(action) }
                    }
                }
                Text(entry.optString("statement"))
            }
        }
    }
}
