package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlinkPhonePolicyTest {
    @Test fun validNigeriaNumber() {
        assertEquals("+2348012345678", BlinkPhonePolicy.normalize("0801 234 5678"))
        assertEquals("+2348012345678", BlinkPhonePolicy.normalize("+2348012345678"))
        assertNull(BlinkPhonePolicy.normalize("123"))
        assertTrue(BlinkPhonePolicy.otpIsValid("123456"))
    }
}
