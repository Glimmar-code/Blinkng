package com.example.ads

import android.app.Activity
import android.os.Looper
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class BlinkRewardedLifecycleTest {
    private val callbacks = mutableListOf<RewardedAdLoadCallback>()
    private lateinit var manager: BlinkRewardedAdManager

    @Before fun setUp() {
        BlinkAdsRuntime.updateConsent(true, false)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        manager = BlinkRewardedAdManager(activity, "test") { _, _, _, callback ->
            callbacks.add(callback)
        }
    }

    @After fun tearDown() {
        manager.close()
        BlinkAdsRuntime.updateConsent(false, false)
    }

    @Test fun stalledRequestCanRetryAndOldCallbackCannotOverwriteNewRequest() {
        manager.load()
        manager.load()
        assertEquals(1, callbacks.size)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        assertTrue(manager.loadFailureMessage!!.contains("timed out"))
        manager.load()
        assertEquals(2, callbacks.size)
        callbacks[0].onAdFailedToLoad(LoadAdError(0, "stale", "test", null, null))
        assertFalse(manager.loadFailureMessage!!.contains("stale"))
        manager.load()
        assertEquals(2, callbacks.size)
        callbacks[1].onAdFailedToLoad(LoadAdError(3, "no inventory", "test", null, null))
        assertTrue(manager.loadFailureMessage!!.contains("code 3"))
        manager.load()
        assertEquals(3, callbacks.size)
    }

    @Test fun closeCancelsTimeoutAndIgnoresCallbacks() {
        manager.load()
        manager.close()
        callbacks[0].onAdFailedToLoad(LoadAdError(0, "late", "test", null, null))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31))
        assertNull(manager.loadFailureMessage)
        manager.load()
        assertEquals(1, callbacks.size)
        assertFalse(manager.isReady)
    }

    @Test fun missingConsentPreventsRequests() {
        BlinkAdsRuntime.updateConsent(false, false)
        manager.load()
        assertTrue(callbacks.isEmpty())
    }
}
