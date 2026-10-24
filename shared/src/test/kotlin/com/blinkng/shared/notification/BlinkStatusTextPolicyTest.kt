package com.blinkng.shared.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlinkStatusTextPolicyTest {
    @Test fun statusTextRequiresContentAndEnforcesLimit() {
        assertEquals("Hello campus!", BlinkStatusTextPolicy.text(" Hello campus! "))
        assertNull(BlinkStatusTextPolicy.text("  "))
        assertNull(BlinkStatusTextPolicy.text("x".repeat(241)))
        assertEquals("x".repeat(240), BlinkStatusTextPolicy.text("x".repeat(240)))
    }

    @Test fun backgroundMustBeChosenFromAllowedPalette() {
        assertEquals("#214870", BlinkStatusTextPolicy.background("#214870"))
        assertEquals("#171717", BlinkStatusTextPolicy.background("random CSS injection"))
        assertEquals("#171717", BlinkStatusTextPolicy.background(""))
    }
}
