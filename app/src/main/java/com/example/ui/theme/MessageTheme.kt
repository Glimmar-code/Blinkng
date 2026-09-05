package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Independent appearance modes for the messaging experience. */
enum class MessageThemeMode(val storageValue: String, val displayName: String) {
    PINK("pink", "Pink"),
    DARK("dark", "Dark"),
    LIGHT("light", "Light");

    companion object {
        fun fromStorage(value: String?): MessageThemeMode =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: PINK
    }
}

@Immutable
data class MessagePalette(
    val mode: MessageThemeMode,
    val backgroundTop: Color,
    val backgroundMiddle: Color,
    val backgroundBottom: Color,
    val glass: Color,
    val glassElevated: Color,
    val border: Color,
    val accent: Color,
    val accentSecondary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val incomingBubble: Color,
    val outgoingBubble: Color,
    val outgoingText: Color,
    val online: Color = Color(0xFF20C997),
    val danger: Color = Color(0xFFFF493F),
    val isLight: Boolean = false
) {
    fun backgroundBrush(): Brush = Brush.verticalGradient(
        colors = listOf(backgroundTop, backgroundMiddle, backgroundBottom)
    )

    fun headerBrush(): Brush = Brush.horizontalGradient(
        colors = listOf(glassElevated.copy(alpha = .96f), glass.copy(alpha = .90f))
    )

    fun outgoingBrush(): Brush = Brush.horizontalGradient(
        colors = listOf(outgoingBubble, accentSecondary)
    )
}

private val PinkMessagePalette = MessagePalette(
    mode = MessageThemeMode.PINK,
    backgroundTop = Color(0xFF852D5D),
    backgroundMiddle = Color(0xFF5C1D48),
    backgroundBottom = Color(0xFF2D1523),
    glass = Color(0xFF6C2C55),
    glassElevated = Color(0xFF8A4675),
    border = Color(0x66CABAC5),
    accent = Color(0xFFF33CA5),
    accentSecondary = Color(0xFFC64FAD),
    textPrimary = Color(0xFFFFF7FC),
    textSecondary = Color(0xFFDEC9D7),
    textMuted = Color(0xFFB78FA9),
    incomingBubble = Color(0xFF873B6D),
    outgoingBubble = Color(0xFF9C3E75),
    outgoingText = Color(0xFFFFF7FC)
)

private val DarkMessagePalette = MessagePalette(
    mode = MessageThemeMode.DARK,
    backgroundTop = Color(0xFF151720),
    backgroundMiddle = Color(0xFF090A0F),
    backgroundBottom = Color(0xFF05060B),
    glass = Color(0xFF10131A),
    glassElevated = Color(0xFF191E2C),
    border = Color(0xFF353B4C),
    accent = Color(0xFF8B5CF6),
    accentSecondary = Color(0xFF6D28D9),
    textPrimary = Color(0xFFF7F5FF),
    textSecondary = Color(0xFFAAA9BD),
    textMuted = Color(0xFF777A91),
    incomingBubble = Color(0xFF202431),
    outgoingBubble = Color(0xFF7C3AED),
    outgoingText = Color.White
)

private val LightMessagePalette = MessagePalette(
    mode = MessageThemeMode.LIGHT,
    backgroundTop = Color(0xFFFFFBFD),
    backgroundMiddle = Color(0xFFF8F2F6),
    backgroundBottom = Color(0xFFF3EAF0),
    glass = Color(0xFFFFFFFF),
    glassElevated = Color(0xFFFFF7FB),
    border = Color(0xFFE4D6DF),
    accent = Color(0xFFC02678),
    accentSecondary = Color(0xFFD9468D),
    textPrimary = Color(0xFF24131F),
    textSecondary = Color(0xFF6F5A68),
    textMuted = Color(0xFF9A8291),
    incomingBubble = Color(0xFFF0E3EB),
    outgoingBubble = Color(0xFFC02678),
    outgoingText = Color.White,
    isLight = true
)

fun messagePalette(mode: MessageThemeMode): MessagePalette = when (mode) {
    MessageThemeMode.PINK -> PinkMessagePalette
    MessageThemeMode.DARK -> DarkMessagePalette
    MessageThemeMode.LIGHT -> LightMessagePalette
}

private fun messageColorScheme(palette: MessagePalette): ColorScheme {
    val common = if (palette.isLight) {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFD9EA),
            onPrimaryContainer = Color(0xFF3B0924),
            background = palette.backgroundMiddle,
            onBackground = palette.textPrimary,
            surface = palette.glass,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.glassElevated,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            outlineVariant = palette.border,
            error = palette.danger,
            onError = Color.White
        )
    } else {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            primaryContainer = palette.outgoingBubble,
            onPrimaryContainer = Color.White,
            background = palette.backgroundMiddle,
            onBackground = palette.textPrimary,
            surface = palette.glass,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.glassElevated,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            outlineVariant = palette.border,
            error = palette.danger,
            onError = Color.White
        )
    }
    return common
}

@Composable
fun BlinkMessageTheme(
    mode: MessageThemeMode,
    content: @Composable (MessagePalette) -> Unit
) {
    val palette = messagePalette(mode)
    MaterialTheme(
        colorScheme = messageColorScheme(palette),
        typography = PoppinsTypography
    ) {
        content(palette)
    }
}
