package com.blinkng.shared

enum class BlinkDropAudienceScope {
    ALL_CAMPUSES,
    MY_CAMPUS,
}

enum class BlinkDropTargetType {
    POST,
    REEL,
    LISTING,
}

enum class BlinkDropAction {
    LIKE,
    COMMENT,
    REPOST,
    SAVE_LISTING,
}

/**
 * Shared guardrails for BLINK Drops.
 *
 * Drops are deterministic rewards funded entirely from the organizer's existing
 * BLINK Coin balance. There is no random draw, wager, or paid entry.
 */
object BlinkDropsPolicy {
    const val MIN_REWARD_PER_USER = 100
    const val MAX_REWARD_PER_USER = 100_000
    const val REWARD_STEP = 100
    const val MIN_WINNERS = 1
    const val MAX_WINNERS = 500
    const val MAX_TOTAL_BUDGET = 10_000_000

    val allowedDurationHours = setOf(1, 6, 12, 24, 72, 168)

    fun isRewardValid(value: Int): Boolean =
        value in MIN_REWARD_PER_USER..MAX_REWARD_PER_USER && value % REWARD_STEP == 0

    fun isWinnerCountValid(value: Int): Boolean = value in MIN_WINNERS..MAX_WINNERS

    fun totalBudget(rewardPerUser: Int, winnerCount: Int): Long {
        require(isRewardValid(rewardPerUser)) { "Reward must be a multiple of 100 BLINK Coins." }
        require(isWinnerCountValid(winnerCount)) { "Invalid number of recipients." }
        val total = rewardPerUser.toLong() * winnerCount.toLong()
        require(total <= MAX_TOTAL_BUDGET) { "Drop budget is above the supported limit." }
        return total
    }

    fun actionsFor(targetType: BlinkDropTargetType): Set<BlinkDropAction> =
        when (targetType) {
            BlinkDropTargetType.POST,
            BlinkDropTargetType.REEL -> setOf(
                BlinkDropAction.LIKE,
                BlinkDropAction.COMMENT,
                BlinkDropAction.REPOST,
            )
            BlinkDropTargetType.LISTING -> setOf(BlinkDropAction.SAVE_LISTING)
        }
}
