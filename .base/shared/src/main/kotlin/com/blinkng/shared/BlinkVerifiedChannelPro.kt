package com.blinkng.shared

/** Read-only analytics from updates already authorized by the Channel backend. */
data class BlinkChannelProSummary(
    val loadedUpdates: Int,
    val totalReportedViews: Long,
    val totalReactions: Long,
    val polls: Int,
    val pollVotes: Long,
    val mediaUpdates: Int,
    val topUpdates: List<BlinkChannelUpdate>,
    val kindCounts: Map<String, Int>
)

object BlinkVerifiedChannelPro {
    fun summarize(updates: List<BlinkChannelUpdate>): BlinkChannelProSummary {
        val clean = updates.asSequence().filter { it.id.isNotBlank() }
            .distinctBy { it.id }.toList()
        return BlinkChannelProSummary(
            loadedUpdates = clean.size,
            totalReportedViews = clean.sumOf { it.viewCount.coerceAtLeast(0L) },
            totalReactions = clean.sumOf { row ->
                row.reactions.values.sumOf { it.coerceAtLeast(0).toLong() }
            },
            polls = clean.count { it.mediaKind == "poll" },
            pollVotes = clean.sumOf { row ->
                row.pollCounts.values.sumOf { it.coerceAtLeast(0).toLong() }
            },
            mediaUpdates = clean.count { it.mediaKind in setOf("image", "video", "audio", "document") },
            topUpdates = clean.sortedWith(
                compareByDescending<BlinkChannelUpdate> { it.viewCount.coerceAtLeast(0) }
                    .thenBy { it.id }
            ).take(5),
            kindCounts = clean.groupingBy { it.mediaKind }.eachCount()
        )
    }

    /** Reusable templates are only editable text. No AI calls or silent posting. */
    fun template(kind: String, channelName: String): String {
        val title = channelName.trim().take(80).ifBlank { "our Channel" }
        return when (kind) {
            "announcement" -> "📣 $title update\n\nWhat you need to know:\n\nWhen:\nWhere:\nNext step:"
            "event" -> "📅 Event on $title\n\nEvent:\nDate and time:\nVenue:\nWho can attend:\nHow to join:"
            "recap" -> "📝 This week on $title\n\nHighlights:\n• \n• \n\nComing up next:"
            else -> ""
        }
    }

    /**
     * Scheduling is already a server feature. This helper only produces valid
     * future UTC timestamps for explicit user selection; never posts by itself.
     */
    fun utcPreset(nowMillis: Long, hoursAhead: Int): String {
        require(hoursAhead in 1..168) { "Choose a scheduled time within the next 7 days" }
        return java.time.Instant.ofEpochMilli(nowMillis)
            .plusSeconds(hoursAhead.toLong() * 3600L).toString()
    }
}
