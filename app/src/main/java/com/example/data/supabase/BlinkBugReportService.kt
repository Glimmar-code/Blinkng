package com.example.data.supabase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class BlinkBugReport(
    val id: String,
    val description: String,
    val surface: String,
    val status: String,
    val note: String,
    val rewardCoins: Int,
    val createdAt: String,
    val username: String = "",
    val screenshotPath: String = ""
)

data class BlinkBugSubmission(val id: String, val dmSent: Boolean, val screenshotUploaded: Boolean, val screenshotWarning: String?)

class BlinkBugReportService {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val base = SupabaseConfig.url.trimEnd('/')

    private suspend fun authorizedRequest(makeRequest: (String) -> Request): String = withContext(Dispatchers.IO) {
        val service = SupabaseService()
        if (!service.restoreSession()) error("Please sign in to BLINK again.")
        fun accessToken(): String = SupabaseService.accessToken()?.takeIf { it.isNotBlank() }
            ?: error("You must be signed in to report a bug.")
        fun execute(): okhttp3.Response = http.newCall(makeRequest(accessToken())).execute()
        var response = execute()
        if (response.code == 401) {
            response.close()
            if (!service.refreshSession()) error("Session expired. Sign in again.")
            response = execute()
        }
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val message = runCatching { JSONObject(text).optString("message") }.getOrNull()
                error(message?.takeIf(String::isNotBlank) ?: "Server request failed (${it.code}).")
            }
            text
        }
    }

    private suspend fun rpc(name: String, payload: JSONObject = JSONObject()): String =
        authorizedRequest { access ->
            Request.Builder()
                .url("$base/rest/v1/rpc/$name")
                .header("apikey", SupabaseConfig.anonKey)
                .header("Authorization", "Bearer $access")
                .post(payload.toString().toRequestBody(jsonType))
                .build()
        }

    suspend fun submit(
        context: Context,
        description: String,
        surface: String,
        appVersion: String,
        screenshot: Uri?
    ): Result<BlinkBugSubmission> = runCatching {
        val text = description.trim()
        require(text.length in 20..2000) { "Describe the issue in 20–2000 characters." }
        // Preprocess before submitting so we do not create a report with a broken screenshot flag.
        val bytes = screenshot?.let { prepareImage(context, it) }
        val result = JSONObject(rpc("submit_blink_bug_report", JSONObject()
            .put("p_description", text)
            .put("p_surface", surface.take(100))
            .put("p_app_version", appVersion.take(80))
            .put("p_screenshot", bytes != null)))
        val path = result.optString("screenshot_path").takeIf { it.isNotBlank() && it != "null" }
        var uploaded = false
        var warning: String? = null
        if (bytes != null && path != null) {
            val attempted = runCatching { uploadScreenshot(path, bytes) }
            uploaded = attempted.isSuccess
            if (!uploaded) warning = "Report sent, but the screenshot could not be uploaded. Please contact support if needed."
        }
        BlinkBugSubmission(result.getString("id"), result.optBoolean("dm_sent"), uploaded, warning)
    }

    private suspend fun prepareImage(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        require(options.outWidth > 0 && options.outHeight > 0) { "Choose a valid screenshot." }
        val sample = generateSequence(1) { it * 2 }
            .first { (options.outWidth / it) <= 1600 && (options.outHeight / it) <= 1600 }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Could not read screenshot.")
        try {
            var quality = 83
            var compressed: ByteArray
            do {
                val output = ByteArrayOutputStream()
                decoded.compress(Bitmap.CompressFormat.JPEG, quality, output)
                compressed = output.toByteArray()
                quality -= 12
            } while (compressed.size > 1800000 && quality >= 35)
            require(compressed.size <= 2097152) { "Screenshot is too large." }
            compressed
        } finally { decoded.recycle() }
    }

    private suspend fun uploadScreenshot(path: String, bytes: ByteArray) {
        authorizedRequest { access ->
            Request.Builder()
                .url("$base/storage/v1/object/blink-bug-reports/$path")
                .header("apikey", SupabaseConfig.anonKey)
                .header("Authorization", "Bearer $access")
                .header("Content-Type", "image/jpeg")
                .post(bytes.toRequestBody("image/jpeg".toMediaType()))
                .build()
        }
    }

    suspend fun myReports(): Result<List<BlinkBugReport>> = runCatching {
        parseReports(JSONArray(rpc("get_my_blink_bug_reports")))
    }

    suspend fun adminReports(): Result<List<BlinkBugReport>> = runCatching {
        parseReports(JSONArray(rpc("admin_list_blink_bug_reports")))
    }

    suspend fun review(id: String, status: String, coins: Int, note: String): Result<Unit> = runCatching {
        require(coins in 0..500) { "Reward must be between 0 and 500 coins." }
        rpc("admin_review_blink_bug_report", JSONObject()
            .put("p_report_id", id)
            .put("p_status", status)
            .put("p_reward_coins", coins)
            .put("p_note", note.take(600)))
        Unit
    }

    /** A private authenticated read: never expose screenshot URLs as public links. */
    suspend fun screenshot(path: String): Result<ByteArray> = runCatching {
        require(Regex("^[a-f0-9-]{36}/[a-f0-9-]{36}\\.jpg$").matches(path)) { "Invalid screenshot." }
        withContext(Dispatchers.IO) {
            val session = SupabaseService()
            if (!session.restoreSession()) error("Sign in again.")
            val token = SupabaseService.accessToken() ?: error("Sign in again.")
            val request = Request.Builder().url("$base/storage/v1/object/authenticated/blink-bug-reports/$path")
                .header("apikey", SupabaseConfig.anonKey)
                .header("Authorization", "Bearer $token").build()
            http.newCall(request).execute().use {
                if (!it.isSuccessful) error("Screenshot unavailable (${it.code}).")
                it.body?.bytes() ?: error("Screenshot unavailable.")
            }
        }
    }

    private fun parseReports(a: JSONArray): List<BlinkBugReport> = (0 until a.length()).mapNotNull { index ->
        val item = a.optJSONObject(index) ?: return@mapNotNull null
        BlinkBugReport(
            id = item.optString("id"),
            description = item.optString("description"),
            surface = item.optString("surface"),
            status = item.optString("status"),
            note = item.optString("admin_note"),
            rewardCoins = item.optInt("reward_coins"),
            createdAt = item.optString("created_at"),
            username = item.optString("username"),
            screenshotPath = item.optString("screenshot_path").takeUnless { it == "null" }.orEmpty()
        )
    }
}
