package com.blinkng.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.desktop.DesktopAppState
import com.blinkng.desktop.data.DesktopRpcActions
import com.blinkng.shared.BlinkAchievement
import com.blinkng.shared.BlinkAchievementRarity
import com.blinkng.shared.BlinkCreatorWeekSummary
import com.blinkng.shared.BlinkProgressHubState
import com.blinkng.shared.BlinkProgressReward
import com.blinkng.shared.BlinkWeeklyMission
import com.blinkng.shared.BlinkXpHistoryEntry
import com.blinkng.shared.progressRewardLabel
import com.blinkng.shared.xpProgress
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class DesktopWeeklyChest(
    val progress: Int = 0,
    val target: Int = 4,
    val coinReward: Int = 0,
    val xpReward: Int = 0,
    val eligible: Boolean = false,
    val claimed: Boolean = false,
) {
    val claimable: Boolean get() = eligible && !claimed
}

@Composable
fun DesktopProgressHubPanel(
    state: DesktopAppState,
    actions: DesktopRpcActions,
) {
    val scope = rememberCoroutineScope()
    var hub by remember { mutableStateOf(BlinkProgressHubState()) }
    var chest by remember { mutableStateOf(DesktopWeeklyChest()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyKey by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    suspend fun reloadProgress() {
        loading = true
        error = null
        runCatching { actions.getProgressHub() }
            .onSuccess { payload ->
                hub = parseDesktopProgressHub(payload)
                chest = parseDesktopWeeklyChest(payload.optJSONObject("weekly_chest"))
            }
            .onFailure { error = it.message ?: "Progress could not be loaded." }
        loading = false
    }

    fun runAction(key: String, action: suspend () -> JSONObject) {
        if (busyKey != null) return
        busyKey = key
        error = null
        scope.launch {
            runCatching { action() }
                .onSuccess {
                    try {
                        state.refreshProfile()
                    } catch (_: Throwable) {
                        // Hub reload below remains authoritative even if the profile cache refresh fails.
                    }
                    reloadKey += 1
                }
                .onFailure { error = it.message ?: "That progress reward could not be claimed." }
            busyKey = null
        }
    }

    LaunchedEffect(state.profile?.id, reloadKey) {
        if (state.profile != null) reloadProgress()
    }

    val xp = remember(hub.totalXp, hub.xpLevel, state.profile?.totalXp, state.profile?.xpLevel) {
        xpProgress(
            hub.totalXp.takeIf { it > 0L } ?: (state.profile?.totalXp ?: 0L),
            hub.xpLevel.takeIf { it > 0 } ?: (state.profile?.xpLevel ?: 1),
        )
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("BLINK Progress Hub", fontWeight = FontWeight.Black, fontSize = 21.sp)
                    Text(
                        "Level ${xp.level} • ${xp.totalXp} XP • ${xp.xpToNextLevel} XP to next level",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = { reloadKey += 1 }) { Text("Refresh") }
            }

            LinearProgressIndicator(
                progress = { xp.progressFraction },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(100.dp)),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DesktopMetric("Streak", "${hub.dailyStreak} days")
                DesktopMetric("Campus", desktopRank(hub.campusRank))
                DesktopMetric("World", desktopRank(hub.worldRank))
                DesktopMetric("Coins", hub.coinBalance.toString())
            }

            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { InlineProgressError(it) }

            HorizontalDivider()
            ProgressHeading("Weekly Missions", "Complete larger goals across the week.")
            if (!loading && hub.weeklyMissions.isEmpty()) {
                Text("Weekly missions are syncing.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            hub.weeklyMissions.forEach { mission ->
                DesktopWeeklyMissionCard(
                    mission = mission,
                    busy = busyKey == "weekly:${mission.key}",
                    onClaim = {
                        runAction("weekly:${mission.key}") { actions.claimWeeklyMission(mission.key) }
                    },
                )
            }

            DesktopWeeklyChestCard(
                chest = chest,
                busy = busyKey == "weekly_chest",
                onClaim = {
                    runAction("weekly_chest") { actions.claimWeeklyCompletionChest() }
                },
            )

            HorizontalDivider()
            ProgressHeading("Achievements", "One shared achievement system across BLINK.")
            hub.achievements.take(8).forEach { achievement ->
                DesktopAchievementCard(
                    achievement = achievement,
                    busy = busyKey == "achievement:${achievement.key}",
                    onClaim = {
                        runAction("achievement:${achievement.key}") {
                            actions.claimAchievement(achievement.key)
                        }
                    },
                )
            }

            HorizontalDivider()
            ProgressHeading("Milestone Rewards", "Level and streak rewards remain server-authoritative.")
            hub.rewards.take(10).forEach { reward ->
                DesktopRewardRow(
                    reward = reward,
                    busy = busyKey == "reward:${reward.key}",
                    onClaim = {
                        runAction("reward:${reward.key}") {
                            actions.claimProgressReward(reward.key)
                        }
                    },
                )
            }

            HorizontalDivider()
            ProgressHeading("This Week", "Private creator recap from real activity.")
            DesktopCreatorWeek(hub.creatorWeek)

            HorizontalDivider()
            ProgressHeading("Recent XP", "A transparent history of progression awards.")
            if (hub.xpHistory.isEmpty()) {
                Text("XP history will appear here as you use BLINK.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            hub.xpHistory.take(10).forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(desktopHumanize(entry.eventType), fontSize = 11.5.sp)
                    Text("+${entry.xpDelta} XP", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun DesktopMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Black, fontSize = 13.sp)
        Text(label, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProgressHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontWeight = FontWeight.Black, fontSize = 16.sp)
        Text(subtitle, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DesktopWeeklyMissionCard(
    mission: BlinkWeeklyMission,
    busy: Boolean,
    onClaim: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(mission.title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(mission.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${mission.progress.coerceAtMost(mission.target)}/${mission.target}", fontWeight = FontWeight.Black, fontSize = 10.5.sp)
            }
            LinearProgressIndicator(
                progress = { mission.progressFraction },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(100.dp)),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("+${mission.coinReward} coins • +${mission.xpReward} XP", fontSize = 10.sp)
                OutlinedButton(onClick = onClaim, enabled = mission.claimable && !busy) {
                    Text(
                        when {
                            mission.claimed -> "Claimed"
                            busy -> "Claiming…"
                            mission.claimable -> "Claim"
                            else -> "In progress"
                        },
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopWeeklyChestCard(
    chest: DesktopWeeklyChest,
    busy: Boolean,
    onClaim: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Weekly Completion Chest", fontWeight = FontWeight.Black, fontSize = 12.5.sp)
                Text(
                    "${chest.progress.coerceAtMost(chest.target)}/${chest.target} missions • +${chest.coinReward} coins • +${chest.xpReward} XP",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onClaim, enabled = chest.claimable && !busy) {
                Text(
                    when {
                        chest.claimed -> "Claimed"
                        busy -> "Opening…"
                        chest.claimable -> "Open"
                        else -> "Locked"
                    },
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun DesktopAchievementCard(
    achievement: BlinkAchievement,
    busy: Boolean,
    onClaim: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(achievement.title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(
                        "${achievement.category} • ${achievement.rarity.label}",
                        fontSize = 9.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${achievement.progress.coerceAtMost(achievement.target)}/${achievement.target}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            LinearProgressIndicator(
                progress = { achievement.progressFraction },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(100.dp)),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("+${achievement.coinReward} coins • +${achievement.xpReward} XP", fontSize = 10.sp)
                OutlinedButton(onClick = onClaim, enabled = achievement.claimable && !busy) {
                    Text(
                        when {
                            achievement.claimed -> "Earned"
                            busy -> "Claiming…"
                            achievement.claimable -> "Claim"
                            else -> "Locked"
                        },
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopRewardRow(
    reward: BlinkProgressReward,
    busy: Boolean,
    onClaim: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(reward.title, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
            Text(
                "${progressRewardLabel(reward.type, reward.threshold)} • +${reward.coinReward} coins • +${reward.xpReward} XP",
                fontSize = 9.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onClaim, enabled = reward.claimable && !busy) {
            Text(
                when {
                    reward.claimed -> "Claimed"
                    busy -> "…"
                    reward.claimable -> "Claim"
                    else -> "Locked"
                },
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun DesktopCreatorWeek(summary: BlinkCreatorWeekSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        DesktopMetric("Posts", summary.postsCreated.toString())
        DesktopMetric("Reels", summary.reelsCreated.toString())
        DesktopMetric("Views", desktopCompact(summary.viewsReceived))
        DesktopMetric("Likes", desktopCompact(summary.likesReceived))
        DesktopMetric("Comments", desktopCompact(summary.commentsReceived))
        DesktopMetric("Shares", desktopCompact(summary.sharesReceived))
        DesktopMetric("Followers", "+${summary.followersGained}")
    }
}

@Composable
private fun InlineProgressError(message: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            message,
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

private fun parseDesktopProgressHub(payload: JSONObject): BlinkProgressHubState =
    BlinkProgressHubState(
        weekStart = payload.optString("week_start"),
        totalXp = payload.optLong("total_xp", 0L),
        xpLevel = payload.optInt("xp_level", 1).coerceIn(1, 100),
        dailyStreak = payload.optInt("daily_streak", 0).coerceAtLeast(0),
        worldRank = payload.optInt("world_rank", 0).coerceAtLeast(0),
        campusRank = payload.optInt("campus_rank", 0).coerceAtLeast(0),
        coinBalance = payload.optLong("coin_balance", 0L).coerceAtLeast(0L),
        weeklyMissions = parseDesktopWeeklyMissions(payload.optJSONArray("weekly_missions")),
        achievements = parseDesktopAchievements(payload.optJSONArray("achievements")),
        rewards = parseDesktopRewards(payload.optJSONArray("rewards")),
        xpHistory = parseDesktopXpHistory(payload.optJSONArray("xp_history")),
        creatorWeek = parseDesktopCreatorWeek(payload.optJSONObject("creator_week")),
    )

private fun parseDesktopWeeklyMissions(array: JSONArray?): List<BlinkWeeklyMission> = buildList {
    if (array == null) return@buildList
    for (index in 0 until array.length()) {
        val row = array.optJSONObject(index) ?: continue
        add(
            BlinkWeeklyMission(
                key = row.optString("key"),
                title = row.optString("title"),
                description = row.optString("description"),
                progress = row.optInt("progress", 0),
                target = row.optInt("target", 1).coerceAtLeast(1),
                coinReward = row.optInt("coin_reward", 0),
                xpReward = row.optInt("xp_reward", 0),
                claimed = row.optBoolean("claimed", false),
            )
        )
    }
}

private fun parseDesktopAchievements(array: JSONArray?): List<BlinkAchievement> = buildList {
    if (array == null) return@buildList
    for (index in 0 until array.length()) {
        val row = array.optJSONObject(index) ?: continue
        add(
            BlinkAchievement(
                key = row.optString("key"),
                title = row.optString("title"),
                description = row.optString("description"),
                category = row.optString("category"),
                rarity = BlinkAchievementRarity.fromWire(row.optString("rarity")),
                progress = row.optLong("progress", 0L),
                target = row.optLong("target", 1L).coerceAtLeast(1L),
                coinReward = row.optInt("coin_reward", 0),
                xpReward = row.optInt("xp_reward", 0),
                claimed = row.optBoolean("claimed", false),
            )
        )
    }
}

private fun parseDesktopRewards(array: JSONArray?): List<BlinkProgressReward> = buildList {
    if (array == null) return@buildList
    for (index in 0 until array.length()) {
        val row = array.optJSONObject(index) ?: continue
        add(
            BlinkProgressReward(
                key = row.optString("key"),
                type = row.optString("type"),
                threshold = row.optInt("threshold", 0),
                title = row.optString("title"),
                description = row.optString("description"),
                coinReward = row.optInt("coin_reward", 0),
                xpReward = row.optInt("xp_reward", 0),
                cosmeticCatalogId = row.optString("cosmetic_catalog_id").takeIf {
                    it.isNotBlank() && it != "null"
                },
                eligible = row.optBoolean("eligible", false),
                claimed = row.optBoolean("claimed", false),
            )
        )
    }
}

private fun parseDesktopXpHistory(array: JSONArray?): List<BlinkXpHistoryEntry> = buildList {
    if (array == null) return@buildList
    for (index in 0 until array.length()) {
        val row = array.optJSONObject(index) ?: continue
        add(
            BlinkXpHistoryEntry(
                eventType = row.optString("event_type"),
                xpDelta = row.optInt("xp_delta", 0),
                sourceType = row.optString("source_type"),
                createdAt = row.optString("created_at"),
            )
        )
    }
}

private fun parseDesktopCreatorWeek(row: JSONObject?): BlinkCreatorWeekSummary =
    if (row == null) BlinkCreatorWeekSummary()
    else BlinkCreatorWeekSummary(
        postsCreated = row.optInt("posts_created", 0),
        reelsCreated = row.optInt("reels_created", 0),
        viewsReceived = row.optLong("views_received", 0L),
        likesReceived = row.optLong("likes_received", 0L),
        commentsReceived = row.optLong("comments_received", 0L),
        sharesReceived = row.optLong("shares_received", 0L),
        followersGained = row.optInt("followers_gained", 0),
    )

private fun parseDesktopWeeklyChest(row: JSONObject?): DesktopWeeklyChest =
    if (row == null) DesktopWeeklyChest()
    else DesktopWeeklyChest(
        progress = row.optInt("progress", 0),
        target = row.optInt("target", 4).coerceAtLeast(1),
        coinReward = row.optInt("coin_reward", 0),
        xpReward = row.optInt("xp_reward", 0),
        eligible = row.optBoolean("eligible", false),
        claimed = row.optBoolean("claimed", false),
    )

private fun desktopRank(rank: Int): String = if (rank > 0) "#$rank" else "—"

private fun desktopCompact(value: Long): String = when {
    value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format("%.1fK", value / 1_000.0)
    else -> value.toString()
}

private fun desktopHumanize(event: String): String =
    event.replace('_', ' ')
        .trim()
        .split(' ')
        .filter(String::isNotBlank)
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
        .ifBlank { "BLINK activity" }
