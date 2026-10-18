package com.blinkng.shared.update

/**
 * Android/Windows-shared, side-effect-free policy for official BLINK APK updates.
 * Android still verifies the real OS package signature before opening the installer.
 */
object BlinkApkUpdatePolicy {
    const val MAX_DOWNLOAD_BYTES: Long = 100L * 1024L * 1024L

    fun isOfficialAssetOrigin(scheme: String, host: String): Boolean =
        scheme == "https" && (
            host == "github.com" || host.endsWith(".githubusercontent.com")
        )

    fun isValidTransfer(downloaded: Long, declaredLength: Long): Boolean =
        downloaded > 0L && downloaded <= MAX_DOWNLOAD_BYTES &&
            declaredLength in -1L..MAX_DOWNLOAD_BYTES &&
            (declaredLength == -1L || declaredLength == downloaded)

    fun matchesInstalledApp(
        release: BlinkReleaseInfo,
        installedPackage: String,
        installedCode: Int,
        installedSignerSha256: String,
        archivePackage: String,
        archiveCode: Long,
        archiveSignerSha256: String,
    ): Boolean =
        release.applicationId == installedPackage &&
            release.applicationId == archivePackage &&
            archiveCode > installedCode.toLong() &&
            archiveCode == release.versionCode.toLong() &&
            installedSignerSha256 == release.signingCertificateSha256 &&
            archiveSignerSha256 == installedSignerSha256
}
