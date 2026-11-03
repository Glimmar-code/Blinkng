package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertTrue

class BlinkMotionContractTest {
    @Test
    fun motionDurationsStayShortAndInOrder() {
        val motion = BlinkDesignTokens.Motion
        assertTrue(motion.Interaction in 60..200)
        assertTrue(motion.ContentReveal in motion.Interaction..300)
        assertTrue(motion.Navigation in motion.ContentReveal..350)
        assertTrue(motion.PressedScale in 0.9f..1f)
        assertTrue(motion.SubtleSelectedScale in 1f..1.08f)
    }
}
