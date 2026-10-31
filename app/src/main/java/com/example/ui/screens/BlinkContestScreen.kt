package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import com.example.data.supabase.BlinkContestMediaUpload
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
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
import kotlinx.coroutines.delay
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

internal fun JSONArray.contestObjects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }
private fun cash(value: Int) = "₦" + NumberFormat.getIntegerInstance(Locale("en", "NG")).format(value)
private fun dateLabel(value: String): String = runCatching {
    OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.of("Africa/Lagos"))
        .format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)) + " WAT"
}.getOrDefault("Date unavailable")

/** An explicit preview: uploaded Contest videos never auto-play in the scrolling UI. */
@Composable
internal fun BlinkContestVideoPreview(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            playWhenReady = false
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                this.player = player
            }
        },
        update = { it.player = player },
        modifier = modifier
    )
}
internal fun contestMediaExtension(url: String): String =
    runCatching { Uri.parse(url).lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase() }
        .getOrDefault("")

@Composable
fun BlinkContestScreen(
    onDiscoverPeople: () -> Unit,
    target: ContestVoteTarget? = null,
    onTargetHandled: () -> Unit = {}
) {
    val api = remember { BlinkContestService() }
    val mediaUpload = remember { BlinkContestMediaUpload() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var contests by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var tab by remember { mutableStateOf("Ongoing") }
    var statement by remember { mutableStateOf("") }
    var mediaUrl by remember { mutableStateOf("") }
    var mediaChoice by remember { mutableStateOf<Uri?>(null) }
    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { selected -> mediaChoice = selected }
    var error by remember { mutableStateOf<String?>(null) }
    var loadingContests by remember { mutableStateOf(true) }
    var detailLoadError by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var invitedEntryId by remember { mutableStateOf<String?>(null) }
    var invitedCandidate by remember { mutableStateOf<JSONObject?>(null) }
    var ownCandidate by remember { mutableStateOf<JSONObject?>(null) }
    var flyerCandidate by remember { mutableStateOf<JSONObject?>(null) }
    var shareLookupBusy by remember { mutableStateOf(false) }
    var inspectedCandidate by remember { mutableStateOf<JSONObject?>(null) }

    // Reset attachment state for each contest, including deep-link navigation.
    LaunchedEffect(selectedId) {
        mediaChoice = null
        mediaUrl = ""
        statement = ""
    }
    // Keep status and leaderboard state in sync with the server while viewing.
    LaunchedEffect(selectedId) {
        while (true) {
            delay(60_000)
            revision++
        }
    }
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
        loadingContests = true
        api.contests().onSuccess { contests = it.contestObjects(); error = null }
            .onFailure { error = it.message ?: "Unable to load contests." }
        loadingContests = false
    }
    LaunchedEffect(selectedId, revision) {
        detail = null
        detailLoadError = null
        selectedId?.let { id ->
            api.detail(id).onSuccess { detail = it }
                .onFailure { detailLoadError = it.message ?: "Unable to open contest." }
        }
    }

    // Leaderboard rows are intentionally compact. The flyer requires the full
    // approved candidate record (including contest_id), fetched on demand.
    val openFlyer: (JSONObject) -> Unit = { candidate ->
        if (!shareLookupBusy) {
            val contestId = selectedId.orEmpty()
            val entryId = candidate.optString("id")
            if (contestId.isBlank() || entryId.isBlank()) {
                error = "This contestant is not available for sharing."
            } else {
                shareLookupBusy = true
                scope.launch {
                    api.candidate(contestId, entryId)
                        .onSuccess { flyerCandidate = it }
                        .onFailure { error = it.message ?: "Unable to prepare the voting flyer." }
                    shareLookupBusy = false
                }
            }
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
                listOf("Ongoing", "Upcoming", "Completed").forEach { name ->
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
                    if (loadingContests) {
                        CircularProgressIndicator(modifier = Modifier.padding(18.dp))
                    } else if (error == null) {
                        Text("No " + tab.lowercase() + " contests yet. Check again soon.",
                            modifier = Modifier.padding(18.dp))
                    } else {
                        Button(onClick = { revision++ }, modifier = Modifier.padding(18.dp)) {
                            Text("Retry loading contests")
                        }
                    }
                }
                items(rows, key = { it.optString("id") }) { contest ->
                    BlinkContestHeroCard(contest) {
                        selectedId = contest.optString("id")
                        statement = ""; mediaUrl = ""; mediaChoice = null; notice = null
                    }

                }
            }
        } else {
            val d = detail
            if (d == null) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                if (detailLoadError == null) {
                    CircularProgressIndicator()
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(detailLoadError.orEmpty(), color = MaterialTheme.colorScheme.error)
                        Button(onClick = { revision++ }) { Text("Retry contest") }
                    }
                }
            } else {
                val c = d.optJSONObject("contest") ?: JSONObject()
                val status = c.optString("status")
                val own = d.optString("my_entry_id", "").takeUnless { it == "null" }.orEmpty()
                val myVote = d.optString("my_vote_entry_id", "").takeUnless { it == "null" }.orEmpty()
                val top = d.optJSONArray("leaderboard")?.contestObjects().orEmpty()
                val winners = d.optJSONArray("winners")?.contestObjects().orEmpty()
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 115.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        val cover = c.optString("banner_url")
                        if (cover.startsWith("https://")) AsyncImage(
                            model = cover, contentDescription = "Contest cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(190.dp)
                        )
                        Text(c.optString("title"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(BlinkContestPolicy.statusLabel(status),
                            color = MaterialTheme.colorScheme.primary)
                        Text(c.optString("description"))
                        Text("Prizes: 1st " + cash(c.optInt("prize_first")) +
                            " · 2nd " + cash(c.optInt("prize_second")) +
                            " · 3rd " + cash(c.optInt("prize_third")), fontWeight = FontWeight.Bold)
                        when (status) {
                            "upcoming" -> Text(
                                "Applications open " + dateLabel(c.optString("registration_opens_at"))
                            )
                            "registration" -> Text(
                                "Applications close " + dateLabel(c.optString("registration_closes_at"))
                            )
                        }
                        Surface(color = Color(0xFF0B1626),
                            shape = RoundedCornerShape(12.dp)) {
                            Box(Modifier.padding(12.dp)) {
                                val (deadlineLabel, deadline) = contestDeadline(c)
                                BlinkContestCountdown(deadline, deadlineLabel)
                            }
                        }
                        if (c.optString("rules").isNotBlank()) Text("Rules: " + c.optString("rules"))
                        if (status == "completed") {
                            Text("Official winners",fontWeight=FontWeight.Bold,fontSize=20.sp)
                            if (winners.isEmpty()) Text("No prize winners recorded.")
                            winners.forEach { w -> BlinkContestWinnerCard(w) }
                        }
                        Text("One free vote per account. Self-voting is not allowed.", fontSize = 12.sp)
                    }
                    if (own.isNotBlank()) item {
                        Surface(shape = RoundedCornerShape(15.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(15.dp)) {
                                Text("Your application", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                val entryStatus = d.optString("my_entry_status")
                                Text(
                                    when(entryStatus) {
                                        "pending" -> "Under review · No voting until approved"
                                        "approved" -> "Approved · Your entry is public"
                                        "rejected" -> "Not approved · Contact contest support for clarification"
                                        "disqualified" -> "Disqualified · No longer eligible"
                                        else -> entryStatus
                                    }, color = MaterialTheme.colorScheme.primary
                                )
                                Text("Your rank: " + if (d.isNull("my_rank")) "Not ranked yet"
                                    else "#" + d.optInt("my_rank") +
                                        BlinkContestRankMovement.label(d,"my_rank_delta"))
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
                            Text("Your entry media", fontWeight = FontWeight.SemiBold)
                            OutlinedButton(
                                onClick = {
                                    mediaPicker.launch(PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (mediaChoice == null) "Choose a photo or video"
                                else "Change selected media") }
                            mediaChoice?.let { selected ->
                                val mime = context.contentResolver.getType(selected).orEmpty()
                                if (mime.startsWith("image/")) AsyncImage(
                                    model = selected, contentDescription = "Selected contest photo",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().height(160.dp)
                                ) else if (mime.startsWith("video/")) {
                                    BlinkContestVideoPreview(
                                        selected.toString(),
                                        Modifier.fillMaxWidth().height(190.dp)
                                    )
                                } else Text("This media format cannot be previewed.",
                                    color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { mediaChoice = null }) { Text("Remove attachment") }
                            }
                            OutlinedTextField(
                                value = mediaUrl, onValueChange = { mediaUrl = it },
                                label = { Text("Only new private uploads are accepted") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = false
                            )
                            Text("A photo or video is required. JPG/PNG/WebP up to 8 MB, or MP4 up to 25 MB. " +
                                "Your selected media is stored privately for moderation. It becomes publicly viewable only after approval.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp)
                            Button(
                                onClick = {
                                    busy = true; error = null; notice = null
                                    scope.launch {
                                        runCatching {
                                            val url = mediaChoice?.let {
                                                mediaUpload.upload(context, it, privateEntry = true).getOrThrow()
                                            } ?: mediaUrl
                                            api.apply(c.optString("id"), statement, url).getOrThrow()
                                        }.onSuccess {
                                            notice = "Application submitted: " + it.optString("status")
                                            mediaChoice = null
                                            mediaUrl = ""
                                            statement = ""
                                            revision++
                                        }.onFailure { error = it.message }
                                        busy = false
                                    }
                                },
                                enabled = !busy && BlinkContestPolicy.canApply(statement, mediaChoice != null, mediaUrl),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Apply for free") }
                        }
                    }
                    if (ownCandidate != null && top.none { it.optString("id") == own }) item {
                        val me = ownCandidate!!
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Your contestant profile", fontWeight = FontWeight.Bold)
                                BlinkContestCandidateSpotlight(me) { inspectedCandidate = me }
                                Button(onClick = { openFlyer(me) }) { Text("Share my personalized flyer") }
                            }
                        }
                    }
                    if (invitedCandidate != null) item {
                        val candidate = invitedCandidate!!
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("You were invited to vote for", fontWeight = FontWeight.SemiBold)
                                BlinkContestCandidateSpotlight(candidate) { inspectedCandidate = candidate }
                                Text(candidate.optString("statement"), maxLines = 3)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        enabled = !busy && BlinkContestPolicy.canVote(status, myVote, own, candidate.optString("id")),
                                        onClick = {
                                            busy = true; error = null; notice = null
                                            scope.launch {
                                                api.vote(c.optString("id"), candidate.optString("id"))
                                                    .onSuccess {
                                                        notice = if (it.optString("vote_status") == "under_review")
                                                            "Your free vote was received and is pending integrity review."
                                                        else "Your free vote was counted."
                                                        revision++
                                                    }
                                                    .onFailure { error = it.message }
                                                busy = false
                                            }
                                        }
                                    ) { Text("Vote for this contestant") }
                                    OutlinedButton(onClick = { openFlyer(candidate) }) { Text("Share") }
                                }
                            }
                        }
                    }
                    if (top.isNotEmpty()) item {
                        BlinkContestPodium(top) { inspectedCandidate = it }
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
                                Spacer(Modifier.width(6.dp))
                                BlinkContestAvatar(candidate.optString("avatar_url"),
                                    candidate.optString("username"), 42.dp)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("@" + candidate.optString("username"), fontWeight = FontWeight.Medium)
                                    Text(candidate.optInt("votes").toString() + " votes" +
                                        BlinkContestRankMovement.label(candidate), fontSize = 12.sp)
                                    Text(candidate.optString("statement"), fontSize = 12.sp, maxLines = 2)
                                    Text("View profile", color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp, modifier = Modifier.clickable {
                                            inspectedCandidate = candidate
                                        })
                                }
                                OutlinedButton(onClick = { openFlyer(candidate) }) { Text("Share") }
                                Spacer(Modifier.width(6.dp))
                                Button(
                                    enabled = !busy && BlinkContestPolicy.canVote(
                                        status, myVote, own, candidate.optString("id")),
                                    onClick = {
                                        busy = true; error = null; notice = null
                                        scope.launch {
                                            api.vote(c.optString("id"), candidate.optString("id"))
                                                .onSuccess {
                                                    notice = if (it.optString("vote_status") == "under_review")
                                                        "Vote received; awaiting integrity review."
                                                    else "Your free vote was counted."
                                                    revision++
                                                }
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
    inspectedCandidate?.let { candidate ->
        val votes = candidate.optInt("votes")
        val highest = detail?.optJSONArray("leaderboard")
            ?.optJSONObject(0)?.optInt("votes")?.coerceAtLeast(1) ?: 1
        AlertDialog(
            onDismissRequest = { inspectedCandidate = null },
            title = { Text("Contestant profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BlinkContestAvatar(candidate.optString("avatar_url"),
                            candidate.optString("username"), 70.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("@" + candidate.optString("username"),
                                fontWeight = FontWeight.Bold)
                            Text("Rank #" + candidate.optInt("rank") + " · " +
                                votes + " votes", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(candidate.optString("statement"), maxLines = 6)
                    Text("Votes relative to current leader", fontSize = 11.sp)
                    LinearProgressIndicator(
                        progress = { (votes.toFloat() / highest).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF1685FF)
                    )
                    val image = candidate.optString("media_url")
                    if (image.startsWith("https://")) {
                        when (contestMediaExtension(image)) {
                            "jpg", "jpeg", "png", "webp" ->
                                AsyncImage(model = image, contentDescription = "Contestant entry photo",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp))
                            "mp4" ->
                                BlinkContestVideoPreview(image, Modifier.fillMaxWidth().height(240.dp))
                        }
                    }
                    Text("One free vote per account. Self-voting isn't permitted.",
                        fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && BlinkContestPolicy.canVote(
                        detail?.optJSONObject("contest")?.optString("status").orEmpty(),
                        detail?.optString("my_vote_entry_id").orEmpty(),
                        detail?.optString("my_entry_id").orEmpty(),
                        candidate.optString("id")),
                    onClick = {
                        val contestId = selectedId ?: return@TextButton
                        busy = true
                        scope.launch {
                            api.vote(contestId, candidate.optString("id"))
                                .onSuccess {
                                    notice = if (it.optString("vote_status") == "under_review")
                                        "Vote received; pending review." else "Free vote counted."
                                    revision++; inspectedCandidate = null
                                }.onFailure { error = it.message }
                            busy = false
                        }
                    }
                ) { Text("Vote free") }
            },
            dismissButton = {
                TextButton(onClick = {
                    openFlyer(candidate)
                    inspectedCandidate = null
                }) { Text("Share flyer") }
            }
        )
    }
    flyerCandidate?.let { candidate ->
        BlinkContestShareDialog(candidate = candidate, onDismiss = { flyerCandidate = null })
    }
}
