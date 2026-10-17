package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarketTrustPolicyTest {
    @Test fun oneSellerCannotFillAllTopPositions() {
        val rows = listOf("a1","a2","a3","b1","b2","c1")
        val ranked = MarketTrustPolicy.diversify(rows) { it.take(1) }
        assertEquals(listOf("a1","b1","c1","a2","b2","a3"), ranked)
    }

    @Test fun orderWithinSellerIsPreserved() {
        val rows = listOf("A1","A2","B1","A3","B2")
        val ranked = MarketTrustPolicy.diversify(rows) { it.take(1) }
        assertEquals(listOf("A1","A2","A3"), ranked.filter { it.startsWith("A") })
    }

    @Test fun onlyStrongSaleIntentRoutesToMarket() {
        assertTrue(MarketIntentPolicy.likelyProductAdvertisement("Phone for sale ₦250,000, DM to buy"))
        assertFalse(MarketIntentPolicy.likelyProductAdvertisement("I bought my laptop for ₦250,000 today!"))
        assertFalse(MarketIntentPolicy.likelyProductAdvertisement("For sale: old phone. This is a poem."))
    }

    @Test fun pricePreviewsAreConsistent() {
        assertEquals(2_000, MarketTrustPolicy.BOOST_PREVIEWS.first { it.targetQualifiedViews == 1000 }.priceNaira)
        assertTrue(MarketTrustPolicy.BOOST_PREVIEWS.all { it.priceNaira > 0 && it.maxDays > 0 })
    }
}
