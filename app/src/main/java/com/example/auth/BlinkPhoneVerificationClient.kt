package com.example.auth

import com.blinkng.shared.BlinkPhonePolicy
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Supabase Auth owns SMS issuance and verified phone association; no self-declared profile phone is trusted. */
class BlinkPhoneVerificationClient {
    private val http = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val mime = "application/json".toMediaType()
    private val baseUrl = SupabaseConfig.url.trimEnd('/')

    private fun request(path: String, access: String) = Request.Builder()
        .url("$baseUrl$path")
        .header("apikey", SupabaseConfig.anonKey)
        .header("Authorization", "Bearer $access")
        .header("Accept", "application/json")

    private fun send(req: Request): JSONObject {
        http.newCall(req).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(
                if (response.code == 429) "Too many requests. Try again later."
                else "Phone verification is unavailable. Check SMS support and retry."
            )
            return raw.takeIf(String::isNotBlank)?.let(::JSONObject) ?: JSONObject()
        }
    }

    suspend fun requestCode(raw: String) = withContext(Dispatchers.IO) {
        val number = requireNotNull(BlinkPhonePolicy.normalize(raw)) { "Enter a valid phone number." }
        val token = requireNotNull(SupabaseService.accessToken()) { "Sign in to verify your number." }
        send(request("/auth/v1/user", token).put(JSONObject().put("phone", number).toString().toRequestBody(mime)).build())
    }

    suspend fun verify(raw: String, code: String): Boolean = withContext(Dispatchers.IO) {
        val number = requireNotNull(BlinkPhonePolicy.normalize(raw)) { "Invalid phone number." }
        require(BlinkPhonePolicy.otpIsValid(code)) { "Enter the six-digit SMS code." }
        val token = requireNotNull(SupabaseService.accessToken()) { "Sign in to verify your number." }
        val otp = JSONObject().put("phone", number).put("token", code).put("type", "phone_change")
        val result = send(request("/auth/v1/verify", token).post(otp.toString().toRequestBody(mime)).build())
        if (result.optString("access_token").isNotBlank()) {
            val previous = SupabaseService.sessionSnapshot()
            SupabaseService.replaceSession(result.optString("access_token"),
                result.optString("refresh_token").ifBlank { previous.refreshToken })
        }
        val newToken = requireNotNull(SupabaseService.accessToken()) { "Session expired. Sign in again." }
        val user = send(request("/auth/v1/user", newToken).get().build())
        user.optString("phone") == number && user.optString("phone_confirmed_at").isNotBlank()
    }
}
