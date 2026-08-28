package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// PREMIUM BLACK + CREAM BRAND PALETTE
// ============================================================

// Primary brand colors
val BlinkBlack = Color(0xFF050505)
val BlinkBlackSoft = Color(0xFF0D0D0D)
val BlinkBlackElevated = Color(0xFF151515)

val BlinkCream = Color(0xFFF5EBDD)
val BlinkCreamSoft = Color(0xFFEDE0CF)
val BlinkCreamBright = Color(0xFFFFF8EE)

val BlinkGold = Color(0xFFC9A86A)
val BlinkGoldSoft = Color(0xFFE2C994)

// Functional colors
val BlinkRed = Color(0xFFE5484D)
val BlinkBlue = Color(0xFF4A90E2)
val BlinkCyan = Color(0xFF5FB8C9)
val BlinkOnlineGreen = Color(0xFF22C55E)

// Keep these aliases so existing screens using the old names
// continue compiling without needing immediate changes.
val BlinkPink = BlinkCream
val BlinkPinkDeep = BlinkCreamSoft
val BlinkPurple = BlinkGold
val BlinkLavender = BlinkCreamBright
val BlinkAccentSoft = Color(0xFFF7ECDD)


// ============================================================
// DARK THEME
// ============================================================

val DarkBackground = Color(0xFF050505)

val DarkSurface = Color(0xFF0D0D0D)

val DarkSurfaceElevated = Color(0xFF151515)

val DarkSurfaceHighest = Color(0xFF1C1A18)

val DarkBorder = Color(0xFF2B2722)

val DarkBorderSoft = Color(0xFF211E1A)

val DarkTextPrimary = Color(0xFFF8EFE3)

val DarkTextSecondary = Color(0xFFB9AEA1)

val DarkTextMuted = Color(0xFF7F766C)


// ============================================================
// LIGHT THEME
// ============================================================

val LightBackground = Color(0xFFF4EEE5)

val LightSurface = Color(0xFFFFFBF5)

val LightSurfaceElevated = Color(0xFFFFFFFF)

val LightSurfaceCream = Color(0xFFF8F1E8)

val LightBorder = Color(0xFFE0D5C6)

val LightBorderSoft = Color(0xFFEBE2D7)

val LightTextPrimary = Color(0xFF11100E)

val LightTextSecondary = Color(0xFF655E55)

val LightTextMuted = Color(0xFF938A7F)


// ============================================================
// SPECIAL COLORS
// ============================================================

val PureWhite = Color(0xFFFFFFFF)

val PureBlack = Color(0xFF000000)

val Transparent = Color.Transparent


// ============================================================
// FACULTY COLORS
// ============================================================

fun getFacultyColor(tag: String?): Color {

    return when (tag?.uppercase()) {

        "SIMME" ->
            BlinkGold

        "SBMS" ->
            Color(0xFF5FB8C9)

        "LAW" ->
            Color(0xFFB8860B)

        "ARTS" ->
            Color(0xFFC97A5A)

        "ENGINEERING" ->
            Color(0xFF4CAF50)

        "SCIENCE" ->
            Color(0xFF269EAA)

        "MEDICINE" ->
            Color(0xFFC96B6B)

        "SOCIAL SCIENCES" ->
            Color(0xFFB28A57)

        else ->
            BlinkGold
    }
}