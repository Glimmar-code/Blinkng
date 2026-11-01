package com.blinkng.shared

import java.util.Locale

/** Posting capabilities shared by the Android and Windows composers. */
object BlinkPostComposerPolicy {
    const val MAX_PHOTOS = 10
    const val MAX_TEXT = 5000
    val audiences = listOf("Everyone", "Campus", "Followers")
    val pollDurationsHours = listOf(1, 24, 72, 168)

    fun requireAudience(audience: String) {
        require(audience in audiences) {
            "Private posting is unavailable until server and media privacy are verified."
        }
    }

    fun validPoll(question: String, options: List<String>): Boolean {
        val cleaned = options.map(String::trim).filter(String::isNotBlank)
        return question.isNotBlank() && question.length <= 240 &&
            options.size in 2..4 && cleaned.size >= 2 &&
            cleaned.all { it.length <= 120 } &&
            cleaned.map { it.lowercase(Locale.ROOT) }.distinct().size == cleaned.size
    }

    fun preparePoll(question: String, options: List<String>, durationHours: Int): BlinkFeedPollDraft {
        require(validPoll(question, options)) { "Add a question and 2–4 different answers." }
        require(durationHours in pollDurationsHours) { "Choose a supported poll duration." }
        return BlinkFeedPollDraft(question.trim(), options.map(String::trim).filter(String::isNotBlank), durationHours)
    }
}

/** Native editor adapters use the same clip and duration limits. */
object BlinkReelEditPolicy {
    const val MAX_CLIPS = 4
    const val MAX_TOTAL_DURATION_MS = 180_000L
}

data class BlinkFeedPollDraft(val question: String, val options: List<String>, val durationHours: Int) {
    fun expiresAtMillis(publishedAtMillis: Long): Long =
        Math.addExact(publishedAtMillis, Math.multiplyExact(durationHours.toLong(), 3_600_000L))
}

/** Color contract for the supplied Create Post reference, shared across clients. */
object BlinkPostReferenceStyle {
    const val Canvas = 0xFF000000L
    const val Card = 0xFF171A1DL
    const val Input = 0xFF202328L
    const val Border = 0xFF30343EL
    const val Muted = 0xFFAAB0BCL
    const val Primary = 0xFF4738FFL
    const val PrimaryEnd = 0xFF8739FFL
    const val Photo = 0xFF0AC98AL
    const val Video = 0xFFFF476CL
    const val Poll = 0xFFF7B52CL
    val choiceGradients = mapOf(
        "Text" to listOf(0xFF5265FFL, 0xFF8135FFL),
        "Photo" to listOf(0xFF10D6A2L, 0xFF0AC56DL),
        "Video" to listOf(0xFFFF6284L, 0xFFFF3558L),
        "Poll" to listOf(0xFFFFD04AL, 0xFFF7B52CL),
    )
    val textBackgrounds = mapOf(
        "aurora" to listOf(0xFF6A7CFFL, 0xFF8B5CF6L, 0xFF52C7EAL),
        "ocean" to listOf(0xFF0D47A1L, 0xFF1976D2L, 0xFF26C6DAL),
        "sunset" to listOf(0xFFFF6B6BL, 0xFFF97316L, 0xFFFACC15L),
    )
}
