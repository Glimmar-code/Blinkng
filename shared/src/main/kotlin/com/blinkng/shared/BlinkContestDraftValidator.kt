package com.blinkng.shared

import java.time.OffsetDateTime

/**
 * Matches the Contest server's draft constraints before uploading a cover.
 * This is UX validation only; the database remains authoritative.
 */
object BlinkContestDraftValidator {
    fun error(
        title: String,
        dates: List<String>,
        prizes: List<String>,
        maxEntries: String,
        coverUrl: String,
        hasSelectedCover: Boolean
    ): String? {
        if (title.trim().length !in 4..100) return "Enter a contest title (4–100 characters)."
        if (dates.size != 3 || dates.any { it.isBlank() })
            return "Select the contest start, end and results dates."
        val instants = dates.map { raw ->
            runCatching { OffsetDateTime.parse(raw.trim()).toInstant() }.getOrNull()
                ?: return "Use valid dates with a time zone."
        }
        if (!instants[0].isBefore(instants[1]) || instants[1].isAfter(instants[2]))
            return "Dates must follow: contest start, end, then results."
        if (prizes.size != 3 || prizes.any { it.toIntOrNull()?.let { amount -> amount >= 0 } != true })
            return "Enter three valid, non-negative prize amounts."
        if (maxEntries.isNotBlank() && (maxEntries.toIntOrNull() ?: -1) !in 2..100000)
            return "Participant limit must be 2–100,000, or blank for unlimited."
        if (!hasSelectedCover && coverUrl.isNotBlank() && !coverUrl.trim().startsWith("https://"))
            return "Contest cover URL must use HTTPS."
        return null
    }
}
