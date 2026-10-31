package com.example.ui.screens
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.Duration
import java.time.OffsetDateTime
import java.time.Instant

private val contestNavy = Color(0xFF0B1626)
private val contestBlue = Color(0xFF1685FF)
internal fun contestDeadline(
    contest: JSONObject, now: Instant = Instant.now()
): Pair<String, String> = when (contest.optString("status")) {
    "upcoming" -> {
        // A contest can be "upcoming" both before applications open and in the
        // gap after they close but before voting starts. Never show a past
        // application-opening date as the next upcoming event.
        val applicationClose = runCatching {
            OffsetDateTime.parse(contest.optString("registration_closes_at")).toInstant()
        }.getOrNull()
        if (applicationClose != null && !now.isBefore(applicationClose))
            "Voting opens" to contest.optString("voting_opens_at")
        else "Applications open" to contest.optString("registration_opens_at")
    }
    "registration" -> "Applications close" to contest.optString("registration_closes_at")
    "voting" -> "Voting closes" to contest.optString("voting_closes_at")
    "review" -> "Results scheduled" to contest.optString("results_at")
    "completed" -> "Results confirmed" to ""
    "cancelled" -> "Contest cancelled" to ""
    "paused" -> "Contest paused" to ""
    else -> "Voting closes" to contest.optString("voting_closes_at")
}
@Composable
internal fun BlinkContestCountdown(iso: String, label: String = "Voting closes") {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(iso) { while (true) { now = System.currentTimeMillis(); delay(30_000) } }
    val end = remember(iso) { runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull()
        ?: runCatching { Instant.parse(iso) }.getOrNull() }
    val left = end?.let { Duration.ofMillis(it.toEpochMilli() - now) }
    val remaining = when {
        left == null -> iso.replace('T',' ').take(16)
        left.isNegative || left.isZero -> "Closed"
        left.toDays() > 0 -> left.toDays().toString() + "d " + (left.toHours() % 24) + "h"
        else -> left.toHours().toString() + "h " + (left.toMinutes() % 60) + "m"
    }
    Text(if (iso.isBlank()) label else label + " · " + remaining,
        color = Color(0xFFBDDFFF), fontSize = 12.sp)
}
@Composable
internal fun BlinkContestHeroCard(contest: JSONObject, onOpen: () -> Unit) {
    val banner = contest.optString("banner_url").takeIf { it.startsWith("https://") }
    Surface(color = contestNavy, shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 205.dp)) {
            if (banner != null) AsyncImage(model = banner,
                contentDescription = "Contest cover", contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                listOf(Color(0xAE060E19),Color(0xF7060E19)))))
            Column(Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("BLINK CONTEST  ·  FREE ENTRY & VOTING",
                    color = Color(0xFF69B9FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(contest.optString("title"), color = Color.White,
                    fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(contest.optString("description"), color = Color(0xFFCAD5E6),
                    maxLines = 2, fontSize = 12.sp)
                if (contest.optString("status") == "cancelled") {
                    Text("Cancelled · No prizes will be awarded",
                        color = Color(0xFFFFCCCB), fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold)
                } else {
                    Text("🥇 ₦" + "%,d".format(contest.optInt("prize_first")) +
                        "   🥈 ₦" + "%,d".format(contest.optInt("prize_second")) +
                        "   🥉 ₦" + "%,d".format(contest.optInt("prize_third")),
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                val (deadlineLabel, deadline) = contestDeadline(contest)
                BlinkContestCountdown(deadline, deadlineLabel)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(contest.optInt("contestants").toString() + " contestants",
                        color = Color(0xFFCAD5E6), fontSize = 11.sp,
                        modifier = Modifier.weight(1f))
                    Surface(color = Color.White, shape = RoundedCornerShape(12.dp)) {
                        Text(when (contest.optString("status")) {
                            "completed" -> "See winners →"
                            "cancelled" -> "See details →"
                            else -> "Open contest →"
                        },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = contestNavy, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
@Composable
internal fun BlinkContestAvatar(url: String, username: String, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Color(0xFF28415D)),
        contentAlignment = Alignment.Center) {
        if (url.startsWith("https://")) AsyncImage(
            model = url, contentDescription = "Contestant @" + username,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
        ) else Text(username.take(1).uppercase().ifBlank { "B" },
            color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}
@Composable
internal fun BlinkContestCandidateSpotlight(
    candidate: JSONObject, onView: () -> Unit
) {
    Surface(shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, contestBlue.copy(alpha = .3f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onView)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BlinkContestAvatar(candidate.optString("avatar_url"),
                candidate.optString("username"), 54.dp)
            Column(Modifier.weight(1f)) {
                Text("@" + candidate.optString("username"), fontWeight = FontWeight.Bold)
                Text("#" + candidate.optInt("rank") + " · " +
                    candidate.optInt("votes") + " votes" + BlinkContestRankMovement.label(candidate),
                    color = contestBlue, fontSize = 12.sp)
                Text(candidate.optString("statement"), fontSize = 11.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("View →", color = contestBlue, fontSize = 12.sp)
        }
    }
}
@Composable
internal fun BlinkContestPodium(leaders: List<JSONObject>, onView: (JSONObject) -> Unit) {
    if (leaders.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Leaderboard leaders", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom) {
            listOf(1, 0, 2).forEach { index ->
                val person = leaders.getOrNull(index)
                if (person == null) Spacer(Modifier.weight(1f))
                else {
                    val place = person.optInt("rank")
                    Column(Modifier.weight(1f)
                        .height(if (place == 1) 164.dp else 140.dp)
                        .clip(RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp))
                        .background(if (place == 1) Color(0xFF123A68) else contestNavy)
                        .clickable { onView(person) }.padding(7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly) {
                        Text(when(place){1->"🥇";2->"🥈";else->"🥉"},fontSize=19.sp)
                        BlinkContestAvatar(person.optString("avatar_url"),
                            person.optString("username"), if (place==1) 52.dp else 43.dp)
                        Text("@" + person.optString("username"), color = Color.White,
                            fontSize = 10.sp, maxLines = 1, fontWeight = FontWeight.Bold)
                        Text(person.optInt("votes").toString()+" votes" +
                            BlinkContestRankMovement.label(person), color=Color(0xFFBBD9FF),
                            fontSize=10.sp)
                    }
                }
            }
        }
    }
}
@Composable
internal fun BlinkContestWinnerCard(winner: JSONObject) {
    Surface(color = contestNavy, shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(when(winner.optInt("place")){1->"🥇";2->"🥈";else->"🥉"},fontSize=27.sp)
            BlinkContestAvatar(winner.optString("avatar_url"),
                winner.optString("username"), 42.dp)
            Column(Modifier.weight(1f)) {
                Text("@" + winner.optString("username"), color=Color.White,
                    fontWeight=FontWeight.Bold)
                Text("Prize ₦" + "%,d".format(winner.optInt("prize_amount")),
                    color=Color(0xFFBBD9FF))
                Text("Payment: " + winner.optString("payment_status") + " (admin recorded)",
                    color=Color(0xFFADBFD2),fontSize=10.sp)
            }
        }
    }
}

internal object BlinkContestRankMovement {
    fun label(candidate: JSONObject, key: String = "rank_delta"): String {
        if (!candidate.has(key) || candidate.isNull(key)) return ""
        val delta = candidate.optInt(key)
        return when {
            delta > 0 -> " · ↑" + delta
            delta < 0 -> " · ↓" + (-delta)
            else -> " · —"
        }
    }
}
