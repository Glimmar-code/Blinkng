package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// BLINK PREMIUM FUTURISTIC PALETTE
// ============================================================

val FeedBackground = Color(0xFF05060B)
val FeedCardSurface = Color(0xFF10131A)
val FeedElevatedSurface = Color(0xFF141824)
val FeedBorder = Color(0xFF272C3A)
val FeedBorderSoft = Color(0xFF1C2130)

val FeedTextPrimary = Color(0xFFF7F5FF)
val FeedTextSecondary = Color(0xFFAAA9BD)
val FeedTextMuted = Color(0xFF777A91)

val FeedPurple = Color(0xFF8B5CF6)
val FeedDeepPurple = Color(0xFF6D28D9)
val FeedBlue = Color(0xFF3B82F6)
val FeedGradientStart = Color(0xFFA855F7)
val FeedGradientMiddle = Color(0xFF7C3AED)
val FeedGradientEnd = Color(0xFF2F80ED)

// Existing brand names stay available so the rest of the app keeps compiling.
// Their values now align with the premium purple/cobalt visual system.
val BlinkBlack = FeedBackground
val BlinkBlackSoft = FeedCardSurface
val BlinkBlackElevated = FeedElevatedSurface
val BlinkCream = FeedTextPrimary
val BlinkCreamSoft = FeedTextSecondary
val BlinkCreamBright = Color(0xFFFFFFFF)
val BlinkGold = Color(0xFFF5C451)
val BlinkGoldSoft = Color(0xFFFFD978)

val BlinkRed = Color(0xFFFF5D73)
val BlinkBlue = FeedBlue
val BlinkCyan = Color(0xFF35C7E8)
val BlinkOnlineGreen = Color(0xFF22C55E)
val BlinkPink = FeedPurple
val BlinkPinkDeep = FeedDeepPurple
val BlinkPurple = FeedPurple
val BlinkLavender = Color(0xFFC4B5FD)
val BlinkAccentSoft = Color(0xFFDDD6FE)

// ============================================================
// DARK THEME
// ============================================================

val DarkBackground = FeedBackground
val DarkSurface = FeedCardSurface
val DarkSurfaceElevated = FeedElevatedSurface
val DarkSurfaceHighest = Color(0xFF191E2C)
val DarkBorder = FeedBorder
val DarkBorderSoft = FeedBorderSoft
val DarkTextPrimary = FeedTextPrimary
val DarkTextSecondary = FeedTextSecondary
val DarkTextMuted = FeedTextMuted

// ============================================================
// LIGHT THEME
// ============================================================

val LightBackground = Color(0xFFF7F6FB)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceElevated = Color(0xFFFFFFFF)
val LightSurfaceCream = Color(0xFFF1EFF8)
val LightBorder = Color(0xFFE0DDEA)
val LightBorderSoft = Color(0xFFECE9F3)
val LightTextPrimary = Color(0xFF16141D)
val LightTextSecondary = Color(0xFF625F70)
val LightTextMuted = Color(0xFF8A8795)

// ============================================================
// SPECIAL COLORS
// ============================================================

val PureWhite = Color(0xFFFFFFFF)
val PureBlack = Color(0xFF000000)
val Transparent = Color.Transparent

// ============================================================
// FACULTY COLORS
// ============================================================

fun getFacultyColor(tag: String?): Color = when (tag?.uppercase()) {
    "SIMME" -> FeedPurple
    "SBMS" -> Color(0xFF5FB8C9)
    "LAW" -> Color(0xFFE5B94B)
    "ARTS" -> Color(0xFFE98B6A)
    "ENGINEERING" -> Color(0xFF4CAF50)
    "SCIENCE" -> Color(0xFF35B8C4)
    "MEDICINE" -> Color(0xFFE47786)
    "SOCIAL SCIENCES" -> Color(0xFFC59B64)
    else -> FeedPurple
}
