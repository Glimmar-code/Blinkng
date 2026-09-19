package com.blinkng.shared

data class XpProgress(
    val level: Int,
    val tierLabel: String,
    val totalXp: Long,
    val currentLevelMinXp: Long,
    val nextLevelMinXp: Long,
) {
    val xpIntoLevel: Long get() = (totalXp - currentLevelMinXp).coerceAtLeast(0)
    val xpForLevel: Long get() = (nextLevelMinXp - currentLevelMinXp).coerceAtLeast(1)
    val xpToNextLevel: Long get() = (nextLevelMinXp - totalXp).coerceAtLeast(0)
    val progressFraction: Float
        get() = if (level >= 100) 1f else (xpIntoLevel.toDouble() / xpForLevel.toDouble()).toFloat().coerceIn(0f, 1f)
}

fun xpThresholdForLevel(level: Int): Long {
    val l = level.coerceIn(1, 100)
    return when {
        l <= 10 -> ((l - 1L) * (l - 1L) * 31L)
        l <= 25 -> 2_500L + ((l - 10L) * (l - 10L) * 56L)
        l <= 50 -> 15_000L + ((l - 25L) * (l - 25L) * 72L)
        l <= 75 -> 60_000L + ((l - 50L) * (l - 50L) * 184L)
        else -> 175_000L + ((l - 75L) * (l - 75L) * 520L)
    }
}

fun xpLevelForTotalXp(totalXp: Long): Int {
    val xp = totalXp.coerceAtLeast(0)
    for (level in 100 downTo 1) {
        if (xp >= xpThresholdForLevel(level)) return level
    }
    return 1
}

fun xpTierLabel(level: Int): String = when (level.coerceIn(1, 100)) {
    in 1..10 -> "New"
    in 11..25 -> "Active"
    in 26..50 -> "Established"
    in 51..75 -> "Highly Active"
    else -> "Long-term"
}

fun xpProgress(totalXp: Long, serverLevel: Int = 0): XpProgress {
    val safeXp = totalXp.coerceAtLeast(0)
    val computed = xpLevelForTotalXp(safeXp)
    val level = serverLevel.takeIf { it in 1..100 }?.coerceAtMost(computed) ?: computed
    val min = xpThresholdForLevel(level)
    val next = if (level >= 100) min else xpThresholdForLevel(level + 1)
    return XpProgress(
        level = level,
        tierLabel = xpTierLabel(level),
        totalXp = safeXp,
        currentLevelMinXp = min,
        nextLevelMinXp = next,
    )
}
