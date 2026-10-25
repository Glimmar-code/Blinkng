package com.example.shared

import com.blinkng.shared.discovery.BlinkDiscoveryPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlinkDiscoveryPolicyTest {
    @Test fun firstRunWelcomeRespectsDelay() {
        assertNull(BlinkDiscoveryPolicy.next(emptySet(), 1_999, true, 0, Long.MAX_VALUE))
        assertEquals("welcome", BlinkDiscoveryPolicy.next(emptySet(), 2_000, true, 0, Long.MAX_VALUE)?.id)
    }

    @Test fun knownAndSkippedStepsDoNotRepeat() {
        val completed = setOf("welcome", "reels")
        assertEquals("shake_report", BlinkDiscoveryPolicy.next(completed, 45_000, true, 0, Long.MAX_VALUE)?.id)
        assertNull(BlinkDiscoveryPolicy.next(BlinkDiscoveryPolicy.allIds(), 1_000_000, true, 0, Long.MAX_VALUE))
    }

    @Test fun neverInterruptBusyScreensOrExceedSessionCap() {
        assertNull(BlinkDiscoveryPolicy.next(emptySet(), 90_000, false, 0, Long.MAX_VALUE))
        assertNull(BlinkDiscoveryPolicy.next(emptySet(), 90_000, true, BlinkDiscoveryPolicy.MAX_PER_SESSION, Long.MAX_VALUE))
        assertNull(BlinkDiscoveryPolicy.next(setOf("welcome"), 30_000, true, 1, 1_000))
        assertEquals("reels", BlinkDiscoveryPolicy.next(setOf("welcome"), 30_000, true, 1, 12_000)?.id)
    }

    @Test fun routesReferToRealSharedFeatureIds() {
        val valid = com.blinkng.shared.BlinkFeatureId.entries.map { it.routeId }.toSet()
        assertEquals(true, BlinkDiscoveryPolicy.steps.all { it.routeId == null || it.routeId in valid })
    }
}
