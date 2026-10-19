package com.example.data.supabase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Owner-only, read-only Paystack revenue and transaction health dashboard. */
class BlinkPaystackFinanceService {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun loadReport(): Result<JSONObject> = runCatching {
        withContext(Dispatchers.IO) {
            val service = SupabaseService()
            if (!service.restoreSession()) error("Sign in to your BLINK Admin account.")
            fun request(): Request {
                val token = SupabaseService.accessToken()?.takeIf { it.isNotBlank() }
                    ?: error("Session expired. Sign in again.")
                return Request.Builder()
                    .url("${SupabaseConfig.url.trimEnd('/')}/functions/v1/paystack-finance")
                    .header("apikey", SupabaseConfig.anonKey)
                    .header("Authorization", "Bearer $token")
                    .post("{}".toRequestBody(jsonType))
                    .build()
            }
            var response = client.newCall(request()).execute()
            if (response.code == 401) {
                response.close()
                if (!service.refreshSession()) error("Please sign in again.")
                response = client.newCall(request()).execute()
            }
            response.use { result ->
                if (!result.isSuccessful) {
                    error(when (result.code) {
                        403 -> "Owner authorization is required for financial reporting."
                        503 -> "Payment reporting is not deployed yet."
                        else -> "Finance report is currently unavailable."
                    })
                }
                JSONObject(result.body?.string().orEmpty())
            }
        }
    }
}
