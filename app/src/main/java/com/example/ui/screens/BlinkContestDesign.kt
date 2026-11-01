package com.example.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blinkng.shared.BlinkContestPolicy
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.text.NumberFormat
import java.time.Duration
import java.time.OffsetDateTime
import java.time.Instant
import java.util.Locale

private val contestNavy = Color(0xFF0B1626)
private val contestBlue = Color(0xFF1685FF)
private val contestGold = Color(0xFFFFC857)
private val contestSilver = Color(0xFFC9D4E3)
private val contestBronze = Color(0xFFD9915B)

// ─────────────────────────────────────────────────────────────────────────────
// Formatting helpers
// ─────────────────────────────────────────────────────────────────────────────

internal fun contestCount(value: Int): String =
    NumberFormat.getIntegerInstance(Locale.US).format(value)

/** Full amounts display with separators; compact mode abbreviates millions+ so large prizes never clip. */
internal fun contestNaira(value: Long, compact: Boolean = false): String {
    fun short(x: Double, unit: String) =
        "₦" + String.format(Locale.US, "%.1f", x).removeSuffix(".0") + unit
    return when {
        compact && value >= 1_000_000_000L -> short(value / 1_000_000_000.0, "B")
        compact && value >= 1_000_000L -> short(value / 1_000_000.0, "M")
        else -> "₦" + NumberFormat.getIntegerInstance(Locale.US).format(value)
    }
}

internal fun contestMedalColor(place: Int): Color = when (place) {
    1 -> contestGold
    2 -> contestSilver
    3 -> contestBronze
    else -> contestBlue
}

internal fun contestStatusColor(status: String): Color = when (status.lowercase()) {
    "voting", "approved", "paid", "accepted" -> Color(0xFF2ECC71)
    "registration", "published", "open" -> contestBlue
    "upcoming", "pending", "processing" -> Color(0xFFF5B942)
    "review" -> Color(0xFFB07CFF)
    "paused" -> Color(0xFFFF9F43)
    "cancelled", "rejected", "disqualified", "failed", "denied" -> Color(0xFFFF6B6B)
    "completed", "draft" -> Color(0xFF8FA3BC)
    else -> contestBlue
}

// ─────────────────────────────────────────────────────────────────────────────
// Motion primitives
// ─────────────────────────────────────────────────────────────────────────────

/** Counts up from zero on first appearance, then glides between values as votes change. */
@Composable
internal fun contestAnimatedInt(value: Int, millis: Int = 800): Int {
    var target by remember { mutableIntStateOf(0) }
    LaunchedEffect(value) { target = value }
    return animateIntAsState(target, tween(millis, easing = FastOutSlowInEasing), label = "count").value
}

@Composable
internal fun contestAnimatedFloat(value: Float, millis: Int = 900): Float {
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(value) { target = value }
    return animateFloatAsState(target, tween(millis, easing = FastOutSlowInEasing), label = "fraction").value
}

/** Springy press feedback with the normal ripple. Drop-in replacement for clickable. */
internal fun Modifier.contestPressable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )
    this.graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = source, indication = LocalIndication.current,
            enabled = enabled, onClick = onClick
        )
}

/** Fade + rise entrance with a short stagger. Use on list cards: Modifier.contestEntrance(index). */
internal fun Modifier.contestEntrance(index: Int = 0): Modifier = composed {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(index.coerceIn(0, 8) * 55L); shown = true }
    val fade by animateFloatAsState(if (shown) 1f else 0f, tween(360), label = "entranceFade")
    val lift by animateFloatAsState(
        if (shown) 0f else 36f, tween(440, easing = FastOutSlowInEasing), label = "entranceLift"
    )
    this.graphicsLayer { alpha = fade; translationY = lift }
}

@Composable
internal fun BlinkContestEntrance(
    index: Int = 0,
    modifier: Modifier = Modifier.fillMaxWidth(),
    content: @Composable () -> Unit
) {
    Box(modifier.contestEntrance(index)) { content() }
}

/** Whole-screen slide/fade used when switching between the contest list and a contest. */
@Composable
internal fun BlinkContestScreenEnter(
    fromRight: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        if (shown) 1f else 0f, tween(320, easing = FastOutSlowInEasing), label = "screenEnter"
    )
    Column(
        Modifier.fillMaxSize().graphicsLayer {
            alpha = progress
            translationX = (1f - progress) * (if (fromRight) 64f else -64f)
        },
        content = content
    )
}

internal fun Modifier.contestShimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -400f, targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "shimmerX"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    this.background(
        Brush.linearGradient(
            listOf(base.copy(alpha = .45f), base.copy(alpha = .95f), base.copy(alpha = .45f)),
            start = Offset(x, 0f), end = Offset(x + 400f, 220f)
        )
    )
}

