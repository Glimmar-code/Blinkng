package com.example.data.supabase

import com.blinkng.shared.BlinkR2MediaPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkR2MediaPolicyAndroidTest {
    @Test fun validatesPublicUploadsAndBlocksPrivateChat() {
        assertTrue(BlinkR2MediaPolicy.mayUploadPublic("feed","image/jpeg",1024))
        assertTrue(BlinkR2MediaPolicy.mayUploadPublic("reel","video/mp4",100000))
        assertFalse(BlinkR2MediaPolicy.mayUploadPublic("chat","image/jpeg",1024))
        assertFalse(BlinkR2MediaPolicy.mayUploadPublic("story","image/jpeg",1024))
        assertFalse(BlinkR2MediaPolicy.mayUploadPublic("feed","image/jpeg",25000001))
    }
    @Test fun onlyIntentionalDisableFallsBackToSupabase() {
        assertTrue(BlinkR2MediaPolicy.intentionalFallback(503,"R2_DISABLED"))
        assertTrue(BlinkR2MediaPolicy.intentionalFallback(404,"R2_FUNCTION_NOT_DEPLOYED"))
        assertFalse(BlinkR2MediaPolicy.intentionalFallback(401,"INVALID_SESSION"))
        assertFalse(BlinkR2MediaPolicy.intentionalFallback(503,"MEDIA_BACKEND_UNAVAILABLE"))
    }
    @Test fun rejectsExternalMediaAddresses() {
        assertTrue(BlinkR2MediaPolicy.isValidPublicUrl("https://media.blink.com.ng/feed/photo.jpg"))
        assertFalse(BlinkR2MediaPolicy.isValidPublicUrl("https://media.blink.com.ng.bad.example/photo.jpg"))
        assertFalse(BlinkR2MediaPolicy.isValidPublicUrl("http://media.blink.com.ng/photo.jpg"))
        assertFalse(BlinkR2MediaPolicy.isValidPublicUrl("https://media.blink.com.ng/p.jpg?token=secret"))
    }
}
