package com.example.ui.screens

/**
 * Pure motion math for the Android feed chrome.
 *
 * Keeping this outside Compose makes the collapse bounds and settle policy testable
 * without touching feed ranking, post state, networking, or exposure tracking.
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
    atFeedTop: Boolean
): Float {
    if (headerHeightPx <= 0f || atFeedTop) return 0f

    val clamped = currentOffsetPx.coerceIn(-headerHeightPx, 0f)
    return when {
        direction > 0 -> 0f
        direction < 0 -> -headerHeightPx
        clamped <= -(headerHeightPx * 0.5f) -> -headerHeightPx
        else -> 0f
    }
}
