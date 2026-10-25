package com.example.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallConnectionRecoveryPolicyTest {
    @Test
    fun `ICE retries are bounded and allow time to reconnect`() {
        val delays = (0 until CallConnectionRecoveryPolicy.ICE_RESTART_ATTEMPTS)
            .map(CallConnectionRecoveryPolicy::iceRetryDelayMillis)

        assertEquals(listOf(1_500L, 4_000L, 7_000L), delays)
        assertTrue(delays.sum() + CallConnectionRecoveryPolicy.FINAL_RECOVERY_GRACE_MS < 35_000L)
    }

    @Test
    fun `SDP writes retry with short increasing backoff`() {
        assertEquals(3, CallConnectionRecoveryPolicy.SDP_SEND_ATTEMPTS)
        assertEquals(350L, CallConnectionRecoveryPolicy.sdpRetryDelayMillis(0))
        assertEquals(700L, CallConnectionRecoveryPolicy.sdpRetryDelayMillis(1))
    }

    @Test
    fun `an unanswered caller eventually exits Calling without interrupting answered calls`() {
        val limit = CallConnectionRecoveryPolicy.MAX_UNANSWERED_RINGING_MS
        assertTrue(CallConnectionRecoveryPolicy.unansweredCallTimedOut(CallStatus.RINGING, limit))
        org.junit.Assert.assertFalse(
            CallConnectionRecoveryPolicy.unansweredCallTimedOut(CallStatus.RINGING, limit - 1)
        )
        org.junit.Assert.assertFalse(
            CallConnectionRecoveryPolicy.unansweredCallTimedOut(CallStatus.CONNECTED, limit * 2)
        )
    }

    @Test
    fun `realtime reconnect delay increases but stays capped`() {
        assertEquals(1_500L, CallConnectionRecoveryPolicy.realtimeReconnectDelayMillis(1))
        assertEquals(3_000L, CallConnectionRecoveryPolicy.realtimeReconnectDelayMillis(2))
        assertEquals(6_000L, CallConnectionRecoveryPolicy.realtimeReconnectDelayMillis(3))
        assertEquals(12_000L, CallConnectionRecoveryPolicy.realtimeReconnectDelayMillis(20))
    }
}
