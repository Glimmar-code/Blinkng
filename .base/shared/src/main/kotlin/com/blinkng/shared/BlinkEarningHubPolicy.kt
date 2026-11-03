package com.blinkng.shared

/** Presentation only: rewards and balances must be confirmed by Supabase. */
object BlinkEarningHubPolicy {
    enum class MissionAction { CLAIM, COMPLETED, IN_PROGRESS }

    fun action(mission: BlinkDailyMission): MissionAction = when {
        mission.claimed -> MissionAction.COMPLETED
        mission.claimable -> MissionAction.CLAIM
        else -> MissionAction.IN_PROGRESS
    }

    fun coinChangeLabel(amount: Long): String =
        if (amount > 0) "+$amount" else amount.toString()

    fun readableTransaction(raw: String): String = when (raw.uppercase()) {
        "REWARDED_AD" -> "Rewarded ad"
        "DAILY_MISSION" -> "Daily mission"
        "VIP_DAILY_BONUS" -> "VIP coin bonus"
        "PURCHASE" -> "Store purchase"
        "VERIFICATION" -> "Verification"
        "BLUE_VERIFICATION" -> "Blue verification"
        "COIN_PURCHASE" -> "Coin purchase"
        else -> "Wallet activity"
    }

    /** Earning from referrals is OFF until a verified backend ruleset exists. */
    const val REFERRAL_COIN_CREDITS_ENABLED = false
    const val CASH_WITHDRAWALS_ENABLED = false
}
