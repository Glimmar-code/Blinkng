package com.example.notification

/**
 * Transient notification previews, never persisted to local storage.
 * Bounded by account + conversation so a signed-out account cannot leak its text
 * into another user's Android notification.
 */
internal object NotificationMessageHistory {
    data class Preview(val messageId: String, val text: String, val timestampMillis: Long)
    private data class Conversation(val updatedAt: Long, val messages: List<Preview>)
    private const val MAX_MESSAGES = 5
    private const val MAX_CONVERSATIONS = 32
    private const val EXPIRY_MILLIS = 30L * 60 * 1000
    private val previews = LinkedHashMap<String, Conversation>()

    @Synchronized
    fun append(
        userId: String,
        conversationKey: String,
        messageId: String,
        text: String,
        timestampMillis: Long = System.currentTimeMillis()
    ): List<Preview> {
        val next = Preview(messageId, text.ifBlank { "New message" }, timestampMillis)
        if (userId.isBlank() || conversationKey.isBlank()) return listOf(next)

        val iterator = previews.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (timestampMillis - entry.value.updatedAt >= EXPIRY_MILLIS) iterator.remove()
        }

        val key = "$userId:$conversationKey"
        val existing = previews.remove(key)?.messages.orEmpty()
        val updated = (existing.filterNot { messageId.isNotBlank() && it.messageId == messageId } + next)
            .takeLast(MAX_MESSAGES)
        previews[key] = Conversation(timestampMillis, updated)
        while (previews.size > MAX_CONVERSATIONS) {
            val oldest = previews.entries.iterator()
            if (!oldest.hasNext()) break
            oldest.next()
            oldest.remove()
        }
        return updated
    }

    @Synchronized
    fun clear() { previews.clear() }
}
