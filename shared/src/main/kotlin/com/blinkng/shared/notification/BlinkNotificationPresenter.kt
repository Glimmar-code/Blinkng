package com.blinkng.shared.notification

/**
 * Presentation-only guard for user-visible notification copy on both Android and Windows.
 * Server events, recipients and notification IDs remain authoritative; this class never
 * invents a sender or changes the stored notification.
 */
object BlinkNotificationPresenter {
    data class Copy(val title: String, val body: String)

    private val spaces = Regex("\\s+")
    private val bogusPrefix = Regex("^(?:@?null|@?undefined|@?none)(?=\\s|[.,:!]|$)", RegexOption.IGNORE_CASE)

    /** JSON APIs sometimes send the literal string "null", not a JSON null. */
    fun clean(value: String?): String {
        val candidate = value.orEmpty().trim().replace(spaces, " ").take(280)
        return if (candidate.isBlank() || bogusPrefix.containsMatchIn(candidate)) "" else candidate
    }

    fun render(
        kind: BlinkNotificationKind,
        rawTitle: String?,
        rawBody: String?,
        senderName: String? = null,
        senderUsername: String? = null
    ): Copy {
        val name = clean(senderName)
        val handle = clean(senderUsername).removePrefix("@")
        val actor = name.ifBlank { if (handle.isNotBlank()) "@$handle" else "Someone" }
        val givenTitle = clean(rawTitle)
        val title = if (givenTitle.isNotEmpty()) {
            givenTitle
        } else {
            when (kind) {
                BlinkNotificationKind.MESSAGE -> if (actor == "Someone") "New message" else "$actor sent you a message"
                BlinkNotificationKind.FOLLOW -> if (actor == "Someone") "New follower" else "$actor started following you"
                BlinkNotificationKind.LIKE -> "$actor liked your post"
                BlinkNotificationKind.COMMENT -> "$actor commented on your post"
                BlinkNotificationKind.REPLY -> "$actor replied to your comment"
                BlinkNotificationKind.MENTION -> "$actor mentioned you"
                BlinkNotificationKind.REPOST -> "$actor reposted your post"
                BlinkNotificationKind.STORY -> "New story activity"
                BlinkNotificationKind.REEL -> "New reel activity"
                BlinkNotificationKind.MARKET, BlinkNotificationKind.MARKET_ORDER -> "BLINK Market update"
                BlinkNotificationKind.ADMIN, BlinkNotificationKind.SYSTEM -> "BLINK announcement"
                BlinkNotificationKind.SECURITY -> "BLINK security alert"
                BlinkNotificationKind.COIN -> "BLINK Coin update"
                BlinkNotificationKind.VIP -> "BLINK verification update"
                BlinkNotificationKind.BOOST -> "BLINK Boost update"
                BlinkNotificationKind.INCOMING_CALL, BlinkNotificationKind.CALL_UPDATE -> "BLINK call"
                else -> "BLINK notification"
            }
        }
        val body = clean(rawBody).ifBlank {
            when (kind) {
                BlinkNotificationKind.MESSAGE -> "Open BLINK to view your message."
                BlinkNotificationKind.COMMENT, BlinkNotificationKind.REPLY -> "Open BLINK to view the reply."
                BlinkNotificationKind.LIKE, BlinkNotificationKind.REPOST -> "Open BLINK to view your post."
                BlinkNotificationKind.FOLLOW -> "View your latest follower on BLINK."
                else -> "Open BLINK to view this update."
            }
        }
        return Copy(title, body)
    }
}
