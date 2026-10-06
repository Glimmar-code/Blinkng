package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlinkDropsPolicyTest {
    @Test
    fun rewardsUseOneHundredCoinSteps() {
        assertTrue(BlinkDropsPolicy.isRewardValid(100))
        assertTrue(BlinkDropsPolicy.isRewardValid(1_000))
        assertFalse(BlinkDropsPolicy.isRewardValid(50))
        assertFalse(BlinkDropsPolicy.isRewardValid(150))
        assertFalse(BlinkDropsPolicy.isRewardValid(0))
    }

    @Test
    fun totalBudgetUsesRewardTimesRecipients() {
        assertEquals(1_000L, BlinkDropsPolicy.totalBudget(200, 5))
        assertEquals(100L, BlinkDropsPolicy.totalBudget(100, 1))
    }

    @Test
    fun invalidRecipientCountsAreRejected() {
        assertFalse(BlinkDropsPolicy.isWinnerCountValid(0))
        assertTrue(BlinkDropsPolicy.isWinnerCountValid(500))
        assertFalse(BlinkDropsPolicy.isWinnerCountValid(501))
        assertFailsWith<IllegalArgumentException> {
            BlinkDropsPolicy.totalBudget(100, 0)
        }
    }

    @Test
    fun supportedActionsMatchExistingBlinkSurfaces() {
        assertEquals(
            setOf(BlinkDropAction.LIKE, BlinkDropAction.COMMENT, BlinkDropAction.REPOST),
            BlinkDropsPolicy.actionsFor(BlinkDropTargetType.POST)
        )
        assertEquals(
            setOf(BlinkDropAction.LIKE, BlinkDropAction.COMMENT, BlinkDropAction.REPOST),
            BlinkDropsPolicy.actionsFor(BlinkDropTargetType.REEL)
        )
        assertEquals(
            setOf(BlinkDropAction.SAVE_LISTING),
            BlinkDropsPolicy.actionsFor(BlinkDropTargetType.LISTING)
        )
    }

    @Test
    fun onlyApprovedDurationsAreAcceptedBySharedContract() {
        assertEquals(setOf(1, 6, 12, 24, 72, 168), BlinkDropsPolicy.allowedDurationHours)
    }
}
