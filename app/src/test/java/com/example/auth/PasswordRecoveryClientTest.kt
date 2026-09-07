package com.example.auth

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PasswordRecoveryClientTest {
    @Test
    fun parseRecoveryLink_readsSupabaseFragmentSession() {
        val result = PasswordRecoveryClient.parseRecoveryLink(
            Uri.parse(
                "blink://auth/reset-password#access_token=access.jwt&refresh_token=refresh-token&type=recovery"
            )
        )

        assertTrue(result.isSuccess)
        assertEquals("access.jwt", result.getOrThrow().accessToken)
        assertEquals("refresh-token", result.getOrThrow().refreshToken)
    }

    @Test
    fun parseRecoveryLink_rejectsNonRecoveryLinks() {
        val result = PasswordRecoveryClient.parseRecoveryLink(
            Uri.parse("blink://auth/reset-password#access_token=access.jwt&type=signup")
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun parseRecoveryLink_surfacesExpiredLinkError() {
        val result = PasswordRecoveryClient.parseRecoveryLink(
            Uri.parse(
                "blink://auth/reset-password#error=access_denied&error_description=Email+link+is+invalid+or+has+expired"
            )
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("expired", ignoreCase = true))
    }
}
