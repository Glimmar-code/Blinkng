package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.example.performance.rememberBlinkReduceMotion

/**
 * Shared, low-cost press feedback for BLINK's reusable controls.
 *
 * The caller MUST pass the same interaction source to its clickable/Button
 * implementation. This modifier is visual only: it does not intercept touch
 * input, modify hit targets, or delay click callbacks.
 */
@Composable
fun Modifier.blinkPressFeedback(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.985f
): Modifier {
    val reduceMotion = rememberBlinkReduceMotion()
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = premiumPressTarget(pressed, enabled, reduceMotion, pressedScale),
        animationSpec = if (reduceMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "blinkPressFeedback"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Pure policy so accessibility, invalid values and disabled controls are testable. */
internal fun premiumPressTarget(
    pressed: Boolean,
    enabled: Boolean,
    reduceMotion: Boolean,
    pressedScale: Float
): Float = if (pressed && enabled && !reduceMotion) {
    if (pressedScale.isFinite()) pressedScale.coerceIn(0.94f, 1f) else 1f
} else {
    1f
}
