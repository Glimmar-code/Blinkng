package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Private channel-media uploader. The backend's storage RLS MUST be deployed
 * before enabling this flow; paths are bound to the channel and current admin.
 * Uses Android's permission-scoped system picker, not gallery-wide access.
 */
object BlinkChannelMediaService {
    private const val BUCKET = "blink-channel-media"
    private const val MAX_BYTES = 25L * 1024L * 1024L
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS).build()
    private val allowed = mapOf(
        "image/jpeg" to ("image" to "jpg"),
        "image/png" to ("image" to "png"),
        "image/webp" to ("image" to "webp"),
        "video/mp4" to ("video" to "mp4"),
        "audio/mp4" to ("audio" to "m4a"),
        "audio/mpeg" to ("audio" to "mp3"),
        "application/pdf" to ("document" to "pdf")
    )

    data class Uploaded(val kind: String, val objectPath: String)

    private fun encoded(path: String): String = path.split('/').joinToString("/") {
        URLEncoder.encode(it, "UTF-8").replace("+", "%20")
    }

    private fun authenticated(path: String): Request.Builder {
        val bearer = SupabaseService.accessToken() ?: error("Sign in to upload channel media.")
        return Request.Builder().url(SupabaseConfig.url.trimEnd('/') + path)
            .header("apikey", SupabaseConfig.anonKey)
            .header("Authorization", "Bearer $bearer")
    }

    suspend fun upload(context: Context, channelId: String, contentUri: Uri): Uploaded =
        withContext(Dispatchers.IO) {
            val owner = SupabaseService().getCurrentUserId() ?: error("Sign in again.")
            val kindAndExtension = allowed[context.contentResolver.getType(contentUri)?.lowercase()]
                ?: throw IllegalArgumentException("Use a JPG, PNG, WebP, MP4, MP3, M4A or PDF.")
            val (kind, extension) = kindAndExtension
            val mime = allowed.entries.first { it.value == kindAndExtension }.key
            val local = File.createTempFile("blink_channel_", ".$extension", context.cacheDir)
            try {
                val input = context.contentResolver.openInputStream(contentUri)
                    ?: error("The selected file is not accessible.")
                input.use { source ->
                    local.outputStream().use { target ->
                        val bytes = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val count = source.read(bytes)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_BYTES) { "The maximum channel upload is 25 MB." }
                            target.write(bytes, 0, count)
                        }
                        require(total > 0) { "Cannot upload an empty file." }
                    }
                }
                val id = UUID.fromString(channelId).toString()
                val path = "$id/$owner/${UUID.randomUUID()}.$extension"
                http.newCall(
                    authenticated("/storage/v1/object/$BUCKET/${encoded(path)}")
                        .header("Content-Type", mime)
                        .header("x-upsert", "false")
                        .post(local.asRequestBody(mime.toMediaType()))
                        .build()
                ).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Channel upload failed: HTTP ${response.code}.")
                    }
                }
                Uploaded(kind, path)
            } finally {
                local.delete()
            }
        }

    suspend fun signedUrl(objectPath: String): String? = withContext(Dispatchers.IO) {
        if (objectPath.isBlank()) return@withContext null
        val request = authenticated("/storage/v1/object/sign/$BUCKET/${encoded(objectPath)}")
            .post(JSONObject().put("expiresIn", 1200).toString()
                .toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            val signed = json.optString("signedURL").ifBlank { json.optString("signedUrl") }
            val host = SupabaseConfig.url.trimEnd('/')
            when {
                signed.startsWith("$host/storage/v1/") -> signed
                signed.startsWith("/storage/v1/") -> host + signed
                signed.startsWith("/object/") -> "$host/storage/v1$signed"
                else -> null
            }
        }
    }
}
