package com.example.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Telegram reference: drag the chat to the RIGHT to reveal the inbox. */
class TelegramChatBackGestureTest {
    @Test fun rightSwipeAfterTouchSlopMayDismiss() {
        assertTrue(isTelegramChatBackIntent(72f, 4f, 12f))
        assertTrue(isTelegramChatBackIntent(180f, 5f, 12f))
    }

    @Test fun leftVerticalAndDiagonalSwipesNeverDismissChat() {
        assertFalse(isTelegramChatBackIntent(-180f, 0f, 12f))
        assertFalse(isTelegramChatBackIntent(7f, 0f, 12f))
        assertFalse(isTelegramChatBackIntent(6f, 200f, 12f))
        assertFalse(isTelegramChatBackIntent(40f, 50f, 12f))
    }

    @Test fun invalidCoordinatesCannotNavigate() {
        assertFalse(isTelegramChatBackIntent(Float.NaN, 0f, 12f))
        assertFalse(isTelegramChatBackIntent(Float.POSITIVE_INFINITY, 0f, 12f))
        assertFalse(isTelegramChatBackIntent(130f, 0f, Float.NaN))
        assertFalse(isTelegramChatBackIntent(130f, 0f, -1f))
    }
}
