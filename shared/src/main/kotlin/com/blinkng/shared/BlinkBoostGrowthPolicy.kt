package com.blinkng.shared

import kotlin.math.ceil

enum class BlinkBoostTargetType { POST, REEL, PROFILE, LISTING }
enum class BlinkBoostObjective { REACH, VIEWS, LIKES, COMMENTS, SAVES, ENGAGEMENT, PROFILE_VISITS, FOLLOWERS, BUYER_INTEREST }
enum class BlinkBoostAudienceScope { MY_UNIVERSITY, SELECTED_UNIVERSITY, ALL_CAMPUSES }
enum class BlinkBoostMissionAction { QUALIFIED_VIEW, LIKE, COMMENT, SAVE, FOLLOW, LISTING_OPEN }

data class BlinkBoostQuoteInput(
    val targetType: BlinkBoostTargetType,
    val boostPower: Int,
    val objective: BlinkBoostObjective,
    val audienceScope: BlinkBoostAudienceScope,
    val durationDays: Int,
)

object BlinkBoostGrowthDefaults {
    const val MIN_CAMPAIGN_COINS = 50
    const val MISSION_DAILY_RANK_POINT_CAP = 20

    fun baseDailyCoins(type: BlinkBoostTargetType): Int = when (type) {
        BlinkBoostTargetType.POST -> 900
        BlinkBoostTargetType.REEL -> 1_100
        BlinkBoostTargetType.PROFILE -> 800
        BlinkBoostTargetType.LISTING -> 1_000
    }

    fun objectiveMultiplier(objective: BlinkBoostObjective): Double = when (objective) {
        BlinkBoostObjective.REACH -> 1.00
        BlinkBoostObjective.VIEWS -> 1.00
        BlinkBoostObjective.LIKES -> 1.15
        BlinkBoostObjective.SAVES -> 1.20
        BlinkBoostObjective.ENGAGEMENT -> 1.25
        BlinkBoostObjective.COMMENTS -> 1.30
        BlinkBoostObjective.PROFILE_VISITS -> 1.15
        BlinkBoostObjective.FOLLOWERS -> 1.30
        BlinkBoostObjective.BUYER_INTEREST -> 1.40
    }

    fun audienceMultiplier(scope: BlinkBoostAudienceScope): Double = when (scope) {
        BlinkBoostAudienceScope.MY_UNIVERSITY -> 1.00
        BlinkBoostAudienceScope.SELECTED_UNIVERSITY -> 1.10
        BlinkBoostAudienceScope.ALL_CAMPUSES -> 1.60
    }

    fun durationMultiplier(days: Int): Double = when (days) {
        1 -> 1.00
        3 -> 0.95
        7 -> 0.90
        14 -> 0.85
        30 -> 0.80
        else -> 1.00
    }

    fun missionPoints(action: BlinkBoostMissionAction): Int = when (action) {
        BlinkBoostMissionAction.QUALIFIED_VIEW -> 1
        BlinkBoostMissionAction.LIKE -> 1
        BlinkBoostMissionAction.COMMENT -> 2
        BlinkBoostMissionAction.SAVE -> 2
        BlinkBoostMissionAction.FOLLOW -> 3
        BlinkBoostMissionAction.LISTING_OPEN -> 1
    }

    fun quoteCoins(input: BlinkBoostQuoteInput): Int {
        val power = input.boostPower.coerceIn(1, 100)
        val days = input.durationDays.takeIf { it in setOf(1, 3, 7, 14, 30) } ?: 1
        val raw = baseDailyCoins(input.targetType) *
            (power / 100.0) *
            objectiveMultiplier(input.objective) *
            audienceMultiplier(input.audienceScope) *
            days *
            durationMultiplier(days)
        return ceil(raw).toInt().coerceAtLeast(MIN_CAMPAIGN_COINS)
    }

    fun missionAward(
        action: BlinkBoostMissionAction,
        pointsEarnedToday: Int,
        alreadyRewardedForTarget: Boolean,
    ): Int {
        if (alreadyRewardedForTarget) return 0
        val remaining = (MISSION_DAILY_RANK_POINT_CAP - pointsEarnedToday).coerceAtLeast(0)
        return missionPoints(action).coerceAtMost(remaining)
    }
}
