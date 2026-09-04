package com.example.ui.screens

/**
 * Tracks transitions into qualified visibility. Remaining on the same post does not create
 * another impression; leaving it and scrolling back does.
 */
internal class PostImpressionTracker {
    private var qualifiedPostIds: Set<String> = emptySet()

    fun update(nextQualifiedPostIds: Set<String>): Set<String> {
        val newlyVisible = nextQualifiedPostIds - qualifiedPostIds
        qualifiedPostIds = nextQualifiedPostIds
        return newlyVisible
    }
}

internal fun qualifiesForPostImpression(
    itemOffset: Int,
    itemSize: Int,
    viewportStart: Int,
    viewportEnd: Int
): Boolean {
    val viewportSize = viewportEnd - viewportStart
    if (itemSize <= 0 || viewportSize <= 0) return false

    val visibleStart = maxOf(itemOffset, viewportStart)
    val visibleEnd = minOf(itemOffset + itemSize, viewportEnd)
    val visibleSize = (visibleEnd - visibleStart).coerceAtLeast(0)
    val referenceSize = minOf(itemSize, viewportSize)

    return visibleSize >= referenceSize * 0.5f
}
