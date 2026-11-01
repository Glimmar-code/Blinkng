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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import coil.request.ImageRequest

internal fun JSONArray.contestObjects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }
private fun cash(value: Long) = contestNaira(value)
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
    // A previous contest must never render during navigation to a new contest.
    var detailContestId by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf("Ongoing") }
    var statement by remember { mutableStateOf("") }
    var mediaChoice by remember { mutableStateOf<Uri?>(null) }
    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { selected -> mediaChoice = selected }
    var error by remember { mutableStateOf<String?>(null) }
    var loadingContests by remember { mutableStateOf(true) }
    var listError by remember { mutableStateOf<String?>(null) }
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
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchOffset by remember { mutableIntStateOf(0) }
    var searchCandidates by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var searchTotal by remember { mutableIntStateOf(0) }
    var searchBusy by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var myAppeal by remember { mutableStateOf<JSONObject?>(null) }
    var appealReason by remember { mutableStateOf("") }
    var appealBusy by remember { mutableStateOf(false) }
    var appealError by remember { mutableStateOf<String?>(null) }

    val haptic = LocalHapticFeedback.current
    var refreshSpin by remember { mutableStateOf(0f) }
    val refreshRotation by animateFloatAsState(refreshSpin, tween(600), label = "refreshSpin")
    LaunchedEffect(notice) {
        if (notice != null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    // Reset attachment state for each contest, including deep-link navigation.
    LaunchedEffect(selectedId) {
        mediaChoice = null
        statement = ""
        searchExpanded = false
        searchQuery = ""
        searchOffset = 0
        searchCandidates = emptyList()
        searchTotal = 0
        searchError = null
        inspectedCandidate = null
        flyerCandidate = null
        shareLookupBusy = false
        notice = null
        error = null
        // The selected contest's detail remains visible only when its ID matches.
        if (detailContestId != selectedId) {
            detail = null
            detailContestId = null
        }
    }
    LaunchedEffect(selectedId, detail?.optString("my_entry_id"), revision) {
        myAppeal = null
        appealError = null
        val entry = detail?.optString("my_entry_id")
            ?.takeUnless { it == "null" }.orEmpty()
        if (!selectedId.isNullOrBlank() && entry.isNotBlank()) {
            api.myAppeal(entry).onSuccess { myAppeal = it }
                .onFailure { appealError = it.message ?: "Could not check appeal status." }
        }
    }
    LaunchedEffect(selectedId, searchExpanded, searchQuery, searchOffset, revision) {
        val id = selectedId
        if (!searchExpanded || id == null || searchQuery.trim().length == 1) {
            searchCandidates = emptyList()
            searchTotal = 0
            searchError = null
            searchBusy = false
        } else {
            searchBusy = true
            searchError = null
            delay(300) // Debounce typing; server performs bounded approved-only lookup.
            api.searchCandidates(id, searchQuery, searchOffset)
                .onSuccess { response ->
                    searchCandidates = response.optJSONArray("entries")?.contestObjects().orEmpty()
                    searchTotal = response.optInt("total")
                }
                .onFailure {
                    searchCandidates = emptyList()
                    searchError = it.message ?: "Cannot load contestants."
                }
            searchBusy = false
        }
    }
    // Keep status and leaderboard state in sync with the server while viewing.
    LaunchedEffect(selectedId) {
        // Avoid polling the entire app every minute while the user is browsing
        // other tabs; only the opened contest needs a live-ish refresh.
        if (selectedId != null) {
            while (true) {
                delay(60_000)
                revision++
            }
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
        api.contests().onSuccess {
            contests = it.contestObjects()
            listError = null
        }.onFailure { listError = it.message ?: "Unable to load contests." }
        loadingContests = false
    }
    LaunchedEffect(selectedId, revision) {
        val id = selectedId
        if (id == null) {
            detail = null
            detailContestId = null
            detailLoadError = null
        } else {
            // Refresh in place: no full-screen loading flicker every 60 seconds.
            // A different ID never reuses the previous contest's details.
            if (detailContestId != id) {
                detail = null
                detailContestId = null
            }
            detailLoadError = null
            api.detail(id).onSuccess {
                detail = it
                detailContestId = id
                detailLoadError = null
            }.onFailure { detailLoadError = it.message ?: "Unable to refresh contest." }
        }
    }

    // Leaderboard rows are intentionally compact. The flyer requires the full
    // approved candidate record (including contest_id), fetched on demand.
    val openFlyer: (JSONObject) -> Unit = { candidate ->
        val currentStatus = detail?.optJSONObject("contest")?.optString("status").orEmpty()
        if (!BlinkContestPolicy.canShareVoteInvite(currentStatus)) {
            error = "Voting invitations are unavailable for this contest."
        } else if (!shareLookupBusy) {
            val contestId = selectedId.orEmpty()
            val entryId = candidate.optString("id")
            if (contestId.isBlank() || entryId.isBlank()) {
                error = "This contestant is not available for sharing."
            } else {
                shareLookupBusy = true
                scope.launch {
                    api.candidate(contestId, entryId)
                        .onSuccess { resolved ->
                            if (BlinkContestPolicy.isRequestedCandidate(
                                    contestId, entryId, selectedId,
                                    resolved.optString("contest_id"), resolved.optString("id")
                                )) {
                                flyerCandidate = resolved
                            } else if (selectedId == contestId) {
                                error = "This contestant is no longer available for sharing."
                            }
                        }
                        .onFailure {
                            if (selectedId == contestId)
                                error = it.message ?: "Unable to prepare the voting flyer."
                        }
                    if (selectedId == contestId) shareLookupBusy = false
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(
                visible = selectedId != null,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                TextButton(onClick = { selectedId = null; invitedEntryId = null }) { Text("Back") }
            }
            Text("Contest", Modifier.weight(1f), fontSize = 25.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { revision++; refreshSpin += 360f }) {
                Text("↻", Modifier.graphicsLayer { rotationZ = refreshRotation }, fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text("Refresh")
            }
        }
        AnimatedVisibility(visible = loadingContests, enter = fadeIn(), exit = fadeOut()) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(2.dp)))
        }
        if (selectedId == null) {
            listError?.let { failure -> BlinkContestBanner(failure, true, "Retry") { revision++ } }
        }
        error?.let { failure -> BlinkContestBanner(failure, true, "Dismiss") { error = null } }
        notice?.let { info -> BlinkContestBanner(info, false) }
        if (selectedId == null) BlinkContestScreenEnter(fromRight = false) {
            val tabNames = listOf("Ongoing", "Upcoming", "Completed")
            BlinkContestTabs(
                tabNames, tab,
                tabNames.associateWith { n ->
                    contests.count { BlinkContestPolicy.section(it.optString("status")) == n }
                }
            ) { tab = it }
            TextButton(onClick = onDiscoverPeople, modifier = Modifier.padding(start = 12.dp)) {
                Text("Find people & campus connections")
            }
            Crossfade(
                targetState = tab, modifier = Modifier.fillMaxSize(),
                animationSpec = tween(260), label = "contestTab"
            ) { currentTab ->
                val rows = contests.filter {
                    BlinkContestPolicy.section(it.optString("status")) == currentTab
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (rows.isEmpty()) item {
                        if (loadingContests) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                BlinkContestSkeletonCard()
                                BlinkContestSkeletonCard()
                            }
                        } else if (listError == null) {
                            BlinkContestEmptyState(
                                "No " + currentTab.lowercase() + " contests yet",
                                "Check again soon.")
                        } else {
                            Column(Modifier.padding(18.dp)) {
                                Text(listError.orEmpty(), color = MaterialTheme.colorScheme.error)
                                Button(onClick = { revision++ }) { Text("Retry loading contests") }
                            }
                        }
                    }
                    itemsIndexed(rows, key = { _, row -> row.optString("id") }) { index, contest ->
                        Box(Modifier.contestEntrance(index)) {
                            BlinkContestHeroCard(contest) {
                                invitedEntryId = null
                                selectedId = contest.optString("id")
                                statement = ""; mediaChoice = null; notice = null
                            }
                        }
                    }
                }
            }
        } else BlinkContestScreenEnter(fromRight = true) {
            val d = detail.takeIf { detailContestId == selectedId }
            if (d == null) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                if (detailLoadError == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BlinkContestSkeletonCard()
                        BlinkContestSkeletonCard()
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(detailLoadError.orEmpty(), color = MaterialTheme.colorScheme.error)
                        Button(onClick = { revision++ }) { Text("Retry contest") }
                    }
                }
            } else {
                detailLoadError?.let { failure ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(failure, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { revision++ }) { Text("Retry") }
                    }
                }
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
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val cover = c.optString("banner_url")
                            if (cover.startsWith("https://")) Box(
                                Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(22.dp))
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(cover).crossfade(500).build(),
                                    contentDescription = "Contest cover",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0x99060E19)))))
                            }
                            BlinkContestStatusChip(
                                BlinkContestPolicy.statusLabel(status), status,
                                live = status == "voting" || status == "registration")
                            Text(c.optString("title"), fontSize = 26.sp, lineHeight = 31.sp,
                                fontWeight = FontWeight.Bold)
                            if (status == "cancelled") {
                                Surface(color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(14.dp)) {
                                    Text(
                                        "This contest was cancelled after publication. Applications and voting are closed; no winners or prizes will be awarded.",
                                        modifier = Modifier.padding(12.dp),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            Text(c.optString("description"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            BlinkContestPrizeRow(
                                c.optLong("prize_first"), c.optLong("prize_second"),
                                c.optLong("prize_third"))
                            when (status) {
                                "upcoming" -> Text(
                                    "Applications open " + dateLabel(c.optString("registration_opens_at")),
                                    fontWeight = FontWeight.Medium
                                )
                                "registration" -> Text(
                                    "Applications close " + dateLabel(c.optString("registration_closes_at")),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Surface(color = Color(0xFF0B1626), shape = RoundedCornerShape(14.dp)) {
                                Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                                    val (deadlineLabel, deadline) = contestDeadline(c)
                                    BlinkContestCountdown(deadline, deadlineLabel)
                                }
                            }
                            if (c.optString("rules").isNotBlank()) Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Rules: " + c.optString("rules"), Modifier.padding(14.dp))
                            }
                            if (status == "completed") {
                                Text("Official winners", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                if (winners.isEmpty()) Text("No prize winners recorded.")
                                winners.forEach { w -> BlinkContestWinnerCard(w) }
                            }
                            Text("One free vote per account. Self-voting is not allowed.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (own.isNotBlank()) item {
                        Surface(shape = RoundedCornerShape(15.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth().animateContentSize()) {
                            Column(Modifier.padding(15.dp)) {
                                Text("Your application", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                val entryStatus = d.optString("my_entry_status")
                                BlinkContestStatusChip(
                                    entryStatus.replaceFirstChar { it.uppercase() }, entryStatus,
                                    live = entryStatus == "pending")
                                Text(
                                    when(entryStatus) {
                                        "pending" -> "Under review · No voting until approved"
                                        "approved" -> "Approved · Your entry is public"
                                        "rejected" -> "Not approved · Check Notifications for the review reason"
                                        "disqualified" -> "Disqualified · Check Notifications for the review reason"
                                        else -> entryStatus
                                    }, color = MaterialTheme.colorScheme.primary
                                )
                                if (entryStatus in listOf("rejected", "disqualified")) {
                                    myAppeal?.let { appeal ->
                                        Text(
                                            "Appeal: " + appeal.optString("status")
                                                .replaceFirstChar { it.uppercase() },
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        val decision = appeal.optString("decision_reason")
                                            .takeUnless { it == "null" }.orEmpty()
                                        if (decision.isNotBlank()) Text("Decision: " + decision)
                                    }
                                    appealError?.let {
                                        Text(it, color = MaterialTheme.colorScheme.error)
                                        TextButton(onClick = { revision++ }) { Text("Retry appeal status") }
                                    }
                                    if (myAppeal == null && status !in listOf("cancelled", "completed", "review") &&
                                        !appealBusy) {
                                        OutlinedTextField(
                                            value = appealReason,
                                            onValueChange = { appealReason = it.take(1000) },
                                            label = { Text("Why should your application be reconsidered?") },
                                            minLines = 3,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Button(
                                            enabled = !busy && appealReason.trim().length in 20..1000,
                                            onClick = {
                                                appealBusy = true
                                                appealError = null
                                                scope.launch {
                                                    api.submitAppeal(own, appealReason)
                                                        .onSuccess {
                                                            notice = "Your appeal was submitted for review."
                                                            myAppeal = it
                                                            appealReason = ""
                                                        }
                                                        .onFailure {
                                                            appealError = it.message ?: "Appeal submission failed."
                                                        }
                                                    appealBusy = false
                                                }
                                            }
                                        ) { Text("Submit one appeal") }
                                    }
                                }
                                Text("Your rank: " + if (d.isNull("my_rank")) "Not ranked yet"
                                    else "#" + d.optInt("my_rank") +
                                        BlinkContestRankMovement.label(d,"my_rank_delta"))
                            }
                        }
                    }
                    if (status == "registration" && own.isBlank()) item {
                        Column(Modifier.animateContentSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            Text("A photo or video is required. JPG/PNG/WebP up to 8 MB, or MP4 up to 25 MB. " +
                                "Your selected media is stored privately for moderation. It becomes publicly viewable only after approval.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp)
                            Button(
                                onClick = {
                                    busy = true; error = null; notice = null
                                    scope.launch {
                                        runCatching {
                                            val selectedMedia = mediaChoice
                                                ?: error("Choose a photo or video to apply.")
                                            val url = mediaUpload.upload(
                                                context, selectedMedia, privateEntry = true
                                            ).getOrThrow()
                                            api.apply(c.optString("id"), statement, url).getOrThrow()
                                        }.onSuccess {
                                            notice = "Application submitted: " + it.optString("status")
                                            mediaChoice = null
                                            statement = ""
                                            revision++
                                        }.onFailure { error = it.message }
                                        busy = false
                                    }
                                },
                                enabled = !busy && BlinkContestPolicy.canApply(statement, mediaChoice != null, ""),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (busy) CircularProgressIndicator(
                                    Modifier.size(18.dp), strokeWidth = 2.dp,
                                    color = LocalContentColor.current)
                                else Text("Apply for free")
                            }
                        }
                    }
                    if (ownCandidate != null && top.none { it.optString("id") == own }) item {
                        val me = ownCandidate!!
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Your contestant profile", fontWeight = FontWeight.Bold)
                                BlinkContestCandidateSpotlight(me) { inspectedCandidate = me }
                                Button(onClick = { openFlyer(me) },
                                    enabled = BlinkContestPolicy.canShareVoteInvite(status)
                                ) { Text("Share my personalized flyer") }
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
                                    OutlinedButton(onClick = { openFlyer(candidate) },
                                        enabled = BlinkContestPolicy.canShareVoteInvite(status)
                                    ) { Text("Share") }
                                }
                            }
                        }
                    }
                    if (top.isNotEmpty()) item {
                        BlinkContestPodium(top) { inspectedCandidate = it }
                    }
                    item {
                        OutlinedButton(onClick = { searchExpanded = !searchExpanded },
                            enabled = status != "cancelled",
                            modifier = Modifier.fillMaxWidth()) {
                            Text(if (searchExpanded) "Hide contestant directory"
                                 else "Find contestants beyond the Top 20")
                        }
                    }
                    if (searchExpanded) {
                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it.take(60); searchOffset = 0 },
                                label = { Text("Search contestants by name or username") },
                                placeholder = { Text("Leave blank to browse all approved entries") },
                                singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                            Text("Only approved contestants appear. Up to 20 per page.",
                                style = MaterialTheme.typography.bodySmall)
                            if (searchQuery.trim().length == 1) {
                                Text("Type at least two characters, or clear to browse everyone.",
                                    color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            } else if (searchBusy) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            } else {
                                Text("$searchTotal approved results",
                                    style = MaterialTheme.typography.labelMedium)
                            }
                            searchError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { revision++ }) { Text("Retry search") }
                            }
                        }
                        items(searchCandidates, key = { "search-" + it.optString("id") }) { candidate ->
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("#" + candidate.optInt("rank"),
                                            fontWeight = FontWeight.Bold)
                                        BlinkContestAvatar(candidate.optString("avatar_url"),
                                            candidate.optString("username"), 40.dp)
                                        Column(Modifier.weight(1f)) {
                                            Text("@" + candidate.optString("username"),
                                                fontWeight = FontWeight.SemiBold)
                                            Text(candidate.optInt("votes").toString() + " votes",
                                                style = MaterialTheme.typography.bodySmall)
                                        }
                                        TextButton(onClick = { inspectedCandidate = candidate }) {
                                            Text("Profile")
                                        }
                                    }
                                    Text(candidate.optString("statement"), maxLines = 2)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { openFlyer(candidate) },
                                        enabled = BlinkContestPolicy.canShareVoteInvite(status)
                                    ) { Text("Share") }
                                        Button(
                                            enabled = !busy && BlinkContestPolicy.canVote(
                                                status, myVote, own, candidate.optString("id")),
                                            onClick = {
                                                busy = true
                                                scope.launch {
                                                    api.vote(c.optString("id"), candidate.optString("id"))
                                                        .onSuccess {
                                                            notice = if (it.optString("vote_status") == "under_review")
                                                                "Free vote received; pending review."
                                                            else "Your free vote was counted."
                                                            revision++
                                                        }
                                                        .onFailure { error = it.message }
                                                    busy = false
                                                }
                                            }) { Text("Vote") }
                                    }
                                }
                            }
                        }
                        if (!searchBusy && searchCandidates.isEmpty() && searchError == null &&
                            searchQuery.trim().length != 1) item {
                            Text("No approved contestants found.")
                        }
                        if (searchTotal > 20) item {
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(enabled = !searchBusy && searchOffset > 0,
                                    onClick = { searchOffset = (searchOffset - 20).coerceAtLeast(0) }) {
                                    Text("Previous")
                                }
                                Text("Page " + (searchOffset / 20 + 1),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelMedium)
                                OutlinedButton(
                                    enabled = !searchBusy && searchOffset + 20 < searchTotal &&
                                        searchOffset < 10000,
                                    onClick = { searchOffset += 20 }) { Text("Next") }
                            }
                        }
                    }
                    item {
                        Text("Top 20 · " + d.optInt("total_ranked") + " ranked contestants",
                            fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        if (myVote.isNotBlank()) Text("You've already voted in this contest.", fontSize = 12.sp)
                    }
                    if (top.isEmpty()) item { Text("No approved contestants yet.") }
                    itemsIndexed(top.filterNot { it.optString("id") == invitedEntryId && invitedCandidate != null },
                        key = { _, row -> row.optString("id") }) { index, candidate ->
                        BlinkContestLeaderboardRow(
                            candidate = candidate,
                            index = index,
                            leaderVotes = top.firstOrNull()?.optInt("votes") ?: 0,
                            isMine = own.isNotBlank() && candidate.optString("id") == own,
                            sharingEnabled = !shareLookupBusy &&
                                BlinkContestPolicy.canShareVoteInvite(status),
                            votingEnabled = !busy && BlinkContestPolicy.canVote(
                                status, myVote, own, candidate.optString("id")),
                            onView = { inspectedCandidate = candidate },
                            onShare = { openFlyer(candidate) },
                            onVote = {
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
                        )
                    }
                }
            }
        }
    }
    inspectedCandidate?.let { candidate ->
        val votes = candidate.optInt("votes")
        val highest = detail?.optJSONArray("leaderboard")
            ?.optJSONObject(0)?.optInt("votes")?.coerceAtLeast(1) ?: 1
        val shownProgress = contestAnimatedFloat((votes.toFloat() / highest).coerceIn(0f, 1f))
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
                        progress = { shownProgress },
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
                TextButton(
                    enabled = BlinkContestPolicy.canShareVoteInvite(
                        detail?.optJSONObject("contest")?.optString("status").orEmpty()),
                    onClick = {
                        openFlyer(candidate)
                        inspectedCandidate = null
                    }
                ) { Text("Share flyer") }
            }
        )
    }
    flyerCandidate?.let { candidate ->
        BlinkContestShareDialog(candidate = candidate, onDismiss = { flyerCandidate = null })
    }
}
 

