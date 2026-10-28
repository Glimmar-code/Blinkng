package com.blinkng.shared

import java.time.LocalDate
import com.blinkng.shared.discovery.BlinkBirthdayPolicy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlinkOnboardingBirthdayTest {
    private val today = LocalDate.of(2026, 10, 28)

    @Test fun birthdayIsRequired() {
        assertFalse(BlinkBirthdayPolicy.isValid("", today))
        assertFalse(BlinkBirthdayPolicy.isValid("  ", today))
    }

    @Test fun validRealDatesPass() {
        assertTrue(BlinkBirthdayPolicy.isValid("2007-02-28", today))
        assertTrue(BlinkBirthdayPolicy.isValid("2008-02-29", today))
        assertTrue(BlinkBirthdayPolicy.isValid("2026-10-28", today))
    }

    @Test fun impossibleCalendarDatesAreRejected() {
        assertFalse(BlinkBirthdayPolicy.isValid("2007-02-29", today))
        assertFalse(BlinkBirthdayPolicy.isValid("2024-13-20", today))
        assertFalse(BlinkBirthdayPolicy.isValid("2024-04-31", today))
        assertFalse(BlinkBirthdayPolicy.isValid("2024-00-00", today))
    }

    @Test fun futureAndMalformedDatesAreRejected() {
        assertFalse(BlinkBirthdayPolicy.isValid("2099-01-01", today))
        assertFalse(BlinkBirthdayPolicy.isValid("2026-10-29", today))
        assertFalse(BlinkBirthdayPolicy.isValid("2020-2-02", today))
        assertFalse(BlinkBirthdayPolicy.isValid("abc", today))
        assertFalse(BlinkBirthdayPolicy.isValid("1850-01-01", today))
    }
}
