package com.example.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.blinkng.shared.update.BlinkReleaseInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Security regressions that do not contact GitHub or request installer permission. */
@RunWith(RobolectricTestRunner::class)
class WebsiteApkDownloadCoordinatorTest {
    private fun release(code: Int) = BlinkReleaseInfo(
        applicationId = "com.aistudio.blink.appvtwo",
        versionCode = code,
        versionName = "1.2.0",
        commit = "a".repeat(40),
        apkSha256 = "b".repeat(64),
        signingCertificateSha256 = "c".repeat(64),
    )

    @Test fun oldOrEqualUpdatesAreRejectedWithoutNetwork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val coordinator = WebsiteApkDownloadCoordinator(context)
        runBlocking {
            coordinator.start(this, release(100), 100)
            assertTrue(coordinator.state.value is WebsiteApkDownloadCoordinator.State.Failed)
            coordinator.start(this, release(99), 100)
            assertTrue(coordinator.state.value is WebsiteApkDownloadCoordinator.State.Failed)
        }
    }

    @Test fun cancelDoesNotLaunchInstallerOrKeepStaleReadyState() {
        val coordinator = WebsiteApkDownloadCoordinator(
            ApplicationProvider.getApplicationContext<Context>()
        )
        coordinator.cancel()
        assertEquals(WebsiteApkDownloadCoordinator.State.Idle, coordinator.state.value)
        coordinator.close()
        assertFalse(coordinator.state.value is WebsiteApkDownloadCoordinator.State.Ready)
    }
}
