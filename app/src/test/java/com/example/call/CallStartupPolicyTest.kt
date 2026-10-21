package com.example.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallStartupPolicyTest {
    @Test fun sdpDeliveryHasBoundedRetries() {
        assertEquals(3, CallStartupPolicy.MAX_SIGNAL_ATTEMPTS)
        assertEquals(700L, CallStartupPolicy.signalRetryDelayMillis(0))
        assertEquals(1_500L, CallStartupPolicy.signalRetryDelayMillis(1))
        assertEquals(1_500L, CallStartupPolicy.signalRetryDelayMillis(20))
    }

    @Test fun ringingTimeoutDoesNotCutOffAnsweredCalls() {
        val limit = CallStartupPolicy.MAX_RINGING_MILLIS
        assertFalse(CallStartupPolicy.shouldFailUnanswered(CallStatus.RINGING, limit - 1))
        assertTrue(CallStartupPolicy.shouldFailUnanswered(CallStatus.RINGING, limit))
        assertFalse(CallStartupPolicy.shouldFailUnanswered(CallStatus.CONNECTING, limit * 2))
        assertFalse(CallStartupPolicy.shouldFailUnanswered(CallStatus.CONNECTED, limit * 2))
        assertFalse(CallStartupPolicy.shouldFailUnanswered(CallStatus.MISSED, limit * 2))
        assertFalse(CallStartupPolicy.shouldFailUnanswered(null, limit * 2))
    }
}
