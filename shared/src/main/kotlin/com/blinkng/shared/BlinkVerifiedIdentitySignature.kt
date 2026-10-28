package com.blinkng.shared

/**
 * Included identity treatment for active subscriptions, deliberately distinct
 * from separately purchased Coin Store cosmetic ownership.
 */
object BlinkVerifiedIdentitySignature {
    fun label(tier: String?, isActive: Boolean): String? {
        if (!isActive) return null
        return when (tier?.trim()?.uppercase()) {
            "BLUE" -> "BLUE · BLINK MEMBER"
            "GOLD" -> "GOLD · CREATOR SIGNATURE"
            else -> null
        }
    }
}
