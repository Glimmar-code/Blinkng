package com.blinkng.shared

data class BlinkRewardMilestone(
    val ads: Int,
    val totalCoins: Int,
) {
    val bonusCoins: Int
        get() = (totalCoins - ads * BlinkEconomyDefaults.BASE_REWARDED_AD_COINS).coerceAtLeast(0)
}

data class BlinkCoinPack(
    val id: String,
    val priceNgn: Int,
    val coins: Int,
) {
    val bonusCoins: Int
        get() = (coins - priceNgn).coerceAtLeast(0)
}

data class BlinkEconomyPolicy(
    val rewardedAdBaseCoins: Int = BlinkEconomyDefaults.BASE_REWARDED_AD_COINS,
    val rewardedAdDailyLimit: Int = 15,
    val rewardedMilestones: List<BlinkRewardMilestone> = BlinkEconomyDefaults.MILESTONES,
    val blueVerificationCashNgn: Int = 800,
    val blueVerificationCoinCost: Int = 3_000,
    val blueVerificationValidDays: Int = 30,
    val coinPacks: List<BlinkCoinPack> = BlinkEconomyDefaults.COIN_PACKS,
    val cashCheckoutEnabled: Boolean = false,
) {
    fun totalRewardForAds(completedAds: Int): Int {
        val clamped = completedAds.coerceIn(0, rewardedAdDailyLimit)
        if (clamped == 0) return 0
        val milestone = rewardedMilestones
            .filter { it.ads <= clamped }
            .maxByOrNull { it.ads }
        val milestoneTotal = milestone?.totalCoins ?: 0
        val milestoneAds = milestone?.ads ?: 0
        return milestoneTotal + (clamped - milestoneAds) * rewardedAdBaseCoins
    }

    fun rewardForCompletedAd(adNumber: Int): Int {
        if (adNumber !in 1..rewardedAdDailyLimit) return 0
        return totalRewardForAds(adNumber) - totalRewardForAds(adNumber - 1)
    }

    fun verificationCoinsRemaining(balance: Long): Long =
        (blueVerificationCoinCost.toLong() - balance).coerceAtLeast(0L)

    fun verificationProgress(balance: Long): Float {
        if (blueVerificationCoinCost <= 0) return 1f
        return (balance.toFloat() / blueVerificationCoinCost.toFloat()).coerceIn(0f, 1f)
    }

    fun canWatchRewardedAd(completedAds: Int): Boolean =
        completedAds < rewardedAdDailyLimit
}

object BlinkEconomyDefaults {
    const val BASE_REWARDED_AD_COINS = 10

    val MILESTONES = listOf(
        BlinkRewardMilestone(ads = 5, totalCoins = 60),
        BlinkRewardMilestone(ads = 10, totalCoins = 130),
        BlinkRewardMilestone(ads = 15, totalCoins = 210),
    )

    val COIN_PACKS = listOf(
        BlinkCoinPack(id = "coins_100", priceNgn = 100, coins = 100),
        BlinkCoinPack(id = "coins_500", priceNgn = 500, coins = 550),
        BlinkCoinPack(id = "coins_1000", priceNgn = 1_000, coins = 1_200),
        BlinkCoinPack(id = "coins_2000", priceNgn = 2_000, coins = 2_600),
        BlinkCoinPack(id = "coins_5000", priceNgn = 5_000, coins = 7_000),
    )

    val policy = BlinkEconomyPolicy()
}
