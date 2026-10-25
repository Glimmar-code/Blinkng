package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lock BLINK's signed-in dark appearance to the existing Auth visual language.
 * Covers the Android/Windows shared contract without requiring a device or backend.
 */
class BlinkPremiumDarkDesignTest {
    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val value = ((argb shr shift) and 0xFF).toDouble() / 255.0
            return if (value <= 0.04045) value / 12.92
                else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrast(first: Long, second: Long): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    @Test fun darkModeUsesAuthBlackAndBrandedPurple() {
        assertEquals(0xFF000000L, BlinkDesignTokens.Dark.Background)
        assertEquals(0xFF8B5CF6L, BlinkDesignTokens.Brand.Primary)
        assertEquals(0xFF0E0E0EL, BlinkDesignTokens.Dark.Surface)
        assertEquals(0xFF151515L, BlinkDesignTokens.Dark.BorderSoft)
        assertEquals(100, BlinkDesignTokens.PremiumDark.ButtonCornerRadius)
        assertTrue(BlinkDesignTokens.PremiumDark.CardOverlayAlpha in 0.04f..0.07f)
        assertTrue(BlinkDesignTokens.PremiumDark.SubtleBorderAlpha in 0.07f..0.12f)
    }

    @Test fun defaultDarkActionsAndTextStayReadable() {
        assertTrue("Dark button text on brand purple must meet WCAG AA",
            contrast(0xFF000000L, BlinkDesignTokens.Brand.Primary) >= 4.5)
        assertTrue("Surface text must meet WCAG AA",
            contrast(BlinkDesignTokens.Dark.TextPrimary, BlinkDesignTokens.Dark.Surface) >= 4.5)
        assertTrue("Secondary text must meet WCAG AA",
            contrast(BlinkDesignTokens.Dark.TextSecondary, BlinkDesignTokens.Dark.Surface) >= 4.5)
    }

    @Test fun lightModeRemainsIndependent() {
        assertEquals(0xFFF7F7FBL, BlinkDesignTokens.Light.Background)
        assertEquals(0xFFFFFFFFL, BlinkDesignTokens.Light.Surface)
        assertEquals(BlinkAppearanceMode.LIGHT, BlinkAppearanceMode.fromPersisted("light"))
        assertEquals(BlinkAppearanceMode.DARK, BlinkAppearanceMode.fromPersisted("dark"))
        assertEquals(BlinkAppearanceMode.SYSTEM, BlinkAppearanceMode.fromPersisted("system"))
    }
}
