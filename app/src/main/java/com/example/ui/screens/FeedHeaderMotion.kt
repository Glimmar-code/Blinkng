package com.example.ui.screens

import kotlin.math.abs

private const val FEED_HEADER_FLING_INTENT_THRESHOLD_PX_PER_SECOND = 1_200f

/**
 * Pure motion and viewport math for the Android feed.
 *
 * Keeping this outside Compose makes header settling, autoplay eligibility, prefetch
 * depth and keyed resume behavior testable without touching feed ranking or networking.
 */
internal fun clampFeedHeaderOffset(
    currentOffsetPx: Float,
    deltaYPx: Float,
    headerHeightPx: Float
): Float {
    if (headerHeightPx <= 0f) return 0f
    return (currentOffsetPx + deltaYPx).coerceIn(-headerHeightPx, 0f)
}

internal fun feedHeaderSettleTarget(
    currentOffsetPx: Float,
    headerHeightPx: Float,
    direction: Int,
    atFeedTop: Boolean,
    velocityYPxPerSecond: Float = 0f
): Float {
    if (headerHeightPx <= 0f || atFeedTop) return 0f

    val clamped = currentOffsetPx.coerceIn(-headerHeightPx, 0f)
    return when {
        abs(velocityYPxPerSecond) >= FEED_HEADER_FLING_INTENT_THRESHOLD_PX_PER_SECOND ->
            if (velocityYPxPerSecond > 0f) 0f else -headerHeightPx
        direction > 0 -> 0f
        direction < 0 -> -headerHeightPx
        clamped <= -(headerHeightPx * 0.5f) -> -headerHeightPx
        else -> 0f
    }
}

internal fun feedVisibleFraction(
    itemOffset: Int,
    itemSize: Int,
    viewportStart: Int,
    viewportEnd: Int
): Float {
    if (itemSize <= 0 || viewportEnd <= viewportStart) return 0f
    val visibleStart = maxOf(itemOffset, viewportStart)
    val visibleEnd = minOf(itemOffset + itemSize, viewportEnd)
    val visiblePx = (visibleEnd - visibleStart).coerceAtLeast(0)
    return (visiblePx.toFloat() / itemSize.toFloat()).coerceIn(0f, 1f)
}

internal fun feedAutoplayEligible(
    itemOffset: Int,
    itemSize: Int,
    viewportStart: Int,
    viewportEnd: Int,
    minimumVisibleFraction: Float = 0.70f
): Boolean =
    feedVisibleFraction(itemOffset, itemSize, viewportStart, viewportEnd) >=
        minimumVisibleFraction.coerceIn(0f, 1f)

internal fun feedPrefetchLookahead(
    itemDelta: Int,
    elapsedMs: Long
): Int {
    if (elapsedMs <= 0L) return 3
    val itemsPerSecond = abs(itemDelta).toFloat() * 1_000f / elapsedMs.toFloat()
    return when {
        itemsPerSecond >= 8f -> 7
        itemsPerSecond >= 4f -> 5
        else -> 3
    }
}

internal fun resolveFeedRestoreIndex(
    savedKey: String?,
    rowKeys: List<String>,
    savedIndex: Int
): Int {
    if (rowKeys.isEmpty()) return 0
    val keyIndex = savedKey
        ?.takeIf { it.isNotBlank() }
        ?.let(rowKeys::indexOf)
        ?.takeIf { it >= 0 }
    return (keyIndex ?: savedIndex).coerceIn(0, rowKeys.lastIndex)
}
