package com.blinkng.shared

/**
 * Reusable breakpoint policy for BLINK's phone surfaces.
 * Use available layout width (dp), not physical display pixels or device models.
 * Font scaling is an independent compact-layout trigger.
 *
 * Desktop/Windows can use wider, resizable pane arrangements rather than
 * stretching phone layouts. This function contains no Android dependencies.
 */
object BlinkAdaptiveLayoutPolicy {
    fun useCompactPhoneLayout(availableWidthDp: Float, fontScale: Float): Boolean =
        availableWidthDp < 380f || fontScale >= 1.3f
}
