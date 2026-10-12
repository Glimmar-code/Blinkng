package com.example.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Observe completed horizontal gestures without consuming child touch events.
 * Image carousels, vertical lists, buttons and text selection keep priority.
 *
 * -1 = left (next page), +1 = right (previous page), 0 = no navigation.
 */
internal fun horizontalPageSwipeDirection(
    deltaX: Float,
    deltaY: Float,
    thresholdPx: Float,
    handledByChild: Boolean,
): Int {
    if (handledByChild || !deltaX.isFinite() || !deltaY.isFinite() || thresholdPx <= 0f) return 0
    if (abs(deltaX) < thresholdPx || abs(deltaX) < abs(deltaY) * 1.35f) return 0
    return if (deltaX < 0f) -1 else 1
}

fun Modifier.blinkHorizontalPageSwipe(
    enabled: Boolean,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier = composed {
    val thresholdPx = with(LocalDensity.current) { 88.dp.toPx() }
    val latestLeft by rememberUpdatedState(onSwipeLeft)
    val latestRight by rememberUpdatedState(onSwipeRight)
    this.pointerInput(enabled, thresholdPx) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var finalX = down.position.x
            var finalY = down.position.y
            var handledByChild = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                // Never turn pinch-to-zoom / multi-touch into page navigation.
                if (event.changes.size > 1) handledByChild = true
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                finalX = change.position.x
                finalY = change.position.y
                if (change.pressed && change.isConsumed) handledByChild = true
                if (!change.pressed) {
                    when (horizontalPageSwipeDirection(
                        deltaX = finalX - down.position.x,
                        deltaY = finalY - down.position.y,
                        thresholdPx = thresholdPx,
                        handledByChild = handledByChild,
                    )) {
                        -1 -> latestLeft()
                         1 -> latestRight()
                    }
                    break
                }
            }
        }
    }
}
