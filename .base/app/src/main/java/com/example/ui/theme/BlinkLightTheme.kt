package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val BlinkLightColorScheme: ColorScheme = lightColorScheme(
    primary = FeedDeepPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DFFF),
    onPrimaryContainer = Color(0xFF25104D),
    inversePrimary = FeedPurpleBright,
    secondary = FeedPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0E9FF),
    onSecondaryContainer = LightTextPrimary,
    tertiary = BlinkCyan,
    onTertiary = Color(0xFF002027),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceCream,
    onSurfaceVariant = LightTextSecondary,
    surfaceTint = FeedPurple,
    inverseSurface = Color(0xFF2D3038),
    inverseOnSurface = Color(0xFFF4F4F8),
    outline = LightBorder,
    outlineVariant = LightBorderSoft,
    error = BlinkRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFD9DF),
    onErrorContainer = Color(0xFF40000B),
    scrim = Color.Black
)
