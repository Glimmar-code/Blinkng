package com.blinkng.shared

/** Shared, display-only post metadata. Never changes backend timestamps or counts. */
object BlinkPostFormat {
    private val verboseAge = Regex(
        """^(\\d+)\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?|days?|weeks?|months?|years?)(?:\\s+ago)?$""",
        RegexOption.IGNORE_CASE
    )

    fun age(value: String): String {
        val age = value.trim()
        if (age.isEmpty()) return ""
        if (age.equals("just now", ignoreCase = true) || age.equals("now", ignoreCase = true)) return "now"
        if (Regex("""^\\d+(?:s|m|h|d|w|mo|y)$""", RegexOption.IGNORE_CASE).matches(age)) return age
        val parts = verboseAge.matchEntire(age) ?: return age
        val unit = parts.groupValues[2].lowercase()
        val short = when {
            unit.startsWith("sec") -> "s"
            unit.startsWith("min") -> "m"
            unit.startsWith("hour") || unit.startsWith("hr") -> "h"
            unit.startsWith("day") -> "d"
            unit.startsWith("week") -> "w"
            unit.startsWith("month") -> "mo"
            else -> "y"
        }
        return parts.groupValues[1] + short
    }

    fun count(value: Int): String {
        val safe = value.coerceAtLeast(0)
        val divisor = when {
            safe >= 1_000_000_000 -> 1_000_000_000.0
            safe >= 1_000_000 -> 1_000_000.0
            safe >= 1_000 -> 1_000.0
            else -> return safe.toString()
        }
        val suffix = when (divisor) {
            1_000_000_000.0 -> "B"
            1_000_000.0 -> "M"
            else -> "K"
        }
        val scaled = safe / divisor
        val shown = if (scaled >= 10) kotlin.math.floor(scaled).toInt().toString()
            else (kotlin.math.floor(scaled * 10) / 10).toString().removeSuffix(".0")
        return shown + suffix
    }
}
