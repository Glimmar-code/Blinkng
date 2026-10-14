package com.example.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowSessionPolicyTest {
    @Test fun acceptsOnlySameAuthenticatedSession() {
        assertTrue(isCurrentFollowSession("account_a", "account_a"))
        assertFalse(isCurrentFollowSession("account_a", "account_b"))
        assertFalse(isCurrentFollowSession("account_a", ""))
        assertFalse(isCurrentFollowSession("", ""))
        assertFalse(isCurrentFollowSession(null, "account_a"))
    }
}
