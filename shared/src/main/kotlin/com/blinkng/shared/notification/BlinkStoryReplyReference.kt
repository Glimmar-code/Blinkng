package com.blinkng.shared.notification

/** Structured DM text used for a safe in-app status reference, not a media URL. */
object BlinkStoryReplyReference {
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private const val prefix = "[BLINK story:"

    data class Reply(val storyId: String, val text: String)

    fun encode(storyId: String, text: String): String? {
        val trimmed = text.trim()
        if (!uuid.matches(storyId) || trimmed.isEmpty()) return null
        return "$prefix$storyId]\n${trimmed.take(1_000)}"
    }

    fun parse(encoded: String): Reply? {
        if (!encoded.startsWith(prefix)) return null
        val ending = encoded.indexOf("]\n", prefix.length)
        if (ending < 0) return null
        val id = encoded.substring(prefix.length, ending)
        if (!uuid.matches(id)) return null
        val body = encoded.substring(ending + 2)
        if (body.isBlank() || body.length > 1_000) return null
        return Reply(id, body)
    }

    fun inboxPreview(text: String): String {
        val reply = parse(text) ?: return text
        return "Status reply: ${reply.text.replace('\n', ' ').take(90)}"
    }
}
