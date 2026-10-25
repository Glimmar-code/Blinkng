package com.example.update

import android.content.Context
import androidx.work.Data
import androidx.work.workDataOf
import com.blinkng.shared.update.BlinkReleaseInfo
import com.blinkng.shared.update.BlinkReleasePolicy
import java.io.File

/** One pinned release identity is carried across process death and worker retries. */
internal object BlinkApkWorkContract {
    const val UNIQUE_WORK = "blink-verified-website-apk"
    const val PROGRESS_BYTES = "downloaded_bytes"
    const val PROGRESS_TOTAL = "total_bytes"
    const val INSTALLED_CODE = "installed_code"
    const val APK_URL =
        "https://github.com/Glimmar-code/Blinkng/releases/latest/download/Blink-latest.apk"

    private const val PREFS = "blink_apk_update_v1"
    private const val PREF_META = "release_metadata"
    private const val PREF_PAUSED = "paused"
    private const val PREF_WORK_ID = "work_id"

    private fun metadata(release: BlinkReleaseInfo): String = """
        applicationId=${release.applicationId}
        versionCode=${release.versionCode}
        versionName=${release.versionName}
        commit=${release.commit}
        productionSource=main
        signingCertificateSha256=${release.signingCertificateSha256}
        apkSha256=${release.apkSha256}
    """.trimIndent()

    fun input(release: BlinkReleaseInfo, installedCode: Int): Data =
        workDataOf("release_metadata" to metadata(release), INSTALLED_CODE to installedCode)

    fun release(data: Data): BlinkReleaseInfo? =
        BlinkReleasePolicy.parseVerifiedPublisherMetadata(data.getString("release_metadata").orEmpty())

    fun save(context: Context, release: BlinkReleaseInfo, paused: Boolean = false) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PREF_META, metadata(release))
            .putBoolean(PREF_PAUSED, paused)
            .apply()
    }

    fun selected(context: Context): BlinkReleaseInfo? {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREF_META, null)
        return BlinkReleasePolicy.parseVerifiedPublisherMetadata(value.orEmpty())
    }

    fun pause(context: Context, paused: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(PREF_PAUSED, paused).apply()
    }

    fun isPaused(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(PREF_PAUSED, false)

    fun saveWorkId(context: Context, id: java.util.UUID) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PREF_WORK_ID, id.toString()).apply()
    }

    fun workId(context: Context): java.util.UUID? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREF_WORK_ID, null)?.let {
                runCatching { java.util.UUID.fromString(it) }.getOrNull()
            }

    fun partialFile(context: Context, release: BlinkReleaseInfo): File =
        File(context.filesDir, "blink-updates/partial/${release.apkSha256}.apk")

    fun verifiedFile(context: Context, release: BlinkReleaseInfo): File =
        File(context.filesDir, "blink-updates/verified/${release.apkSha256}.apk")

    fun pruneOld(context: Context, release: BlinkReleaseInfo) {
        for (subfolder in listOf("partial", "verified")) {
            val folder = File(context.filesDir, "blink-updates/$subfolder")
            folder.listFiles()?.forEach { file ->
                if (file.isFile && file.name != "${release.apkSha256}.apk") file.delete()
            }
        }
    }
}
