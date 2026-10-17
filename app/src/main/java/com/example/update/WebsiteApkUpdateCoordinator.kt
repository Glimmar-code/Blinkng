package com.example.update

import com.blinkng.shared.update.BlinkReleaseInfo
import com.blinkng.shared.update.BlinkReleasePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Read-only website APK release check. Unlike PlayInAppUpdateCoordinator, this
 * works for APKs downloaded directly from BLINK's website.
 *
 * A stale/missing GitHub release never blocks startup or interrupts offline use.
 * Only the official release publisher's verified metadata is accepted.
 */
object WebsiteApkUpdateCoordinator {
    const val DOWNLOAD_PAGE = "https://www.blink.com.ng/"
    private const val METADATA_URL =
        "https://github.com/Glimmar-code/Blinkng/releases/latest/download/release-info.txt"
    private const val MAX_METADATA_CHARS = 4_096

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun checkForUpdate(installedVersionCode: Int): BlinkReleaseInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(METADATA_URL)
                    .header("Accept", "text/plain")
                    .header("Cache-Control", "no-cache")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    // Refuse oversized metadata instead of allocating arbitrary response bodies.
                    val reader = response.body?.charStream() ?: return@use null
                    val buffer = CharArray(MAX_METADATA_CHARS + 1)
                    var read = 0
                    while (read < buffer.size) {
                        val count = reader.read(buffer, read, buffer.size - read)
                        if (count < 0) break
                        if (count == 0) break
                        read += count
                    }
                    if (read == 0 || read > MAX_METADATA_CHARS) return@use null
                    BlinkReleasePolicy.availableUpdate(
                        BlinkReleasePolicy.parseVerifiedPublisherMetadata(String(buffer, 0, read)),
                        installedVersionCode,
                    )
                }
            }.getOrNull()
        }
}
