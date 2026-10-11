package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilitySheetDismissPolicyTest {
    @Test
    fun smallPullDoesNotDismissUtility() {
        assertFalse(UtilitySheetDismissPolicy.allowsDownwardDismiss(80f, 1000f))
        assertFalse(UtilitySheetDismissPolicy.allowsDownwardDismiss(570f, 1000f))
        assertFalse(UtilitySheetDismissPolicy.allowsDownwardDismiss(-900f, 1000f))
    }

    @Test
    fun deliberateLongDownwardPullDismissesUtility() {
        assertTrue(UtilitySheetDismissPolicy.allowsDownwardDismiss(580f, 1000f))
        assertTrue(UtilitySheetDismissPolicy.allowsDownwardDismiss(840f, 1000f))
    }

    @Test
    fun missingWindowSizeNeverDismissesUtility() {
        assertFalse(UtilitySheetDismissPolicy.allowsDownwardDismiss(900f, 0f))
        assertFalse(UtilitySheetDismissPolicy.allowsDownwardDismiss(Float.NaN, 900f))
    }
}
