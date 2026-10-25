package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkPerformancePolicyTest {
    @Test fun automaticNeverForcesOneHundredTwentyHertz() {
        assertEquals(0f, BlinkPerformancePolicy.targetRefreshRate(
            BlinkPerformanceMode.AUTOMATIC, listOf(60f, 90f, 120f)), 0.001f)
    }
    @Test fun balancedAndPowerSavingUseSupportedModesOnly() {
        assertEquals(90f, BlinkPerformancePolicy.targetRefreshRate(
            BlinkPerformanceMode.BALANCED, listOf(60f, 90f, 120f)), 0.001f)
        assertEquals(60f, BlinkPerformancePolicy.targetRefreshRate(
            BlinkPerformanceMode.POWER_SAVING, listOf(60f, 120f)), 0.001f)
        assertTrue(BlinkPerformancePolicy.reduceDecorativeMotion(
            BlinkPerformanceMode.POWER_SAVING, false))
    }
}
