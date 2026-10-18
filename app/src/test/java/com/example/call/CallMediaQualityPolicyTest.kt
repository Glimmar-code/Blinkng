package com.example.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallMediaQualityPolicyTest {
    @Test fun `low memory phones always use power saving camera mode`() {
        assertEquals(CallMediaQualityPolicy.LOW,
            CallMediaQualityPolicy.select(true, 512, false))
        assertEquals(CallMediaQualityPolicy.LOW,
            CallMediaQualityPolicy.select(false, 128, false))
        assertEquals(CallMediaQualityPolicy.LOW,
            CallMediaQualityPolicy.select(false, 128, true))
    }

    @Test fun `cellular calls reduce capture size and frame rate`() {
        assertEquals(CallMediaQualityPolicy.BALANCED,
            CallMediaQualityPolicy.select(false, 512, true))
        assertEquals(24, CallMediaQualityPolicy.BALANCED.framesPerSecond)
    }

    @Test fun `mid memory devices use balanced mode even on WiFi`() {
        assertEquals(CallMediaQualityPolicy.BALANCED,
            CallMediaQualityPolicy.select(false, 256, false))
    }

    @Test fun `high memory unmetered devices can use 720p`() {
        assertEquals(CallMediaQualityPolicy.HIGH,
            CallMediaQualityPolicy.select(false, 512, false))
        assertEquals(720, CallMediaQualityPolicy.HIGH.width)
    }

    @Test fun `missing capture source cannot report camera enabled`() {
        assertFalse(CallMediaQualityPolicy.canEnableCamera(false, true))
        assertFalse(CallMediaQualityPolicy.canEnableCamera(true, false))
        assertTrue(CallMediaQualityPolicy.canEnableCamera(true, true))
    }
}
