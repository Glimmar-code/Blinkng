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
    fun mapsAlternateInvalidPasswordErrorsWithoutRevealingAccountExistence() {
        listOf("Invalid credentials", "Wrong password", "Incorrect password",
            "Username was not found in Supabase.", "User not found").forEach { raw ->
            assertEquals(
                "The email/username or password is incorrect.",
                AuthErrorMapper.friendly(raw, "Unable to sign in.")
            )
        }
    }

    @Test
    fun serverFailuresDoNotPretendThePasswordIsWrong() {
        assertEquals("Unable to sign in.",
            AuthErrorMapper.friendly("Authentication service temporarily unavailable.", "Unable to sign in."))
        assertEquals("Blink could not reach the server. Check your connection and try again.",
            AuthErrorMapper.friendly("Connection timed out", "Unable to sign in."))
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
