package com.example.util

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

object TimeFormatters {
    private val absoluteFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a", Locale.getDefault())

    fun relativeOrDate(
        rawTimestamp: String?,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val instant = parseInstant(rawTimestamp) ?: return "Recently"
        val seconds = max(0L, now.epochSecond - instant.epochSecond)
        val minutes = seconds / 60L
        val hours = seconds / 3_600L
        val days = seconds / 86_400L

        return when {
            seconds < 60L -> "Just now"
            minutes == 1L -> "a min ago"
            minutes < 60L -> "$minutes mins ago"
            hours == 1L -> "1 hour ago"
            hours < 24L -> "$hours hours ago"
            days == 1L -> "1 day ago"
            days < 7L -> "$days days ago"
            else -> absoluteFormatter.format(instant.atZone(zoneId))
        }
    }

    private fun parseInstant(rawTimestamp: String?): Instant? {
        val raw = rawTimestamp?.trim().orEmpty()
        if (raw.isBlank() || raw.equals("null", ignoreCase = true)) return null
        return runCatching { Instant.parse(raw) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
    }
}
