package com.blinkng.desktop.data

import com.blinkng.shared.BlinkBackendDefaults
import com.blinkng.shared.BlinkR2MediaPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.URI
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
        // Same authenticated server-side signing contract as Android.
        tryUploadR2(bucket, file)?.let { return@withContext it }
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

    private fun tryUploadR2(bucket: String, file: File): String? {
        val purpose = when (bucket) {
            "post-media" -> "feed"
            "marketplace-media" -> "market"
            "avatars" -> "profile"
            else -> return null
        }
        val mime = detectContentType(file)
        if (!BlinkR2MediaPolicy.mayUploadPublic(purpose, mime, file.length())) return null
        val token = requireSession().accessToken
        val endpoint = "$baseUrl/functions/v1/blink-r2-media"

        fun request(action: JSONObject): Pair<Int, String> {
            val call = Request.Builder().url(endpoint)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .post(action.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .build()
            return http.newCall(call).execute().use { it.code to it.body?.string().orEmpty() }
        }
        val (status, body) = request(JSONObject()
            .put("action", "init").put("purpose", purpose)
            .put("mimeType", mime).put("bytes", file.length()))
        if (status !in 200..299) {
            val code = runCatching {
                val reply = JSONObject(body)
                if (reply.optString("code") == "NOT_FOUND" &&
                    reply.optString("message") == "Requested function was not found")
                    "R2_FUNCTION_NOT_DEPLOYED"
                else reply.optString("error")
            }.getOrDefault("")
            if (BlinkR2MediaPolicy.intentionalFallback(status, code)) return null
            throw IllegalStateException("BLINK media upload could not start ($status).")
        }
        val init = JSONObject(body)
        val signed = init.getString("uploadUrl")
        val signedUri = URI(signed)
        require(signedUri.scheme == "https" && signedUri.userInfo == null &&
            signedUri.host?.endsWith(".r2.cloudflarestorage.com") == true) {
            "Invalid R2 upload destination."
        }
        val put = Request.Builder().url(signed).header("Content-Type",mime)
            .put(file.asRequestBody(mime.toMediaTypeOrNull())).build()
        http.newCall(put).execute().use {
            check(it.isSuccessful) { "BLINK media transfer failed (${it.code})." }
        }
        val (completeStatus, completeRaw) = request(JSONObject()
            .put("action", "complete").put("purpose", purpose)
            .put("assetId", init.getString("assetId")))
        check(completeStatus in 200..299) {
            "BLINK media confirmation failed ($completeStatus)."
        }
        val result = JSONObject(completeRaw).getString("publicUrl")
        check(BlinkR2MediaPolicy.isValidPublicUrl(result)) {
            "Invalid BLINK public media URL."
        }
        return result
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
