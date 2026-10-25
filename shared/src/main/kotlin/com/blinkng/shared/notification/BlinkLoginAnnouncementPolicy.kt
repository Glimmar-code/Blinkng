package com.blinkng.shared.notification

/**
 * Only server-authorized campaign activity targets qualify as official login pop-ups.
 * Generic user social posts and other "system" activities are never promoted.
 */
data class BlinkLoginAnnouncement(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: String = "",
)

object BlinkLoginAnnouncementPolicy {
    const val MAX_REMEMBERED_IDS = 100

    fun fromCampaignActivity(
        id: String,
        targetType: String?,
        title: String,
        body: String?,
        isUnread: Boolean,
    ): BlinkLoginAnnouncement? {
        if (!isUnread || !targetType.equals("notification", true)) return null
        if (id.isBlank() || id.length > 150) return null
        val cleanTitle = title.trim().take(160)
        if (cleanTitle.isEmpty()) return null
        return BlinkLoginAnnouncement(id, cleanTitle, body.orEmpty().trim().take(800))
    }

    fun nextUnseen(
        candidates: List<BlinkLoginAnnouncement>,
        seenIds: Set<String>,
        safeToShow: Boolean,
        shownThisSession: Boolean,
    ): BlinkLoginAnnouncement? {
        if (!safeToShow || shownThisSession) return null
        return candidates.firstOrNull { it.id !in seenIds }
    }

    fun remember(previous: List<String>, id: String): List<String> =
        (listOf(id) + previous.filterNot { it == id }).take(MAX_REMEMBERED_IDS)
}
