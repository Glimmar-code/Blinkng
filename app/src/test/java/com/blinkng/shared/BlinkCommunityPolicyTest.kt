package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BlinkCommunityPolicyTest {
    @Test fun validatesCommunityInputs() {
        assertEquals("Reading Club", BlinkCommunityPolicy.validatedName(" Reading Club "))
        assertEquals(BlinkCommunityPolicy.Visibility.PRIVATE,
            BlinkCommunityPolicy.Visibility.fromWire("private"))
        assertEquals(listOf("Be kind"),
            BlinkCommunityPolicy.validatedRules(listOf("Be kind", "Be kind", "")))
        assertThrows(IllegalArgumentException::class.java) {
            BlinkCommunityPolicy.validatedName("x")
        }
    }
}
