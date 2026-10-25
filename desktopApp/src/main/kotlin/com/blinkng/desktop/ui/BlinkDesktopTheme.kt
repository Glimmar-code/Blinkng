package com.blinkng.desktop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import com.blinkng.shared.BlinkAppearanceMode
import com.blinkng.shared.BlinkDesignTokens

val DesktopBlinkPurple = Color(BlinkDesignTokens.Brand.Primary)
val DesktopBlinkPurpleBright = Color(BlinkDesignTokens.Brand.PrimaryBright)
val DesktopBlinkPurpleDeep = Color(BlinkDesignTokens.Brand.PrimaryDeep)

private val DesktopBlinkDarkColors = darkColorScheme(
    primary = DesktopBlinkPurple,
    onPrimary = Color.Black,
    primaryContainer = DesktopBlinkPurpleDeep,
    onPrimaryContainer = Color.White,
    secondary = DesktopBlinkPurpleBright,
    onSecondary = Color.Black,
    secondaryContainer = Color(BlinkDesignTokens.Dark.SurfaceElevated),
    onSecondaryContainer = Color(BlinkDesignTokens.Dark.TextPrimary),
    tertiary = Color(BlinkDesignTokens.Brand.Cyan),
    background = Color(BlinkDesignTokens.Dark.Background),
    onBackground = Color(BlinkDesignTokens.Dark.TextPrimary),
    surface = Color(BlinkDesignTokens.Dark.Surface),
    onSurface = Color(BlinkDesignTokens.Dark.TextPrimary),
    surfaceDim = Color(BlinkDesignTokens.Dark.Background),
    surfaceBright = Color(BlinkDesignTokens.Dark.SurfaceHighest),
    surfaceContainerLowest = Color(BlinkDesignTokens.Dark.Background),
    surfaceContainerLow = Color(BlinkDesignTokens.Dark.Surface),
    surfaceContainer = Color(BlinkDesignTokens.Dark.SurfaceElevated),
    surfaceContainerHigh = Color(BlinkDesignTokens.Dark.SurfaceHighest),
    surfaceContainerHighest = Color(BlinkDesignTokens.Dark.SurfaceHighest),
    surfaceVariant = Color(BlinkDesignTokens.Dark.SurfaceElevated),
    onSurfaceVariant = Color(BlinkDesignTokens.Dark.TextSecondary),
    surfaceTint = DesktopBlinkPurple,
    outline = Color(BlinkDesignTokens.Dark.Border),
    outlineVariant = Color(BlinkDesignTokens.Dark.BorderSoft),
    error = Color(BlinkDesignTokens.Semantic.Error),
    onError = Color.White,
    scrim = Color.Black,
)

// Preserve the Windows light appearance that DesktopShell used before this migration.
// The Auth-inspired redesign intentionally affects dark mode only.
private val DesktopBlinkLightColors = lightColorScheme(primary = Color(0xFF6D3DF5))

private val DesktopBlinkShapes = Shapes(
    extraSmall = RoundedCornerShape(BlinkDesignTokens.Shape.Small.dp),
    small = RoundedCornerShape(BlinkDesignTokens.Shape.Small.dp),
    medium = RoundedCornerShape(BlinkDesignTokens.Shape.Medium.dp),
    large = RoundedCornerShape(BlinkDesignTokens.Shape.Large.dp),
    extraLarge = RoundedCornerShape(BlinkDesignTokens.Shape.ExtraLarge.dp),
)

@Composable
fun BlinkDesktopTheme(
    appearance: String?,
    content: @Composable () -> Unit,
) {
    val mode = BlinkAppearanceMode.fromPersisted(appearance, fallbackDark = true)
    val dark = when (mode) {
        BlinkAppearanceMode.DARK -> true
        BlinkAppearanceMode.LIGHT -> false
        BlinkAppearanceMode.SYSTEM -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (dark) DesktopBlinkDarkColors else DesktopBlinkLightColors,
        shapes = if (dark) DesktopBlinkShapes else Shapes(),
        content = content,
    )
}