/** Shared live leaderboard row and compact-device screenshot fixture. */
@Composable
internal fun BlinkContestLeaderboardRow(
    candidate: JSONObject,
    sharingEnabled: Boolean = true,
    votingEnabled: Boolean = true,
    onView: () -> Unit,
    onShare: () -> Unit,
    onVote: () -> Unit,
    index: Int = 0,
    leaderVotes: Int = 0,
    isMine: Boolean = false
) {
    val rank = candidate.optInt("rank")
    val votes = contestAnimatedInt(candidate.optInt("votes"))
    val share = contestAnimatedFloat(
        if (leaderVotes > 0) (candidate.optInt("votes").toFloat() / leaderVotes).coerceIn(0f, 1f) else 0f)
    val topThree = rank in 1..3
    val accent = if (topThree) contestMedalColor(rank) else MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(18.dp)
    ElevatedCard(
        Modifier.fillMaxWidth().contestEntrance(index)
            .then(if (isMine) Modifier.border(1.5.dp, accent, shape) else Modifier)
            .contestPressable(onClick = onView),
        shape = shape
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = .18f)),
                    contentAlignment = Alignment.Center) {
                    Text("#" + rank, color = accent, fontWeight = FontWeight.Bold,
                        fontSize = if (rank >= 100) 10.sp else 13.sp, maxLines = 1)
                }
                BlinkContestAvatar(candidate.optString("avatar_url"),
                    candidate.optString("username"), 44.dp,
                    ring = accent.takeIf { topThree || isMine })
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("@" + candidate.optString("username"),
                            fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                        if (isMine) BlinkContestStatusChip("You", "open")
                    }
                    Text(contestCount(votes) + " votes" +
                        BlinkContestRankMovement.label(candidate),
                        fontSize = 12.sp, color = accent, fontWeight = FontWeight.Medium)
                    Text(candidate.optString("statement"), fontSize = 12.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onView) { Text("View") }
            }
            if (leaderVotes > 0) LinearProgressIndicator(
                progress = { share },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = accent, trackColor = accent.copy(alpha = .15f)
            )
            // Actions occupy a separate row so both remain operable at 320dp.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onShare, enabled = sharingEnabled,
                    modifier = Modifier.weight(1f)) { Text("Share flyer") }
                Button(onClick = onVote, enabled = votingEnabled,
                    modifier = Modifier.weight(1f)) { Text("Vote free") }
            }
        }
    }
}
