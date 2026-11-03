package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilitySheetDismissPolicyTest {
    @Test
    fun smallPullDoesNotDismissUtility() {
        assertFalse(UtilitySheetDismissPolicy.allowsDownwardDismiss(80f, 1000f))
        assertFalse(UtilitySheetDismissPolicy.allowsDownwardDismiss(499f, 1000f))
        assertFalse(UtilitySheetDismissPolicy.allowsDownwardDismiss(-900f, 1000f))
    }

    @Test
    fun deliberateLongDownwardPullDismissesUtility() {
        assertTrue(UtilitySheetDismissPolicy.allowsDownwardDismiss(500f, 1000f))
        assertTrue(UtilitySheetDismissPolicy.allowsDownwardDismiss(840f, 1000f))
    }

    @Test
    fun missingWindowSizeNeverDismissesUtility() {
        assertFalse(UtilitySheetDismissPolicy.allowsDownwardDismiss(900f, 0f))
        assertFalse(UtilitySheetDismissPolicy.allowsDownwardDismiss(Float.NaN, 900f))
    }
    @Test
    fun halfScreenCloseRequiresReleaseAndNotCancellation() {
        assertFalse(UtilitySheetDismissPolicy.allowsCompletedDownwardDismiss(700f, 1000f, false))
        assertFalse(UtilitySheetDismissPolicy.allowsCompletedDownwardDismiss(499f, 1000f, true))
        assertTrue(UtilitySheetDismissPolicy.allowsCompletedDownwardDismiss(500f, 1000f, true))
        assertTrue(UtilitySheetDismissPolicy.allowsCompletedDownwardDismiss(760f, 1000f, true))
    }
}
