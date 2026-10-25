package com.blinkng.shared.discovery

/** Shared Android + Windows referral contract. The server owns all credits and ranking. */
data class BlinkReferralSuggestion(
    val id: String,
    val username: String,
    val fullName: String,
    val avatarUrl: String? = null,
    val university: String? = null,
    val isVerified: Boolean = false,
    val verificationBadge: String? = null,
    val visibilityPoints: Int = 0,
)

data class BlinkReferralStats(
    val total: Long = 0,
    val activeLast30Days: Long = 0,
    val visibilityPoints: Int = 0,
    val visibilityLabel: String = "Normal discovery",
    val achievementTier: String = "New member",
    val nextAchievementTarget: Int? = 1,
)

data class BlinkReferralCoinStatus(
    val earnedCoins: Long = 0,
    val nextMilestone: Int? = null,
    val rewardEnabled: Boolean = false,
    val nextRewardCoins: Int = 0,
)

data class BlinkReferralLinkStats(
    val estimatedUniqueOpens30Days: Long = 0,
    val confirmedSignups30Days: Long = 0,
)

data class BlinkReferralSourceStats(
    val searchProfileOpens: Long = 0,
    val recommendationProfileOpens: Long = 0,
)

/** Aggregated counts from real owned activity; do not infer missing traffic sources. */
data class BlinkReferralGrowthStats(
    val profileViewsThisWeek: Long = 0,
    val followersGainedThisWeek: Long = 0,
    val shareActions: Long = 0,
)

data class BlinkReferralLeaderboardEntry(
    val position: Long,
    val id: String,
    val username: String,
    val fullName: String,
    val avatarUrl: String? = null,
    val university: String? = null,
    val confirmedReferrals: Long = 0,
)

/** Copy is deliberately a possibility, not a promise of followers, views or placement. */
object BlinkReferralPolicy {
    const val PROMO_TITLE = "Refer friends. Get discovered."
    const val PROMO_MESSAGE =
        "Invite students to BLINK. Confirmed referrals can improve your chances of being recommended to new members."
    const val INVITATION_MESSAGE = "Join me on BLINK — connect and discover students beyond link."
    const val MAX_PAGE_SIZE = 10

    /** Only actual confirmed referrals earn a permanent achievement. */
    val MILESTONE_TARGETS = listOf(1, 5, 10, 25, 50, 100)

    fun tierForLifetime(total: Long): String = when {
        total >= 100L -> "Ambassador"
        total >= 50L -> "Champion"
        total >= 25L -> "Connector"
        total >= 10L -> "Advocate"
        total >= 5L -> "Builder"
        total >= 1L -> "Starter"
        else -> "New member"
    }

    fun nextMilestone(total: Long): Int? =
        MILESTONE_TARGETS.firstOrNull { total < it }

    fun milestoneLabel(stats: BlinkReferralStats): String =
        stats.nextAchievementTarget?.let { target ->
            val remaining = (target.toLong() - stats.total).coerceAtLeast(0)
            "$remaining more confirmed referrals to reach $target"
        } ?: "All referral milestones unlocked"

    /** A three-step 30-day fade with a hard cap. The backend is authoritative. */
    fun pointsForReferralAgeDays(days: Long): Int = when {
        days < 0L -> 0
        days < 10L -> 3
        days < 20L -> 2
        days < 30L -> 1
        else -> 0
    }

    fun normalizedQuery(value: String): String = value.trim().removePrefix("@").take(40)
    fun inviteUrl(username: String): String {
        val clean = username.trim().lowercase().removePrefix("@")
        require(clean.matches(Regex("[a-z0-9][a-z0-9._-]{1,29}"))) { "Invalid BLINK username" }
        return "https://blink.com.ng/invite/$clean"
    }

    fun selectionLabel(suggestion: BlinkReferralSuggestion): String =
        "${suggestion.fullName.ifBlank { suggestion.username }} (@${suggestion.username})"

    /** Exact username always wins; a visibility boost must not override an exact match. */
    fun suggestionRank(query: String, suggestion: BlinkReferralSuggestion): Int {
        val term = normalizedQuery(query).lowercase()
        if (term.isBlank()) return 9
        val username = suggestion.username.lowercase()
        val name = suggestion.fullName.lowercase()
        return when {
            username == term -> 0
            username.startsWith(term) -> 1
            name.startsWith(term) -> 2
            username.contains(term) -> 3
            name.contains(term) -> 4
            else -> 9
        }
    }
}
