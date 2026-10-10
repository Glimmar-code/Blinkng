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
    primary = Color(BlinkDesignTokens.Dark.TextPrimary),
    onPrimary = Color.Black,
    primaryContainer = Color(BlinkDesignTokens.Dark.SurfaceHighest),
    onPrimaryContainer = Color.White,
    secondary = Color(BlinkDesignTokens.Dark.TextPrimary),
    onSecondary = Color.Black,
    secondaryContainer = Color(BlinkDesignTokens.Dark.SurfaceElevated),
    onSecondaryContainer = Color(BlinkDesignTokens.Dark.TextPrimary),
    tertiary = Color(BlinkDesignTokens.Brand.Cyan),
    background = Color(BlinkDesignTokens.Dark.Background),
    onBackground = Color(BlinkDesignTokens.Dark.TextPrimary),
    surface = Color(BlinkDesignTokens.Dark.Surface),
    onSurface = Color(BlinkDesignTokens.Dark.TextPrimary),
    surfaceVariant = Color(BlinkDesignTokens.Dark.SurfaceElevated),
    onSurfaceVariant = Color(BlinkDesignTokens.Dark.TextSecondary),
    surfaceTint = Color.Transparent,
    outline = Color(BlinkDesignTokens.Dark.Border),
    outlineVariant = Color(BlinkDesignTokens.Dark.BorderSoft),
    error = Color(BlinkDesignTokens.Semantic.Error),
    onError = Color.Black,
    scrim = Color.Black,
)

private val DesktopBlinkLightColors = lightColorScheme(
    primary = Color(BlinkDesignTokens.Light.TextPrimary),
    onPrimary = Color.White,
    primaryContainer = Color(BlinkDesignTokens.Light.SurfaceHighest),
    onPrimaryContainer = Color(BlinkDesignTokens.Light.TextPrimary),
    secondary = Color(BlinkDesignTokens.Light.TextPrimary),
    onSecondary = Color.White,
    secondaryContainer = Color(BlinkDesignTokens.Light.SurfaceHighest),
    onSecondaryContainer = Color(BlinkDesignTokens.Light.TextPrimary),
    tertiary = Color(BlinkDesignTokens.Brand.Cyan),
    background = Color(BlinkDesignTokens.Light.Background),
    onBackground = Color(BlinkDesignTokens.Light.TextPrimary),
    surface = Color(BlinkDesignTokens.Light.Surface),
    onSurface = Color(BlinkDesignTokens.Light.TextPrimary),
    surfaceVariant = Color(BlinkDesignTokens.Light.SurfaceHighest),
    onSurfaceVariant = Color(BlinkDesignTokens.Light.TextSecondary),
    surfaceTint = Color.Transparent,
    outline = Color(BlinkDesignTokens.Light.Border),
    outlineVariant = Color(BlinkDesignTokens.Light.BorderSoft),
    error = Color(0xFFB3261E),
    onError = Color.White,
    scrim = Color.Black,
)

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
        shapes = DesktopBlinkShapes,
        content = content,
    )
}
