package com.example.update

import com.blinkng.shared.update.BlinkApkUpdatePolicy
import com.blinkng.shared.update.BlinkReleaseInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline APK updater policy tests. Do not fetch a Robolectric SDK or contact
 * GitHub during production CI; actual installer behavior is device-tested.
 */
class WebsiteApkDownloadCoordinatorTest {
    private val release = BlinkReleaseInfo(
        applicationId = "com.aistudio.blink.appvtwo",
        versionCode = 200,
        versionName = "2.0.0",
        commit = "a".repeat(40),
        apkSha256 = "b".repeat(64),
        signingCertificateSha256 = "c".repeat(64),
    )

    @Test fun redirectsAreRestrictedToOfficialHttpsGitHubHosts() {
        assertTrue(BlinkApkUpdatePolicy.isOfficialAssetOrigin("https", "github.com"))
        assertTrue(BlinkApkUpdatePolicy.isOfficialAssetOrigin("https", "release-assets.githubusercontent.com"))
        assertFalse(BlinkApkUpdatePolicy.isOfficialAssetOrigin("http", "github.com"))
        assertFalse(BlinkApkUpdatePolicy.isOfficialAssetOrigin("https", "github.com.attacker.example"))
        assertFalse(BlinkApkUpdatePolicy.isOfficialAssetOrigin("https", "fakegithubusercontent.com"))
        assertFalse(BlinkApkUpdatePolicy.isOfficialAssetOrigin("https", "example.com"))
    }

    @Test fun truncatedEmptyAndOversizedApksNeverPass() {
        val max = BlinkApkUpdatePolicy.MAX_DOWNLOAD_BYTES
        assertTrue(BlinkApkUpdatePolicy.isValidTransfer(2048, 2048))
        assertTrue(BlinkApkUpdatePolicy.isValidTransfer(2048, -1))
        assertFalse(BlinkApkUpdatePolicy.isValidTransfer(0, -1))
        assertFalse(BlinkApkUpdatePolicy.isValidTransfer(1024, 2048))
        assertFalse(BlinkApkUpdatePolicy.isValidTransfer(max + 1, -1))
        assertFalse(BlinkApkUpdatePolicy.isValidTransfer(123, max + 1))
    }

    private fun matches(
        installed: Int = 100,
        new: Long = 200,
        packageName: String = "com.aistudio.blink.appvtwo",
        signer: String = "c".repeat(64),
        installedSigner: String = "c".repeat(64),
    ) = BlinkApkUpdatePolicy.matchesInstalledApp(
        release = release,
        installedPackage = "com.aistudio.blink.appvtwo",
        installedCode = installed,
        installedSignerSha256 = installedSigner,
        archivePackage = packageName,
        archiveCode = new,
        archiveSignerSha256 = signer,
    )

    @Test fun onlyNewerPackagesWithTheOriginalSignerPass() {
        assertTrue(matches())
        assertFalse(matches(installed = 200))
        assertFalse(matches(installed = 201))
        assertFalse(matches(new = 199))
        assertFalse(matches(new = 201))
        assertFalse(matches(packageName = "com.attacker.apk"))
        assertFalse(matches(signer = "d".repeat(64)))
        assertFalse(matches(installedSigner = "d".repeat(64)))
    }
}
