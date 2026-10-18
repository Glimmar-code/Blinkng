package com.example.data.supabase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class BlinkPaystackCheckout(val url: String, val reference: String, val kind: String)

/**
 * BLINK Paystack bridge. Never carries secret keys or client-controlled prices.
 * All charges and fulfilment are authorized on the Supabase backend.
 */
class BlinkPaystackCheckoutService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private suspend fun invoke(function: String, payload: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val session = SupabaseService()
            if (!session.restoreSession()) error("Please sign in to BLINK to pay.")

            fun request(): Request {
                val token = SupabaseService.accessToken()?.takeIf { it.isNotBlank() }
                    ?: error("Please sign in to BLINK to pay.")
                return Request.Builder()
                    .url("${SupabaseConfig.url.trimEnd('/')}/functions/v1/$function")
                    .header("apikey", SupabaseConfig.anonKey)
                    .header("Authorization", "Bearer $token")
                    .post(payload.toString().toRequestBody(mediaType))
                    .build()
            }

            var response = client.newCall(request()).execute()
            if (response.code == 401) {
                response.close()
                if (!session.refreshSession()) error("Your BLINK session has expired.")
                response = client.newCall(request()).execute()
            }
            response.use {
                val raw = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    val code = runCatching { JSONObject(raw).optString("error") }.getOrDefault("")
                    error(
                        when (code) {
                            "CHECKOUT_DISABLED" -> "Paystack checkout is not enabled yet."
                            "GOLD_FOLLOWER_REQUIREMENT_NOT_MET" -> "Gold verification requires 1,000 followers."
                            "PAYMENT_RATE_LIMIT" -> "Too many payment attempts. Try again later."
                            "INVALID_COIN_PACK" -> "Please select an available coin pack."
                            else -> "Secure payment is unavailable right now."
                        }
                    )
                }
                JSONObject(raw)
            }
        }

    suspend fun createCheckout(kind: String, itemCode: String): BlinkPaystackCheckout {
        require(kind in setOf("verification", "coin_pack")) { "Invalid purchase." }
        require(itemCode.matches(Regex("^[a-zA-Z0-9_]{1,30}$"))) { "Invalid selection." }
        val response = invoke("paystack-checkout", JSONObject()
            .put("kind", kind)
            .put("itemCode", itemCode))
        val url = response.optString("checkoutUrl")
        val reference = response.optString("reference")
        check(url.startsWith("https://checkout.paystack.com/") &&
            reference.matches(Regex("^blink-ps-[0-9a-f]{32}$"))) {
            "Secure checkout could not be opened."
        }
        return BlinkPaystackCheckout(url, reference, kind)
    }

    suspend fun confirmPayment(reference: String): Boolean {
        require(reference.matches(Regex("^blink-ps-[0-9a-f]{32}$"))) {
            "Invalid payment reference."
        }
        return invoke("paystack-confirm", JSONObject().put("reference", reference))
            .optString("status") == "fulfilled"
    }
}
