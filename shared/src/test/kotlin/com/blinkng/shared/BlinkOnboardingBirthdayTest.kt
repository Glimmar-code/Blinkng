package com.blinkng.shared

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlinkOnboardingBirthdayTest {
    private val today = LocalDate.of(2026, 10, 28)

    @Test fun blankBirthdayIsOptional() {
        assertTrue(BlinkOnboardingPolicy.validOptionalBirthday("", today))
        assertTrue(BlinkOnboardingPolicy.validOptionalBirthday("  ", today))
    }

    @Test fun validRealDatesPass() {
        assertTrue(BlinkOnboardingPolicy.validOptionalBirthday("2007-02-28", today))
        assertTrue(BlinkOnboardingPolicy.validOptionalBirthday("2008-02-29", today))
        assertTrue(BlinkOnboardingPolicy.validOptionalBirthday("2026-10-28", today))
    }

    @Test fun impossibleCalendarDatesAreRejected() {
        assertFalse(BlinkOnboardingPolicy.validOptionalBirthday("2007-02-29", today))
        assertFalse(BlinkOnboardingPolicy.validOptionalBirthday("2024-13-20", today))
        assertFalse(BlinkOnboardingPolicy.validOptionalBirthday("2024-04-31", today))
        assertFalse(BlinkOnboardingPolicy.validOptionalBirthday("2024-00-00", today))
    }

    @Test fun futureAndMalformedDatesAreRejected() {
        assertFalse(BlinkOnboardingPolicy.validOptionalBirthday("2099-01-01", today))
        assertFalse(BlinkOnboardingPolicy.validOptionalBirthday("2026-10-29", today))
        assertFalse(BlinkOnboardingPolicy.validOptionalBirthday("2020-2-02", today))
        assertFalse(BlinkOnboardingPolicy.validOptionalBirthday("abc", today))
    }
}
