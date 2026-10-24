package com.blinkng.shared.discovery

/** Shared Android + Windows referral contract. The server owns all credits and ranking. */
data class BlinkReferralSuggestion(
    val id: String,
    val username: String,
    val fullName: String,
    val avatarUrl: String? = null,
    val university: String? = null,
)

data class BlinkReferralStats(
    val total: Long = 0,
    val activeLast30Days: Long = 0,
    val bonusPercent: Int = 0,
    val visibilityLabel: String = "Normal discovery",
)

/** Copy is deliberately a possibility, not a promise of followers, views or placement. */
object BlinkReferralPolicy {
    const val PROMO_TITLE = "Refer friends. Get discovered."
    const val PROMO_MESSAGE =
        "Invite students to BLINK. Confirmed referrals can improve your chances of being recommended to new members."
    const val INVITATION_MESSAGE = "Join me on BLINK — connect and discover students beyond link."
    const val MAX_PAGE_SIZE = 10

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
