package com.example.ui.theme

import org.junit.Assert.assertEquals
import androidx.compose.ui.graphics.Color
import org.junit.Test

class MessageThemeModeTest {
    @Test
    fun generatedReferenceDarkChatUsesCobaltAndNavyNotGreen() {
        val current = referenceChatPalette(messagePalette(MessageThemeMode.DARK))
        assertEquals(Color(0xFF060D16), current.backgroundMiddle)
        assertEquals(Color(0xFF1C2A3B), current.incomingBubble)
        assertEquals(Color(0xFF0879F8), current.outgoingBubble)
        assertEquals(Color(0xFF1685FF), current.accent)
        assertEquals(Color.White, current.outgoingText)
        assertEquals(
            messagePalette(MessageThemeMode.LIGHT),
            referenceChatPalette(messagePalette(MessageThemeMode.LIGHT))
        )
    }

    @Test
    fun missingPreferenceDefaultsToDark() {
        assertEquals(MessageThemeMode.DARK, MessageThemeMode.fromStorage(null))
    }

    @Test
    fun invalidPreferenceFallsBackToDark() {
        assertEquals(MessageThemeMode.DARK, MessageThemeMode.fromStorage("unknown"))
    }

    @Test
    fun storedModesAreCaseInsensitive() {
        assertEquals(MessageThemeMode.DARK, MessageThemeMode.fromStorage("DARK"))
        assertEquals(MessageThemeMode.LIGHT, MessageThemeMode.fromStorage("light"))
    }
}
