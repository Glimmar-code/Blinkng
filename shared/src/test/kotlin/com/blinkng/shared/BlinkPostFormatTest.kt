package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class BlinkPostFormatTest {
    @Test
    fun agesFitTheIdentityRail() {
        assertEquals("18h", BlinkPostFormat.age("18 hours ago"))
        assertEquals("2d", BlinkPostFormat.age("2d"))
        assertEquals("3mo", BlinkPostFormat.age("3 months ago"))
        assertEquals("now", BlinkPostFormat.age("just now"))
        assertEquals("", BlinkPostFormat.age("  "))
    }

    @Test
    fun countsAreSafeAndCompact() {
        assertEquals("0", BlinkPostFormat.count(-5))
        assertEquals("999", BlinkPostFormat.count(999))
        assertEquals("1.2K", BlinkPostFormat.count(1_200))
        assertEquals("12K", BlinkPostFormat.count(12_499))
        assertEquals("1.2M", BlinkPostFormat.count(1_200_000))
    }
}
