package com.example

import com.blinkng.shared.BlinkLifePlace
import com.blinkng.shared.BlinkLifeRules
import com.blinkng.shared.BlinkLifeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkLifeRulesTest {
    @Test
    fun movementIsOneTileAtATimeAndNeverLeavesMap() {
        val start = BlinkLifeState()
        assertEquals(start, BlinkLifeRules.move(start, 2, 0))
        assertEquals(start, BlinkLifeRules.move(start, 1, 1))
        assertEquals(start.copy(x = 4), BlinkLifeRules.move(start, 1, 0))
        assertEquals(BlinkLifeState(x = 0, y = 0), BlinkLifeRules.move(BlinkLifeState(x = 0, y = 0), -1, 0))
        assertEquals(start, BlinkLifeRules.move(start, Int.MIN_VALUE, 0))
    }

    @Test
    fun noPayOutAwayFromARealWorkplace() {
        val original = BlinkLifeState()
        val attempt = BlinkLifeRules.answer(original, 1)
        assertFalse(attempt.succeeded)
        assertEquals(original, attempt.state)
    }

    @Test
    fun jobOnlyPaysAfterCorrectAnswer() {
        val cashier = BlinkLifeState(x = BlinkLifePlace.CAFE.x, y = BlinkLifePlace.CAFE.y)
        val wrong = BlinkLifeRules.answer(cashier, 2)
        assertFalse(wrong.succeeded)
        assertEquals(cashier, wrong.state)

        val earned = BlinkLifeRules.answer(cashier, 1)
        assertTrue(earned.succeeded)
        assertEquals(620, earned.state.gameCash)
        assertEquals(1, earned.state.completedJobs)
    }

    @Test
    fun invalidAnswerCannotPay() {
        val cashier = BlinkLifeState(x = BlinkLifePlace.CAFE.x, y = BlinkLifePlace.CAFE.y)
        assertFalse(BlinkLifeRules.answer(cashier, -1).succeeded)
        assertFalse(BlinkLifeRules.answer(cashier, 999).succeeded)
        assertEquals(cashier, BlinkLifeRules.answer(cashier, -1).state)
    }

    @Test
    fun marketPurchaseCannotBeRepeatedOrOverdrawWallet() {
        val market = BlinkLifeState(x = BlinkLifePlace.MARKET.x, y = BlinkLifePlace.MARKET.y)
        val purchased = BlinkLifeRules.buyDeskPlant(market)
        assertTrue(purchased.succeeded)
        assertEquals(250, purchased.state.gameCash)
        assertTrue(purchased.state.ownsDeskPlant)

        val repeated = BlinkLifeRules.buyDeskPlant(purchased.state)
        assertFalse(repeated.succeeded)
        assertEquals(purchased.state, repeated.state)

        val broke = market.copy(gameCash = 10)
        assertFalse(BlinkLifeRules.buyDeskPlant(broke).succeeded)
        assertEquals(broke, BlinkLifeRules.buyDeskPlant(broke).state)

        val outsideMarket = BlinkLifeState()
        assertFalse(BlinkLifeRules.buyDeskPlant(outsideMarket).succeeded)
    }
}
