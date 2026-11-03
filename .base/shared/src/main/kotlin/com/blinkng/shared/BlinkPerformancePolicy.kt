package com.blinkng.shared

/** Device performance choices; Automatic delegates refresh scheduling to the OS. */
enum class BlinkPerformanceMode(val label: String) {
    AUTOMATIC("Automatic"),
    BALANCED("Balanced"),
    POWER_SAVING("Power Saving");

    companion object {
        fun fromStorage(value: String?): BlinkPerformanceMode =
            entries.firstOrNull { it.name == value } ?: AUTOMATIC
    }
}

object BlinkPerformancePolicy {
    fun targetRefreshRate(mode: BlinkPerformanceMode, availableHz: List<Float>): Float {
        if (mode == BlinkPerformanceMode.AUTOMATIC) return 0f // no forced rate
        val supported = availableHz.filter { it >= 24f && it <= 240f }.sorted()
        if (supported.isEmpty()) return 0f
        val maxPreferred = when (mode) {
            BlinkPerformanceMode.BALANCED -> 90f
            BlinkPerformanceMode.POWER_SAVING -> 60f
            else -> return 0f
        }
        return supported.filter { it <= maxPreferred + 0.1f }.maxOrNull()
            ?: supported.first()
    }

    fun reduceDecorativeMotion(mode: BlinkPerformanceMode, systemReducedMotion: Boolean): Boolean =
        systemReducedMotion || mode == BlinkPerformanceMode.POWER_SAVING
}
