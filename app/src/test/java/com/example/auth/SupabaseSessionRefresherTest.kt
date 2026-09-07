package com.example.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseSessionRefresherTest {
    @Test
    fun invalidRefreshToken_isClassifiedAsExpired() {
        assertTrue(
            SupabaseSessionRefresher.isExpiredRefreshResponse(
                400,
                "{\"code\":\"refresh_token_not_found\",\"message\":\"Invalid Refresh Token\"}"
            )
        )
    }

    @Test
    fun serverFailure_isNotClassifiedAsExpired() {
        assertFalse(
            SupabaseSessionRefresher.isExpiredRefreshResponse(
                503,
                "service unavailable"
            )
        )
    }
}
