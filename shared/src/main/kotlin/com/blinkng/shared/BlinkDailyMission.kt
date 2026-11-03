package com.blinkng.shared

data class BlinkDailyMission(
    val key: String,
    val title: String,
    val description: String,
    val progress: Int,
    val target: Int,
    val coinReward: Int,
    val xpReward: Int,
    val claimed: Boolean,
) {
    val completed: Boolean
        get() = progress >= target.coerceAtLeast(1)

    val claimable: Boolean
        get() = completed && !claimed

    val progressFraction: Float
        get() = if (target <= 0) 1f else (progress.toFloat() / target.toFloat()).coerceIn(0f, 1f)
}
