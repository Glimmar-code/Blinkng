package com.blinkng.shared.live

/** Public *presentation-only* configuration. Never a source of authorization. */
data class BlinkLiveSetting(val key: String, val fields: Map<String, String>)

data class BlinkLiveMessage(val title: String, val message: String) {
    val id: String get() = title + "\n" + message
}

data class BlinkLiveSnapshot(
    val announcement: BlinkLiveMessage? = null,
    val promotion: BlinkLiveMessage? = null,
    val showAnnouncements: Boolean = true,
    val showPromotions: Boolean = true,
    val homePromoEnabled: Boolean = true,
    val campaignPlacementFlags: Map<String, Boolean> = emptyMap(),
) {
    val visibleAnnouncement: BlinkLiveMessage?
        get() = announcement.takeIf { showAnnouncements }
    val visiblePromotion: BlinkLiveMessage?
        get() = promotion.takeIf { showPromotions && homePromoEnabled }

    /** Only optional presentation is affected; navigation, account and payment rules stay compiled. */
    fun campaignsEnabledOn(placement: String): Boolean = campaignPlacementFlags[placement] != false
}

/** Strict allowlist + bounded text. New client code is required for new capabilities. */
object BlinkLiveConfigPolicy {
    private fun text(value: String?, maxLength: Int): String =
        value.orEmpty().trim().take(maxLength).filter { it >= ' ' && it != '\u007f' }

    private fun flag(value: String?, default: Boolean = true): Boolean = when (value) {
        "true" -> true
        "false" -> false
        else -> default
    }

    private fun message(fields: Map<String, String>?): BlinkLiveMessage? {
        if (fields == null) return null
        val title = text(fields["title"], 80)
        val body = text(fields["message"], 300)
        return if (title.isNotEmpty() && body.isNotEmpty()) BlinkLiveMessage(title, body) else null
    }

    fun evaluate(rows: List<BlinkLiveSetting>): BlinkLiveSnapshot {
        val allowed = rows.filter {
            it.key in setOf("announcement", "promotion", "display", "feature_visibility")
        }.associateBy { it.key }
        val display = allowed["display"]?.fields.orEmpty()
        val features = allowed["feature_visibility"]?.fields.orEmpty()
        return BlinkLiveSnapshot(
            announcement = message(allowed["announcement"]?.fields),
            promotion = message(allowed["promotion"]?.fields),
            showAnnouncements = flag(display["show_announcements"]),
            showPromotions = flag(display["show_promotions"]),
            homePromoEnabled = flag(features["home_promo_enabled"]),
            campaignPlacementFlags = BlinkLiveCampaignPolicy.placements.associateWith {
                page -> flag(features[page + "_campaign_enabled"])
            },
        )
    }
}
