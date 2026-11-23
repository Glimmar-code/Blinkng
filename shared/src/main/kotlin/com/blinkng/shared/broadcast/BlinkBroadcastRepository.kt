package com.blinkng.shared.broadcast

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant

/**
 * Server-authoritative broadcast contract shared by Android and Windows adapters.
 * The in-memory preview implementation is deliberately not a production backend.
 */
interface BlinkBroadcastRepository {
    fun observeBroadcasts(): Flow<List<Broadcast>>
    suspend fun getActiveBroadcasts(): List<Broadcast>
    suspend fun dismiss(id: String)
    suspend fun markRead(id: String)
    suspend fun markAllRead()
    suspend fun trackImpression(id: String)
    suspend fun trackClick(id: String)
    suspend fun joinCampaign(id: String)
    suspend fun getCampaignProgress(id: String): Int
    suspend fun getLeaderboard(id: String): List<BroadcastLeaderboardEntry>
    suspend fun claimPromo(id: String): BroadcastClaimState
    suspend fun getInbox(): List<BroadcastInboxItem>
    suspend fun getPrefs(): BroadcastNotificationPrefs
    suspend fun updatePrefs(prefs: BroadcastNotificationPrefs)
    suspend fun registerDeviceToken(token: String)
    suspend fun uploadBroadcastImage(imageBytes: ByteArray, mimeType: String): String

    suspend fun getAdminBroadcasts(): List<Broadcast>
    suspend fun createBroadcast(draft: BroadcastDraft): Broadcast
    suspend fun updateBroadcast(id: String, draft: BroadcastDraft): Broadcast
    suspend fun scheduleBroadcast(id: String)
    suspend fun endBroadcast(id: String)
    suspend fun deleteDraft(id: String)
    suspend fun estimateAudience(filters: BroadcastAudienceFilter): BroadcastAudienceEstimate
    suspend fun sendTest(id: String)
    suspend fun getAnalytics(id: String): BroadcastAnalytics
    suspend fun getAuditLog(): List<BroadcastAuditEntry>
}

/** Convenience base for explicit disconnected/migration adapters, never a live demo dataset. */
abstract class BlinkBroadcastRepositoryAdapter : BlinkBroadcastRepository {
    protected fun unavailable(): Nothing = throw IllegalStateException("Broadcast backend is not connected.")
    override fun observeBroadcasts(): Flow<List<Broadcast>> = flowOf(emptyList())
    override suspend fun getActiveBroadcasts(): List<Broadcast> = emptyList()
    override suspend fun dismiss(id: String): Unit = unavailable()
    override suspend fun markRead(id: String): Unit = unavailable()
    override suspend fun markAllRead(): Unit = unavailable()
    override suspend fun trackImpression(id: String): Unit = unavailable()
    override suspend fun trackClick(id: String): Unit = unavailable()
    override suspend fun joinCampaign(id: String): Unit = unavailable()
    override suspend fun getCampaignProgress(id: String): Int = unavailable()
    override suspend fun getLeaderboard(id: String): List<BroadcastLeaderboardEntry> = unavailable()
    override suspend fun claimPromo(id: String): BroadcastClaimState = unavailable()
    override suspend fun getInbox(): List<BroadcastInboxItem> = emptyList()
    override suspend fun getPrefs(): BroadcastNotificationPrefs = BroadcastNotificationPrefs()
    override suspend fun updatePrefs(prefs: BroadcastNotificationPrefs): Unit = unavailable()
    override suspend fun registerDeviceToken(token: String): Unit = unavailable()
    override suspend fun uploadBroadcastImage(imageBytes: ByteArray, mimeType: String): String = unavailable()
    override suspend fun getAdminBroadcasts(): List<Broadcast> = unavailable()
    override suspend fun createBroadcast(draft: BroadcastDraft): Broadcast = unavailable()
    override suspend fun updateBroadcast(id: String, draft: BroadcastDraft): Broadcast = unavailable()
    override suspend fun scheduleBroadcast(id: String): Unit = unavailable()
    override suspend fun endBroadcast(id: String): Unit = unavailable()
    override suspend fun deleteDraft(id: String): Unit = unavailable()
    override suspend fun estimateAudience(filters: BroadcastAudienceFilter): BroadcastAudienceEstimate = unavailable()
    override suspend fun sendTest(id: String): Unit = unavailable()
    override suspend fun getAnalytics(id: String): BroadcastAnalytics = unavailable()
    override suspend fun getAuditLog(): List<BroadcastAuditEntry> = unavailable()
}

/** Sample data is explicitly watermarked and only installed for preview/demo/test wiring. */
object BlinkBroadcastPreviewData {
    fun samples(now: Instant = Instant.now()): List<Broadcast> = listOf(
        Broadcast(
            id = "preview-announcement",
            type = BroadcastType.ANNOUNCEMENT,
            title = "A more connected campus starts here",
            body = "Meet people, discover events and find new opportunities across BLINK.",
            ctaLabel = "Explore BLINK",
            deepLink = "blink://contest",
            priority = 100,
            startsAt = now.minusSeconds(300),
            endsAt = now.plusSeconds(86_400),
            isPreview = true,
        ),
        Broadcast(
            id = "preview-campaign",
            type = BroadcastType.CAMPAIGN,
            title = "Bring your circle together",
            body = "Invite friends to join the campus conversation.",
            ctaLabel = "View campaign",
            deepLink = "blink://campaign/preview-campaign",
            priority = 80,
            startsAt = now.minusSeconds(600),
            endsAt = now.plusSeconds(172_800),
            progress = 3,
            target = 5,
            rewardText = "Earn a campaign reward",
            rules = BroadcastRules(
                goalType = CampaignGoalType.INVITES,
                target = 5,
                rewardTiers = listOf(
                    BroadcastRewardTier(3, "3 invites reward"),
                    BroadcastRewardTier(5, "5 invites reward"),
                ),
            ),
            joined = true,
            isPreview = true,
        ),
        Broadcast(
            id = "preview-promo",
            type = BroadcastType.PROMO,
            title = "A little extra for your next order",
            body = "A sample offer shown for layout preview only.",
            ctaLabel = "View offer",
            deepLink = "blink://promo/preview-promo",
            priority = 50,
            startsAt = now.minusSeconds(120),
            endsAt = now.plusSeconds(3_600),
            rewardText = "10 bonus coins",
            rules = BroadcastRules(
                rewardType = PromotionRewardType.COINS,
                rewardAmount = 10,
                perUserLimit = 1,
                totalBudget = 500,
                placement = setOf("market"),
            ),
            placement = setOf("market"),
            isPreview = true,
        ),
    )
}
