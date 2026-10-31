package com.example.data.supabase

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Explicit contestant/admin selection only; never uploads media during screen load.
 * Owner-segment storage policy and public read URL require the paired staging SQL.
 */
class BlinkContestMediaUpload {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).writeTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS).build()

    suspend fun upload(context: Context, selected: Uri): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val auth = SupabaseService()
            require(auth.restoreSession()) { "Sign in before attaching media." }
            val owner = auth.getCurrentUserId() ?: error("Your session is no longer valid.")
            val mime = context.contentResolver.getType(selected).orEmpty()
            val extension = when (mime) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                "video/mp4" -> "mp4"
                else -> error("Choose a JPG, PNG, WebP or MP4 file.")
            }
            val max = com.blinkng.shared.BlinkContestMediaPolicy.maxBytes(mime)
            // Stage to a bounded cache file rather than holding a 25 MB video
            // plus an HTTP buffer in memory on low-RAM Android devices.
            val staged = File.createTempFile("blink-contest-", ".$extension", context.cacheDir)
            try {
                val source = context.contentResolver.openInputStream(selected)
                    ?: error("Cannot read selected attachment.")
                source.use { input ->
                    staged.outputStream().use { output ->
                        val buffer = ByteArray(16_384)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= max) { "Attachment too large. Choose a smaller file." }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                require(staged.length() > 0) { "The selected file is empty." }
                val path = owner + "/" + UUID.randomUUID().toString() + "." + extension
                val endpoint = SupabaseConfig.url.trimEnd('/') +
                    "/storage/v1/object/blink-contest-media/" + path
                val token = SupabaseService.accessToken() ?: error("Sign in again.")
                val request = Request.Builder().url(endpoint)
                    .header("apikey", SupabaseConfig.anonKey)
                    .header("Authorization", "Bearer " + token)
                    .header("x-upsert", "false")
                    .post(staged.asRequestBody(mime.toMediaType()))
                    .build()
                client.newCall(request).execute().use { response ->
                    require(response.isSuccessful) {
                        "Unable to upload entry media (" + response.code + ")."
                    }
                }
                SupabaseConfig.url.trimEnd('/') +
                    "/storage/v1/object/public/blink-contest-media/" + path
            } finally {
                staged.delete()
            }
        }
    }
}
