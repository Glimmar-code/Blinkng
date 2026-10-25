package com.blinkng.shared

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Conversation date dividers shared by Android and Windows.
 * Date chips live inside message rows, preserving stable lazy-list indices
 * for reply navigation, new-message counters and paginated history.
 */
object BlinkMessageDatePolicy {
    data class Entry(val id: String, val timestamp: String)

    fun labels(
        messages: List<Entry>,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Map<String, String> {
        val today = now.atZone(zoneId).toLocalDate()
        val yesterday = today.minusDays(1)
        val format = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
        var previousDate: LocalDate? = null
        return buildMap {
            for (message in messages) {
                val date = runCatching {
                    OffsetDateTime.parse(message.timestamp).atZoneSameInstant(zoneId).toLocalDate()
                }.recoverCatching {
                    Instant.parse(message.timestamp).atZone(zoneId).toLocalDate()
                }.getOrNull() ?: continue
                if (date != previousDate) {
                    put(message.id, when (date) {
                        today -> "Today"
                        yesterday -> "Yesterday"
                        else -> date.format(format)
                    })
                }
                previousDate = date
            }
        }
    }
}
