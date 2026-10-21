package com.example.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkAppearanceModeTest {
    @Test fun newInstallUsesLight() {
        assertEquals(BlinkAppearanceMode.LIGHT, BlinkAppearanceMode.restored(null, null))
    }

    @Test fun legacyChoiceIsPreserved() {
        assertEquals(BlinkAppearanceMode.DARK, BlinkAppearanceMode.restored(null, true))
        assertEquals(BlinkAppearanceMode.LIGHT, BlinkAppearanceMode.restored(null, false))
    }

    @Test fun explicitChoiceOverridesLegacyPreference() {
        assertEquals(BlinkAppearanceMode.SYSTEM, BlinkAppearanceMode.restored("system", false))
        assertEquals(BlinkAppearanceMode.LIGHT, BlinkAppearanceMode.restored("light", true))
    }

    @Test fun deviceDefaultTracksAndroidTheme() {
        assertTrue(BlinkAppearanceMode.SYSTEM.isDark(true))
        assertFalse(BlinkAppearanceMode.SYSTEM.isDark(false))
        assertFalse(BlinkAppearanceMode.LIGHT.isDark(true))
        assertTrue(BlinkAppearanceMode.DARK.isDark(false))
    }
}
