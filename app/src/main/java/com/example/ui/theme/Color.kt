package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import com.blinkng.shared.BlinkDesignTokens

// ============================================================
// BLINK PREMIUM DESIGN SYSTEM
// ============================================================
// These platform colors are mapped from the shared Blink design contract so the
// Android and Windows clients can use the same visual language.

val FeedBackground = Color(BlinkDesignTokens.Dark.Background)
val FeedCardSurface = Color(BlinkDesignTokens.Dark.Surface)
val FeedElevatedSurface = Color(BlinkDesignTokens.Dark.SurfaceElevated)
val FeedInputSurface = Color(BlinkDesignTokens.Dark.Input)
val FeedBorder = Color(BlinkDesignTokens.Dark.Border)
val FeedBorderSoft = Color(BlinkDesignTokens.Dark.BorderSoft)

val FeedTextPrimary = Color(BlinkDesignTokens.Dark.TextPrimary)
val FeedTextSecondary = Color(BlinkDesignTokens.Dark.TextSecondary)
val FeedTextMuted = Color(BlinkDesignTokens.Dark.TextMuted)

val FeedPurple = Color(BlinkDesignTokens.Brand.Primary)
val FeedPurpleBright = Color(BlinkDesignTokens.Brand.PrimaryBright)
val FeedDeepPurple = Color(BlinkDesignTokens.Brand.PrimaryDeep)
val FeedBlue = Color(BlinkDesignTokens.Brand.Blue)
val FeedGradientStart = FeedPurpleBright
val FeedGradientMiddle = FeedPurple
val FeedGradientEnd = FeedBlue

// Existing names stay available so older screens keep compiling while they are
// migrated to semantic MaterialTheme/Blink tokens screen-by-screen.
val BlinkBlack = FeedBackground
val BlinkBlackSoft = FeedCardSurface
val BlinkBlackElevated = FeedElevatedSurface
val BlinkCream = FeedTextPrimary
val BlinkCreamSoft = FeedTextSecondary
val BlinkCreamBright = Color.White
val BlinkGold = Color(BlinkDesignTokens.Semantic.Gold)
val BlinkGoldSoft = Color(0xFFFFD978)

val BlinkRed = Color(BlinkDesignTokens.Semantic.Error)
val BlinkBlue = FeedBlue
val BlinkCyan = Color(BlinkDesignTokens.Brand.Cyan)
val BlinkOnlineGreen = Color(BlinkDesignTokens.Semantic.Success)
val BlinkPink = FeedPurple
val BlinkPinkDeep = FeedDeepPurple
val BlinkPurple = FeedPurple
val BlinkLavender = Color(BlinkDesignTokens.Brand.Lavender)
val BlinkAccentSoft = Color(0xFFDDD6FE)
val BlinkWarning = Color(BlinkDesignTokens.Semantic.Warning)
val BlinkSuccess = Color(BlinkDesignTokens.Semantic.Success)

// ============================================================
// DARK THEME
// ============================================================

val DarkBackground = Color(BlinkDesignTokens.Dark.Background)
val DarkSurface = Color(BlinkDesignTokens.Dark.Surface)
val DarkSurfaceElevated = Color(BlinkDesignTokens.Dark.SurfaceElevated)
val DarkInput = Color(BlinkDesignTokens.Dark.Input)
val DarkSurfaceHighest = Color(BlinkDesignTokens.Dark.SurfaceHighest)
val DarkBorder = Color(BlinkDesignTokens.Dark.Border)
val DarkBorderSoft = Color(BlinkDesignTokens.Dark.BorderSoft)
val DarkTextPrimary = Color(BlinkDesignTokens.Dark.TextPrimary)
val DarkTextSecondary = Color(BlinkDesignTokens.Dark.TextSecondary)
val DarkTextMuted = Color(BlinkDesignTokens.Dark.TextMuted)

// ============================================================
// LIGHT THEME
// ============================================================

val LightBackground = Color(BlinkDesignTokens.Light.Background)
val LightSurface = Color(BlinkDesignTokens.Light.Surface)
val LightSurfaceElevated = Color(BlinkDesignTokens.Light.SurfaceElevated)
val LightInput = Color(BlinkDesignTokens.Light.Input)
val LightSurfaceCream = Color(BlinkDesignTokens.Light.SurfaceHighest)
val LightBorder = Color(BlinkDesignTokens.Light.Border)
val LightBorderSoft = Color(BlinkDesignTokens.Light.BorderSoft)
val LightTextPrimary = Color(BlinkDesignTokens.Light.TextPrimary)
val LightTextSecondary = Color(BlinkDesignTokens.Light.TextSecondary)
val LightTextMuted = Color(BlinkDesignTokens.Light.TextMuted)

// ============================================================
// SPECIAL COLORS
// ============================================================

val PureWhite = Color.White
val PureBlack = Color.Black
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
