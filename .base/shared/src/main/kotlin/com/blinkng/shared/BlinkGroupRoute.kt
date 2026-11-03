package com.blinkng.shared

/** Stable synthetic route identity for private group conversations.
 * Never interpreted as a person's username or used to call the DM send RPC.
 */
object BlinkGroupRoute {
    private const val PREFIX = "group_"
    fun key(conversationId: String): String {
        require(runCatching { java.util.UUID.fromString(conversationId) }.isSuccess) {
            "A valid group conversation ID is required."
        }
        return PREFIX + conversationId.lowercase()
    }

    fun conversationId(key: String): String? {
        if (!key.startsWith(PREFIX)) return null
        val raw = key.removePrefix(PREFIX)
        return raw.takeIf { runCatching { java.util.UUID.fromString(it) }.isSuccess }
    }
}
