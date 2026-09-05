package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = FeedPurple,
    onPrimary = Color.White,
    primaryContainer = FeedDeepPurple,
    onPrimaryContainer = Color.White,
    secondary = FeedBlue,
    onSecondary = Color.White,
    secondaryContainer = FeedElevatedSurface,
    onSecondaryContainer = FeedTextPrimary,
    tertiary = BlinkCyan,
    background = FeedBackground,
    onBackground = FeedTextPrimary,
    surface = FeedCardSurface,
    onSurface = FeedTextPrimary,
    surfaceVariant = FeedElevatedSurface,
    onSurfaceVariant = FeedTextSecondary,
    outline = FeedBorder,
    outlineVariant = FeedBorderSoft,
    error = BlinkRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = FeedDeepPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8DFFF),
    onPrimaryContainer = Color(0xFF241047),
    secondary = FeedBlue,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceCream,
    onSecondaryContainer = LightTextPrimary,
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

fun feedAccentBrush(): Brush = Brush.linearGradient(
    colors = listOf(FeedGradientStart, FeedGradientMiddle, FeedGradientEnd)
)

fun blinkBackgroundBrush(isDark: Boolean): Brush = if (isDark) {
    Brush.radialGradient(
        colors = listOf(
            FeedElevatedSurface.copy(alpha = 0.72f),
            FeedBackground,
            FeedBackground
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

@Composable
fun BlinkTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
