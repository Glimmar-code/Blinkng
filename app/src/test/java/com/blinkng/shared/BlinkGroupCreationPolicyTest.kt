package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BlinkGroupCreationPolicyTest {
    private val owner = "00000000-0000-4000-8000-000000000001"
    private val a = "00000000-0000-4000-8000-000000000002"
    private val b = "00000000-0000-4000-8000-000000000003"

    @Test fun creatorCannotCountTowardTwoInvitedMembers() {
        assertEquals(listOf(a, b), BlinkGroupCreationPolicy.normalizedMembers(owner, listOf(a, b, owner, a)))
        assertThrows(IllegalArgumentException::class.java) {
            BlinkGroupCreationPolicy.normalizedMembers(owner, listOf(a))
        }
    }
    @Test fun titleIsBoundedAndNormalized() {
        assertEquals("Study Group", BlinkGroupCreationPolicy.normalizedTitle("  Study Group  "))
        assertThrows(IllegalArgumentException::class.java) {
            BlinkGroupCreationPolicy.normalizedTitle(" ")
        }
    }
}
