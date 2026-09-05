package com.example.data.repository

import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Single source of truth for the authenticated user's follow relationships.
 *
 * The profile button and Following feed observe the same StateFlow, so an
 * optimistic follow/unfollow instantly updates both surfaces while Supabase is
 * still confirming the RPC. A failed RPC rolls the state back.
 */
object FollowStateStore {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = SupabaseConfig.url.trimEnd('/')
    private val _followingIds = MutableStateFlow<Set<String>>(emptySet())
    val followingIds: StateFlow<Set<String>> = _followingIds.asStateFlow()

    suspend fun refresh(): Set<String> = withContext(Dispatchers.IO) {
        val token = SupabaseService.accessToken().orEmpty()
        if (token.isBlank()) return@withContext _followingIds.value

        val request = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/get_my_following_ids")
            .header("apikey", SupabaseConfig.anonKey)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .post("{}".toRequestBody(jsonMediaType))
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("Follow state refresh failed (${response.code})")
                val parsed = parseIds(raw)
                _followingIds.value = parsed
                parsed
            }
        }.getOrElse { _followingIds.value }
    }

    suspend fun setFollowing(profileId: String, shouldFollow: Boolean): Boolean = withContext(Dispatchers.IO) {
        val cleanId = profileId.trim()
        val token = SupabaseService.accessToken().orEmpty()
        if (cleanId.isBlank() || token.isBlank()) return@withContext false

        val before = _followingIds.value
        _followingIds.value = if (shouldFollow) before + cleanId else before - cleanId

        val functionName = if (shouldFollow) "follow_user" else "unfollow_user"
        val body = JSONObject().put("p_following_id", cleanId).toString()
        val request = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/$functionName")
            .header("apikey", SupabaseConfig.anonKey)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        val saved = runCatching {
            client.newCall(request).execute().use { response ->
                response.body?.close()
                response.isSuccessful
            }
        }.getOrDefault(false)

        if (!saved) {
            _followingIds.value = before
            return@withContext false
        }

        // Reconcile with the database so duplicate taps, RLS and cross-device
        // changes cannot leave the local button in the wrong state.
        refresh()
        true
    }

    fun isFollowing(profileId: String): Boolean = profileId.isNotBlank() && profileId in _followingIds.value

    private fun parseIds(raw: String): Set<String> {
        if (raw.isBlank() || raw == "null") return emptySet()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptySet()
        return buildSet {
            for (index in 0 until array.length()) {
                when (val item = array.opt(index)) {
                    is String -> item.takeIf { it.isNotBlank() }?.let(::add)
                    is JSONObject -> item.optString("following_id").takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
    }
}
