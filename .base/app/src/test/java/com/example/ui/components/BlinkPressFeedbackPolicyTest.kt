package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class BlinkPressFeedbackPolicyTest {
    @Test fun onlyAnEnabledPressChangesTheScale() {
        assertEquals(0.985f, premiumPressTarget(true, true, false, 0.985f), 0.0001f)
        assertEquals(1f, premiumPressTarget(false, true, false, 0.985f), 0.0001f)
        assertEquals(1f, premiumPressTarget(true, false, false, 0.985f), 0.0001f)
    }

    @Test fun systemReducedMotionDisablesDecorativeScaling() {
        assertEquals(1f, premiumPressTarget(true, true, true, 0.985f), 0.0001f)
    }

    @Test fun invalidOrExcessiveScalingIsClamped() {
        assertEquals(1f, premiumPressTarget(true, true, false, Float.NaN), 0.0001f)
        assertEquals(0.94f, premiumPressTarget(true, true, false, 0.01f), 0.0001f)
        assertEquals(1f, premiumPressTarget(true, true, false, 1.5f), 0.0001f)
    }
}
