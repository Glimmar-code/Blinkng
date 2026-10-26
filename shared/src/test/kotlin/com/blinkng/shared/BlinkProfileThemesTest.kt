package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BlinkProfileThemesTest {
    @Test fun palettesAreStableAndDistinct() {
        assertEquals(6, BlinkProfileThemes.all.size)
        assertEquals(6, BlinkProfileThemes.all.map { it.id }.distinct().size)
        assertTrue(BlinkProfileThemes.all.all { it.topArgb != 0L && it.bottomArgb != 0L })
        assertNotEquals(BlinkProfileThemes.resolve("royal_purple").topArgb,
            BlinkProfileThemes.resolve("sapphire").topArgb)
    }

    @Test fun blackNeverInheritsTheViewerAppTheme() {
        val black = BlinkProfileThemes.resolve("black")
        assertEquals(0xFF000000, black.topArgb)
        assertEquals(0xFF000000, black.bottomArgb)
        assertEquals("black", BlinkProfileThemes.normalize(null))
        assertEquals("black", BlinkProfileThemes.normalize("invalid"))
    }

    @Test fun persistedIdentifiersRoundTrip() {
        BlinkProfileThemes.all.forEach { theme ->
            assertEquals(theme.id, BlinkProfileThemes.normalize(theme.id.uppercase()))
            assertEquals(theme, BlinkProfileThemes.resolve(theme.id))
        }
    }
}
