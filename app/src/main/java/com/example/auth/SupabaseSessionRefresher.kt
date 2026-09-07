package com.example.auth

import com.example.data.supabase.SupabaseConfig
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object SupabaseSessionRefresher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    data class Session(val accessToken: String, val refreshToken: String)

    class RefreshFailure(
        message: String,
        val sessionExpired: Boolean
    ) : Exception(message)

    fun refresh(refreshToken: String): Result<Session> {
        if (refreshToken.isBlank()) {
            return Result.failure(
                RefreshFailure("Saved session has no refresh token.", sessionExpired = true)
            )
        }

        return try {
            val body = JSONObject()
                .put("refresh_token", refreshToken)
                .toString()
                .toRequestBody(jsonType)
            val request = Request.Builder()
                .url("${SupabaseConfig.url.trimEnd('/')}/auth/v1/token?grant_type=refresh_token")
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.anonKey}")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val expired = isExpiredRefreshResponse(response.code, raw)
                    Result.failure(
                        RefreshFailure(
                            message = if (expired) "Saved session expired." else "Session refresh failed (${response.code}).",
                            sessionExpired = expired
                        )
                    )
                } else {
                    val json = JSONObject(raw)
                    val access = json.optString("access_token")
                    val refresh = json.optString("refresh_token", refreshToken)
                    if (access.isBlank()) {
                        Result.failure(
                            RefreshFailure("Session refresh returned no access token.", sessionExpired = false)
                        )
                    } else {
                        Result.success(Session(access, refresh.ifBlank { refreshToken }))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun isExpiredRefreshResponse(statusCode: Int, responseBody: String): Boolean {
        if (statusCode !in setOf(400, 401, 403)) return false
        val body = responseBody.lowercase()
        return body.contains("refresh_token_not_found") ||
            body.contains("invalid refresh token") ||
            body.contains("refresh token") && body.contains("revoked") ||
            body.contains("invalid_grant") ||
            body.contains("session_not_found")
    }
}
