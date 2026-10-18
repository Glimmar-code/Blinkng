package com.example.shared

import com.blinkng.shared.discovery.BlinkProfileVisitorBadgePolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class BlinkProfileVisitorBadgePolicyTest {
    @Test fun uniqueVisibleVisitorsSinceReceiptOnly() {
        val visits = listOf(
            BlinkProfileVisitorBadgePolicy.Visit("a", 150),
            BlinkProfileVisitorBadgePolicy.Visit("a", 180),
            BlinkProfileVisitorBadgePolicy.Visit("b", 200),
            BlinkProfileVisitorBadgePolicy.Visit("c", 220, visible = false),
            BlinkProfileVisitorBadgePolicy.Visit("me", 300),
            BlinkProfileVisitorBadgePolicy.Visit("d", 90),
        )
        assertEquals(2, BlinkProfileVisitorBadgePolicy.unreadCount(visits, 100, "me"))
    }

    @Test fun openingHistoryNeverClearsNewerVisits() {
        val seenThrough = BlinkProfileVisitorBadgePolicy.acknowledgedThrough(100, 200)
        assertEquals(1, BlinkProfileVisitorBadgePolicy.unreadCount(
            listOf(BlinkProfileVisitorBadgePolicy.Visit("a", 250)), seenThrough, "me",
        ))
        assertEquals(200, BlinkProfileVisitorBadgePolicy.acknowledgedThrough(200, 150))
    }
}
