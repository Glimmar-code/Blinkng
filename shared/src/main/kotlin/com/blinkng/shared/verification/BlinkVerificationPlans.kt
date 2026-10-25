package com.blinkng.shared.verification

/** All prices are in NGN, quotes are display-only until server checkout verifies them. */
enum class BlinkVerificationTier(val displayName: String, val monthlyNgn: Int) {
    FREE("Free", 0),
    BLUE("Blue", 800),
    GOLD("Gold", 2_000),
}

data class BlinkPlanDuration(val months: Int, val discountPercent: Int) {
    init {
        require(months in listOf(1, 3, 6, 12))
        require(discountPercent in 0..100)
    }
}

data class BlinkPlanQuote(
    val tier: BlinkVerificationTier,
    val months: Int,
    val totalNgn: Int,
    val savingsNgn: Int,
    val discountPercent: Int,
) {
    val description: String
        get() = if (months == 1) "1 month" else "$months months"
}

object BlinkVerificationPlans {
    val durations = listOf(
        BlinkPlanDuration(1, 0),
        BlinkPlanDuration(3, 10),
        BlinkPlanDuration(6, 20),
        BlinkPlanDuration(12, 30),
    )
    const val BLUE_COIN_PRICE_30_DAYS = 3_000

    fun quote(tier: BlinkVerificationTier, months: Int): BlinkPlanQuote {
        val duration = durations.firstOrNull { it.months == months }
            ?: throw IllegalArgumentException("Unsupported subscription duration")
        val subtotal = tier.monthlyNgn * months
        val savings = subtotal * duration.discountPercent / 100
        return BlinkPlanQuote(tier, months, subtotal - savings, savings, duration.discountPercent)
    }

    fun isCheckoutReady(providerReady: Boolean, verifiedWebhookReady: Boolean): Boolean =
        providerReady && verifiedWebhookReady

    /** Renewals are allowed only after explicit, recorded informed consent. */
    fun authorizedAutoRenew(
        userOptedIn: Boolean,
        paymentProviderSupportsRecurring: Boolean,
        verifiedWebhookReady: Boolean,
    ): Boolean = userOptedIn && paymentProviderSupportsRecurring && verifiedWebhookReady

    fun tierBenefits(tier: BlinkVerificationTier): List<String> = when (tier) {
        BlinkVerificationTier.FREE -> listOf("Posts, reels and messages", "Browse BLINK Market", "Join campus communities")
        BlinkVerificationTier.BLUE -> listOf("Free benefits", "Blue profile badge", "Verified-user seller application eligibility")
        BlinkVerificationTier.GOLD -> listOf("Blue benefits", "Gold profile badge", "Gold status presentation")
    }
}
