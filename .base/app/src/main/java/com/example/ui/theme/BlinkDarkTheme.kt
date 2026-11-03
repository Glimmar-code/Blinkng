package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val BlinkDarkColorScheme: ColorScheme = darkColorScheme(
    primary = FeedPurple,
    // Dark text matches Auth's purple CTA and reaches AA contrast.
    onPrimary = Color.Black,
    primaryContainer = FeedDeepPurple,
    onPrimaryContainer = Color.White,
    inversePrimary = FeedPurpleBright,
    secondary = FeedPurpleBright,
    onSecondary = Color.Black,
    secondaryContainer = DarkSurfaceElevated,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = BlinkCyan,
    onTertiary = Color(0xFF001F26),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    // Without explicit containers, Material 3 defaults wash some cards gray/purple.
    surfaceDim = DarkBackground,
    surfaceBright = DarkSurfaceHighest,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceElevated,
    surfaceContainerHigh = DarkSurfaceHighest,
    surfaceContainerHighest = DarkSurfaceHighest,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    surfaceTint = FeedPurple,
    inverseSurface = DarkTextPrimary,
    inverseOnSurface = DarkBackground,
    outline = DarkBorder,
    outlineVariant = DarkBorderSoft,
    error = BlinkRed,
    onError = Color.White,
    errorContainer = Color(0xFF4A1822),
    onErrorContainer = Color(0xFFFFD9DF),
    scrim = Color.Black
)
