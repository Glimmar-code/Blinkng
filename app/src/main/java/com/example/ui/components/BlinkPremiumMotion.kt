package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Reusable layout easing for expand/collapse, never for virtualized media cells
 * or the IME-attached full-screen host. Respects BLINK's motion preferences.
 */
@Composable
fun Modifier.blinkSmoothSize(enabled: Boolean = true): Modifier {
    val reduceMotion = blinkReducedMotionForControls()
    return if (!enabled || reduceMotion) this
        else this.animateContentSize(animationSpec = tween(durationMillis = 220))
}

/** Small state swaps without zooming or remounting unrelated screen content. */
@Composable
fun <T> BlinkFadeThrough(
    targetState: T,
    modifier: Modifier = Modifier,
    label: String = "blinkStateTransition",
    content: @Composable AnimatedContentScope.(T) -> Unit
) {
    val reduceMotion = blinkReducedMotionForControls()
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            if (reduceMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                fadeIn(animationSpec = tween(180)) togetherWith
                    fadeOut(animationSpec = tween(120))
            }
        },
        label = label,
        content = content
    )
}
