package com.example.call

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLifecyclePolicyTest {
    @Test
    fun ringingCanAdvanceOrTerminate() {
        assertTrue(CallLifecyclePolicy.canApply(CallStatus.RINGING, CallStatus.CONNECTING))
        assertTrue(CallLifecyclePolicy.canApply(CallStatus.RINGING, CallStatus.CONNECTED))
        assertTrue(CallLifecyclePolicy.canApply(CallStatus.RINGING, CallStatus.DECLINED))
        assertTrue(CallLifecyclePolicy.canApply(CallStatus.RINGING, CallStatus.MISSED))
        assertTrue(CallLifecyclePolicy.canApply(CallStatus.RINGING, CallStatus.CANCELLED))
    }

    @Test
    fun connectingCannotRegressToRinging() {
        assertFalse(CallLifecyclePolicy.canApply(CallStatus.CONNECTING, CallStatus.RINGING))
        assertTrue(CallLifecyclePolicy.canApply(CallStatus.CONNECTING, CallStatus.CONNECTED))
        assertTrue(CallLifecyclePolicy.canApply(CallStatus.CONNECTING, CallStatus.FAILED))
    }

    @Test
    fun connectedCannotRegress() {
        assertFalse(CallLifecyclePolicy.canApply(CallStatus.CONNECTED, CallStatus.RINGING))
        assertFalse(CallLifecyclePolicy.canApply(CallStatus.CONNECTED, CallStatus.CONNECTING))
        assertTrue(CallLifecyclePolicy.canApply(CallStatus.CONNECTED, CallStatus.ENDED))
    }

    @Test
    fun terminalStatesAreSticky() {
        for (terminal in CallStatus.entries.filter { it.isTerminal }) {
            assertTrue(CallLifecyclePolicy.canApply(terminal, terminal))
            assertFalse(CallLifecyclePolicy.canApply(terminal, CallStatus.RINGING))
            assertFalse(CallLifecyclePolicy.canApply(terminal, CallStatus.CONNECTING))
            assertFalse(CallLifecyclePolicy.canApply(terminal, CallStatus.CONNECTED))
        }
    }
}
