package com.example.ui.screens

import kotlin.math.abs

/**
 * Reels' Telegram-style preview-to-fullscreen gesture policy.
 *
 * The video player stays mounted; only the surrounding frame/chrome changes.
 * Drag updates are applied synchronously to Compose state and not launched as
 * one coroutine per pointer event, which previously caused visible lag.
 */
internal object ReelExpansionMotion {
    fun progressAfterDrag(progress: Float, dragDeltaPx: Float, viewportHeightPx: Float): Float {
        if (!progress.isFinite()) return 0f
        if (!dragDeltaPx.isFinite() ||
            !viewportHeightPx.isFinite() || viewportHeightPx <= 0f) return progress.coerceIn(0f, 1f)
        return (progress - dragDeltaPx / viewportHeightPx).coerceIn(0f, 1f)
    }

    fun shouldCompleteFastSwipe(
        dragDistancePx: Float,
        releaseVelocityPxPerSecond: Float,
        minimumDistancePx: Float,
        velocityThresholdPxPerSecond: Float
    ): Boolean =
        dragDistancePx.isFinite() && releaseVelocityPxPerSecond.isFinite() &&
            minimumDistancePx.isFinite() && velocityThresholdPxPerSecond.isFinite() &&
            minimumDistancePx > 0f && velocityThresholdPxPerSecond > 0f &&
            abs(dragDistancePx) >= minimumDistancePx &&
            abs(releaseVelocityPxPerSecond) >= velocityThresholdPxPerSecond &&
            dragDistancePx * releaseVelocityPxPerSecond > 0f
}
