package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.shared.BlinkContestPolicy
import com.example.data.supabase.BlinkContestService
import com.example.sharing.ContestVoteTarget
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

internal fun JSONArray.contestObjects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }
private fun cash(value: Int) = "₦" + NumberFormat.getIntegerInstance(Locale("en", "NG")).format(value)
private fun dateLabel(value: String) = value.replace('T', ' ').take(16)

@Composable
fun BlinkContestScreen(
    onDiscoverPeople: () -> Unit,
    target: ContestVoteTarget? = null,
    onTargetHandled: () -> Unit = {}
) {
    val api = remember { BlinkContestService() }
    val scope = rememberCoroutineScope()
    var contests by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var tab by remember { mutableStateOf("Ongoing") }
    var statement by remember { mutableStateOf("") }
    var mediaUrl by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var invitedEntryId by remember { mutableStateOf<String?>(null) }
    var invitedCandidate by remember { mutableStateOf<JSONObject?>(null) }
    var ownCandidate by remember { mutableStateOf<JSONObject?>(null) }
    var flyerCandidate by remember { mutableStateOf<JSONObject?>(null) }

    LaunchedEffect(target?.contestId, target?.entryId) {
        if (target != null) {
            selectedId = target.contestId
            invitedEntryId = target.entryId
            onTargetHandled()
        }
    }
    LaunchedEffect(selectedId, invitedEntryId, revision) {
        invitedCandidate = null
        val contest = selectedId
        val entry = invitedEntryId
        if (contest != null && entry != null) {
            api.candidate(contest, entry).onSuccess { invitedCandidate = it }
                .onFailure { error = "This contestant is unavailable or hasn't been approved." }
        }
    }
    LaunchedEffect(selectedId, detail?.optString("my_entry_id"), revision) {
        ownCandidate = null
        val contest = selectedId
        val mine = detail?.optString("my_entry_id")?.takeUnless { it == "null" }.orEmpty()
        if (contest != null && mine.isNotBlank() && detail?.optString("my_entry_status") == "approved") {
            api.candidate(contest, mine).onSuccess { ownCandidate = it }
        }
    }

    LaunchedEffect(revision) {
        api.contests().onSuccess { contests = it.contestObjects() }
            .onFailure { error = it.message ?: "Unable to load contests." }
    }
    LaunchedEffect(selectedId, revision) {
        detail = null
        selectedId?.let { id ->
            api.detail(id).onSuccess { detail = it }
                .onFailure { error = it.message ?: "Unable to open contest." }
        }
    }

    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (selectedId != null) TextButton(onClick = { selectedId = null; invitedEntryId = null }) { Text("Back") }
            Text("Contest", Modifier.weight(1f), fontSize = 25.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { revision++ }) { Text("Refresh") }
        }
        error?.let {
            Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { error = null }) { Text("Dismiss") }
        }
        notice?.let {
            Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primary)
        }
        if (selectedId == null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("Ongoing", "Upcoming", "Winners").forEach { name ->
                    FilterChip(selected = tab == name, onClick = { tab = name }, label = { Text(name) })
                }
            }
            TextButton(onClick = onDiscoverPeople, modifier = Modifier.padding(start = 12.dp)) {
                Text("Find people & campus connections")
            }
            val rows = contests.filter { BlinkContestPolicy.section(it.optString("status")) == tab }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (rows.isEmpty()) item {
                    Text("No " + tab.lowercase() + " contests yet. Check again soon.",
                        modifier = Modifier.padding(18.dp))
                }
                items(rows, key = { it.optString("id") }) { contest ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedId = contest.optString("id")
                            statement = ""; mediaUrl = ""; notice = null
                        }
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(contest.optString("title"), fontWeight = FontWeight.Bold, fontSize = 19.sp)
                            Text(BlinkContestPolicy.statusLabel(contest.optString("status")),
                                color = MaterialTheme.colorScheme.primary)
                            Text(contest.optString("description"), maxLines = 3)
                            Text(
                                "1st " + cash(contest.optInt("prize_first")) +
                                    " · 2nd " + cash(contest.optInt("prize_second")) +
                                    " · 3rd " + cash(contest.optInt("prize_third")),
                                fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                            )
                            Text(contest.optInt("contestants").toString() +
                                " contestants · Free entry · Free voting", fontSize = 12.sp)
                            Text("Voting: " + dateLabel(contest.optString("voting_opens_at")) +
                                " to " + dateLabel(contest.optString("voting_closes_at")), fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            val d = detail
            if (d == null) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            } else {
                val c = d.optJSONObject("contest") ?: JSONObject()
                val status = c.optString("status")
                val own = d.optString("my_entry_id", "").takeUnless { it == "null" }.orEmpty()
                val myVote = d.optString("my_vote_entry_id", "").takeUnless { it == "null" }.orEmpty()
                val top = d.optJSONArray("leaderboard")?.contestObjects().orEmpty()
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 115.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(c.optString("title"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(BlinkContestPolicy.statusLabel(status),
                            color = MaterialTheme.colorScheme.primary)
                        Text(c.optString("description"))
                        Text("Prizes: 1st " + cash(c.optInt("prize_first")) +
                            " · 2nd " + cash(c.optInt("prize_second")) +
                            " · 3rd " + cash(c.optInt("prize_third")), fontWeight = FontWeight.Bold)
                        Text("Applications close " + dateLabel(c.optString("registration_closes_at")))
                        Text("Voting closes " + dateLabel(c.optString("voting_closes_at")))
                        if (c.optString("rules").isNotBlank()) Text("Rules: " + c.optString("rules"))
                        Text("One free vote per account. Self-voting is not allowed.", fontSize = 12.sp)
                    }
                    if (own.isNotBlank()) item {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text("Your entry: " + d.optString("my_entry_status"),
                                    fontWeight = FontWeight.SemiBold)
                                Text("Your rank: " + if (d.isNull("my_rank")) "Not ranked yet"
                                     else "#" + d.optInt("my_rank"))
                            }
                        }
                    }
                    if (status == "registration" && own.isBlank()) item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = statement, onValueChange = { statement = it.take(500) },
                                label = { Text("Why should people vote for you?") },
                                minLines = 3, modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = mediaUrl, onValueChange = { mediaUrl = it },
                                label = { Text("Optional entry photo/Reel HTTPS URL") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    busy = true; error = null; notice = null
                                    scope.launch {
                                        api.apply(c.optString("id"), statement, mediaUrl)
                                            .onSuccess {
                                                notice = "Application submitted: " + it.optString("status")
                                                revision++
                                            }
                                            .onFailure { error = it.message }
                                        busy = false
                                    }
                                },
                                enabled = !busy && BlinkContestPolicy.validStatement(statement),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Apply for free") }
                        }
                    }
                    if (ownCandidate != null && top.none { it.optString("id") == own }) item {
                        val me = ownCandidate!!
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Your contestant profile", fontWeight = FontWeight.Bold)
                                Text("Rank #" + me.optInt("rank") + " · " + me.optInt("votes") + " votes")
                                Button(onClick = { flyerCandidate = me }) { Text("Share my personalized flyer") }
                            }
                        }
                    }
                    if (invitedCandidate != null) item {
                        val candidate = invitedCandidate!!
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("You were invited to vote for", fontWeight = FontWeight.SemiBold)
                                Text("@" + candidate.optString("username"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text("Rank #" + candidate.optInt("rank") + " · " +
                                    candidate.optInt("votes") + " votes")
                                Text(candidate.optString("statement"), maxLines = 3)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        enabled = !busy && BlinkContestPolicy.canVote(status, myVote, own, candidate.optString("id")),
                                        onClick = {
                                            busy = true; error = null; notice = null
                                            scope.launch {
                                                api.vote(c.optString("id"), candidate.optString("id"))
                                                    .onSuccess { notice = "Your free vote was recorded."; revision++ }
                                                    .onFailure { error = it.message }
                                                busy = false
                                            }
                                        }
                                    ) { Text("Vote for this contestant") }
                                    OutlinedButton(onClick = { flyerCandidate = candidate }) { Text("Share") }
                                }
                            }
                        }
                    }
                    item {
                        Text("Top 20 · " + d.optInt("total_ranked") + " ranked contestants",
                            fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        if (myVote.isNotBlank()) Text("You've already voted in this contest.", fontSize = 12.sp)
                    }
                    if (top.isEmpty()) item { Text("No approved contestants yet.") }
                    items(top.filterNot { it.optString("id") == invitedEntryId && invitedCandidate != null }, key = { it.optString("id") }) { candidate ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("#" + candidate.optInt("rank"), fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("@" + candidate.optString("username"), fontWeight = FontWeight.Medium)
                                    Text(candidate.optInt("votes").toString() + " votes", fontSize = 12.sp)
                                    Text(candidate.optString("statement"), fontSize = 12.sp, maxLines = 2)
                                }
                                OutlinedButton(onClick = { flyerCandidate = candidate }) { Text("Share") }
                                Spacer(Modifier.width(6.dp))
                                Button(
                                    enabled = !busy && BlinkContestPolicy.canVote(
                                        status, myVote, own, candidate.optString("id")),
                                    onClick = {
                                        busy = true; error = null; notice = null
                                        scope.launch {
                                            api.vote(c.optString("id"), candidate.optString("id"))
                                                .onSuccess { notice = "Your vote was counted."; revision++ }
                                                .onFailure { error = it.message }
                                            busy = false
                                        }
                                    }
                                ) { Text("Vote") }
                            }
                        }
                    }
                }
            }
        }
    }
    flyerCandidate?.let { candidate ->
        BlinkContestShareDialog(candidate = candidate, onDismiss = { flyerCandidate = null })
    }
}
