package com.example.data.repository

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

/**
 * Small authenticated RPC client for profile-to-profile actions that do not
 * belong to the feed itself. The database remains the authority for friend
 * requests and coin transfers; failures simply return false so UI taps never
 * crash the feed or Reels surface.
 */
class UserInteractionRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = SupabaseConfig.url.trimEnd('/')

    suspend fun sendFriendRequest(profileId: String): Boolean = withContext(Dispatchers.IO) {
        val target = profileId.trim()
        if (target.isBlank()) return@withContext false
        runCatching {
            rpc("send_friend_request", JSONObject().put("p_receiver_id", target)).isNotBlank()
        }.getOrDefault(false)
    }

    suspend fun giftCoins(profileId: String, amount: Int = 10): Boolean = withContext(Dispatchers.IO) {
        val target = profileId.trim()
        if (target.isBlank() || amount !in 1..1000) return@withContext false
        runCatching {
            rpc(
                "gift_blink_coins",
                JSONObject()
                    .put("p_receiver_id", target)
                    .put("p_amount", amount)
            ).toLongOrNull() != null
        }.getOrDefault(false)
    }

    private fun rpc(name: String, body: JSONObject): String {
        val token = SupabaseService.accessToken()
            ?: throw IllegalStateException("Please sign in again.")
        val request = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/$name")
            .header("apikey", SupabaseConfig.anonKey)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(jsonType))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty().trim()
            if (!response.isSuccessful) {
                throw IllegalStateException("$name failed (${response.code})")
            }
            return raw.removeSurrounding("\"")
        }
    }
}
