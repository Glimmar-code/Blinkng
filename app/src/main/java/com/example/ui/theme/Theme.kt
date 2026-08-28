package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = BlinkCream,
    onPrimary = BlinkBlack,
    primaryContainer = BlinkGold,
    onPrimaryContainer = BlinkBlack,
    secondary = BlinkGold,
    onSecondary = BlinkBlack,
    secondaryContainer = DarkSurfaceElevated,
    onSecondaryContainer = BlinkCreamBright,
    tertiary = BlinkCyan,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorderSoft,
    error = BlinkRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = BlinkBlack,
    onPrimary = BlinkCream,
    primaryContainer = BlinkCreamSoft,
    onPrimaryContainer = BlinkBlack,
    secondary = BlinkGold,
    onSecondary = BlinkBlack,
    secondaryContainer = LightSurfaceCream,
    onSecondaryContainer = BlinkBlack,
    tertiary = BlinkCyan,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceCream,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = LightBorderSoft,
    error = BlinkRed,
    onError = Color.White
)

fun blinkBackgroundBrush(isDark: Boolean): Brush {
    return if (isDark) {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFF1A1714),
                Color(0xFF0F0E0C),
                DarkBackground
            ),
            radius = 1200f
        )
    } else {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFF9F0),
                Color(0xFFF6EDE2),
                LightBackground
            ),
            radius = 1200f
        )
    }
}

@Composable
fun BlinkTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
