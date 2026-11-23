package com.example.data.repository

import com.blinkng.shared.broadcast.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * Demo-only adapter. It has no Supabase client and does not persist or award
 * anything. Replace the injected repository with the backend adapter before
 * enabling this feature for production accounts.
 */
class BlinkBroadcastPreviewRepository(
    initial: List<Broadcast> = BlinkBroadcastPreviewData.samples(),
) : BlinkBroadcastRepository {
    private val lock = Mutex()
    private val items = MutableStateFlow(initial)
    private var prefs = BroadcastNotificationPrefs()
    private var inbox = initial.map { BroadcastInboxItem(it, Instant.now(), read = it.isRead) }
    private val impressions = mutableMapOf<String, Long>()
    private val clicks = mutableMapOf<String, Long>()

    override fun observeBroadcasts(): Flow<List<Broadcast>> = items
    override suspend fun getActiveBroadcasts() = items.value.filterNot { it.isDismissed }
        .sortedWith(compareByDescending<Broadcast> { it.priority }.thenBy { it.endsAt })

    override suspend fun dismiss(id: String) = lock.withLock {
        items.value = items.value.map { if (it.id == id) it.copy(isDismissed = true) else it }
        inbox = inbox.filterNot { it.broadcast.id == id }
    }

    override suspend fun markRead(id: String) = lock.withLock {
        items.value = items.value.map { if (it.id == id) it.copy(isRead = true) else it }
        inbox = inbox.map { if (it.broadcast.id == id) it.copy(read = true) else it }
    }

    override suspend fun markAllRead() = lock.withLock {
        items.value = items.value.map { it.copy(isRead = true) }
        inbox = inbox.map { it.copy(read = true) }
    }

    override suspend fun trackImpression(id: String) { impressions[id] = (impressions[id] ?: 0) + 1 }
    override suspend fun trackClick(id: String) { clicks[id] = (clicks[id] ?: 0) + 1 }
    override suspend fun joinCampaign(id: String) = lock.withLock {
        items.value = items.value.map { if (it.id == id) it.copy(joined = true) else it }
    }
    override suspend fun getCampaignProgress(id: String) = items.value.firstOrNull { it.id == id }?.progress ?: 0
    override suspend fun getLeaderboard(id: String) = (1..20).map {
        BroadcastLeaderboardEntry(it, "Preview participant $it", (21 - it) * 2, it == 7)
    }

    override suspend fun claimPromo(id: String): BroadcastClaimState = lock.withLock {
        val current = items.value.firstOrNull { it.id == id } ?: return@withLock BroadcastClaimState.EXPIRED
        val state = if (Instant.now().isAfter(current.endsAt)) BroadcastClaimState.EXPIRED
        else BroadcastClaimState.CLAIMED
        items.value = items.value.map { if (it.id == id) it.copy(claimState = state) else it }
        state
    }

    override suspend fun getInbox() = inbox
    override suspend fun getPrefs() = prefs
    override suspend fun updatePrefs(prefs: BroadcastNotificationPrefs) { this.prefs = prefs }
    override suspend fun registerDeviceToken(token: String) = Unit
    override suspend fun uploadBroadcastImage(imageBytes: ByteArray, mimeType: String): String {
        require(imageBytes.isNotEmpty() && imageBytes.size <= 8 * 1024 * 1024)
        require(mimeType in setOf("image/jpeg", "image/png", "image/webp"))
        return "preview-upload://$mimeType/${imageBytes.size}"
    }
    override suspend fun getAdminBroadcasts() = items.value
    override suspend fun createBroadcast(draft: BroadcastDraft): Broadcast {
        val item = draft.toBroadcast(id = "preview-${System.nanoTime()}")
        items.value = listOf(item) + items.value
        inbox = listOf(BroadcastInboxItem(item, Instant.now())) + inbox
        return item
    }
    override suspend fun updateBroadcast(id: String, draft: BroadcastDraft): Broadcast {
        val updated = draft.toBroadcast(id)
        items.value = items.value.map { if (it.id == id) updated else it }
        return updated
    }
    override suspend fun scheduleBroadcast(id: String) {
        items.value = items.value.map {
            if (it.id == id) it.copy(status = if (it.startsAt.isAfter(Instant.now())) BroadcastStatus.SCHEDULED else BroadcastStatus.LIVE)
            else it
        }
    }
    override suspend fun endBroadcast(id: String) {
        items.value = items.value.map {
            if (it.id == id) it.copy(status = BroadcastStatus.ENDED, endsAt = Instant.now()) else it
        }
    }
    override suspend fun deleteDraft(id: String) {
        items.value = items.value.filterNot { it.id == id && it.status == BroadcastStatus.DRAFT }
    }
    override suspend fun estimateAudience(filters: BroadcastAudienceFilter) =
        BroadcastAudienceEstimate(if (filters.kind == BroadcastAudienceKind.EVERYONE) 12_450 else 860)
    override suspend fun sendTest(id: String) = Unit
    override suspend fun getAnalytics(id: String) = BroadcastAnalytics(
        reach = 12450, impressions = impressions[id] ?: 4820, clicks = clicks[id] ?: 617,
        conversions = 183, dismissals = 74, claims = 91, budgetUsed = 910, budgetTotal = 2500,
        series = listOf(8, 15, 12, 28, 34, 51, 48),
        audienceBreakdown = mapOf("Students" to 8900, "Sellers" to 2300, "Other" to 1250),
    )
    override suspend fun getAuditLog() = listOf(
        BroadcastAuditEntry("preview-audit", "Preview admin", "Edited", "Preview broadcast", Instant.now())
    )
}

private fun BroadcastDraft.toBroadcast(id: String) = Broadcast(
    id = id, type = type, title = title, body = body, imageUrl = imageUrl,
    ctaLabel = ctaLabel, deepLink = deepLink, priority = priority,
    startsAt = startsAt, endsAt = endsAt, dismissible = dismissible,
    isBlocking = isBlocking, audience = audience, channels = channels,
    rules = rules, target = rules.target, rewardText = rules.rewardTiers.firstOrNull()?.reward.orEmpty(),
    isPreview = true, status = BroadcastStatus.DRAFT, placement = rules.placement,
)
