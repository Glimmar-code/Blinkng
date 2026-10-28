package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlinkMerchantInsightsPolicyTest {
    private fun item(
        id: String,
        title: String = id,
        category: String = "Books",
        isSold: Boolean = false,
        views: Long? = null,
        saves: Long? = null,
        quantity: Int? = null,
        hasPhoto: Boolean = true
    ) = BlinkMerchantListing(id, title, category, isSold, views, saves, quantity, hasPhoto)

    @Test fun ownedSnapshotDeduplicatesAndKeepsSoldItemsOutOfStockAlerts() {
        val result = BlinkMerchantInsightsPolicy.summarize(listOf(
            item("a", views = 14L, quantity = 1, hasPhoto = false),
            item("a", views = 14L, quantity = 1, hasPhoto = false),
            item("b", views = 6L, quantity = 3),
            item("c", isSold = true, views = 90L, quantity = 0),
            item("", views = 99L, quantity = 1),
        ))
        assertEquals(3, result.listingCount)
        assertEquals(2, result.activeCount)
        assertEquals(1, result.soldCount)
        assertEquals(listOf("a"), result.lowStock.map { it.id })
        assertEquals(listOf("a"), result.noPhoto.map { it.id })
        assertEquals("c", result.topViewed.first().id)
        assertEquals(2, result.categories.first().activeCount)
    }

    @Test fun unavailableStockAndEngagementRemainUnknown() {
        val result = BlinkMerchantInsightsPolicy.summarize(listOf(
            item("a", quantity = null, views = null, saves = null),
            item("b", quantity = 0, views = -50L, saves = -1L)
        ))
        assertEquals(1, result.knownQuantityCount)
        assertEquals(1, result.knownViewCount)
        assertEquals(1, result.knownSaveCount)
        assertEquals(listOf("b"), result.lowStock.map { it.id })
        assertEquals(listOf("b"), result.topViewed.map { it.id })
        assertEquals(0L, result.categories.first().reportedViews)
    }

    @Test fun emptySellerSnapshotIsNotReportedAsLifetimeZero() {
        val result = BlinkMerchantInsightsPolicy.summarize(emptyList())
        assertEquals(0, result.listingCount)
        assertTrue(result.noPhoto.isEmpty())
        assertTrue(result.categories.isEmpty())
    }
}
