package com.example.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageThemeModeTest {
    @Test
    fun missingPreferenceDefaultsToPink() {
        assertEquals(MessageThemeMode.PINK, MessageThemeMode.fromStorage(null))
    }

    @Test
    fun invalidPreferenceFallsBackToPink() {
        assertEquals(MessageThemeMode.PINK, MessageThemeMode.fromStorage("unknown"))
    }

    @Test
    fun storedModesAreCaseInsensitive() {
        assertEquals(MessageThemeMode.DARK, MessageThemeMode.fromStorage("DARK"))
        assertEquals(MessageThemeMode.LIGHT, MessageThemeMode.fromStorage("light"))
    }
}
