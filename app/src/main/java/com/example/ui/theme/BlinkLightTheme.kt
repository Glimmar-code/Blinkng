package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val BlinkLightColorScheme: ColorScheme = lightColorScheme(
    primary = LightTextPrimary,
    onPrimary = Color.White,
    primaryContainer = LightSurfaceCream,
    onPrimaryContainer = LightTextPrimary,
    inversePrimary = FeedPurpleBright,
    secondary = LightTextPrimary,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceCream,
    onSecondaryContainer = LightTextPrimary,
    tertiary = BlinkCyan,
    onTertiary = Color(0xFF002027),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceCream,
    onSurfaceVariant = LightTextSecondary,
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF2D3038),
    inverseOnSurface = Color(0xFFF4F4F8),
    outline = LightBorder,
    outlineVariant = LightBorderSoft,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFD9DF),
    onErrorContainer = Color(0xFF40000B),
    scrim = Color.Black
)
