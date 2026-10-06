package com.blinkng.shared

import com.example.data.models.BlinkStoreCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkStoreProductGroupsTest {
    @Test
    fun storeIsPresentedAsTwentyFiveMergedCollections() {
        assertEquals(25, BlinkStoreProductGroups.all.size)
        assertEquals(
            listOf("For You", "Profile", "Social", "Content", "Promote", "Collectibles", "App"),
            BlinkStoreProductGroups.categories,
        )
    }

    @Test
    fun everyExistingCatalogItemRemainsReachableThroughAGroup() {
        val catalogIds = BlinkStoreCatalog.items.map { it.id }.toSet()

        assertEquals(catalogIds, BlinkStoreProductGroups.representedCatalogIds)
        BlinkStoreProductGroups.all.forEach { group ->
            assertTrue(group.primaryItemId in group.itemIds)
            assertTrue(group.itemIds.all { it in catalogIds })
        }
    }

    @Test
    fun onlyVipVariantsAreIntentionallyCrossListed() {
        val repeated = BlinkStoreProductGroups.all
            .flatMap { group -> group.itemIds.map { id -> id to group.id } }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size > 1 }
            .keys

        assertEquals(
            setOf("vip_comment_effect", "vip_reaction_pack", "vip_profile_entrance"),
            repeated,
        )
    }

    @Test
    fun promotionGroupsReuseExistingServerAuthoritativeIds() {
        assertEquals(
            listOf("post_boost", "post_boost_plus", "post_spotlight_6h", "post_spotlight_24h"),
            BlinkStoreProductGroups.byId("promote_post")?.itemIds,
        )
        assertEquals(
            listOf("reel_boost", "reel_boost_plus", "reel_spotlight_6h", "reel_spotlight_24h"),
            BlinkStoreProductGroups.byId("promote_reel")?.itemIds,
        )
        assertEquals(
            listOf("profile_spotlight_1h", "profile_spotlight_24h", "profile_discovery_boost", "discovery_boost_7d"),
            BlinkStoreProductGroups.byId("promote_profile")?.itemIds,
        )
    }
}
