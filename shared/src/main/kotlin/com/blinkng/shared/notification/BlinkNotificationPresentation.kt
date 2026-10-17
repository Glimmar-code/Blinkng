package com.blinkng.shared.notification

/**
 * Shared notification presentation contract for Android and Windows.
 *
 * Backend records remain authoritative. This only prevents missing JSON/database
 * values from leaking as "null" or "undefined" in user-facing notification copy.
 */
object BlinkNotificationPresentation {
    private val missing = setOf(
        "null", "undefined", "none", "(null)", "[null]", "@null", "@undefined",
    )
    private val missingActorPrefix = Regex(
        "^@?(?:null|undefined|none)\\b(?=\\s+)",
        RegexOption.IGNORE_CASE,
    )

    private fun nonMissing(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() !in missing }

    fun username(raw: String?): String =
        nonMissing(raw)?.removePrefix("@")?.takeIf { it.isNotBlank() && it.lowercase() !in missing }
            .orEmpty()

    fun displayName(raw: String?, fallback: String = "Blink user"): String =
        nonMissing(raw) ?: fallback

    fun title(raw: String?, fallback: String = "BLINK notification"): String {
        val value = nonMissing(raw) ?: return fallback
        return value.replace(missingActorPrefix, "Someone")
    }

    /** Do not rewrite a real user's message text; only reject missing-value sentinels. */
    fun body(raw: String?, fallback: String = "You have a new notification."): String =
        nonMissing(raw) ?: fallback

    fun defaultTitle(kind: BlinkNotificationKind): String = when (kind) {
        BlinkNotificationKind.MESSAGE -> "New message"
        BlinkNotificationKind.LIKE -> "Someone liked your post"
        BlinkNotificationKind.COMMENT -> "New comment on your post"
        BlinkNotificationKind.REPLY -> "New reply"
        BlinkNotificationKind.MENTION -> "Someone mentioned you"
        BlinkNotificationKind.FOLLOW -> "New follower"
        BlinkNotificationKind.MARKET, BlinkNotificationKind.MARKET_ORDER -> "BLINK Market update"
        BlinkNotificationKind.ADMIN, BlinkNotificationKind.SYSTEM -> "BLINK announcement"
        else -> "BLINK notification"
    }
}
