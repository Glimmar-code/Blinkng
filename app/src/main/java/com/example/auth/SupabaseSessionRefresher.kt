package com.example.auth

import com.example.data.supabase.SupabaseConfig
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object SupabaseSessionRefresher {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    data class Session(val accessToken: String, val refreshToken: String)

    fun refresh(refreshToken: String): Result<Session> = try {
        val body = JSONObject().put("refresh_token", refreshToken).toString().toRequestBody(jsonType)
        val request = Request.Builder()
            .url("${SupabaseConfig.url.trimEnd('/')}/auth/v1/token?grant_type=refresh_token")
            .addHeader("apikey", SupabaseConfig.anonKey)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) return Result.failure(Exception("Session refresh failed (${response.code})."))
            val json = JSONObject(raw)
            val access = json.optString("access_token")
            val refresh = json.optString("refresh_token", refreshToken)
            if (access.isBlank()) return Result.failure(Exception("Session refresh returned no access token."))
            Result.success(Session(access, refresh))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
