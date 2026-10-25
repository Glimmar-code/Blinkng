package com.blinkng.shared.discovery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BlinkBirthdayPolicyTest {
    private val today = LocalDate.of(2026, 10, 25)

    @Test fun birthdayRequiresARealNonFutureDate() {
        assertTrue(BlinkBirthdayPolicy.isValid("2005-02-28", today))
        assertTrue(BlinkBirthdayPolicy.isValid("2004-02-29", today))
        assertFalse(BlinkBirthdayPolicy.isValid("2005-02-29", today))
        assertFalse(BlinkBirthdayPolicy.isValid("2030-01-01", today))
        assertFalse(BlinkBirthdayPolicy.isValid("1900-01-01", today))
        assertFalse(BlinkBirthdayPolicy.isValid("2005-13-01", today))
        assertFalse(BlinkBirthdayPolicy.isValid("", today))
    }
}
