package com.example.notification

/** Stable, wide chat notification IDs and timestamp/ID recovery cursors. */
internal object NotificationIdentity {
    private const val CHAT_BASE = 100_000
    private const val CHAT_SPAN = 1_000_000_000

    fun conversationId(conversationKey: String): Int =
        CHAT_BASE + ((conversationKey.trim().hashCode() and Int.MAX_VALUE) % CHAT_SPAN)

    /** Sort by creation time, then server ID, to preserve equal-timestamp events. */
    fun isAfterCursor(createdAt: String, id: String, cursorTime: String, cursorId: String): Boolean {
        if (createdAt.isBlank()) return false
        if (cursorTime.isBlank()) return true
        if (createdAt != cursorTime) return createdAt > cursorTime
        // Older installs persisted only time: replay ties once with ID deduplication.
        if (cursorId.isBlank()) return true
        return id > cursorId
    }
}
