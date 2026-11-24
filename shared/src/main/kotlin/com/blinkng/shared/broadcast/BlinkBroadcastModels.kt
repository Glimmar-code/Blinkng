package com.blinkng.shared.broadcast

import java.time.Instant

enum class BroadcastType { ANNOUNCEMENT, CAMPAIGN, PROMO }
enum class BroadcastClaimState { AVAILABLE, CLAIMING, CLAIMED, LIMIT_REACHED, EXPIRED, SOLD_OUT }
enum class BroadcastStatus { DRAFT, SCHEDULED, LIVE, ENDED }
enum class BroadcastAudienceKind {
    EVERYONE, CAMPUS, NEW_USERS, INACTIVE_7_DAYS, INACTIVE_30_DAYS, SELLERS, VERIFIED, APP_VERSION
}
enum class CampaignGoalType { INVITES, PURCHASES, ORDERS, VOTES }
enum class PromotionRewardType { COINS, FREE_SPIN, FEE_DISCOUNT, BOOST }
enum class BroadcastChannel { IN_APP, PUSH, EMAIL, WEBSITE }

data class BroadcastRewardTier(val threshold: Int, val reward: String, val amount: Int = 0)
data class BroadcastAudienceFilter(
    val kind: BroadcastAudienceKind = BroadcastAudienceKind.EVERYONE,
    val campuses: Set<String> = emptySet(),
    val appVersion: String = "",
)
data class BroadcastRules(
    val goalType: CampaignGoalType? = null,
    val target: Int? = null,
    val rewardTiers: List<BroadcastRewardTier> = emptyList(),
    val rewardType: PromotionRewardType? = null,
    val rewardAmount: Int? = null,
    val perUserLimit: Int? = null,
    val totalBudget: Int? = null,
    val minimumPurchase: Int? = null,
    val placement: Set<String> = emptySet(),
    val respectQuietHours: Boolean = true,
    val maxPushPerWeek: Int = 3,
)

/** Server-backed object. The preview repository is only for previews/demo wiring. */
data class Broadcast(
    val id: String,
    val type: BroadcastType,
    val title: String,
    val body: String,
    val imageUrl: String? = null,
    val ctaLabel: String = "",
    val deepLink: String = "",
    val priority: Int = 0,
    val startsAt: Instant,
    val endsAt: Instant,
    val dismissible: Boolean = true,
    val isBlocking: Boolean = false,
    val progress: Int = 0,
    val target: Int? = null,
    val rewardText: String = "",
    val claimState: BroadcastClaimState = BroadcastClaimState.AVAILABLE,
    val status: BroadcastStatus = BroadcastStatus.LIVE,
    val audience: BroadcastAudienceFilter = BroadcastAudienceFilter(),
    val channels: Set<BroadcastChannel> = setOf(BroadcastChannel.IN_APP),
    val rules: BroadcastRules = BroadcastRules(),
    val joined: Boolean = false,
    val isRead: Boolean = false,
    val isDismissed: Boolean = false,
    val isPreview: Boolean = false,
    val completed: Boolean = false,
    val placement: Set<String> = emptySet(),
)

data class BroadcastLeaderboardEntry(val rank: Int, val name: String, val score: Int, val isCurrentUser: Boolean = false)
data class BroadcastInboxItem(val broadcast: Broadcast, val receivedAt: Instant, val read: Boolean = false)
data class BroadcastNotificationPrefs(
    val pushEnabled: Boolean = true,
    val announcements: Boolean = true,
    val campaigns: Boolean = true,
    val offers: Boolean = true,
    val emailEnabled: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: String = "22:00",
    val quietHoursEnd: String = "08:00",
)
data class BroadcastAudienceEstimate(val reach: Int, val isEstimate: Boolean = true)
data class BroadcastAnalytics(
    val reach: Long = 0,
    val impressions: Long = 0,
    val clicks: Long = 0,
    val conversions: Long = 0,
    val dismissals: Long = 0,
    val claims: Long = 0,
    val budgetUsed: Long = 0,
    val budgetTotal: Long = 0,
    val series: List<Long> = emptyList(),
    val audienceBreakdown: Map<String, Long> = emptyMap(),
)
data class BroadcastAuditEntry(
    val id: String,
    val actor: String,
    val action: String,
    val broadcastTitle: String,
    val at: Instant,
)

