package com.blinkng.shared

import java.net.URI

/**
 * A fixed-price coin pack returned by the authenticated backend.
 * Client code must never invent an offer or credit coins locally.
 */
data class BlinkCoinCheckoutOffer(val id: String, val priceNgn: Int, val coins: Int) {
    val bonusCoins: Int get() = (coins - priceNgn).coerceAtLeast(0)
}

/** Safe, identical checkout decisions on Android and Windows. */
object BlinkCoinCheckoutPolicy {
    fun validOffer(id: String, priceNgn: Int, coins: Int): BlinkCoinCheckoutOffer? {
        if (!Regex("coins_[0-9]+").matches(id) || priceNgn <= 0 || coins <= 0) return null
        // These guardrails avoid a corrupt or fabricated backend row, not a pricing policy.
        if (priceNgn > 1_000_000 || coins > 20_000_000) return null
        return BlinkCoinCheckoutOffer(id, priceNgn, coins)
    }

    fun canCheckout(cashCheckoutEnabled: Boolean, offer: BlinkCoinCheckoutOffer?): Boolean =
        cashCheckoutEnabled && offer != null

    /**
     * Protect users from a compromised checkout response pointing to an unexpected website.
     * Valid Paystack hosted pages use https://checkout.paystack.com/...
     */
    fun trustedHostedCheckoutUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return runCatching {
            val uri = URI(url.trim())
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals("checkout.paystack.com", ignoreCase = true) &&
                uri.rawUserInfo == null &&
                uri.port == -1 &&
                uri.rawFragment == null
        }.getOrDefault(false)
    }
}
