package com.blinkng.shared.notification

/** Actions are exposed only for trusted, account-bound notification targets. */
enum class BlinkNotificationQuickAction {
    REPLY,
    MARK_READ,
    FOLLOW_BACK,
    LIKE_POST,
    LIKE_COMMENT,
    VIEW_PROFILE,
    VIEW_POST,
}

object BlinkNotificationQuickActionPolicy {
    private val uuidPattern = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    )

    fun isId(value: String?): Boolean = value != null && uuidPattern.matches(value)

    fun available(
        kind: BlinkNotificationKind,
        recipientBound: Boolean,
        senderId: String? = null,
        postId: String? = null,
        commentId: String? = null,
    ): Set<BlinkNotificationQuickAction> {
        val result = mutableSetOf<BlinkNotificationQuickAction>()
        if (kind == BlinkNotificationKind.MESSAGE && recipientBound) {
            result += BlinkNotificationQuickAction.REPLY
            result += BlinkNotificationQuickAction.MARK_READ
        }
        if (kind == BlinkNotificationKind.FOLLOW && isId(senderId)) {
            result += BlinkNotificationQuickAction.VIEW_PROFILE
            if (recipientBound) result += BlinkNotificationQuickAction.FOLLOW_BACK
        }
        if (isId(postId)) {
            result += BlinkNotificationQuickAction.VIEW_POST
            // A "like your own post" push must NOT offer to like your own post.
            if (recipientBound && kind in setOf(
                BlinkNotificationKind.MENTION,
                BlinkNotificationKind.REPOST,
            )) result += BlinkNotificationQuickAction.LIKE_POST
        }
        if (recipientBound && kind in setOf(
            BlinkNotificationKind.COMMENT,
            BlinkNotificationKind.REPLY,
        ) && isId(commentId)) result += BlinkNotificationQuickAction.LIKE_COMMENT
        return result
    }
}
