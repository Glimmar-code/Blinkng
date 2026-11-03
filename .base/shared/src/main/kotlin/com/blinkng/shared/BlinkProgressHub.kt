package com.blinkng.shared

enum class BlinkAchievementRarity(val label: String) {
    COMMON("Common"),
    RARE("Rare"),
    EPIC("Epic"),
    LEGENDARY("Legendary");

    companion object {
        fun fromWire(value: String?): BlinkAchievementRarity =
            entries.firstOrNull { it.name.equals(value.orEmpty(), ignoreCase = true) } ?: COMMON
    }
}

data class BlinkWeeklyMission(
    val key: String,
    val title: String,
    val description: String,
    val progress: Int,
    val target: Int,
    val coinReward: Int,
    val xpReward: Int,
    val claimed: Boolean,
) {
    val completed: Boolean get() = progress >= target.coerceAtLeast(1)
    val claimable: Boolean get() = completed && !claimed
    val progressFraction: Float
        get() = if (target <= 0) 1f else (progress.toFloat() / target.toFloat()).coerceIn(0f, 1f)
}

data class BlinkAchievement(
    val key: String,
    val title: String,
    val description: String,
    val category: String,
    val rarity: BlinkAchievementRarity,
    val progress: Long,
    val target: Long,
    val coinReward: Int,
    val xpReward: Int,
    val claimed: Boolean,
) {
    val unlocked: Boolean get() = progress >= target.coerceAtLeast(1L)
    val claimable: Boolean get() = unlocked && !claimed
    val progressFraction: Float
        get() = if (target <= 0L) 1f else (progress.toDouble() / target.toDouble()).toFloat().coerceIn(0f, 1f)
}

data class BlinkProgressReward(
    val key: String,
    val type: String,
    val threshold: Int,
    val title: String,
    val description: String,
    val coinReward: Int,
    val xpReward: Int,
    val cosmeticCatalogId: String? = null,
    val eligible: Boolean,
    val claimed: Boolean,
) {
    val claimable: Boolean get() = eligible && !claimed
}

data class BlinkXpHistoryEntry(
    val eventType: String,
    val xpDelta: Int,
    val sourceType: String,
    val createdAt: String,
)

data class BlinkCreatorWeekSummary(
    val postsCreated: Int = 0,
    val reelsCreated: Int = 0,
    val viewsReceived: Long = 0,
    val likesReceived: Long = 0,
    val commentsReceived: Long = 0,
    val sharesReceived: Long = 0,
    val followersGained: Int = 0,
)

data class BlinkProgressHubState(
    val weekStart: String = "",
    val totalXp: Long = 0,
    val xpLevel: Int = 1,
    val dailyStreak: Int = 0,
    val worldRank: Int = 0,
    val campusRank: Int = 0,
    val coinBalance: Long = 0,
    val weeklyMissions: List<BlinkWeeklyMission> = emptyList(),
    val achievements: List<BlinkAchievement> = emptyList(),
    val rewards: List<BlinkProgressReward> = emptyList(),
    val xpHistory: List<BlinkXpHistoryEntry> = emptyList(),
    val creatorWeek: BlinkCreatorWeekSummary = BlinkCreatorWeekSummary(),
)

fun progressRewardLabel(type: String, threshold: Int): String = when (type.lowercase()) {
    "level" -> "Level $threshold"
    "streak" -> "$threshold-day streak"
    else -> threshold.toString()
}
