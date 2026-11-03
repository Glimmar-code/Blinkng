package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlinkDeliveryReceiptTest {
    @Test fun receiptsOnlyMoveForward() {
        assertEquals("READ", BlinkDeliveryReceipt.strongest("READ", "SENT"))
        assertEquals("DELIVERED", BlinkDeliveryReceipt.strongest("SENT", "DELIVERED"))
        assertEquals("READ", BlinkDeliveryReceipt.strongest("DELIVERED", "READ"))
        assertEquals("SENT", BlinkDeliveryReceipt.strongest("SENT", "SENDING"))
    }

    @Test fun oneTwoAndBlueTicksHaveDistinctMeaning() {
        assertEquals("◷", BlinkDeliveryReceipt.ticks("SENDING"))
        assertEquals("✓", BlinkDeliveryReceipt.ticks("SENT"))
        assertEquals("✓✓", BlinkDeliveryReceipt.ticks("DELIVERED"))
        assertEquals("✓✓", BlinkDeliveryReceipt.ticks("READ"))
        assertTrue(BlinkDeliveryReceipt.isRead("READ"))
        assertFalse(BlinkDeliveryReceipt.isRead("DELIVERED"))
        assertFalse(BlinkDeliveryReceipt.isRead("SENT"))
    }

    @Test fun unknownStatusesCannotClaimDelivery() {
        assertEquals("", BlinkDeliveryReceipt.ticks("online"))
        assertEquals(-1, BlinkDeliveryReceipt.rank("connected"))
        assertEquals("SENT", BlinkDeliveryReceipt.strongest("SENT", "online"))
    }
}
