package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.shared.BlinkAchievement
import com.blinkng.shared.BlinkAchievementRarity
import com.blinkng.shared.BlinkCreatorWeekSummary
import com.blinkng.shared.BlinkDailyMission
import com.blinkng.shared.BlinkProgressHubState
import com.blinkng.shared.BlinkProgressReward
import com.blinkng.shared.BlinkWeeklyMission
import com.blinkng.shared.BlinkXpHistoryEntry
import com.blinkng.shared.progressRewardLabel
import com.blinkng.shared.xpProgress
import com.example.data.models.UserProfile
import com.example.data.supabase.BlinkEconomyService
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkPurple
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class WeeklyChestState(
    val progress: Int = 0,
    val target: Int = 4,
    val coinReward: Int = 0,
    val xpReward: Int = 0,
    val eligible: Boolean = false,
    val claimed: Boolean = false,
) {
    val claimable: Boolean get() = eligible && !claimed
    val progressFraction: Float
        get() = if (target <= 0) 1f else (progress.toFloat() / target.toFloat()).coerceIn(0f, 1f)
}

@Composable
fun ProgressHubEntryCard(
    profile: UserProfile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val xp = remember(profile.totalXp, profile.xpLevel) { xpProgress(profile.totalXp, profile.xpLevel) }
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = BlinkPink.copy(alpha = 0.13f)) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = BlinkPink,
                        modifier = Modifier.padding(9.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("BLINK Progress", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text(
                        "Level ${xp.level} • ${xp.xpToNextLevel} XP to next level",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("Open", fontWeight = FontWeight.Bold, color = BlinkPink, fontSize = 12.sp)
            }
            LinearProgressIndicator(
                progress = { xp.progressFraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(100.dp)),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("🔥 ${profile.dailyStreak} day streak", fontSize = 11.sp)
                Text(
                    "Campus ${rankLabel(profile.campusRank)} • World ${rankLabel(profile.worldRank)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressHubSheet(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onProgressChanged: () -> Unit,
) {
    val service = remember { BlinkEconomyService() }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var dailyMissions by remember { mutableStateOf<List<BlinkDailyMission>>(emptyList()) }
    var hub by remember { mutableStateOf(BlinkProgressHubState()) }
    var chest by remember { mutableStateOf(WeeklyChestState()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyKey by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    fun reload() {
        reloadKey += 1
    }

    fun launchAction(key: String, action: suspend () -> Result<JSONObject>) {
        if (busyKey != null) return
        busyKey = key
        error = null
        scope.launch {
            action()
                .onSuccess {
                    onProgressChanged()
                    reload()
                }
                .onFailure { error = it.message ?: "That reward could not be claimed." }
            busyKey = null
        }
    }

    LaunchedEffect(profile.id, reloadKey) {
        if (profile.id.isBlank()) return@LaunchedEffect
        loading = true
        error = null

        val hubResult = service.progressHub()
        val dailyResult = service.dailyMissions()

        hubResult
            .onSuccess { payload ->
                hub = parseProgressHub(payload)
                chest = parseWeeklyChest(payload.optJSONObject("weekly_chest"))
            }
            .onFailure { error = it.message ?: "Progress could not be loaded." }

        dailyResult
            .onSuccess { dailyMissions = parseDailyMissions(it) }
            .onFailure {
                if (error == null) error = it.message ?: "Daily missions could not be loaded."
            }

        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        val xp = remember(hub.totalXp, hub.xpLevel, profile.totalXp, profile.xpLevel) {
            xpProgress(
                totalXp = hub.totalXp.takeIf { it > 0L } ?: profile.totalXp,
                serverLevel = hub.xpLevel.takeIf { it > 0 } ?: profile.xpLevel,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ProgressHero(
                    level = xp.level,
                    totalXp = xp.totalXp,
                    xpToNext = xp.xpToNextLevel,
                    progress = xp.progressFraction,
                    streak = hub.dailyStreak.takeIf { it > 0 } ?: profile.dailyStreak,
                    campusRank = hub.campusRank.takeIf { it > 0 } ?: profile.campusRank,
                    worldRank = hub.worldRank.takeIf { it > 0 } ?: profile.worldRank,
                    coins = hub.coinBalance,
                    onRefresh = ::reload,
                )
            }

            if (loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }

            error?.let { message ->
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                message,
                                modifier = Modifier.weight(1f),
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            TextButton(onClick = ::reload) { Text("Retry") }
                        }
                    }
                }
            }

            item { SectionTitle("Daily Missions", "Small meaningful actions that reset every day.") }
            if (!loading && dailyMissions.isEmpty()) {
                item { EmptyProgressCard("Daily missions are not available yet.") }
            } else {
                items(dailyMissions, key = { "daily-${it.key}" }) { mission ->
                    MissionCard(
                        title = mission.title,
                        description = mission.description,
                        progress = mission.progress,
                        target = mission.target,
                        progressFraction = mission.progressFraction,
                        coinReward = mission.coinReward,
                        xpReward = mission.xpReward,
                        claimed = mission.claimed,
                        claimable = mission.claimable,
                        busy = busyKey == "daily:${mission.key}",
                        onClaim = {
                            launchAction("daily:${mission.key}") {
                                service.claimDailyMission(mission.key)
                            }
                        },
                    )
                }
            }

            item { SectionTitle("Weekly Missions", "Bigger goals that reward consistency across the week.") }
            if (!loading && hub.weeklyMissions.isEmpty()) {
                item { EmptyProgressCard("Weekly missions will appear after the engagement migration is available.") }
            } else {
                items(hub.weeklyMissions, key = { "weekly-${it.key}" }) { mission ->
                    MissionCard(
                        title = mission.title,
                        description = mission.description,
                        progress = mission.progress,
                        target = mission.target,
                        progressFraction = mission.progressFraction,
                        coinReward = mission.coinReward,
                        xpReward = mission.xpReward,
                        claimed = mission.claimed,
                        claimable = mission.claimable,
                        busy = busyKey == "weekly:${mission.key}",
                        onClaim = {
                            launchAction("weekly:${mission.key}") {
                                service.claimWeeklyMission(mission.key)
                            }
                        },
                    )
                }
            }

            item {
                WeeklyChestCard(
                    chest = chest,
                    busy = busyKey == "weekly_chest",
                    onClaim = {
                        launchAction("weekly_chest") { service.claimWeeklyCompletionChest() }
                    },
                )
            }

            item { SectionTitle("Achievements", "Permanent milestones based on genuine BLINK activity.") }
            if (!loading && hub.achievements.isEmpty()) {
                item { EmptyProgressCard("Achievements are syncing.") }
            } else {
                items(hub.achievements, key = { "achievement-${it.key}" }) { achievement ->
                    AchievementCard(
                        achievement = achievement,
                        busy = busyKey == "achievement:${achievement.key}",
                        onClaim = {
                            launchAction("achievement:${achievement.key}") {
                                service.claimAchievement(achievement.key)
                            }
                        },
                    )
                }
            }

            item {
                SectionTitle(
                    "Milestone Rewards",
                    "Claim rewards when your level or active-day streak reaches a milestone.",
                )
            }
            if (!loading && hub.rewards.isEmpty()) {
                item { EmptyProgressCard("Milestone rewards are syncing.") }
            } else {
                items(hub.rewards, key = { "reward-${it.key}" }) { reward ->
                    RewardCard(
                        reward = reward,
                        busy = busyKey == "reward:${reward.key}",
                        onClaim = {
                            launchAction("reward:${reward.key}") {
                                service.claimProgressReward(reward.key)
                            }
                        },
                    )
                }
            }

            item { SectionTitle("This Week", "A private creator recap from your real posts and reels.") }
            item { CreatorWeekCard(hub.creatorWeek) }

            item { SectionTitle("Recent XP", "See exactly which actions moved your progression.") }
            if (hub.xpHistory.isEmpty()) {
                item { EmptyProgressCard("Your XP history will appear here as you use BLINK.") }
            } else {
                items(
                    hub.xpHistory.take(20),
                    key = { "${it.createdAt}-${it.eventType}-${it.xpDelta}" },
                ) { entry ->
                    XpHistoryRow(entry)
                }
            }
        }
    }
}

@Composable
private fun ProgressHero(
    level: Int,
    totalXp: Long,
    xpToNext: Long,
    progress: Float,
    streak: Int,
    campusRank: Int,
    worldRank: Int,
    coins: Long,
    onRefresh: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("BLINK Progress", fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text(
                        "Level $level • $totalXp XP",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh progress")
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(100.dp)),
            )
            Text(
                if (level >= 100) "Maximum level reached" else "$xpToNext XP to Level ${level + 1}",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ProgressMetric("🔥", streak.toString(), "Streak")
                ProgressMetric("🏛️", rankLabel(campusRank), "Campus")
                ProgressMetric("🌐", rankLabel(worldRank), "World")
                ProgressMetric("◉", coins.toString(), "Coins")
            }
        }
    }
}

