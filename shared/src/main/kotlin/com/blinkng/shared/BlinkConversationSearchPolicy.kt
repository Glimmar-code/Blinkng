package com.blinkng.shared

/**
 * Shared, case-insensitive inbox search for Android and Windows.
 * Search only conversation metadata already permitted in the signed-in inbox.
 */
object BlinkConversationSearchPolicy {
    fun matches(
        query: String,
        partnerName: String,
        partnerUsername: String?,
        lastMessage: String?
    ): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return sequenceOf(partnerName, partnerUsername.orEmpty(), lastMessage.orEmpty())
            .any { it.contains(needle, ignoreCase = true) }
    }
}
