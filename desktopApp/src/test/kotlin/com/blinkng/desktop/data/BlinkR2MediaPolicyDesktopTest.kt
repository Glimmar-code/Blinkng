package com.blinkng.desktop.data

import com.blinkng.shared.BlinkR2MediaPolicy
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BlinkR2MediaPolicyDesktopTest {
    @Test fun sharedPublicMediaRules() {
        assertTrue(BlinkR2MediaPolicy.mayUploadPublic("feed","image/png",1024))
        assertTrue(BlinkR2MediaPolicy.mayUploadPublic("profile","image/webp",4000))
        assertFalse(BlinkR2MediaPolicy.mayUploadPublic("market","application/pdf",100))
        assertFalse(BlinkR2MediaPolicy.mayUploadPublic("feed","video/mp4",100))
        assertFalse(BlinkR2MediaPolicy.mayUploadPublic("feed","image/png",0))
    }
    @Test fun rejectsUnsafeFallbackAndPublicUrl() {
        assertTrue(BlinkR2MediaPolicy.intentionalFallback(503,"R2_DISABLED"))
        assertFalse(BlinkR2MediaPolicy.intentionalFallback(403,"MEDIA_SCOPE_FORBIDDEN"))
        assertTrue(BlinkR2MediaPolicy.isValidPublicUrl("https://media.blink.com.ng/profile/avatar.png"))
        assertFalse(BlinkR2MediaPolicy.isValidPublicUrl("https://evil.example/media.png"))
    }
}
