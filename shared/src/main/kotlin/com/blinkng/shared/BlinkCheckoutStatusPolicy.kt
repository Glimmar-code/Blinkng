package com.blinkng.shared

/**
 * Server-authoritative Paystack verification presentation state, shared by Android and Windows.
 *
 * These values are ONLY for client UX. A browser return, cancelled checkout or client-side
 * dismissal never authorizes payment, refunds, fulfils an order or changes backend records.
 */
enum class BlinkCheckoutVerificationState {
    FULFILLED, ABANDONED, FAILED, REVERSED, PENDING, ONGOING, PROCESSING, UNKNOWN;

    val isConfirmed: Boolean get() = this == FULFILLED
    val stopsLocalReminder: Boolean get() = this in setOf(ABANDONED, FAILED, REVERSED)
    val maySettleLater: Boolean get() = this in setOf(PENDING, ONGOING, PROCESSING, UNKNOWN)
}

object BlinkCheckoutStatusPolicy {
    fun parse(status: String?): BlinkCheckoutVerificationState = when (status?.trim()?.lowercase()) {
        "fulfilled" -> BlinkCheckoutVerificationState.FULFILLED
        "abandoned", "cancelled", "canceled" -> BlinkCheckoutVerificationState.ABANDONED
        "failed" -> BlinkCheckoutVerificationState.FAILED
        "reversed" -> BlinkCheckoutVerificationState.REVERSED
        "pending" -> BlinkCheckoutVerificationState.PENDING
        "ongoing" -> BlinkCheckoutVerificationState.ONGOING
        "processing" -> BlinkCheckoutVerificationState.PROCESSING
        else -> BlinkCheckoutVerificationState.UNKNOWN
    }
}
