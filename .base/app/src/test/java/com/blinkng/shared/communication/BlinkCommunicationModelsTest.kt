package com.blinkng.shared.communication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkCommunicationModelsTest {
    @Test
    fun retryBackoffIsBounded() {
        assertEquals(0L, CommunicationRetryPolicy.delayMillis(0))
        assertEquals(500L, CommunicationRetryPolicy.delayMillis(1))
        assertTrue(CommunicationRetryPolicy.delayMillis(20) <= 60_000L)
        assertTrue(CommunicationRetryPolicy.shouldDeadLetter(CommunicationRetryPolicy.maxMessageAttempts))
    }

    @Test
    fun executableAttachmentsAreRejected() {
        val unsafe = AttachmentDescriptor(
            kind = MessageAttachmentKind.DOCUMENT,
            displayName = "setup.exe",
            mimeType = "application/octet-stream",
            byteSize = 100,
        )
        assertFalse(AttachmentPolicy.validate(unsafe).accepted)

        val safe = unsafe.copy(displayName = "lecture.pdf", mimeType = "application/pdf")
        assertTrue(AttachmentPolicy.validate(safe).accepted)
    }

    @Test
    fun linksRequireSupportedSchemes() {
        assertEquals(LinkRisk.SAFE_HTTPS, LinkSafety.classify("https://example.com/path"))
        assertEquals(LinkRisk.INSECURE_HTTP, LinkSafety.classify("http://example.com"))
        assertEquals(LinkRisk.UNSUPPORTED_SCHEME, LinkSafety.classify("javascript:alert(1)"))
        assertEquals(LinkRisk.SUSPICIOUS_HOST, LinkSafety.classify("https://xn--example.test"))
    }

    @Test
    fun poorNetworkScoresBelowHealthyNetwork() {
        val healthy = CallQualitySnapshot(
            connectionState = "connected",
            networkType = "wifi",
            roundTripMs = 80.0,
            jitterMs = 3.0,
            packetsLost = 1,
            packetsReceived = 999,
        )
        val poor = healthy.copy(
            roundTripMs = 900.0,
            jitterMs = 90.0,
            packetsLost = 300,
            packetsReceived = 700,
        )
        assertTrue(healthy.qualityScore > poor.qualityScore)
        assertTrue(healthy.qualityScore in 0.0..100.0)
        assertTrue(poor.qualityScore in 0.0..100.0)
    }

    @Test
    fun usernamesAndMessagesNormalizeConsistentlyAcrossPlatforms() {
        assertEquals("glimmar", MessageTextRules.normalizeUsername("  @Glimmar "))
        assertEquals("hello", MessageTextRules.normalizeMessage("  hello  "))
        assertTrue(MessageTextRules.isSendable("hello"))
        assertFalse(MessageTextRules.isSendable("   "))
    }
}
