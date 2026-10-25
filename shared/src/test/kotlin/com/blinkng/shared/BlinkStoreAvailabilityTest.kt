package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertTrue

class BlinkStoreAvailabilityTest {
    @Test fun storeCatalogIsIntentionallyPausedForEveryClient() {
        assertTrue(BlinkStoreAvailability.isPaused)
        assertTrue(BlinkStoreAvailability.message.contains("temporarily paused"))
    }
}
