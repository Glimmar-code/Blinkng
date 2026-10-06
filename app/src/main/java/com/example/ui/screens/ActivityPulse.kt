package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.random.Random

internal fun communityActivityRange(realOnlineCount: Int): IntRange {
    val units = realOnlineCount.coerceAtLeast(1).coerceAtMost(100_000)
    return (units * 30)..(units * 50)
}

internal fun rankPulseRange(rankUpsInWindow: Int): IntRange {
    val units = rankUpsInWindow.coerceAtLeast(1).coerceAtMost(100_000)
    return (units * 10)..(units * 15)
}

internal fun nextPulseValue(
    current: Int,
    range: IntRange,
    step: Int
): Int {
    val min = minOf(range.first, range.last)
    val max = maxOf(range.first, range.last)
    if (min == max) return min
    return (current + step.coerceIn(-4, 4)).coerceIn(min, max)
}

@Composable
internal fun rememberFluctuatingPulse(
    range: IntRange,
    tickMillis: Long = 2_800L
): Int {
    val min = minOf(range.first, range.last)
    val max = maxOf(range.first, range.last)
    var value by remember(min, max) {
        mutableIntStateOf(if (min == max) min else Random.nextInt(min, max + 1))
    }

    LaunchedEffect(min, max, tickMillis) {
        while (true) {
            delay(tickMillis)
            val magnitude = Random.nextInt(1, 5)
            val direction = if (Random.nextBoolean()) 1 else -1
            var next = nextPulseValue(value, min..max, magnitude * direction)
            if (next == value && min < max) {
                next = if (value <= min) value + 1 else value - 1
            }
            value = next.coerceIn(min, max)
        }
    }

    return value
}
