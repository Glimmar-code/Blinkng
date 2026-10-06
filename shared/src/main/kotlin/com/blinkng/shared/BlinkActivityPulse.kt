package com.blinkng.shared

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

enum class BlinkPulseTrend(val label: String) {
    RISING("Rising"),
    STABLE("Stable"),
    COOLING("Cooling"),
}

data class BlinkActivityPulsePolicy(
    val enabled: Boolean = true,
    val communityMinPerActive: Int = 30,
    val communityMaxPerActive: Int = 50,
    val rankMinPerEvent: Int = 10,
    val rankMaxPerEvent: Int = 15,
    val connectTickMillis: Long = 2_800L,
    val rankTickMillis: Long = 2_400L,
    val minHoldMillis: Long = 2_400L,
    val rankWindowMillis: Long = 20_000L,
    val maxStep: Int = 4,
    val transitionStepMultiplier: Int = 5,
    val onlinePreviewLimit: Int = 4,
    val campusActiveThreshold: Int = 2,
    val campusHotThreshold: Int = 5,
    val hotRankUpsThreshold: Int = 4,
    val maxUnits: Int = 100_000,
    val maxDisplayValue: Int = 2_000_000_000,
) {
    fun normalized(): BlinkActivityPulsePolicy {
        val communityMin = communityMinPerActive.coerceAtLeast(1)
        val communityMax = max(communityMin, communityMaxPerActive)
        val rankMin = rankMinPerEvent.coerceAtLeast(1)
        val rankMax = max(rankMin, rankMaxPerEvent)
        return copy(
            communityMinPerActive = communityMin,
            communityMaxPerActive = communityMax,
            rankMinPerEvent = rankMin,
            rankMaxPerEvent = rankMax,
            connectTickMillis = connectTickMillis.coerceIn(1_000L, 60_000L),
            rankTickMillis = rankTickMillis.coerceIn(1_000L, 60_000L),
            minHoldMillis = minHoldMillis.coerceIn(1_000L, 60_000L),
            rankWindowMillis = rankWindowMillis.coerceIn(10_000L, 300_000L),
            maxStep = maxStep.coerceIn(1, 100),
            transitionStepMultiplier = transitionStepMultiplier.coerceIn(1, 20),
            onlinePreviewLimit = onlinePreviewLimit.coerceIn(0, 8),
            campusActiveThreshold = campusActiveThreshold.coerceAtLeast(1),
            campusHotThreshold = max(campusActiveThreshold, campusHotThreshold),
            hotRankUpsThreshold = hotRankUpsThreshold.coerceAtLeast(1),
            maxUnits = maxUnits.coerceIn(1, 1_000_000),
            maxDisplayValue = maxDisplayValue.coerceIn(1_000, Int.MAX_VALUE),
        )
    }
}

object BlinkActivityPulseDefaults {
    val policy = BlinkActivityPulsePolicy()
}

fun communityActivityRange(
    realOnlineCount: Int,
    policy: BlinkActivityPulsePolicy = BlinkActivityPulseDefaults.policy,
): IntRange {
    val p = policy.normalized()
    return safePulseRange(
        units = realOnlineCount,
        minPerUnit = p.communityMinPerActive,
        maxPerUnit = p.communityMaxPerActive,
        maxUnits = p.maxUnits,
        maxDisplayValue = p.maxDisplayValue,
    )
}

fun rankPulseRange(
    rankUpsInWindow: Int,
    policy: BlinkActivityPulsePolicy = BlinkActivityPulseDefaults.policy,
): IntRange {
    val p = policy.normalized()
    return safePulseRange(
        units = rankUpsInWindow,
        minPerUnit = p.rankMinPerEvent,
        maxPerUnit = p.rankMaxPerEvent,
        maxUnits = p.maxUnits,
        maxDisplayValue = p.maxDisplayValue,
    )
}

fun safePulseRange(
    units: Int,
    minPerUnit: Int,
    maxPerUnit: Int,
    maxUnits: Int,
    maxDisplayValue: Int,
): IntRange {
    val safeUnits = units.coerceAtLeast(1).coerceAtMost(maxUnits.coerceAtLeast(1)).toLong()
    val lowFactor = min(minPerUnit, maxPerUnit).coerceAtLeast(1).toLong()
    val highFactor = max(minPerUnit, maxPerUnit).coerceAtLeast(1).toLong()
    val cap = maxDisplayValue.coerceAtLeast(1).toLong()
    val low = (safeUnits * lowFactor).coerceAtMost(cap).toInt()
    val high = (safeUnits * highFactor).coerceAtMost(cap).toInt()
    return low..max(low, high)
}

fun nextPulseValue(
    current: Int,
    range: IntRange,
    requestedStep: Int,
    maxStep: Int = 4,
    transitionStepMultiplier: Int = 5,
): Int {
    val minValue = min(range.first, range.last)
    val maxValue = max(range.first, range.last)
    if (minValue == maxValue) return minValue

    val stepLimit = maxStep.coerceAtLeast(1)
    val transitionLimit = (stepLimit.toLong() * transitionStepMultiplier.coerceAtLeast(1))
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()

    if (current < minValue) {
        return (current.toLong() + min((minValue - current).toLong(), transitionLimit.toLong()))
            .coerceAtMost(minValue.toLong())
            .toInt()
    }
    if (current > maxValue) {
        return (current.toLong() - min((current - maxValue).toLong(), transitionLimit.toLong()))
            .coerceAtLeast(maxValue.toLong())
            .toInt()
    }

    return (current + requestedStep.coerceIn(-stepLimit, stepLimit))
        .coerceIn(minValue, maxValue)
}

fun pulseTrend(previous: Int, current: Int): BlinkPulseTrend = when {
    current > previous -> BlinkPulseTrend.RISING
    current < previous -> BlinkPulseTrend.COOLING
    else -> BlinkPulseTrend.STABLE
}

fun campusActivityLabel(realCampusOnline: Int, policy: BlinkActivityPulsePolicy): String {
    val p = policy.normalized()
    return when {
        realCampusOnline >= p.campusHotThreshold -> "High"
        realCampusOnline >= p.campusActiveThreshold -> "Active"
        else -> "Quiet"
    }
}

object BlinkPulseSessionStore {
    private val values = ConcurrentHashMap<String, Int>()

    fun get(key: String): Int? = values[key]

    fun put(key: String, value: Int) {
        values[key] = value
    }

    fun clear(key: String) {
        values.remove(key)
    }
}
