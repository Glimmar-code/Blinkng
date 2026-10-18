package com.example.data.supabase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class BlinkPaystackCheckout(val checkoutUrl: String, val reference: String, val kind: String)

/** All payment amounts and wallet/badge effects are controlled by Supabase, not Android. */
class BlinkPaystackCheckoutService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private suspend fun invoke(name: String, body: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val session = SupabaseService()
            if (!session.restoreSession()) error("Sign in to BLINK to make a payment.")

            fun request(): Request {
                val access = SupabaseService.accessToken()?.takeIf { it.isNotBlank() }
                    ?: error("Please sign in to BLINK.")
                return Request.Builder()
                    .url("${SupabaseConfig.url.trimEnd('/')}/functions/v1/$name")
                    .header("apikey", SupabaseConfig.anonKey)
                    .header("Authorization", "Bearer $access")
                    .header("Content-Type", "application/json")
                    .post(body.toString().toRequestBody(jsonType))
                    .build()
            }

            var response = client.newCall(request()).execute()
            if (response.code == 401) {
                response.close()
                if (!session.refreshSession()) error("Session expired. Sign in again.")
                response = client.newCall(request()).execute()
            }
            response.use {
                val raw = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    val reason = runCatching { JSONObject(raw).optString("error") }.getOrDefault("")
                    error(when (reason) {
                        "CASH_CHECKOUT_DISABLED" -> "Cash checkout is not enabled yet."
                        "GOLD_FOLLOWER_REQUIREMENT_NOT_MET" -> "Gold verification requires 1,000 followers."
                        "ALREADY_GOLD_VERIFIED" -> "Gold verification is already active."
                        "PAYMENT_RATE_LIMIT" -> "Too many payment attempts. Try later."
                        "INVALID_COIN_PACK" -> "This coin package is unavailable."
                        "ORDER_NOT_FOUND" -> "This payment isn't associated with your account."
                        else -> "Secure payment isn't available right now."
                    })
                }
                JSONObject(raw)
            }
        }

    suspend fun initialize(kind: String, packId: String? = null): BlinkPaystackCheckout {
        require(kind in setOf("BLUE_VERIFICATION","GOLD_VERIFICATION","COIN_PACK")) {
            "Choose a supported payment."
        }
        val body = JSONObject().put("kind", kind)
        if (packId != null) body.put("pack_id", packId)
        val result = invoke("paystack-initialize", body)
        val link = result.optString("checkoutUrl")
        val reference = result.optString("reference")
        val uri = java.net.URI(link)
        check(uri.scheme == "https" &&
            uri.host == "checkout.paystack.com" &&
            uri.port == -1 &&
            uri.rawUserInfo == null &&
            reference.matches(Regex("^BLINK-PS-[0-9a-f]{32}$"))) {
            "Invalid secure checkout link."
        }
        return BlinkPaystackCheckout(link, reference, kind)
    }

    suspend fun confirm(reference: String): Boolean {
        require(reference.matches(Regex("^BLINK-PS-[0-9a-f]{32}$"))) {
            "Invalid payment reference."
        }
        return invoke("paystack-verify", JSONObject().put("reference", reference))
            .optString("status") == "fulfilled"
    }
}
