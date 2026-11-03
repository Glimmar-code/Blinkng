package com.blinkng.shared

import kotlin.math.abs

/** Platform-neutral media navigation, shared by Android's touch viewer and desktop input. */
enum class BlinkChatMediaSwipe { NONE, PREVIOUS, NEXT, DISMISS }

/**
 * A zoomed image pans instead of changing messages. A swipe must clearly dominate
 * the other axis; ambiguous diagonals do not dismiss private media accidentally.
 * Direction is expressed in visual coordinates: negative X means next photo.
 */
object BlinkChatMediaGesturePolicy {
    private const val AXIS_DOMINANCE = 1.25f
    const val SWIPE_THRESHOLD_DP = 84f
    const val ZOOM_LOCK_THRESHOLD = 1.05f

    fun action(
        dragX: Float,
        dragY: Float,
        thresholdPx: Float,
        zoom: Float,
        hasPrevious: Boolean,
        hasNext: Boolean,
    ): BlinkChatMediaSwipe {
        if (!dragX.isFinite() || !dragY.isFinite() || !zoom.isFinite() ||
            !thresholdPx.isFinite() || thresholdPx <= 0f ||
            zoom > ZOOM_LOCK_THRESHOLD) return BlinkChatMediaSwipe.NONE
        val x = abs(dragX)
        val y = abs(dragY)
        return when {
            y >= thresholdPx && y > x * AXIS_DOMINANCE -> BlinkChatMediaSwipe.DISMISS
            x >= thresholdPx && x > y * AXIS_DOMINANCE && dragX < 0f && hasNext ->
                BlinkChatMediaSwipe.NEXT
            x >= thresholdPx && x > y * AXIS_DOMINANCE && dragX > 0f && hasPrevious ->
                BlinkChatMediaSwipe.PREVIOUS
            else -> BlinkChatMediaSwipe.NONE
        }
    }

    /** During a vertical swipe, reveal the underlying conversation gradually. */
    fun backdropAlpha(dragY: Float, viewportHeightPx: Float): Float =
        if (!dragY.isFinite() || !viewportHeightPx.isFinite() ||
            viewportHeightPx <= 0f) 1f
        else (1f - abs(dragY) / (viewportHeightPx * .75f)).coerceIn(.25f, 1f)
}
