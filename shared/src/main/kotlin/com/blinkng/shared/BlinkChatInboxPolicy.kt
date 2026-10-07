package com.blinkng.shared

enum class BlinkMessageInbox { PRIMARY, REQUESTS, ARCHIVED }

object BlinkChatInboxPolicy {
    fun matches(inbox: BlinkMessageInbox, archived: Boolean, category: String): Boolean = when (inbox) {
        BlinkMessageInbox.PRIMARY -> !archived && category != "requests"
        BlinkMessageInbox.REQUESTS -> !archived && category == "requests"
        BlinkMessageInbox.ARCHIVED -> archived
    }
}
