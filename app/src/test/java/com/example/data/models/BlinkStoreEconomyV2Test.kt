package com.example.data.models

import com.blinkng.shared.BlinkPremiumCosmetics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkStoreEconomyV2Test {
    private val shortDistributionIds = setOf(
        "post_boost",
        "reel_boost",
        "post_boost_plus",
        "reel_boost_plus",
        "profile_discovery_boost",
    )

    @Test
    fun timedCosmeticsUseSevenOrThirtyDayWindows() {
        BlinkStoreCatalog.items
            .filter { it.durationSeconds != null && it.id !in shortDistributionIds }
            .forEach { item ->
                assertTrue(
                    "${item.id} should last at least seven days",
                    item.durationSeconds!! >= 7L * 24L * 60L * 60L,
                )
            }

        assertEquals(30L * 24L * 60L * 60L, BlinkStoreCatalog.items.first { it.id == "profile_theme_3d" }.durationSeconds)
        assertEquals(30L * 24L * 60L * 60L, BlinkStoreCatalog.items.first { it.id == "blink_vip_10d" }.durationSeconds)
    }

    @Test
    fun permanentCosmeticsAreNotImpulsePriced() {
        BlinkStoreCatalog.items
            .filter { it.type == BlinkStoreItemType.PERMANENT }
            .forEach { item ->
                assertTrue("${item.id} should cost at least 200 coins", item.price >= 200)
            }
    }

    @Test
    fun everyStoreItemHasARealPreviewSpec() {
        BlinkStoreCatalog.items.forEach { item ->
            val preview = BlinkPremiumCosmetics.spec(item.id)
            assertEquals(item.id, preview.catalogId)
            assertTrue(preview.displayName.isNotBlank())
            assertTrue(preview.signatureLabel.isNotBlank())
        }
    }

    @Test
    fun flagshipEconomyMatchesStoreV2() {
        val verifiedVip = BlinkStoreCatalog.items.first { it.id == "blink_vip_10d" }
        val frame = BlinkStoreCatalog.items.first { it.id == "premium_profile_frame" }
        val creatorBadge = BlinkStoreCatalog.items.first { it.id == "creator_badge" }

        assertEquals("Blink VIP — 30 Days", verifiedVip.name)
        assertEquals(1_200, verifiedVip.price)
        assertEquals(1_000, frame.price)
        assertEquals(1_200, creatorBadge.price)
    }
}
