package com.example.ui.screens

/**
 * User-facing Search summaries. Avoid claiming pagination where none exists
 * and keep implementation terminology out of the interface.
 */
internal fun searchResultsSummary(count: Int, category: String?, hasMore: Boolean): String {
    val resultCount = count.coerceAtLeast(0)
    val categoryName = category?.trim()?.takeIf { it.isNotEmpty() } ?: "all categories"
    val noun = if (resultCount == 1) "result" else "results"
    return "$resultCount $noun · $categoryName" +
        if (hasMore) " · scroll for more" else ""
}
