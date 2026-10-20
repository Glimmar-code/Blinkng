package com.example.data.supabase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class BlinkPaystackCheckout(val url: String, val reference: String, val kind: String)
data class BlinkPaystackReceipt(
    val reference: String,
    val kind: String,
    val itemCode: String,
    val amountNgn: Int,
    val status: String,
    val createdAt: String,
    val months: Int,
)

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

    suspend fun createCheckout(kind: String, itemCode: String, months: Int = 1): BlinkPaystackCheckout {
        require(kind in setOf("verification", "coin_pack")) { "Invalid purchase." }
        require(itemCode.matches(Regex("^[a-zA-Z0-9_]{1,30}$"))) { "Invalid selection." }
        require(months in setOf(1, 3, 6, 12) && (kind == "verification" || months == 1)) {
            "Select an available one-time purchase duration."
        }
        val response = invoke("paystack-checkout", JSONObject()
            .put("kind", kind)
            .put("itemCode", itemCode).put("months", months))
        val url = response.optString("checkoutUrl")
        val reference = response.optString("reference")
        check(url.startsWith("https://checkout.paystack.com/") &&
            reference.matches(Regex("^blink-ps-[0-9a-f]{32}$"))) {
            "Secure checkout could not be opened."
        }
        return BlinkPaystackCheckout(url, reference, kind)
    }

    suspend fun listReceipts(): List<BlinkPaystackReceipt> {
        val data = invoke("paystack-history", JSONObject())
        val entries = data.optJSONArray("receipts") ?: JSONArray()
        return (0 until entries.length()).mapNotNull { index ->
            val item = entries.optJSONObject(index) ?: return@mapNotNull null
            BlinkPaystackReceipt(
                reference = item.optString("reference"),
                kind = item.optString("purchase_kind"),
                itemCode = item.optString("item_code"),
                amountNgn = item.optInt("amount_ngn"),
                status = item.optString("status"),
                createdAt = item.optString("created_at").take(10),
                months = if (item.optString("purchase_kind") == "verification") {
                    item.optInt("duration_months", item.optInt("duration_days", 30) / 30)
                } else 0,
            )
        }
    }

    suspend fun renewalStatus(): JSONObject =
        invoke("paystack-autorenew", JSONObject().put("action", "status"))

    suspend fun enableRenewal(): JSONObject {
        val paidReference = listReceipts()
            .firstOrNull { it.kind == "verification" && it.status == "fulfilled" }
            ?.reference ?: error("Complete a Paystack verification payment first.")
        return invoke("paystack-autorenew", JSONObject()
            .put("action", "enable")
            .put("reference", paidReference)
            .put("agreeToRecurring", true)
            .put("consentVersion", "v1.2026-10"))
    }

    suspend fun stopRenewal(): JSONObject =
        invoke("paystack-autorenew", JSONObject().put("action", "cancel"))

    suspend fun createBoostCheckout(
        targetType: String, targetId: String, boostPower: Int,
        objective: String, audienceScope: String, durationDays: Int,
        targetUniversity: String?,
    ): BlinkPaystackCheckout {
        require(targetType in setOf("POST", "REEL", "PROFILE", "LISTING"))
        require(targetId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        require(boostPower in 1..100 && durationDays in setOf(1, 3, 7, 14, 30))
        val result = invoke("paystack-checkout", JSONObject()
            .put("kind", "boost").put("itemCode", "BOOST").put("months", 1)
            .put("targetType", targetType).put("targetId", targetId)
            .put("boostPower", boostPower).put("objective", objective)
            .put("audienceScope", audienceScope)
            .put("durationDays", durationDays)
            .put("targetUniversity", targetUniversity))
        val url = result.optString("checkoutUrl")
        val reference = result.optString("reference")
        check(url.startsWith("https://checkout.paystack.com/") &&
            reference.matches(Regex("^blink-ps-[0-9a-f]{32}$")))
        return BlinkPaystackCheckout(url, reference, "boost")
    }

    /** Only available when the verified merchant has separately enabled Market checkout. */
    suspend fun createMarketCheckout(listingId: String): BlinkPaystackCheckout {
        require(listingId.matches(Regex("^[0-9a-fA-F-]{36}$")))
        val result = invoke("paystack-checkout", JSONObject()
            .put("kind", "marketplace").put("itemCode", "MARKET_ITEM")
            .put("listingId", listingId))
        val url = result.optString("checkoutUrl")
        val ref = result.optString("reference")
        check(url.startsWith("https://checkout.paystack.com/") &&
            ref.matches(Regex("^blink-ps-[0-9a-f]{32}$")))
        return BlinkPaystackCheckout(url, ref, "marketplace")
    }

    /** Coin gifts credit the selected recipient only after Paystack confirms funds. */
    suspend fun createGiftCheckout(
        recipientUsername: String, packId: String, message: String = ""
    ): BlinkPaystackCheckout {
        require(recipientUsername.matches(Regex("^[a-zA-Z0-9_.]{2,40}$")))
        require(packId.matches(Regex("^coins_[0-9]+$")))
        require(message.length <= 160)
        val result = invoke("paystack-checkout", JSONObject()
            .put("kind", "gift").put("itemCode", "GIFT_COINS")
            .put("recipientUsername", recipientUsername)
            .put("packId", packId).put("message", message))
        val url = result.optString("checkoutUrl")
        val ref = result.optString("reference")
        check(url.startsWith("https://checkout.paystack.com/") &&
            ref.matches(Regex("^blink-ps-[0-9a-f]{32}$")))
        return BlinkPaystackCheckout(url, ref, "gift")
    }

    suspend fun requestRefund(reference: String, reason: String): JSONObject {
        require(reference.matches(Regex("^blink-ps-[0-9a-f]{32}$")))
        require(reason.trim().length in 10..1000)
        return invoke("paystack-market-ops", JSONObject()
            .put("action", "request_refund")
            .put("reference", reference).put("reason", reason.trim()))
    }

    suspend fun confirmMarketDelivery(reference: String): JSONObject {
        require(reference.matches(Regex("^blink-ps-[0-9a-f]{32}$")))
        return invoke("paystack-market-ops", JSONObject()
            .put("action", "confirm_delivery").put("reference", reference))
    }

    suspend fun confirmPayment(reference: String): Boolean {
        require(reference.matches(Regex("^blink-ps-[0-9a-f]{32}$"))) {
            "Invalid payment reference."
        }
        return invoke("paystack-confirm", JSONObject().put("reference", reference))
            .optString("status") == "fulfilled"
    }
}
