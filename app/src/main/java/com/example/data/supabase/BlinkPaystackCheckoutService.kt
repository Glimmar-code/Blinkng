package com.example.data.supabase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Unprivileged client. Prices, Paystack keys and order fulfilment stay on the server. */
data class BlinkPaystackCheckout(val url: String, val reference: String, val kind: String)
data class BlinkPaystackResult(val status: String, val kind: String)

class BlinkPaystackCheckoutService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private suspend fun call(name: String, payload: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val session = SupabaseService()
            if (!session.restoreSession()) error("Sign in to BLINK before paying.")

            fun request(): Request {
                val token = SupabaseService.accessToken()?.takeIf(String::isNotBlank)
                    ?: error("Sign in to BLINK before paying.")
                return Request.Builder()
                    .url("${SupabaseConfig.url.trimEnd('/')}/functions/v1/$name")
                    .header("apikey", SupabaseConfig.anonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody(jsonType)).build()
            }

            var response = client.newCall(request()).execute()
            if (response.code == 401) {
                response.close()
                if (!session.refreshSession()) error("Your BLINK session expired.")
                response = client.newCall(request()).execute()
            }
            response.use {
                val body = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    val code = runCatching { JSONObject(body).optString("error") }.getOrNull()
                    error(
                        when (code) {
                            "CHECKOUT_NOT_ENABLED", "CASH_CHECKOUT_DISABLED" -> "Secure cash checkout is not available yet."
                            "GOLD_REQUIRES_1000_FOLLOWERS" -> "Gold verification requires 1,000 followers."
                            "PAYMENT_RATE_LIMIT" -> "Too many payment attempts. Try later."
                            "ALREADY_GOLD_VERIFIED" -> "Gold verification is already active."
                            "PAYMENT_NOT_FOUND" -> "This payment was not found for your account."
                            else -> "The payment service is unavailable right now."
                        }
                    )
                }
                JSONObject(body)
            }
        }

    private suspend fun initialize(payload: JSONObject): BlinkPaystackCheckout {
        val data = call("paystack-checkout", payload)
        val url = data.optString("checkoutUrl")
        val reference = data.optString("reference")
        check(Regex("^https://checkout\\.paystack\\.com/[a-zA-Z0-9_-]+$").matches(url) &&
            Regex("^blink-ps-[0-9a-f]{32}$").matches(reference)) {
            "Invalid secure payment link."
        }
        return BlinkPaystackCheckout(url, reference, data.optString("kind"))
    }

    suspend fun verifyBadge(tier: String): BlinkPaystackCheckout {
        require(tier == "BLUE" || tier == "GOLD")
        return initialize(JSONObject().put("kind", "VERIFICATION").put("tier", tier))
    }

    suspend fun buyCoins(packId: String): BlinkPaystackCheckout {
        require(Regex("^coins_(100|500|1000|2000|5000)$").matches(packId))
        return initialize(JSONObject().put("kind", "COINS").put("packId", packId))
    }

    suspend fun checkPayment(reference: String): BlinkPaystackResult {
        require(Regex("^blink-ps-[0-9a-f]{32}$").matches(reference))
        val result = call("paystack-confirm", JSONObject().put("reference", reference))
        return BlinkPaystackResult(
            result.optString("status", "pending"),
            result.optString("kind")
        )
    }
}
