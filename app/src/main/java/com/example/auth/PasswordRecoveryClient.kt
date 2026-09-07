package com.example.auth

import android.net.Uri
import com.example.data.supabase.SupabaseConfig
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Raw GoTrue password-recovery client used by Blink's existing HTTP auth stack. */
object PasswordRecoveryClient {
    const val RESET_REDIRECT_URL = "blink://auth/reset-password"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    data class RecoverySession(
        val accessToken: String,
        val refreshToken: String?
    )

    suspend fun requestPasswordReset(email: String): Boolean = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) return@withContext false

        try {
            val redirect = URLEncoder.encode(RESET_REDIRECT_URL, StandardCharsets.UTF_8.name())
            val body = JSONObject()
                .put("email", cleanEmail)
                .toString()
                .toRequestBody(jsonType)
            val request = Request.Builder()
                .url("${SupabaseConfig.url.trimEnd('/')}/auth/v1/recover?redirect_to=$redirect")
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.anonKey}")
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                // 429 and email_address_not_authorized are real failures. The old code
                // incorrectly reported 429 as success, making Blink claim an email was sent.
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Parse the implicit-flow recovery session Supabase adds to the redirect fragment. */
    fun parseRecoveryLink(uri: Uri?): Result<RecoverySession> {
        if (uri == null || uri.scheme != "blink" || uri.host != "auth" || uri.path != "/reset-password") {
            return Result.failure(IllegalArgumentException("This password reset link is invalid."))
        }

        val params = linkedMapOf<String, String>()
        uri.queryParameterNames.forEach { key ->
            uri.getQueryParameter(key)?.let { params[key] = it }
        }
        uri.fragment
            ?.split('&')
            ?.forEach { pair ->
                if (pair.isBlank()) return@forEach
                val pieces = pair.split('=', limit = 2)
                val key = decode(pieces[0])
                val value = decode(pieces.getOrElse(1) { "" })
                params[key] = value
            }

        val errorDescription = params["error_description"]
            ?.replace('+', ' ')
            ?.takeIf { it.isNotBlank() }
            ?: params["error"]?.takeIf { it.isNotBlank() }
        if (errorDescription != null) {
            return Result.failure(IllegalArgumentException(errorDescription))
        }

        if (!params["type"].equals("recovery", ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("This link is not a password recovery link."))
        }

        val accessToken = params["access_token"].orEmpty()
        if (accessToken.isBlank()) {
            return Result.failure(IllegalArgumentException("This password reset link has expired or is incomplete."))
        }

        return Result.success(
            RecoverySession(
                accessToken = accessToken,
                refreshToken = params["refresh_token"]?.takeIf { it.isNotBlank() }
            )
        )
    }

    suspend fun updatePassword(accessToken: String, newPassword: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) return@withContext Result.failure(IllegalArgumentException("Reset session is missing."))
        if (newPassword.length < 8) return@withContext Result.failure(IllegalArgumentException("Password must be at least 8 characters."))

        try {
            val body = JSONObject()
                .put("password", newPassword)
                .toString()
                .toRequestBody(jsonType)
            val request = Request.Builder()
                .url("${SupabaseConfig.url.trimEnd('/')}/auth/v1/user")
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .method("PUT", body)
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val serverMessage = runCatching {
                        JSONObject(raw).let { json ->
                            json.optString("msg")
                                .ifBlank { json.optString("message") }
                                .ifBlank { json.optString("error_description") }
                        }
                    }.getOrNull().orEmpty()
                    Result.failure(
                        IllegalStateException(
                            serverMessage.ifBlank {
                                if (response.code == 401 || response.code == 403) {
                                    "This password reset link has expired. Request a new one."
                                } else {
                                    "Password could not be updated. Please try again."
                                }
                            }
                        )
                    )
                }
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)
}
