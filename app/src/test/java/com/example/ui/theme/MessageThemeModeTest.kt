package com.example.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageThemeModeTest {
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
