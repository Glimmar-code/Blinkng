package com.blinkng.shared

enum class BlinkMessageInbox { ALL, PRIMARY, UNREAD, REQUESTS, ARCHIVED }

object BlinkChatInboxPolicy {
    fun matches(
        inbox: BlinkMessageInbox,
        archived: Boolean,
        category: String,
        unread: Boolean = false
    ): Boolean = when (inbox) {
        BlinkMessageInbox.ALL -> !archived
        BlinkMessageInbox.PRIMARY -> !archived && category != "requests"
        BlinkMessageInbox.UNREAD -> !archived && category != "requests" && unread
        BlinkMessageInbox.REQUESTS -> !archived && category == "requests"
        BlinkMessageInbox.ARCHIVED -> archived
    }
}
