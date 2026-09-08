package com.example.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.blinkng.shared.BlinkDesignTokens

/** Reusable motion primitives. Keep motion purposeful and consistent rather than animating everything. */
object BlinkMotion {
    const val fastMillis = BlinkDesignTokens.Motion.Fast
    const val standardMillis = BlinkDesignTokens.Motion.Standard
    const val emphasizedMillis = BlinkDesignTokens.Motion.Emphasized
    const val slowMillis = BlinkDesignTokens.Motion.Slow
    const val pressedScale = BlinkDesignTokens.Motion.PressedScale

    fun <T> fastTween() = tween<T>(durationMillis = fastMillis)
    fun <T> standardTween() = tween<T>(durationMillis = standardMillis)
    fun <T> emphasizedTween() = tween<T>(durationMillis = emphasizedMillis)

    fun <T> responsiveSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
}
