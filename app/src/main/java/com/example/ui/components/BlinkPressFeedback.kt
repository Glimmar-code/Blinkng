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
import androidx.compose.ui.platform.LocalContext
import android.provider.Settings
import com.blinkng.shared.BlinkPerformancePolicy
import com.example.performance.HighRefreshRateController

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
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = blinkReducedMotionForControls()
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

/**
 * An interaction-ready snapshot avoids registering a ContentObserver for every
 * feed card. Press state recomposition re-reads Android's current setting.
 * Screen-level navigation continues to use the observable motion preference.
 */
@Composable
fun blinkReducedMotionForControls(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val animationsDisabled = runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) <= 0f
    }.getOrDefault(false)
    return BlinkPerformancePolicy.reduceDecorativeMotion(
        HighRefreshRateController.currentModeState.value,
        animationsDisabled
    )
}
