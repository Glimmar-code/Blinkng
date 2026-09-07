package com.example.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthErrorMapperTest {
    @Test
    fun mapsInvalidCredentials() {
        assertEquals(
            "The email/username or password is incorrect.",
            AuthErrorMapper.friendly("Invalid login credentials")
        )
    }

    @Test
    fun mapsRateLimit() {
        assertEquals(
            "Too many attempts. Please wait a little and try again.",
            AuthErrorMapper.friendly("Too many requests")
        )
    }

    @Test
    fun hidesUnknownProviderDetails() {
        assertEquals(
            "Unable to sign in.",
            AuthErrorMapper.friendly("internal provider implementation detail", "Unable to sign in.")
        )
    }
}
