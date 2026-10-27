package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlinkR2MediaPolicyTest {
    @Test fun publicUploadAllowlistIsBounded() {
        assertTrue(BlinkR2MediaPolicy.mayUploadPublic("feed","image/jpeg",1000))
        assertTrue(BlinkR2MediaPolicy.mayUploadPublic("reel","video/mp4",50_000_000))
        assertFalse(BlinkR2MediaPolicy.mayUploadPublic("chat","image/jpeg",1000))
        assertFalse(BlinkR2MediaPolicy.mayUploadPublic("story","image/jpeg",1000))
        assertFalse(BlinkR2MediaPolicy.mayUploadPublic("feed","application/pdf",1000))
        assertFalse(BlinkR2MediaPolicy.mayUploadPublic("feed","image/jpeg",0))
        assertFalse(BlinkR2MediaPolicy.mayUploadPublic("feed","image/jpeg",25_000_001))
    }

    @Test fun onlyIntentionalDisableAllowsLegacyFallback() {
        assertTrue(BlinkR2MediaPolicy.intentionalFallback(503,"R2_DISABLED"))
        assertTrue(BlinkR2MediaPolicy.intentionalFallback(404,"Function not found"))
        assertFalse(BlinkR2MediaPolicy.intentionalFallback(401,"INVALID_SESSION"))
        assertFalse(BlinkR2MediaPolicy.intentionalFallback(503,"MEDIA_BACKEND_UNAVAILABLE"))
        assertFalse(BlinkR2MediaPolicy.intentionalFallback(400,"INVALID_PURPOSE"))
    }

    @Test fun rejectExternalOrSignedMediaUrls() {
        assertTrue(BlinkR2MediaPolicy.isValidPublicUrl("https://media.blink.com.ng/feed/example.jpg"))
        assertFalse(BlinkR2MediaPolicy.isValidPublicUrl("http://media.blink.com.ng/file.jpg"))
        assertFalse(BlinkR2MediaPolicy.isValidPublicUrl("https://media.blink.com.ng.evil.test/file.jpg"))
        assertFalse(BlinkR2MediaPolicy.isValidPublicUrl("https://media.blink.com.ng/file.jpg?X-Amz-Signature=abc"))
        assertFalse(BlinkR2MediaPolicy.isValidPublicUrl("https://bucket.r2.cloudflarestorage.com/file.jpg"))
    }
}