/** Draft shared by the stepped create form and admin repository. */
data class BroadcastDraft(
    val id: String? = null,
    val type: BroadcastType = BroadcastType.ANNOUNCEMENT,
    val title: String = "",
    val body: String = "",
    val imageUrl: String? = null,
    val ctaLabel: String = "",
    val deepLink: String = "",
    val isBlocking: Boolean = false,
    val rules: BroadcastRules = BroadcastRules(),
    val audience: BroadcastAudienceFilter = BroadcastAudienceFilter(),
    val channels: Set<BroadcastChannel> = setOf(BroadcastChannel.IN_APP),
    val startsAt: Instant = Instant.now(),
    val endsAt: Instant = Instant.now().plusSeconds(86_400),
    val timezone: String = "Africa/Lagos",
    val priority: Int = 0,
    val dismissible: Boolean = true,
)

object BlinkBroadcastRoutes {
    const val INBOX = "broadcasts/inbox"
    const val SETTINGS = "broadcasts/settings"
    const val ADMIN = "broadcasts/admin"
    const val CREATE = "broadcasts/create"
    const val ANALYTICS = "broadcasts/analytics"
    const val AUDIT = "broadcasts/audit"
    const val HOME = "home"

    fun campaign(id: String) = "broadcasts/campaign/${id.encodeRoutePart()}"
    fun promo(id: String) = "broadcasts/promo/${id.encodeRoutePart()}"
    fun edit(id: String) = "broadcasts/edit/${id.encodeRoutePart()}"
    fun analytics(id: String) = "broadcasts/analytics/${id.encodeRoutePart()}"

    fun resolveDeepLink(raw: String?): String {
        val value = raw?.trim().orEmpty()
        val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return HOME
        if (!uri.scheme.equals("blink", ignoreCase = true)) return HOME
        val host = uri.host?.lowercase() ?: return HOME
        val id = uri.path.trim('/').takeIf { it.matches(Regex("[A-Za-z0-9._~-]{1,128}")) }
        return when {
            host in setOf("spin", "market", "contest", "wallet") -> host
            host == "campaign" && id != null -> campaign(id)
            host == "promo" && id != null -> promo(id)
            host == "profile" && id != null -> "profile/$id"
            else -> HOME
        }
    }

    private fun String.encodeRoutePart(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
        .replace("+", "%20")
}

data class BroadcastComposition(
    val title: String,
    val body: String,
    val type: BroadcastType,
    val ctaLabel: String = "",
    val deepLink: String = "",
    val isBlocking: Boolean = false,
)

object BlinkBroadcastValidation {
    fun validate(draft: BroadcastDraft, audienceConfirm: String = ""): List<String> = buildList {
        if (draft.title.isBlank() || draft.title.length > 60) add("title")
        if (draft.body.isBlank() || draft.body.length > 240) add("body")
        if (draft.endsAt <= draft.startsAt) add("schedule")
        if (draft.channels.isEmpty()) add("channel")
        if (draft.audience.kind == BroadcastAudienceKind.EVERYONE && audienceConfirm != "CONFIRM") {
            add("confirm_everyone")
        }
        when (draft.type) {
            BroadcastType.CAMPAIGN -> if ((draft.rules.target ?: 0) <= 0 || draft.rules.goalType == null || draft.rules.rewardTiers.any { it.threshold <= 0 || it.amount <= 0 }) add("campaign_rules")
            BroadcastType.PROMO -> if ((draft.rules.rewardAmount ?: 0) <= 0 || draft.rules.rewardType != PromotionRewardType.COINS ||
                (draft.rules.perUserLimit ?: 0) <= 0 || (draft.rules.minimumPurchase ?: 0) > 0) add("promo_rules")
            BroadcastType.ANNOUNCEMENT -> Unit
        }
    }
}
