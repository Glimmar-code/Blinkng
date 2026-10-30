package com.example.notification

/**
 * Single presentation policy for message notifications. The caller must continue
 * checking recipient, category, quiet hours and per-conversation mute state.
 * An unknown/unloaded preference fails closed rather than displaying private text.
 */
internal object ChatNotificationPreviewPolicy {
    fun normalized(mode: String?): String = when (mode?.lowercase()) {
        "full" -> "full"
        "sender_only" -> "sender_only"
        else -> "hidden"
    }

    fun disclosureRank(mode: String?): Int = when (normalized(mode)) {
        "hidden" -> 0
        "sender_only" -> 1
        else -> 2
    }

    fun title(mode: String?, sender: String): String =
        if (normalized(mode) == "hidden") "BLINK Messages"
        else sender.ifBlank { "BLINK Messages" }

    fun body(mode: String?, content: String): String =
        if (normalized(mode) == "full") content.ifBlank { "New message" }
        else "New message"

    fun allowHistory(mode: String?): Boolean = normalized(mode) == "full"
}
