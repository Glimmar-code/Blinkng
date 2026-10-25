package com.example.update

import com.blinkng.shared.update.BlinkReleaseAlertPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkReleaseAlertPolicyTest {
    @Test fun onlyNewerVersionsNotify() {
        assertTrue(BlinkReleaseAlertPolicy.shouldNotify(100, 101, 0))
        assertFalse(BlinkReleaseAlertPolicy.shouldNotify(100, 100, 0))
        assertFalse(BlinkReleaseAlertPolicy.shouldNotify(101, 100, 0))
        assertFalse(BlinkReleaseAlertPolicy.shouldNotify(100, -1, 0))
    }
    @Test fun duplicatePushNeverReNotifies() {
        assertTrue(BlinkReleaseAlertPolicy.shouldNotify(100, 150, 149))
        assertFalse(BlinkReleaseAlertPolicy.shouldNotify(100, 150, 150))
        assertFalse(BlinkReleaseAlertPolicy.shouldNotify(100, 120, 150))
        assertTrue(BlinkReleaseAlertPolicy.shouldNotify(100, 151, 150))
    }
}
