package com.blinkng.shared

/** A broadcast update, never a group-chat message or a public list of followers. */
data class BlinkChannelUpdate(
    val id: String,
    val channelId: String,
    val authorId: String,
    val body: String,
    val createdAt: String,
    val reactions: Map<String, Int> = emptyMap(),
    val myReaction: String? = null,
)

object BlinkChannelPolicy {
    const val MAX_UPDATE_LENGTH = 5000
    val reactions = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")

    fun validatedUpdate(text: String): String = text.trim().also {
        require(it.isNotEmpty()) { "Channel update cannot be empty." }
        require(it.length <= MAX_UPDATE_LENGTH) { "Channel update must be 5,000 characters or less." }
    }

    fun validatedReaction(emoji: String): String = emoji.also {
        require(it in reactions) { "Unsupported channel reaction." }
    }
}
