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
    val mediaKind: String = "text",
    val mediaPath: String? = null,
    val pollQuestion: String? = null,
    val pollOptions: List<String> = emptyList(),
    val pollCounts: Map<Int, Int> = emptyMap(),
    val myVote: Int? = null,
    val pinned: Boolean = false,
    val viewCount: Long = 0
)

data class BlinkChannelReport(
    val id: String,
    val updateId: String,
    val reason: String,
    val reviewed: Boolean,
)

object BlinkChannelPolicy {
    const val MAX_UPDATE_LENGTH = 5000
    const val MAX_POLL_QUESTION = 300
    const val MAX_POLL_OPTION = 100
    val mediaKinds = setOf("text", "image", "video", "audio", "document", "poll")
    val reactions = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")

    fun validatedUpdate(text: String): String = text.trim().also {
        require(it.isNotEmpty()) { "Channel update cannot be empty." }
        require(it.length <= MAX_UPDATE_LENGTH) { "Channel update must be 5,000 characters or less." }
    }

    fun validatedPoll(question: String, options: List<String>): Pair<String, List<String>> {
        val title = question.trim()
        val answers = options.map { it.trim() }
        require(title.length in 3..MAX_POLL_QUESTION) { "Poll question must be 3–300 characters." }
        require(answers.size in 2..8 && answers.distinct().size == answers.size) {
            "Add 2–8 different poll answers."
        }
        require(answers.all { it.length in 1..MAX_POLL_OPTION }) {
            "Poll answers must be 1–100 characters."
        }
        return title to answers
    }

    fun validatedMediaKind(kind: String): String = kind.also {
        require(it in mediaKinds) { "Unsupported channel media type." }
    }

    fun validatedReaction(emoji: String): String = emoji.also {
        require(it in reactions) { "Unsupported channel reaction." }
    }
}
