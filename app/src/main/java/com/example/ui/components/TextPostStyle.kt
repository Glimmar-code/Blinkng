package com.example.ui.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

internal data class TextPostStyleSpec(
    val key: String,
    val label: String,
    val colors: List<Color>,
    val textColor: Color = Color.White
) {
    fun brush(): Brush = if (colors.size == 1) {
        Brush.linearGradient(listOf(colors.first(), colors.first()))
    } else {
        Brush.linearGradient(colors)
    }
}

internal val TextPostStyles = listOf(
    TextPostStyleSpec("aurora", "Aurora", listOf(Color(0xFF6A7CFF), Color(0xFF8B5CF6), Color(0xFF52C7EA))),
    TextPostStyleSpec("ocean", "Ocean", listOf(Color(0xFF0D47A1), Color(0xFF1976D2), Color(0xFF26C6DA))),
    TextPostStyleSpec("violet", "Violet", listOf(Color(0xFF4C1D95), Color(0xFF7C3AED), Color(0xFFB65CFF))),
    TextPostStyleSpec("sunset", "Sunset", listOf(Color(0xFFFF6B6B), Color(0xFFF97316), Color(0xFFFACC15))),
    TextPostStyleSpec("rose", "Rose", listOf(Color(0xFF9F1239), Color(0xFFE11D48), Color(0xFFFB7185))),
    TextPostStyleSpec("midnight", "Midnight", listOf(Color(0xFF060B1A), Color(0xFF172554), Color(0xFF312E81))),
    TextPostStyleSpec("mint", "Mint", listOf(Color(0xFF0F766E), Color(0xFF14B8A6), Color(0xFF5EEAD4))),
    TextPostStyleSpec("amber", "Amber", listOf(Color(0xFFB45309), Color(0xFFF59E0B), Color(0xFFFDE047))),
    TextPostStyleSpec("cobalt", "Cobalt", listOf(Color(0xFF1E3A8A), Color(0xFF2563EB), Color(0xFF60A5FA))),
    TextPostStyleSpec("berry", "Berry", listOf(Color(0xFF581C87), Color(0xFFBE185D), Color(0xFFF472B6))),
    TextPostStyleSpec("lime", "Lime", listOf(Color(0xFF3F6212), Color(0xFF65A30D), Color(0xFFA3E635))),
    TextPostStyleSpec("blush", "Blush", listOf(Color(0xFF7C2D12), Color(0xFFFB7185), Color(0xFFFBCFE8)))
)

internal fun resolveTextPostStyle(key: String?, seed: String = ""): TextPostStyleSpec {
    val requested = key?.trim()?.lowercase()
    TextPostStyles.firstOrNull { it.key == requested }?.let { return it }
    val hash = seed.fold(17) { acc, char -> (acc * 31 + char.code) and 0x7fffffff }
    return TextPostStyles[hash % TextPostStyles.size]
}
