package com.blinkng.shared.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class BlinkStatusVideoTrimPolicyTest {
    private val p = BlinkStatusVideoTrimPolicy

    @Test fun originalShortClipPassesWithoutExport() {
        val w = p.window(17_500, 0)
        assertEquals(p.Window(0, 17_500), w)
        assertFalse(p.needsExport(17_500, w))
    }

    @Test fun longVideoDefaultsToThirtySeconds() {
        val w = p.window(90_000, 0)
        assertEquals(p.Window(0, 30_000), w)
        assertTrue(p.needsExport(90_000, w))
    }

    @Test fun movingStartPreservesBounds() {
        assertEquals(p.Window(70_000, 90_000), p.window(90_000, 70_000))
        assertEquals(p.Window(90_000, 120_000), p.window(120_000, 90_000, 120_000))
        assertEquals(p.Window(89_000, 90_000), p.window(90_000, Long.MAX_VALUE))
        assertEquals(p.Window(10_000, 11_000), p.window(120_000, 10_000, 10_000))
    }

    @Test fun invalidVideoLengthFailsEarly() {
        assertFailsWith<IllegalArgumentException> { p.window(999, 0) }
    }
}
