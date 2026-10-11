package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val BlinkDarkColorScheme: ColorScheme = darkColorScheme(
    primary = DarkTextPrimary,
    onPrimary = DarkBackground,
    primaryContainer = DarkSurfaceHighest,
    onPrimaryContainer = Color.White,
    inversePrimary = FeedPurpleBright,
    secondary = DarkTextPrimary,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurfaceElevated,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = BlinkCyan,
    onTertiary = Color(0xFF001F26),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    surfaceTint = Color.Transparent,
    inverseSurface = DarkTextPrimary,
    inverseOnSurface = DarkBackground,
    outline = DarkBorder,
    outlineVariant = DarkBorderSoft,
    error = BlinkRed,
    onError = Color.Black,
    errorContainer = Color(0xFF4A1822),
    onErrorContainer = Color(0xFFFFD9DF),
    scrim = Color.Black
)