@Composable
private fun ProgressMetric(icon: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$icon $value", fontWeight = FontWeight.Black, fontSize = 13.sp)
        Text(label, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MissionCard(
    title: String,
    description: String,
    progress: Int,
    target: Int,
    progressFraction: Float,
    coinReward: Int,
    xpReward: Int,
    claimed: Boolean,
    claimable: Boolean,
    busy: Boolean,
    onClaim: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (claimed) Color(0xFF22C55E).copy(alpha = 0.08f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(description, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "${progress.coerceAtMost(target)}/$target",
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    color = if (progress >= target) Color(0xFF22C55E) else BlinkPink,
                )
            }
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(100.dp)),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("+$coinReward coins • +$xpReward XP", fontSize = 10.5.sp)
                OutlinedButton(
                    onClick = onClaim,
                    enabled = claimable && !busy,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        when {
                            claimed -> "Claimed"
                            busy -> "Claiming…"
                            claimable -> "Claim"
                            else -> "In progress"
                        },
                        fontSize = 10.5.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyChestCard(
    chest: WeeklyChestState,
    busy: Boolean,
    onClaim: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BlinkPurple.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, BlinkPurple.copy(alpha = 0.24f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, null, tint = BlinkPink)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Weekly Completion Chest", fontWeight = FontWeight.Black)
                    Text(
                        "Complete ${chest.target} weekly missions.",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${chest.progress.coerceAtMost(chest.target)}/${chest.target}",
                    fontWeight = FontWeight.Black,
                )
            }
            LinearProgressIndicator(
                progress = { chest.progressFraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(100.dp)),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("+${chest.coinReward} coins • +${chest.xpReward} XP", fontSize = 10.5.sp)
                Button(onClick = onClaim, enabled = chest.claimable && !busy) {
                    Text(
                        when {
                            chest.claimed -> "Claimed"
                            busy -> "Opening…"
                            chest.claimable -> "Open"
                            else -> "Locked"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AchievementCard(
    achievement: BlinkAchievement,
    busy: Boolean,
    onClaim: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EmojiEvents, null, tint = BlinkPink, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text(achievement.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
            Text(achievement.description, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { achievement.progressFraction },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(100.dp)),
            )
            Row(
                Modifier.fillMaxWidth(),
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
                        fontSize = 10.5.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun RewardCard(
    reward: BlinkProgressReward,
    busy: Boolean,
    onClaim: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (reward.type.equals("streak", true)) Icons.Default.LocalFireDepartment else Icons.Default.TrendingUp,
                contentDescription = null,
                tint = BlinkPink,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(reward.title, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                Text(
                    "${progressRewardLabel(reward.type, reward.threshold)} • +${reward.coinReward} coins • +${reward.xpReward} XP",
                    fontSize = 10.sp,
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
}

@Composable
private fun CreatorWeekCard(summary: BlinkCreatorWeekSummary) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniMetric("Posts", summary.postsCreated.toString())
                MiniMetric("Reels", summary.reelsCreated.toString())
                MiniMetric("Views", compactNumber(summary.viewsReceived))
                MiniMetric("Followers", "+${summary.followersGained}")
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniMetric("Likes", compactNumber(summary.likesReceived))
                MiniMetric("Comments", compactNumber(summary.commentsReceived))
                MiniMetric("Shares", compactNumber(summary.sharesReceived))
            }
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Black, fontSize = 13.sp)
        Text(label, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun XpHistoryRow(entry: BlinkXpHistoryEntry) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(humanizeEvent(entry.eventType), fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
                Text(
                    entry.sourceType.ifBlank { "BLINK activity" },
                    fontSize = 9.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("+${entry.xpDelta} XP", fontWeight = FontWeight.Black, color = BlinkPink, fontSize = 11.5.sp)
        }
    }
}

@Composable
private fun EmptyProgressCard(message: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
    ) {
        Text(
            message,
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun parseDailyMissions(payload: JSONObject): List<BlinkDailyMission> {
    val array = payload.optJSONArray("missions") ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            add(
                BlinkDailyMission(
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
}

private fun parseProgressHub(payload: JSONObject): BlinkProgressHubState =
    BlinkProgressHubState(
        weekStart = payload.optString("week_start"),
        totalXp = payload.optLong("total_xp", 0L),
        xpLevel = payload.optInt("xp_level", 1).coerceIn(1, 100),
        dailyStreak = payload.optInt("daily_streak", 0).coerceAtLeast(0),
        worldRank = payload.optInt("world_rank", 0).coerceAtLeast(0),
        campusRank = payload.optInt("campus_rank", 0).coerceAtLeast(0),
        coinBalance = payload.optLong("coin_balance", 0L).coerceAtLeast(0L),
        weeklyMissions = parseWeeklyMissions(payload.optJSONArray("weekly_missions")),
        achievements = parseAchievements(payload.optJSONArray("achievements")),
        rewards = parseRewards(payload.optJSONArray("rewards")),
        xpHistory = parseXpHistory(payload.optJSONArray("xp_history")),
        creatorWeek = parseCreatorWeek(payload.optJSONObject("creator_week")),
    )

private fun parseWeeklyMissions(array: JSONArray?): List<BlinkWeeklyMission> = buildList {
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

private fun parseAchievements(array: JSONArray?): List<BlinkAchievement> = buildList {
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

private fun parseRewards(array: JSONArray?): List<BlinkProgressReward> = buildList {
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

private fun parseXpHistory(array: JSONArray?): List<BlinkXpHistoryEntry> = buildList {
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

private fun parseCreatorWeek(row: JSONObject?): BlinkCreatorWeekSummary =
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

private fun parseWeeklyChest(row: JSONObject?): WeeklyChestState =
    if (row == null) WeeklyChestState()
    else WeeklyChestState(
        progress = row.optInt("progress", 0),
        target = row.optInt("target", 4).coerceAtLeast(1),
        coinReward = row.optInt("coin_reward", 0),
        xpReward = row.optInt("xp_reward", 0),
        eligible = row.optBoolean("eligible", false),
        claimed = row.optBoolean("claimed", false),
    )

private fun rankLabel(rank: Int): String = if (rank > 0) "#$rank" else "—"

private fun compactNumber(value: Long): String = when {
    value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format("%.1fK", value / 1_000.0)
    else -> value.toString()
}

private fun humanizeEvent(event: String): String =
    event.replace('_', ' ')
        .trim()
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
        .ifBlank { "BLINK activity" }
