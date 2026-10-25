package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkCommunityPolicyTest {
    @Test fun validatesCommunityInputs() {
        assertEquals("Reading Club", BlinkCommunityPolicy.validatedName(" Reading Club "))
        assertEquals(BlinkCommunityPolicy.Visibility.PRIVATE,
            BlinkCommunityPolicy.Visibility.fromWire("private"))
        assertEquals(listOf("Be kind"),
            BlinkCommunityPolicy.validatedRules(listOf("Be kind", "Be kind", "")))
        assertTrue(runCatching {
            BlinkCommunityPolicy.validatedName("x")
        }.exceptionOrNull() is IllegalArgumentException)
    }
}
