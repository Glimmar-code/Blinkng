package com.example.data.supabase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class BlinkFlutterwaveCheckout(val url: String, val reference: String)

/**
 * Unprivileged Android client. Flutterwave secret keys, prices and payment fulfilment
 * remain exclusively in authenticated Supabase Edge Functions and Postgres.
 */
class BlinkFlutterwaveCheckoutService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private suspend fun invoke(function: String, body: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val session = SupabaseService()
            if (!session.restoreSession()) error("Please sign in to BLINK before paying.")

            fun request(): Request {
                val access = SupabaseService.accessToken()?.takeIf { it.isNotBlank() }
                    ?: error("Please sign in to BLINK before paying.")
                return Request.Builder()
                    .url("${SupabaseConfig.url.trimEnd('/')}/functions/v1/$function")
                    .header("apikey", SupabaseConfig.anonKey)
                    .header("Authorization", "Bearer $access")
                    .header("Content-Type", "application/json")
                    .post(body.toString().toRequestBody(jsonType))
                    .build()
            }

            var response = client.newCall(request()).execute()
            if (response.code == 401) {
                response.close()
                if (!session.refreshSession()) error("Your session expired. Sign in again.")
                response = client.newCall(request()).execute()
            }
            response.use {
                val raw = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    val code = runCatching { JSONObject(raw).optString("error") }.getOrDefault("")
                    error(
                        when (code) {
                            "CHECKOUT_NOT_ENABLED" -> "Cash verification is not available yet."
                            "GOLD_FOLLOWER_REQUIREMENT_NOT_MET" -> "Gold requires 1,000 followers."
                            "PAYMENT_RATE_LIMIT" -> "Too many payment attempts. Try again later."
                            else -> "Secure payment is unavailable right now."
                        }
                    )
                }
                JSONObject(raw)
            }
        }

    suspend fun createVerificationCheckout(tier: String): BlinkFlutterwaveCheckout {
        require(tier == "BLUE" || tier == "GOLD") { "Choose Blue or Gold verification." }
        val result = invoke("flutterwave-checkout", JSONObject().put("tier", tier))
        val url = result.optString("checkoutUrl")
        val reference = result.optString("reference")
        check(url.startsWith("https://checkout.flutterwave.com/") &&
            reference.matches(Regex("^blink-flw-[0-9a-f]{32}$"))) {
            "Secure checkout response could not be verified."
        }
        return BlinkFlutterwaveCheckout(url = url, reference = reference)
    }

    suspend fun confirmPayment(reference: String): Boolean {
        require(reference.matches(Regex("^blink-flw-[0-9a-f]{32}$"))) {
            "Invalid checkout reference."
        }
        val response = invoke("flutterwave-confirm", JSONObject().put("reference", reference))
        return response.optString("status") == "fulfilled"
    }
}
