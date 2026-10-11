package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleBackExitGateTest {
    @Test fun firstPressNeverExits() {
        val gate = DoubleBackExitGate()
        assertFalse(gate.shouldExit(1000L))
    }

    @Test fun quickSecondPressExits() {
        val gate = DoubleBackExitGate()
        assertFalse(gate.shouldExit(1000L))
        assertTrue(gate.shouldExit(2500L))
        assertFalse(gate.shouldExit(2600L))
    }

    @Test fun stalePressRequiresNewConfirmation() {
        val gate = DoubleBackExitGate()
        assertFalse(gate.shouldExit(1000L))
        assertFalse(gate.shouldExit(4001L))
        assertTrue(gate.shouldExit(4500L))
    }

    @Test fun clockResetAndNavigationNeverExitUnexpectedly() {
        val gate = DoubleBackExitGate()
        assertFalse(gate.shouldExit(5000L))
        assertFalse(gate.shouldExit(1000L))
        gate.reset()
        assertFalse(gate.shouldExit(1200L))
    }
}
