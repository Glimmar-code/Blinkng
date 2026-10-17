package com.blinkng.shared.notification

/** Non-private admin/system notification previews safe to keep in an account-scoped offline inbox. */
data class BlinkAnnouncementPreview(
    val id: String,
    val title: String,
    val message: String,
    val publishedAt: String,
)

/** Shared by Android and Windows. No fabricated alerts, rewards or notifications. */
object BlinkAnnouncementInboxRules {
    const val MAX_CACHED = 50
    const val MAX_TITLE = 140
    const val MAX_MESSAGE = 600

    fun isOfficialCategory(type: String?, targetType: String?): Boolean {
        val kind = BlinkNotificationKind.fromWire(type)
        val target = targetType.orEmpty().trim().lowercase()
        // Do not persist chat, wallet, security or marketplace previews merely
        // because an older backend labelled their target "notification".
        val legacyOfficial = target == "notification" &&
            kind !in setOf(
                BlinkNotificationKind.MESSAGE,
                BlinkNotificationKind.SECURITY,
                BlinkNotificationKind.COIN,
                BlinkNotificationKind.MARKET,
                BlinkNotificationKind.MARKET_ORDER,
                BlinkNotificationKind.INCOMING_CALL,
                BlinkNotificationKind.CALL_UPDATE,
            )
        return kind == BlinkNotificationKind.ADMIN ||
            kind == BlinkNotificationKind.SYSTEM || legacyOfficial
    }

    fun create(
        id: String,
        title: String,
        message: String,
        publishedAt: String = "",
    ): BlinkAnnouncementPreview? {
        val stableId = id.trim()
        if (stableId.isBlank() || stableId.length > 100 ||
            stableId.any { !it.isLetterOrDigit() && it !in "-_:." }) return null
        val safeTitle = title.trim().take(MAX_TITLE).ifBlank { "BLINK announcement" }
        val safeMessage = message.trim().take(MAX_MESSAGE)
        if (safeMessage.isBlank() && title.isBlank()) return null
        return BlinkAnnouncementPreview(stableId, safeTitle, safeMessage, publishedAt.take(64))
    }

    /** Newest first; server IDs dedupe simultaneous push and reconnect recovery. */
    fun upsert(
        existing: List<BlinkAnnouncementPreview>,
        incoming: BlinkAnnouncementPreview,
    ): List<BlinkAnnouncementPreview> =
        (listOf(incoming) + existing.filterNot { it.id == incoming.id })
            .take(MAX_CACHED)
}
