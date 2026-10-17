package com.blinkng.shared

/** Shared Android/Windows Market discovery and advertising rules. Backend RLS is authoritative. */
object MarketTrustPolicy {
    const val MAX_NEW_LISTINGS_PER_24H = 3

    /**
     * Round-robin seller diversity without changing the relative order of each
     * seller's results. Call after server filtering so a broad search isn't overridden.
     */
    fun <T> diversify(items: List<T>, sellerKey: (T) -> String): List<T> {
        if (items.size < 2) return items
        val buckets = linkedMapOf<String, ArrayDeque<T>>()
        items.forEachIndexed { index, item ->
            val key = sellerKey(item).trim().ifBlank { "unknown-$index" }
            buckets.getOrPut(key) { ArrayDeque() }.addLast(item)
        }
        val result = ArrayList<T>(items.size)
        while (result.size < items.size) {
            buckets.values.forEach { queue ->
                if (queue.isNotEmpty()) result.add(queue.removeFirst())
            }
        }
        return result
    }

    /** Price previews, not guaranteed views and not an activated cash checkout. */
    data class BoostPreview(val priceNaira: Int, val targetQualifiedViews: Int, val maxDays: Int)
    val BOOST_PREVIEWS = listOf(
        BoostPreview(500, 250, 3),
        BoostPreview(1_000, 500, 5),
        BoostPreview(2_000, 1_000, 7),
        BoostPreview(5_000, 2_500, 10)
    )
}

/**
 * High-precision routing hint. Do not block legitimate non-commercial discussion
 * based on a single currency sign or product name. Appeal is required in the UI.
 */
object MarketIntentPolicy {
    private val salePhrases = Regex(
        """(?i)\\b(for sale|selling this|available for purchase|order now|dm to buy|buy now|price is)\\b"""
    )
    private val priceSignal = Regex("""(?i)(?:₦|ngn\\s*)\\s*[0-9][0-9,]*""")

    fun likelyProductAdvertisement(body: String): Boolean {
        val text = body.trim().take(4000)
        return salePhrases.containsMatchIn(text) && priceSignal.containsMatchIn(text)
    }
}
