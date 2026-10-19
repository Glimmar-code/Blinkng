package com.blinkng.shared.live

/** Campaign content only; no permission, payment or verification state. */
data class BlinkLiveCampaign(
    val id: String,
    val kind: String,
    val title: String,
    val message: String,
    val imageUrl: String? = null,
    val actionLabel: String? = null,
    val actionRoute: String? = null,
    val style: String = "standard",
    val accent: String = "neutral",
    val pinned: Boolean = false,
    val priority: Int = 0,
    val frequencyHours: Int = 24,
    val endsAt: String? = null,
)

object BlinkLiveCampaignPolicy {
    val placements = setOf("home", "reels", "marketplace", "connect", "messages", "games")
    val routes = setOf("home", "reels", "marketplace", "connect", "messages", "games",
        "notifications", "store", "leaderboard", "profile")
    val kinds = setOf("announcement", "promotion", "marketplace", "game", "challenge",
        "event", "maintenance", "emergency")
    val styles = setOf("standard", "highlight", "urgent")
    val accents = setOf("neutral", "blue", "gold")
    private fun strip(s: String, limit: Int): String =
        s.trim().filter { it >= ' ' && it != '\u007f' }.take(limit)

    /** Server may change content only, never add a new route/feature or external executable link. */
    fun sanitize(item: BlinkLiveCampaign): BlinkLiveCampaign? {
        if (item.id.isBlank() || item.kind !in kinds) return null
        val title = strip(item.title, 80)
        val message = strip(item.message, 500)
        if (title.isBlank() || message.isBlank()) return null
        val route = item.actionRoute?.takeIf { it in routes }
        val label = item.actionLabel?.let { strip(it, 36) }?.takeIf { it.isNotBlank() }
        val image = item.imageUrl?.takeIf {
            it.length <= 500 && it.startsWith("https://", ignoreCase = false)
                    && it.none(Char::isWhitespace)
        }
        return item.copy(
            title = title,
            message = message,
            imageUrl = image,
            actionLabel = if (route != null) label else null,
            actionRoute = if (label != null) route else null,
            style = item.style.takeIf { it in styles } ?: "standard",
            accent = item.accent.takeIf { it in accents } ?: "neutral",
            frequencyHours = item.frequencyHours.coerceIn(0, 168),
            priority = item.priority.coerceIn(0, 100)
        )
    }
    fun displayFor(placement: String, campaigns: List<BlinkLiveCampaign>): List<BlinkLiveCampaign> {
        if (placement !in placements) return emptyList()
        return campaigns.mapNotNull(::sanitize)
            .sortedWith(compareByDescending<BlinkLiveCampaign> { it.pinned || it.kind == "emergency" }
                .thenByDescending { it.priority })
            .distinctBy { it.id }
            .take(2)
    }
}
