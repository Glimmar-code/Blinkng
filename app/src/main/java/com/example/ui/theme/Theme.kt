package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

// Keep a standard readable type scale while respecting the device accessibility font scale.
private const val APP_FONT_SCALE = 1.0f

fun feedAccentBrush(): Brush = Brush.linearGradient(
    colors = listOf(FeedGradientStart, FeedGradientMiddle, FeedGradientEnd)
)

fun blinkBackgroundBrush(isDark: Boolean): Brush = if (isDark) {
    Brush.radialGradient(
        colors = listOf(
            DarkSurfaceElevated.copy(alpha = 0.74f),
            DarkBackground,
            DarkBackground
        ),
        radius = 1200f
    )
} else {
    Brush.radialGradient(
        colors = listOf(
            Color(0xFFF0EBFF),
            Color(0xFFF7F6FB),
            LightBackground
        ),
        radius = 1200f
    )
}

/**
 * Root Blink Material 3 theme.
 *
 * Existing screens that already use MaterialTheme inherit the premium palette immediately.
 * New/migrated components can additionally consume BlinkThemeTokens for elevated surfaces,
 * input surfaces, muted text, semantic status colors and the shared spacing/motion scale.
 */
@Composable
fun BlinkTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val systemDensity = LocalDensity.current
    val appDensity = Density(
        density = systemDensity.density,
        fontScale = systemDensity.fontScale * APP_FONT_SCALE
    )
    val semanticColors = if (darkTheme) BlinkDarkSemanticColors else BlinkLightSemanticColors

    CompositionLocalProvider(
        LocalDensity provides appDensity,
        LocalBlinkSemanticColors provides semanticColors
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) BlinkDarkColorScheme else BlinkLightColorScheme,
            typography = BlinkTypography,
            shapes = BlinkShapes,
            content = content
        )
    }
}
