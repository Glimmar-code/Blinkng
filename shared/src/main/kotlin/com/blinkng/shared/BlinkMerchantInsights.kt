package com.blinkng.shared

/**
 * Read-only Merchant Pro insights. Values are from explicitly loaded listings:
 * never call them lifetime shop metrics, forecasted sales or total inventory.
 */
data class BlinkMerchantListing(
    val id: String,
    val title: String,
    val category: String,
    val isSold: Boolean,
    val views: Long?,
    val saves: Long?,
    val quantity: Int?,
    val hasPhoto: Boolean
)

data class BlinkMerchantCategory(
    val name: String,
    val activeCount: Int,
    val reportedViews: Long,
    val countedViewItems: Int
)

data class BlinkMerchantInsightsSummary(
    val listingCount: Int,
    val activeCount: Int,
    val soldCount: Int,
    val lowStock: List<BlinkMerchantListing>,
    val noPhoto: List<BlinkMerchantListing>,
    val topViewed: List<BlinkMerchantListing>,
    val categories: List<BlinkMerchantCategory>,
    val knownViewCount: Int,
    val knownSaveCount: Int,
    val knownQuantityCount: Int
)

/** All computations intentionally have no user identity or payment side effects. */
object BlinkMerchantInsightsPolicy {
    fun summarize(listings: List<BlinkMerchantListing>): BlinkMerchantInsightsSummary {
        val clean = listings.asSequence().filter { it.id.isNotBlank() }
            .distinctBy { it.id }.toList()
        val active = clean.filter { !it.isSold }
        val cats = active.groupBy { it.category.trim().ifBlank { "Other" } }
            .map { (name, rows) -> BlinkMerchantCategory(
                name = name,
                activeCount = rows.size,
                reportedViews = rows.sumOf { it.views?.coerceAtLeast(0L) ?: 0L },
                countedViewItems = rows.count { it.views != null }
            ) }.sortedWith(compareByDescending<BlinkMerchantCategory> { it.activeCount }
                .thenBy { it.name })
        return BlinkMerchantInsightsSummary(
            listingCount = clean.size,
            activeCount = active.size,
            soldCount = clean.size - active.size,
            lowStock = active.filter { it.quantity != null && it.quantity <= 2 }
                .sortedWith(compareBy<BlinkMerchantListing> { it.quantity ?: Int.MAX_VALUE }.thenBy { it.id })
                .take(6),
            noPhoto = active.filter { !it.hasPhoto }.sortedBy { it.id }.take(6),
            topViewed = clean.filter { it.views != null }
                .sortedWith(compareByDescending<BlinkMerchantListing> { it.views ?: 0L }
                    .thenBy { it.id }).take(5),
            categories = cats.take(8),
            knownViewCount = clean.count { it.views != null },
            knownSaveCount = clean.count { it.saves != null },
            knownQuantityCount = clean.count { it.quantity != null }
        )
    }
}
