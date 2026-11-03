package com.blinkng.shared

/** Common E.164 validation for signed-in phone verification; not proof of WhatsApp use. */
object BlinkPhonePolicy {
    /** Fail open during staged launch: production SMS provider is not verified. Switch only after live OTP QA. */
    const val REQUIRE_VERIFIED_PHONE_IN_ONBOARDING: Boolean = false
    fun normalize(raw: String): String? {
        val compact = raw.trim().replace(Regex("[\\s()-]"), "")
        val value = when {
            compact.startsWith("+") -> compact
            compact.startsWith("0") && compact.length == 11 -> "+234" + compact.drop(1)
            compact.startsWith("234") && compact.length == 13 -> "+" + compact
            else -> return null
        }
        return value.takeIf { it.matches(Regex("^\\+[1-9][0-9]{7,14}$")) }
    }
    fun otpIsValid(raw: String) = raw.matches(Regex("^[0-9]{6}$"))
}
