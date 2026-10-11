package com.example

import com.blinkng.shared.BlinkUiRecovery
import org.junit.Assert.*
import org.junit.Test

class BlinkUiRecoveryTest {
    @Test fun priceRangesHandleBoundariesAndInvalidInput() {
        assertNull(BlinkUiRecovery.priceRangeError("", ""))
        assertNull(BlinkUiRecovery.priceRangeError("0", "0"))
        assertTrue(BlinkUiRecovery.matchesPrice(100, "100", "100"))
        assertFalse(BlinkUiRecovery.matchesPrice(99, "100", ""))
        assertFalse(BlinkUiRecovery.matchesPrice(101, "", "100"))
        assertNotNull(BlinkUiRecovery.priceRangeError("200", "100"))
        assertNotNull(BlinkUiRecovery.priceRangeError("-1", "100"))
        assertNotNull(BlinkUiRecovery.priceRangeError("99999999999999999999", ""))
    }

    @Test fun messageJumpsAccountForHistoryLoadingRowAndMissingTargets() {
        val ids = listOf("first", "starred", "last")
        assertEquals(1, BlinkUiRecovery.messageScrollIndex(ids, "starred", false))
        assertEquals(2, BlinkUiRecovery.messageScrollIndex(ids, "starred", true))
        assertNull(BlinkUiRecovery.messageScrollIndex(ids, "deleted", false))
    }

    @Test fun profileErrorsDoNotExposeServerPayloads() {
        val secret = "token=private details from server"
        val message = BlinkUiRecovery.profileSaveMessage(IllegalStateException(secret))
        assertFalse(message.contains(secret))
        assertTrue(message.contains("try again"))
        assertTrue(BlinkUiRecovery.profileSaveMessage(java.io.IOException(secret)).contains("connection"))
    }
}
