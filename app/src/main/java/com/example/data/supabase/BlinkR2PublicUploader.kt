package com.example.data.supabase

import com.blinkng.shared.BlinkR2MediaPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Optional direct-to-R2 public-media writer. No R2 credentials enter the APK.
 * Fallback is allowed ONLY when the server explicitly reports R2_DISABLED
 * (or has not deployed the function). Upload errors must not silently fork
 * the user's media across providers.
 */
internal object BlinkR2PublicUploader {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun tryUpload(
        purpose: String, mimeType: String, byteCount: Long, body: RequestBody
    ): String? = withContext(Dispatchers.IO) {
        if (!BlinkR2MediaPolicy.mayUploadPublic(purpose, mimeType, byteCount))
            return@withContext null
        val token = SupabaseService.accessToken() ?: return@withContext null
        val base = SupabaseConfig.url.trimEnd('/')
        val endpoint = "$base/functions/v1/blink-r2-media"

        fun send(action: JSONObject): Pair<Int, String> {
            val request = Request.Builder().url(endpoint)
                .header("apikey", SupabaseConfig.anonKey)
                .header("Authorization", "Bearer $token")
                .post(action.toString().toRequestBody(jsonType)).build()
            return http.newCall(request).execute().use {
                it.code to it.body?.string().orEmpty()
            }
        }

        val (initStatus, initRaw) = send(JSONObject()
            .put("action","init").put("purpose",purpose)
            .put("mimeType",mimeType).put("bytes",byteCount))
        if (initStatus !in 200..299) {
            val serverError = runCatching { JSONObject(initRaw).optString("error") }
                .getOrDefault("")
            if (BlinkR2MediaPolicy.intentionalFallback(initStatus, serverError))
                return@withContext null
            throw IllegalStateException("BLINK media upload could not start ($initStatus).")
        }
        val init = JSONObject(initRaw)
        val id = init.getString("assetId")
        val signed = init.getString("uploadUrl")
        val uri = URI(signed)
        check(uri.scheme == "https" && uri.userInfo == null &&
            uri.host?.endsWith(".r2.cloudflarestorage.com") == true) {
            "Invalid R2 upload destination."
        }
        val put = Request.Builder().url(signed)
            .header("Content-Type",mimeType).put(body).build()
        http.newCall(put).execute().use { response ->
            check(response.isSuccessful) { "BLINK media transfer failed (${response.code})." }
        }
        val (completeStatus, completeRaw) = send(JSONObject()
            .put("action","complete").put("purpose",purpose).put("assetId",id))
        check(completeStatus in 200..299) { "BLINK media confirmation failed ($completeStatus)." }
        val publicUrl = JSONObject(completeRaw).getString("publicUrl")
        check(BlinkR2MediaPolicy.isValidPublicUrl(publicUrl)) {
            "Invalid BLINK public media URL."
        }
        publicUrl
    }
}
