package com.blinkng.shared

/** Market query rules shared by Android and Windows; the server remains authoritative. */
object MarketQueryPolicy {
    fun validPriceRange(minimum: Long?, maximum: Long?): Boolean =
        (minimum == null || minimum >= 0) &&
            (maximum == null || maximum >= 0) &&
            (minimum == null || maximum == null || minimum <= maximum)

    fun category(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() && !it.equals("All Categories", ignoreCase = true) }

    /** ID is a mandatory stable tiebreaker for offset pagination. */
    fun order(sort: String): String = when (sort.lowercase()) {
        "price_low" -> "price.asc,created_at.desc,id.desc"
        "price_high" -> "price.desc,created_at.desc,id.desc"
        "popular" -> "views_count.desc,created_at.desc,id.desc"
        else -> "created_at.desc,id.desc"
    }

    /** Count fetched server rows, not rows remaining after local presentation filters. */
    fun nextOffset(previousOffset: Int, fetchedCount: Int): Int =
        (previousOffset.toLong().coerceAtLeast(0L) + fetchedCount.toLong().coerceAtLeast(0L))
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
