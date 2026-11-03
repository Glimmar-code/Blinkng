package com.example

import com.blinkng.shared.BlinkAdaptiveLayoutPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkAdaptiveLayoutPolicyTest {
    @Test
    fun narrowPhoneUsesCompactArrangement() {
        assertTrue(BlinkAdaptiveLayoutPolicy.useCompactPhoneLayout(320f, 1f))
        assertTrue(BlinkAdaptiveLayoutPolicy.useCompactPhoneLayout(360f, 1f))
        assertFalse(BlinkAdaptiveLayoutPolicy.useCompactPhoneLayout(393f, 1f))
    }

    @Test
    fun largeFontTriggersCompactArrangementEvenOnWiderPhone() {
        assertTrue(BlinkAdaptiveLayoutPolicy.useCompactPhoneLayout(412f, 1.3f))
        assertTrue(BlinkAdaptiveLayoutPolicy.useCompactPhoneLayout(600f, 2f))
        assertFalse(BlinkAdaptiveLayoutPolicy.useCompactPhoneLayout(600f, 1f))
    }
}
