package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkSkeletonPolicyTest {
    @Test fun onlyShowForPendingAbsentContent() {
        assertTrue(BlinkSkeletonPolicy.shouldShow(true, false))
        assertFalse(BlinkSkeletonPolicy.shouldShow(true, true))
        assertFalse(BlinkSkeletonPolicy.shouldShow(false, false))
        assertFalse(BlinkSkeletonPolicy.shouldShow(true, false, true))
    }
    @Test fun capLoadingRowsForPerformance() {
        assertEquals(1, BlinkSkeletonPolicy.placeholderRows(0))
        assertEquals(4, BlinkSkeletonPolicy.placeholderRows(4))
        assertEquals(8, BlinkSkeletonPolicy.placeholderRows(100))
    }
}
