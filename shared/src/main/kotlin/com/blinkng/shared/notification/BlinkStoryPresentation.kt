package com.blinkng.shared.notification

import java.time.Instant
import java.time.format.DateTimeParseException

/** Shared story lifecycle, active ring, and durable DM preview rules. */
object BlinkStoryPresentation {
    const val LIFE_SECONDS = 24L * 60 * 60
    enum class Ring { UNVIEWED, VIEWED, NONE }

    fun active(createdAt: String?, active: Boolean, now: Instant = Instant.now()): Boolean {
        if (!active || createdAt.isNullOrBlank()) return false
        val time = try { Instant.parse(createdAt) } catch (_: DateTimeParseException) { return false }
        return !time.isAfter(now) && time.plusSeconds(LIFE_SECONDS).isAfter(now)
    }

    fun ring(hasActiveStory: Boolean, hasUnseenStory: Boolean): Ring = when {
        !hasActiveStory -> Ring.NONE
        hasUnseenStory -> Ring.UNVIEWED
        else -> Ring.VIEWED
    }

    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val header = Regex("^\\[BLINK story:(${uuid.pattern})]\\n(.*)", RegexOption.DOT_MATCHES_ALL)

    fun reply(storyId: String, caption: String, message: String): String? {
        if (!uuid.matches(storyId) || message.trim().isEmpty()) return null
        // Caption is not copied to the DM: the story itself remains protected by
        // its original availability and audience restrictions.
        return "[BLINK story:$storyId]\n${message.trim().take(1000)}"
    }

    data class StoryReply(val storyId: String, val text: String)

    fun parseReply(text: String): StoryReply? {
        val match = header.matchEntire(text) ?: return null
        return StoryReply(match.groupValues[1], match.groupValues[2])
    }
}
