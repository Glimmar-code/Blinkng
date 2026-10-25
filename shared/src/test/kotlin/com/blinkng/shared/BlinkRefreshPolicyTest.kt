package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlinkRefreshPolicyTest {
    @Test fun firstVisitAndExpiredContentRefresh() {
        assertTrue(BlinkRefreshPolicy.isStale(0L, 20_000L))
        assertFalse(BlinkRefreshPolicy.isStale(1_000L, 180_999L))
        assertTrue(BlinkRefreshPolicy.isStale(1_000L, 181_000L))
    }

    @Test fun rapidResumesAndClockResetsDoNotSpamOrFreeze() {
        assertFalse(BlinkRefreshPolicy.isStale(9_900L, 10_000L))
        assertTrue(BlinkRefreshPolicy.isStale(15_000L, 1_000L))
        assertTrue(BlinkRefreshPolicy.isStale(50L, 50L, 0L))
    }

    @Test fun callerCanUseDifferentScopeAges() {
        assertTrue(BlinkRefreshPolicy.isStale(1_000L, 61_000L, 60_000L))
        assertFalse(BlinkRefreshPolicy.isStale(1_000L, 61_000L, 120_000L))
    }
}
