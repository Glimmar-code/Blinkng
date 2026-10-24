package com.blinkng.shared.notification

import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Story visibility contract shared by Android and desktop.
 * Backend RLS must enforce the same 24-hour rule independently.
 */
object BlinkStoryLifecycle {
    const val LIFE_SECONDS: Long = 24L * 60L * 60L

    fun isActive(createdAt: String?, enabled: Boolean, now: Instant = Instant.now()): Boolean {
        if (!enabled || createdAt.isNullOrBlank()) return false
        val created = try {
            Instant.parse(createdAt)
        } catch (_: DateTimeParseException) {
            return false
        }
        return !created.isAfter(now) && created.plusSeconds(LIFE_SECONDS).isAfter(now)
    }
}