@Composable
internal fun BlinkContestPulseDot(color: Color = Color(0xFF3DDC84), dot: Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val ring by transition.animateFloat(
        initialValue = 1f, targetValue = 2.4f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing)),
        label = "pulseRing"
    )
    val fade by transition.animateFloat(
        initialValue = .55f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing)),
        label = "pulseFade"
    )
    Box(Modifier.size(dot * 2.4f), contentAlignment = Alignment.Center) {
        Box(Modifier.size(dot).graphicsLayer { scaleX = ring; scaleY = ring; alpha = fade }
            .background(color, CircleShape))
        Box(Modifier.size(dot).background(color, CircleShape))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun BlinkContestStatusChip(label: String, status: String = label, live: Boolean = false) {
    val color by animateColorAsState(contestStatusColor(status), tween(300), label = "chipColor")
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = .16f))
            .border(1.dp, color.copy(alpha = .38f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (live) BlinkContestPulseDot(color, 5.dp)
        Text(label.uppercase(), color = color, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = .6.sp, maxLines = 1)
    }
}

/** Banner that slides in; use for errors (with Retry/Dismiss) and success notices. */
@Composable
internal fun BlinkContestBanner(
    text: String,
    isError: Boolean,
    actionLabel: String? = null,
    modifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    onAction: () -> Unit = {}
) {
    val tone = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(modifier.contestEntrance()) {
        Surface(
            color = tone.copy(alpha = .10f), shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, tone.copy(alpha = .35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(text, color = tone, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
internal fun BlinkContestSectionTitle(title: String, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(4.dp).height(if (subtitle == null) 20.dp else 34.dp)
            .clip(RoundedCornerShape(2.dp)).background(contestBlue))
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun BlinkContestStatTile(
    label: String, value: Int, modifier: Modifier = Modifier, accent: Color = contestBlue
) {
    val shown = contestAnimatedInt(value)
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = .12f))
            .border(1.dp, accent.copy(alpha = .30f), RoundedCornerShape(14.dp)).padding(12.dp)
    ) {
        Text(contestCount(shown), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = accent)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Sliding-indicator tab bar for Ongoing / Upcoming / Completed with live counts. */
@Composable
internal fun BlinkContestTabs(
    names: List<String>, selected: String, counts: Map<String, Int> = emptyMap(),
    onSelect: (String) -> Unit
) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp)
    ) {
        val tabWidth = maxWidth / names.size
        val offset by animateDpAsState(
            tabWidth * names.indexOf(selected).coerceAtLeast(0),
            spring(dampingRatio = .8f, stiffness = Spring.StiffnessMedium), label = "tabOffset"
        )
        Box(Modifier.offset(x = offset).width(tabWidth).height(42.dp)
            .clip(RoundedCornerShape(12.dp)).background(contestBlue))
        Row(Modifier.fillMaxWidth()) {
            names.forEach { name ->
                val active = name == selected
                val textColor by animateColorAsState(
                    if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    tween(220), label = "tabText"
                )
                Box(
                    Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(name) },
                    contentAlignment = Alignment.Center
                ) {
                    val count = counts[name]
                    Text(if (count == null || count == 0) name else "$name · $count",
                        color = textColor, fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun BlinkContestSkeletonCard(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().heightIn(min = 205.dp).clip(RoundedCornerShape(22.dp))
            .contestShimmer().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf(.35f, .8f, .6f, .45f).forEach { fraction ->
            Box(Modifier.fillMaxWidth(fraction).height(if (fraction == .8f) 22.dp else 12.dp)
                .clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = .22f)))
        }
    }
}

@Composable
internal fun BlinkContestEmptyState(title: String, subtitle: String) {
    val transition = rememberInfiniteTransition(label = "float")
    val bob by transition.animateFloat(
        initialValue = -6f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )
    Column(Modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("🏆", fontSize = 44.sp, modifier = Modifier.graphicsLayer { translationY = bob })
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun BlinkContestPrizeRow(first: Long, second: Long, third: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple("🥇", "1st", first), Triple("🥈", "2nd", second), Triple("🥉", "3rd", third))
            .forEachIndexed { i, (medal, label, amount) ->
                val accent = contestMedalColor(i + 1)
                val shape = RoundedCornerShape(16.dp)
                Column(
                    Modifier.weight(1f).contestEntrance(i).clip(shape)
                        .background(Brush.verticalGradient(listOf(accent.copy(alpha = .24f), contestNavy)))
                        .border(1.dp, accent.copy(alpha = .45f), shape).padding(vertical = 12.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(medal, fontSize = 22.sp)
                    Text(label, fontSize = 11.sp, color = Color(0xFFBBD9FF))
                    Text(contestNaira(amount, compact = true), color = Color.White, fontSize = 14.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Deadline + countdown (logic unchanged)
// ─────────────────────────────────────────────────────────────────────────────

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
    val end = remember(iso) { runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull()
        ?: runCatching { Instant.parse(iso) }.getOrNull() }
    // Tick every second in the final hour so the countdown feels alive, otherwise every 30s.
    LaunchedEffect(iso) {
        while (true) {
            now = System.currentTimeMillis()
            val remainingMs = (end?.toEpochMilli() ?: Long.MAX_VALUE) - now
            delay(if (remainingMs in 1..3_600_000L) 1_000L else 30_000L)
        }
    }
    val left = end?.let { Duration.ofMillis(it.toEpochMilli() - now) }
    val remaining = when {
        left == null -> iso.replace('T', ' ').take(16)
        left.isNegative || left.isZero -> "Closed"
        left.toDays() > 0 -> left.toDays().toString() + "d " + (left.toHours() % 24) + "h"
        left.toHours() > 0 -> left.toHours().toString() + "h " + (left.toMinutes() % 60) + "m"
        else -> left.toMinutes().toString() + "m " + (left.seconds % 60) + "s"
    }
    val urgent = left != null && !left.isNegative && !left.isZero && left.toHours() < 1
    val tint by animateColorAsState(
        if (urgent) Color(0xFFFFC857) else Color(0xFFBDDFFF), tween(400), label = "countdownTint"
    )
    val text = if (iso.isBlank()) label else label + " · " + remaining
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (urgent) BlinkContestPulseDot(Color(0xFFFFC857), 5.dp)
        Crossfade(targetState = text, animationSpec = tween(250), label = "countdownText") { shown ->
            Text(shown, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ContestPrizeChip(medal: String, amount: Long, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.clip(shape).background(Color.White.copy(alpha = .10f))
            .border(1.dp, Color.White.copy(alpha = .14f), shape)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(medal, fontSize = 12.sp)
        Text(contestNaira(amount, compact = true), color = Color.White, fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun BlinkContestHeroCard(contest: JSONObject, onOpen: () -> Unit) {
    val context = LocalContext.current
    val banner = contest.optString("banner_url").takeIf { it.startsWith("https://") }
    val status = contest.optString("status")
    val nudge by rememberInfiniteTransition(label = "arrow").animateFloat(
        initialValue = 0f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "arrowNudge"
    )
    Surface(color = contestNavy, shape = RoundedCornerShape(22.dp), shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth().contestPressable(onClick = onOpen)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 215.dp)) {
            if (banner != null) AsyncImage(
                model = remember(banner) {
                    ImageRequest.Builder(context).data(banner).crossfade(500).build()
                },
                contentDescription = "Contest cover", contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                listOf(Color(0x66060E19), Color(0xF7060E19)))))
            Column(Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("BLINK CONTEST  ·  FREE ENTRY & VOTING", modifier = Modifier.weight(1f),
                        color = Color(0xFF69B9FF), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = .5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    BlinkContestStatusChip(
                        BlinkContestPolicy.statusLabel(status), status,
                        live = status == "voting" || status == "registration")
                }
                Text(contest.optString("title"), color = Color.White,
                    fontSize = 23.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(contest.optString("description"), color = Color(0xFFCAD5E6),
                    maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, lineHeight = 17.sp)
                if (status == "cancelled") {
                    Text("Cancelled · No prizes will be awarded",
                        color = Color(0xFFFFCCCB), fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold)
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ContestPrizeChip("🥇", contest.optLong("prize_first"), Modifier.weight(1f))
                        ContestPrizeChip("🥈", contest.optLong("prize_second"), Modifier.weight(1f))
                        ContestPrizeChip("🥉", contest.optLong("prize_third"), Modifier.weight(1f))
                    }
                }
                val (deadlineLabel, deadline) = contestDeadline(contest)
                BlinkContestCountdown(deadline, deadlineLabel)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(contestCount(contestAnimatedInt(contest.optInt("contestants"))) + " contestants",
                        color = Color(0xFFCAD5E6), fontSize = 11.sp,
                        modifier = Modifier.weight(1f))
                    Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(when (status) {
                                "completed" -> "See winners"
                                "cancelled" -> "See details"
                                else -> "Open contest"
                            }, color = contestNavy, fontWeight = FontWeight.Bold)
                            // Keep the animated arrow as its own semantic node; the CTA label remains stable.
                            Text("→", color = contestNavy, fontWeight = FontWeight.Bold,
                                modifier = Modifier.offset(x = nudge.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BlinkContestAvatar(url: String, username: String, size: Dp, ring: Color? = null) {
    val context = LocalContext.current
    Box(
        Modifier.size(size).clip(CircleShape).background(Color(0xFF28415D))
            .then(if (ring != null) Modifier.border(2.dp, ring, CircleShape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (url.startsWith("https://")) AsyncImage(
            model = remember(url) { ImageRequest.Builder(context).data(url).crossfade(400).build() },
            contentDescription = "Contestant @" + username,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
        ) else Text(username.take(1).uppercase().ifBlank { "B" },
            color = Color.White, fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun BlinkContestCandidateSpotlight(
    candidate: JSONObject, onView: () -> Unit
) {
    val votes = contestAnimatedInt(candidate.optInt("votes"))
    Surface(shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, contestBlue.copy(alpha = .3f)),
        modifier = Modifier.fillMaxWidth().contestPressable(onClick = onView)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BlinkContestAvatar(candidate.optString("avatar_url"),
                candidate.optString("username"), 54.dp,
                ring = contestMedalColor(candidate.optInt("rank")).takeIf { candidate.optInt("rank") in 1..3 })
            Column(Modifier.weight(1f)) {
                Text("@" + candidate.optString("username"), fontWeight = FontWeight.Bold)
                Text("#" + candidate.optInt("rank") + " · " +
                    contestCount(votes) + " votes" + BlinkContestRankMovement.label(candidate),
                    color = contestBlue, fontSize = 12.sp)
                Text(candidate.optString("statement"), fontSize = 11.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("View →", color = contestBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun BlinkContestPodium(leaders: List<JSONObject>, onView: (JSONObject) -> Unit) {
    if (leaders.isEmpty()) return
    var rise by remember { mutableStateOf(false) }
    LaunchedEffect(leaders.size) { rise = true }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Leaderboard leaders", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom) {
            listOf(1, 0, 2).forEach { index ->
                val person = leaders.getOrNull(index)
                if (person == null) Spacer(Modifier.weight(1f))
                else {
                    val place = person.optInt("rank").takeIf { it in 1..3 } ?: (index + 1)
                    val accent = contestMedalColor(place)
                    val stagger = (3 - place) * 140
                    val podiumHeight by animateDpAsState(
                        if (rise) (if (place == 1) 176.dp else 148.dp) else 56.dp,
                        tween(650, delayMillis = stagger, easing = FastOutSlowInEasing),
                        label = "podiumHeight"
                    )
                    val contentFade by animateFloatAsState(
                        if (rise) 1f else 0f, tween(450, delayMillis = stagger + 250),
                        label = "podiumFade"
                    )
                    val votes = contestAnimatedInt(person.optInt("votes"), 1000)
                    val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    Box(
                        Modifier.weight(1f).height(podiumHeight).clip(shape)
                            .background(Brush.verticalGradient(
                                listOf(accent.copy(alpha = .30f), contestNavy)))
                            .border(1.dp, accent.copy(alpha = .45f), shape)
                            .contestPressable { onView(person) }.padding(7.dp)
                    ) {
                        Column(
                            Modifier.fillMaxSize().graphicsLayer { alpha = contentFade },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Text(when (place) { 1 -> "🥇"; 2 -> "🥈"; else -> "🥉" }, fontSize = 20.sp)
                            BlinkContestAvatar(person.optString("avatar_url"),
                                person.optString("username"), if (place == 1) 54.dp else 44.dp,
                                ring = accent)
                            Text("@" + person.optString("username"), color = Color.White,
                                fontSize = 10.sp, maxLines = 1, fontWeight = FontWeight.Bold,
                                overflow = TextOverflow.Ellipsis)
                            Text(contestCount(votes) + " votes" +
                                BlinkContestRankMovement.label(person), color = Color(0xFFBBD9FF),
                                fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BlinkContestWinnerCard(winner: JSONObject) {
    val place = winner.optInt("place")
    val accent = contestMedalColor(place)
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().contestEntrance(place.coerceAtLeast(0)).clip(shape)
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = .26f), contestNavy)))
            .border(1.dp, accent.copy(alpha = .5f), shape).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(when (place) { 1 -> "🥇"; 2 -> "🥈"; else -> "🥉" }, fontSize = 28.sp)
        BlinkContestAvatar(winner.optString("avatar_url"),
            winner.optString("username"), 44.dp, ring = accent)
        Column(Modifier.weight(1f)) {
            Text("@" + winner.optString("username"), color = Color.White,
                fontWeight = FontWeight.Bold)
            Text("Prize " + contestNaira(winner.optLong("prize_amount")),
                color = Color(0xFFBBD9FF))
            Text("Payment: " + winner.optString("payment_status") + " (admin recorded)",
                color = Color(0xFFADBFD2), fontSize = 10.sp)
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
