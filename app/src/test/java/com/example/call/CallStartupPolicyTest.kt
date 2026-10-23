package com.example.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallStartupPolicyTest {
    @Test fun unansweredCallsExpireButAnsweredCallsDoNot() {
        assertTrue(CallStartupPolicy.unansweredCallTimedOut(CallStatus.RINGING, true, 75_000L))
        assertFalse(CallStartupPolicy.unansweredCallTimedOut(CallStatus.RINGING, true, 74_999L))
        assertFalse(CallStartupPolicy.unansweredCallTimedOut(CallStatus.RINGING, false, 90_000L))
        assertFalse(CallStartupPolicy.unansweredCallTimedOut(CallStatus.CONNECTED, true, 90_000L))
    }

    @Test fun signalingRetriesAreBounded() {
        assertEquals(3, CallStartupPolicy.MAX_SDP_ATTEMPTS)
        assertEquals(700L, CallStartupPolicy.sdpRetryDelayMs(0))
        assertEquals(1_400L, CallStartupPolicy.sdpRetryDelayMs(1))
    }
}
