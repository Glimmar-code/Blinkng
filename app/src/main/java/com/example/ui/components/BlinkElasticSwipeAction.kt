package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blinkng.shared.BlinkElasticGesturePhysics
import com.example.performance.rememberBlinkReduceMotion
import kotlin.math.abs

enum class BlinkElasticSwipeDirection { LEFT, RIGHT, UP, DOWN }

/**
 * Passive screen-level navigation. Child scroll, zoom, text editing and carousel
 * gestures always win. It never consumes pointers, and never runs a destructive
 * operation directly: pass only a reversible navigation callback.
 */
fun Modifier.blinkElasticSwipeAction(
    direction: BlinkElasticSwipeDirection,
    onAction: () -> Unit,
    enabled: Boolean = true,
    activation: Dp = 98.dp,
): Modifier = composed {
    val density = LocalDensity.current
    val activationPx = with(density) { activation.toPx() }
    val minFlickPx = with(density) { BlinkElasticGesturePhysics.MIN_FLICK_DP.dp.toPx() }
    val fastVelocityPx = with(density) {
        BlinkElasticGesturePhysics.FAST_FLING_DP_PER_SECOND.dp.toPx()
    }
    val reducedMotion = rememberBlinkReduceMotion()
    val latestAction by rememberUpdatedState(onAction)
    var distance by remember(direction) { mutableFloatStateOf(0f) }
    var dragging by remember(direction) { mutableStateOf(false) }
    val travel by animateFloatAsState(
        targetValue = if (reducedMotion) 0f
            else BlinkElasticGesturePhysics.visualDistance(distance, activationPx),
        animationSpec = if (dragging || reducedMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "blinkElasticNavigation"
    )
    this.graphicsLayer {
        if (direction == BlinkElasticSwipeDirection.LEFT ||
            direction == BlinkElasticSwipeDirection.RIGHT) translationX = travel
        else translationY = travel
    }.pointerInput(enabled, direction, activationPx) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var childOwned = false
            var committed = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                if (event.changes.size != 1) childOwned = true
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                tracker.addPosition(change.uptimeMillis, change.position)
                if (change.pressed && change.isConsumed) childOwned = true
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                val raw = directionalSwipeDistance(dx, dy, direction)
                if (change.pressed) {
                    dragging = !childOwned && raw != 0f
                    distance = if (dragging) raw else 0f
                } else {
                    val v = tracker.calculateVelocity()
                    val projectedV = when (direction) {
                        BlinkElasticSwipeDirection.LEFT, BlinkElasticSwipeDirection.RIGHT -> v.x
                        BlinkElasticSwipeDirection.UP, BlinkElasticSwipeDirection.DOWN -> v.y
                    }
                    committed = !childOwned && raw != 0f &&
                        BlinkElasticGesturePhysics.shouldCommit(
                            raw, projectedV, activationPx, minFlickPx, fastVelocityPx
                        )
                    break
                }
            }
            dragging = false
            distance = 0f
            if (committed) latestAction()
        }
    }
}

/** Ratio-based direction lock: horizontal carousels never become vertical back actions. */
internal fun directionalSwipeDistance(
    dx: Float, dy: Float, direction: BlinkElasticSwipeDirection
): Float {
    if (!dx.isFinite() || !dy.isFinite()) return 0f
    return when (direction) {
        BlinkElasticSwipeDirection.LEFT ->
            if (dx < 0f && abs(dx) > abs(dy) * 1.35f) dx else 0f
        BlinkElasticSwipeDirection.RIGHT ->
            if (dx > 0f && dx > abs(dy) * 1.35f) dx else 0f
        BlinkElasticSwipeDirection.UP ->
            if (dy < 0f && abs(dy) > abs(dx) * 1.35f) dy else 0f
        BlinkElasticSwipeDirection.DOWN ->
            if (dy > 0f && dy > abs(dx) * 1.35f) dy else 0f
    }
}
