package com.blinkng.shared.update

/** Metadata accompanies a production-signed APK after the publish workflow verifies its bytes. */
data class BlinkReleaseInfo(
    val applicationId: String,
    val versionCode: Int,
    val versionName: String,
    val commit: String,
    val apkSha256: String,
    val signingCertificateSha256: String,
)

/**
 * Android/Windows-neutral version comparison.
 *
 * Accept ONLY metadata from the preconfigured public BLINK GitHub release endpoint.
 * The caller is responsible for TLS, bounded download size and the official link.
 * This parses metadata; it is not a replacement for Android's package-signature
 * verification when the user installs the downloaded APK.
 */
object BlinkReleasePolicy {
    private const val PACKAGE_NAME = "com.aistudio.blink.appvtwo"
    private val sha256 = Regex("[a-f0-9]{64}")
    private val sha1 = Regex("[a-f0-9]{40}")
    private val versionNamePattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")

    fun parseVerifiedPublisherMetadata(content: String): BlinkReleaseInfo? {
        if (content.isBlank() || content.length > 4_096) return null
        val pairs = linkedMapOf<String, String>()
        for (raw in content.lines()) {
            if (raw.isBlank()) continue
            val splitAt = raw.indexOf('=')
            if (splitAt <= 0 || splitAt == raw.length - 1) return null
            val key = raw.substring(0, splitAt).trim()
            val value = raw.substring(splitAt + 1).trim()
            if (key.isEmpty() || value.isEmpty() || pairs.putIfAbsent(key, value) != null) return null
        }

        val versionCode = pairs["versionCode"]?.toIntOrNull() ?: return null
        val versionName = pairs["versionName"].orEmpty()
        val commit = pairs["commit"].orEmpty()
        val checksum = pairs["apkSha256"].orEmpty()
        val signingCert = pairs["signingCertificateSha256"].orEmpty()

        if (pairs["applicationId"] != PACKAGE_NAME ||
            pairs["productionSource"] != "main" ||
            versionCode !in 1..2_100_000_000 ||
            !versionNamePattern.matches(versionName) ||
            !sha1.matches(commit) ||
            !sha256.matches(checksum) ||
            !sha256.matches(signingCert)
        ) return null

        return BlinkReleaseInfo(PACKAGE_NAME, versionCode, versionName, commit, checksum, signingCert)
    }

    fun availableUpdate(metadata: BlinkReleaseInfo?, installedVersionCode: Int): BlinkReleaseInfo? =
        metadata?.takeIf { installedVersionCode > 0 && it.versionCode > installedVersionCode }
}
