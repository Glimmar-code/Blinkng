package com.example.ui.theme

import org.junit.Assert.assertEquals
import androidx.compose.ui.graphics.Color
import org.junit.Test

class MessageThemeModeTest {
    @Test
    fun screenshotReferenceDarkChatUsesCharcoalPaleBubblesAndBlueActions() {
        val current = referenceChatPalette(messagePalette(MessageThemeMode.DARK))
        assertEquals(Color(0xFF090D10), current.backgroundMiddle)
        assertEquals(Color(0xFF23272A), current.incomingBubble)
        assertEquals(Color(0xFFEDEFF2), current.outgoingBubble)
        assertEquals(Color(0xFF1A2026), current.outgoingText)
        assertEquals(Color(0xFF0879F8), current.accent)
        assertEquals(Color(0xFF0879F8), current.online)
        assertEquals(Color(0xFF202328), current.glass)
        assertEquals(Color(0xFF24282B), current.glassElevated)
        assertEquals(
            messagePalette(MessageThemeMode.LIGHT),
            referenceChatPalette(messagePalette(MessageThemeMode.LIGHT))
        )
    }

    @Test
    fun otherThemeModesRemainUnchanged() {
        assertEquals(messagePalette(MessageThemeMode.PINK),
            referenceChatPalette(messagePalette(MessageThemeMode.PINK)))
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
