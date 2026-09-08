package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class BlinkSemanticColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceHighest: Color,
    val input: Color,
    val border: Color,
    val borderSoft: Color,
    val primary: Color,
    val primaryBright: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val success: Color,
    val warning: Color,
    val error: Color
)

internal val BlinkDarkSemanticColors = BlinkSemanticColors(
    background = DarkBackground,
    surface = DarkSurface,
    surfaceElevated = DarkSurfaceElevated,
    surfaceHighest = DarkSurfaceHighest,
    input = DarkInput,
    border = DarkBorder,
    borderSoft = DarkBorderSoft,
    primary = FeedPurple,
    primaryBright = FeedPurpleBright,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textMuted = DarkTextMuted,
    success = BlinkSuccess,
    warning = BlinkWarning,
    error = BlinkRed
)

internal val BlinkLightSemanticColors = BlinkSemanticColors(
    background = LightBackground,
    surface = LightSurface,
    surfaceElevated = LightSurfaceElevated,
    surfaceHighest = LightSurfaceCream,
    input = LightInput,
    border = LightBorder,
    borderSoft = LightBorderSoft,
    primary = FeedDeepPurple,
    primaryBright = FeedPurple,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textMuted = LightTextMuted,
    success = BlinkSuccess,
    warning = BlinkWarning,
    error = BlinkRed
)

internal val LocalBlinkSemanticColors = staticCompositionLocalOf { BlinkDarkSemanticColors }

/** Semantic tokens for components that need more precision than MaterialTheme.colorScheme. */
object BlinkThemeTokens {
    val colors: BlinkSemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalBlinkSemanticColors.current

    val spacing: BlinkSpacing
        get() = BlinkSpacing

    val elevation: BlinkElevation
        get() = BlinkElevation

    val motion: BlinkMotion
        get() = BlinkMotion
}
