package com.example.ads

import android.app.Activity
import android.app.Application
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
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class BlinkRewardedLifecycleTest {
    private val callbacks = mutableListOf<RewardedAdLoadCallback>()
    private val failures = mutableListOf<String>()
    private lateinit var manager: BlinkRewardedAdManager

    @Before fun setUp() {
        BlinkAdsRuntime.updateConsent(true, false)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        manager = BlinkRewardedAdManager(activity, "test") { _, _, _, callback -> callbacks.add(callback) }
    }

    @After fun tearDown() {
        manager.close()
        BlinkAdsRuntime.updateConsent(false, false)
    }

    private fun waitForAd() {
        manager.load(onReady = { fail("No successful ad was supplied") }, onUnavailable = { failures.add(it) })
    }

    @Test fun stalledRequestCanRetryAndOldCallbackCannotOverwriteNewRequest() {
        waitForAd()
        waitForAd()
        assertEquals(1, callbacks.size)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(BlinkRewardedAdManager.LOAD_TIMEOUT_MS))
        assertEquals(listOf(BlinkRewardedAdManager.LOAD_TIMEOUT_MESSAGE), failures)
        assertFalse(manager.isLoading)
        waitForAd()
        assertEquals(2, callbacks.size)
        callbacks[0].onAdFailedToLoad(LoadAdError(0, "stale", "test", null, null))
        assertEquals(1, failures.size)
        assertTrue(manager.isLoading)
        manager.load()
        assertEquals(2, callbacks.size)
        callbacks[1].onAdFailedToLoad(LoadAdError(3, "no inventory", "test", null, null))
        assertTrue(failures.last().contains("Ad error 3, test"))
        waitForAd()
        assertEquals(3, callbacks.size)
    }

    @Test fun closeCancelsTimeoutAndIgnoresCallbacks() {
        waitForAd()
        manager.close()
        callbacks[0].onAdFailedToLoad(LoadAdError(0, "late", "test", null, null))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31))
        assertTrue(failures.isEmpty())
        manager.load()
        assertEquals(1, callbacks.size)
        assertFalse(manager.isReady)
        assertFalse(manager.isLoading)
    }

    @Test fun missingConsentPreventsRequests() {
        BlinkAdsRuntime.updateConsent(false, false)
        waitForAd()
        assertTrue(callbacks.isEmpty())
        assertTrue(failures.single().contains("privacy"))
    }

    @Test fun closedManagerRefusesShowAndCompletesCaller() {
        manager.close()
        var finished = false
        manager.show("", "", { fail("No reward expected") }, { failures.add(it) }, { finished = true })
        assertTrue(finished)
        assertTrue(failures.single().contains("Reopen"))
        assertTrue(callbacks.isEmpty())
    }
}
