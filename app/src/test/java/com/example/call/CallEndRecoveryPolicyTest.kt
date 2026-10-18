package com.example.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallEndRecoveryPolicyTest {
    @Test fun `retry only transient failures and never loop forever`() {
        assertTrue(CallEndRecoveryPolicy.shouldRetry(0, null))
        assertTrue(CallEndRecoveryPolicy.shouldRetry(2, 503))
        assertTrue(CallEndRecoveryPolicy.shouldRetry(1, 429))
        assertFalse(CallEndRecoveryPolicy.shouldRetry(0, 401))
        assertFalse(CallEndRecoveryPolicy.shouldRetry(0, 403))
        assertFalse(CallEndRecoveryPolicy.shouldRetry(0, 404))
        assertFalse(CallEndRecoveryPolicy.shouldRetry(0, 409))
        assertFalse(CallEndRecoveryPolicy.shouldRetry(6, 503))
    }

    @Test fun `decline applies only while call is ringing`() {
        val action = CallEndRecoveryPolicy.Action.DECLINE
        assertTrue(CallEndRecoveryPolicy.canApply(action, CallStatus.RINGING))
        assertFalse(CallEndRecoveryPolicy.canApply(action, CallStatus.CONNECTING))
        assertFalse(CallEndRecoveryPolicy.canApply(action, CallStatus.ENDED))
        assertFalse(CallEndRecoveryPolicy.canApply(action, CallStatus.MISSED))
    }

    @Test fun `hangup never changes already-ended calls`() {
        assertTrue(CallEndRecoveryPolicy.canApply(
            CallEndRecoveryPolicy.Action.END, CallStatus.CONNECTED))
        assertTrue(CallEndRecoveryPolicy.canApply(
            CallEndRecoveryPolicy.Action.FAIL, CallStatus.CONNECTING))
        assertFalse(CallEndRecoveryPolicy.canApply(
            CallEndRecoveryPolicy.Action.END, CallStatus.DECLINED))
    }

    @Test fun `unknown action is rejected`() {
        assertEquals(CallEndRecoveryPolicy.Action.END,
            CallEndRecoveryPolicy.Action.fromWire("end"))
        assertNull(CallEndRecoveryPolicy.Action.fromWire("ignore-auth"))
    }
}
