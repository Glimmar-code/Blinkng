package com.example.ui.screens

import com.blinkng.shared.BlinkActivityPulsePolicy
import com.blinkng.shared.communityActivityRange
import com.blinkng.shared.nextPulseValue
import com.blinkng.shared.rankPulseRange
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityPulseTest {
    private val policy = BlinkActivityPulsePolicy()

    @Test
    fun communityActivity_usesThirtyToFiftyPerRealOnlineUser() {
        assertEquals(30..50, communityActivityRange(0, policy))
        assertEquals(30..50, communityActivityRange(1, policy))
        assertEquals(60..100, communityActivityRange(2, policy))
        assertEquals(90..150, communityActivityRange(3, policy))
    }

    @Test
    fun rankPulse_usesTenToFifteenPerRankUpAndKeepsBaseline() {
        assertEquals(10..15, rankPulseRange(0, policy))
        assertEquals(10..15, rankPulseRange(1, policy))
        assertEquals(20..30, rankPulseRange(2, policy))
        assertEquals(30..45, rankPulseRange(3, policy))
    }

    @Test
    fun nextPulseValue_neverLeavesItsBandAndMovesTowardNewBandGradually() {
        assertEquals(30, nextPulseValue(31, 30..50, -4))
        assertEquals(50, nextPulseValue(49, 30..50, 4))
        assertEquals(34, nextPulseValue(32, 30..50, 2))
        assertEquals(60, nextPulseValue(40, 60..100, 4, maxStep = 4, transitionStepMultiplier = 5))
    }
    @Test
    fun nextPulseValue_handlesIntegerBoundaryWithoutOverflow() {
        assertEquals(
            Int.MAX_VALUE,
            nextPulseValue(
                current = Int.MAX_VALUE - 1,
                range = (Int.MAX_VALUE - 10)..Int.MAX_VALUE,
                requestedStep = 4,
            )
        )
        assertEquals(
            Int.MAX_VALUE - 20,
            nextPulseValue(
                current = Int.MAX_VALUE,
                range = (Int.MAX_VALUE - 100)..(Int.MAX_VALUE - 50),
                requestedStep = -4,
                maxStep = 4,
                transitionStepMultiplier = 5,
            )
        )
    }

}
