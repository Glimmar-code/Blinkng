package com.example.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordRecoveryLinkParserTest {
    @Test
    fun parsesCanonicalFragmentTokens() {
        val link = PasswordRecoveryLinkParser.parse(
            "blink://auth/reset-password#access_token=abc123&refresh_token=refresh456&type=recovery"
        )
        assertNotNull(link)
        assertEquals("abc123", link?.accessToken)
        assertEquals("refresh456", link?.refreshToken)
        assertNull(link?.error)
    }

    @Test
    fun keepsLegacyRecoveryLinksCompatible() {
        val link = PasswordRecoveryLinkParser.parse(
            "blink://reset-password#access_token=legacy&refresh_token=old&type=recovery"
        )
        assertEquals("legacy", link?.accessToken)
        assertEquals("old", link?.refreshToken)
    }

    @Test
    fun parsesProviderErrorWithoutRequiringSessionDestruction() {
        val link = PasswordRecoveryLinkParser.parse(
            "blink://auth/reset-password#error=access_denied&error_description=Link%20expired"
        )
        assertEquals("Link expired", link?.error)
    }

    @Test
    fun ignoresNonRecoveryLinks() {
        assertNull(PasswordRecoveryLinkParser.parse("blink://post/123"))
    }
}
