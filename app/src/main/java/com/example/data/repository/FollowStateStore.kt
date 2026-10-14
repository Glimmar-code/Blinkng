package com.example.data.repository

import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
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

    /**
     * All read/write operations share one lock. Without it, a delayed refresh
     * or a failed Follow RPC could overwrite a newer optimistic user action.
     */
    private val operationMutex = kotlinx.coroutines.sync.Mutex()
    private var sessionToken: String = ""

    private fun activateSession(token: String) {
        if (sessionToken != token) {
            sessionToken = token
            // Avoid showing the previous account's relationships while the
            // current user's list is being fetched.
            _followingIds.value = emptySet()
        }
    }

    private fun fetchLatestLocked(token: String): Set<String> {
        val request = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/get_my_following_ids")
            .header("apikey", SupabaseConfig.anonKey)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .post("{}".toRequestBody(jsonMediaType))
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("Follow state refresh failed (${response.code})")
                val parsed = parseIds(raw)
                val currentToken = SupabaseService.accessToken().orEmpty()
                if (!isCurrentFollowSession(token, currentToken)) {
                    activateSession(currentToken)
                    emptySet()
                } else {
                    _followingIds.value = parsed
                    parsed
                }
            }
        }.getOrElse {
            if (isCurrentFollowSession(token, SupabaseService.accessToken().orEmpty())) {
                _followingIds.value
            } else {
                activateSession(SupabaseService.accessToken().orEmpty())
                emptySet()
            }
        }
    }

    suspend fun refresh(): Set<String> = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val token = SupabaseService.accessToken().orEmpty()
            activateSession(token)
            if (token.isBlank()) emptySet() else fetchLatestLocked(token)
        }
    }

    suspend fun setFollowing(profileId: String, shouldFollow: Boolean): Boolean = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val cleanId = profileId.trim()
            val token = SupabaseService.accessToken().orEmpty()
            activateSession(token)
            if (cleanId.isBlank() || token.isBlank()) return@withLock false

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

            val currentToken = SupabaseService.accessToken().orEmpty()
            if (!isCurrentFollowSession(token, currentToken)) {
                // The user signed out or switched accounts before the response.
                // Never apply that old account's result to the new account.
                activateSession(currentToken)
                return@withLock false
            }
            if (!saved) {
                _followingIds.value = before
                return@withLock false
            }

            // Reconcile only while still in the same session; avoid deadlocking
            // by using the private locked variant instead of calling refresh().
            fetchLatestLocked(token)
            true
        }
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
