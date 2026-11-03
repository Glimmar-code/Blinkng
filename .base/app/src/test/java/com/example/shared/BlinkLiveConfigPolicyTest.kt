package com.example.shared

import com.blinkng.shared.live.BlinkLiveConfigPolicy
import com.blinkng.shared.live.BlinkLiveSetting
import org.junit.Assert.*
import org.junit.Test

class BlinkLiveConfigPolicyTest {
    @Test fun defaultsSafeWhenBackendHasNoRows() {
        val result = BlinkLiveConfigPolicy.evaluate(emptyList())
        assertNull(result.visibleAnnouncement)
        assertNull(result.visiblePromotion)
    }

    @Test fun canPublishAndInstantlyWithdrawAnnouncement() {
        val rows = listOf(BlinkLiveSetting("announcement", mapOf(
            "title" to "Important", "message" to "BLINK maintenance update"
        )))
        assertEquals("Important", BlinkLiveConfigPolicy.evaluate(rows).visibleAnnouncement?.title)
        assertNull(BlinkLiveConfigPolicy.evaluate(emptyList()).visibleAnnouncement)
    }

    @Test fun displayAndFeatureSwitchesHidePromotionsWithoutAffectingAccountRules() {
        val rows = listOf(
            BlinkLiveSetting("promotion", mapOf("title" to "Hello", "message" to "World")),
            BlinkLiveSetting("feature_visibility", mapOf("home_promo_enabled" to "false"))
        )
        assertNull(BlinkLiveConfigPolicy.evaluate(rows).visiblePromotion)
        assertNotNull(BlinkLiveConfigPolicy.evaluate(rows).promotion)
    }

    @Test fun ignoresUnknownKeysAndSanitizesLongContent() {
        val rows = listOf(
            BlinkLiveSetting("payment_override", mapOf("free_coins" to "99999")),
            BlinkLiveSetting("announcement", mapOf(
                "title" to "A".repeat(120), "message" to "B".repeat(500)
            ))
        )
        val result = BlinkLiveConfigPolicy.evaluate(rows)
        assertEquals(80, result.visibleAnnouncement?.title?.length)
        assertEquals(300, result.visibleAnnouncement?.message?.length)
    }
}
