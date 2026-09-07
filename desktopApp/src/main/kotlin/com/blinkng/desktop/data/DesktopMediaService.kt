package com.blinkng.desktop.data

import com.blinkng.shared.BlinkBackendDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Authenticated Supabase Storage adapter for Windows file workflows. */
class DesktopMediaService(private val client: DesktopSupabaseClient) {
    private val baseUrl = BlinkBackendDefaults.resolve(
        System.getenv("SUPABASE_URL") ?: System.getenv("VITE_SUPABASE_URL"),
        BlinkBackendDefaults.SUPABASE_URL,
    ).trimEnd('/')
    private val anonKey = BlinkBackendDefaults.resolve(
        System.getenv("SUPABASE_ANON_KEY") ?: System.getenv("VITE_SUPABASE_ANON_KEY"),
        BlinkBackendDefaults.SUPABASE_ANON_KEY,
    )
    private val http = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun uploadPostMedia(file: File): String = uploadPublic("post-media", file)

    suspend fun uploadMarketplaceMedia(file: File): String = uploadPublic("marketplace-media", file)

    suspend fun uploadAvatar(file: File): String = uploadPublic("avatars", file)

    suspend fun createImagePost(text: String, file: File): DesktopFeedPost = withContext(Dispatchers.IO) {
        validateMediaFile(file, MAX_POST_BYTES)
        val url = uploadPostMedia(file)
        val post = client.createPost(text)
        patchRow(
            "/rest/v1/feed_posts?id=eq.${encode(post.id)}",
            JSONObject().put("image_url", url).put("type", "image"),
        )
        post.copy(imageUrl = url)
    }

    suspend fun attachMarketplaceImage(itemId: String, file: File): String = withContext(Dispatchers.IO) {
        validateMediaFile(file, MAX_MARKET_BYTES)
        val url = uploadMarketplaceMedia(file)
        patchRow(
            "/rest/v1/market_items?id=eq.${encode(itemId)}",
            JSONObject().put("image_url", url).put("image_urls", org.json.JSONArray().put(url)),
        )
        url
    }

    suspend fun updateAvatar(file: File): String = withContext(Dispatchers.IO) {
        validateMediaFile(file, MAX_AVATAR_BYTES)
        val url = uploadAvatar(file)
        val userId = requireSession().userId
        patchRow("/rest/v1/profiles?id=eq.${encode(userId)}", JSONObject().put("avatar_url", url))
        url
    }

    private suspend fun uploadPublic(bucket: String, file: File): String = withContext(Dispatchers.IO) {
        require(file.exists() && file.isFile) { "The selected file no longer exists." }
        val session = requireSession()
        val extension = file.extension.lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "bin"
        val objectPath = "${session.userId}/${UUID.randomUUID()}.$extension"
        val encodedPath = objectPath.split('/').joinToString("/") { encode(it) }
        val contentType = detectContentType(file)
        val request = Request.Builder()
            .url("$baseUrl/storage/v1/object/$bucket/$encodedPath")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("x-upsert", "false")
            .post(file.readBytes().toRequestBody(contentType.toMediaTypeOrNull()))
            .build()
        execute(request)
        "$baseUrl/storage/v1/object/public/$bucket/$encodedPath"
    }

    private fun patchRow(path: String, body: JSONObject) {
        val session = requireSession()
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Prefer", "return=minimal")
            .patch(body.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()
        execute(request)
    }

    private fun requireSession(): DesktopSession =
        client.session ?: throw IllegalStateException("Sign in to upload media.")

    private fun validateMediaFile(file: File, maxBytes: Long) {
        require(file.exists() && file.isFile) { "Choose a valid file." }
        require(file.length() in 1..maxBytes) { "This file is too large for Blinkng." }
        require(detectContentType(file).startsWith("image/")) { "Choose a JPG, PNG, WEBP or GIF image." }
    }

    private fun detectContentType(file: File): String = when (file.extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "application/octet-stream"
    }

    private fun execute(request: Request): String {
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(text).optString("message").ifBlank { JSONObject(text).optString("error") }
                }.getOrNull().orEmpty()
                throw IllegalStateException(message.ifBlank { "Media upload failed (${response.code})." })
            }
            return text
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    companion object {
        private const val MAX_AVATAR_BYTES = 8L * 1024 * 1024
        private const val MAX_POST_BYTES = 25L * 1024 * 1024
        private const val MAX_MARKET_BYTES = 20L * 1024 * 1024
    }
}
