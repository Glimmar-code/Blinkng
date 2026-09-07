package com.example.call

import android.util.Log
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CallRepository {
    companion object {
        private const val TAG = "CallRepository"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = SupabaseConfig.url.trimEnd('/')
    private val anonKey = SupabaseConfig.anonKey

    fun currentUserId(): String = SupabaseService().getCurrentUserId().orEmpty()

    suspend fun startCall(
        conversationId: String,
        calleeId: String,
        type: CallType
    ): Result<BlinkCall> = rpcCall(
        "start_call",
        JSONObject()
            .put("p_conversation_id", conversationId)
            .put("p_callee_id", calleeId)
            .put("p_call_type", type.wireValue)
    )

    suspend fun answerCall(callId: String): Result<BlinkCall> {
        val result = rpcCall("answer_call", JSONObject().put("p_call_id", callId))
        if (result.isSuccess) {
            // Tell every signed-in device for this receiver that another device answered,
            // so stale incoming-call notifications stop ringing immediately.
            dispatchPush(callId, "answered")
                .onFailure { Log.w(TAG, "Unable to synchronize answered call across devices", it) }
        }
        return result
    }

    suspend fun declineCall(callId: String): Result<BlinkCall> =
        rpcCall("decline_call", JSONObject().put("p_call_id", callId))

    suspend fun markConnected(callId: String): Result<BlinkCall> =
        rpcCall("mark_call_connected", JSONObject().put("p_call_id", callId))

    suspend fun endCall(callId: String, reason: String = "ended"): Result<BlinkCall> =
        rpcCall(
            "end_call",
            JSONObject().put("p_call_id", callId).put("p_reason", reason.take(120))
        )

    suspend fun expireCall(callId: String): Result<BlinkCall> {
        val result = rpcCall("expire_call", JSONObject().put("p_call_id", callId))
        val expired = result.getOrNull()
        if (
            expired?.status == CallStatus.MISSED &&
            expired.callerId == currentUserId()
        ) {
            // The caller owns the missed-call push. This avoids the receiver notifying the
            // caller about the caller's own unanswered attempt when both apps are polling.
            dispatchPush(callId, "missed")
                .onFailure { Log.w(TAG, "Unable to dispatch missed call notification", it) }
        }
        return result
    }

    suspend fun fetchCall(callId: String): Result<BlinkCall> = withContext(Dispatchers.IO) {
        runCatching {
            val response = executeAuthorized {
                Request.Builder()
                    .url("$baseUrl/rest/v1/calls?id=eq.$callId&select=*")
                    .get()
            }
            response.use {
                val body = it.body?.string().orEmpty()
                if (!it.isSuccessful) throw CallApiException(parseError(body, "Unable to load call."), it.code)
                val array = JSONArray(body)
                if (array.length() == 0) throw CallApiException("Call was not found.", 404)
                BlinkCall.fromJson(array.getJSONObject(0))
            }
        }
    }

    suspend fun fetchPeer(userId: String): Result<CallPeer> = withContext(Dispatchers.IO) {
        runCatching {
            val response = executeAuthorized {
                Request.Builder()
                    .url("$baseUrl/rest/v1/profiles?id=eq.$userId&select=id,username,full_name,avatar_url")
                    .get()
            }
            response.use {
                val body = it.body?.string().orEmpty()
                if (!it.isSuccessful) throw CallApiException(parseError(body, "Unable to load caller."), it.code)
                val array = JSONArray(body)
                if (array.length() == 0) return@runCatching CallPeer(userId)
                val row = array.getJSONObject(0)
                CallPeer(
                    id = row.optString("id", userId),
                    username = row.optString("username"),
                    name = row.optString("full_name").ifBlank { row.optString("username", "Blink user") },
                    avatar = row.optString("avatar_url")
                )
            }
        }
    }

    suspend fun sendSignal(callId: String, kind: String, payload: JSONObject): Result<CallSignal> =
        withContext(Dispatchers.IO) {
            runCatching {
                val userId = currentUserId()
                if (userId.isBlank()) throw CallApiException("You need to sign in again before calling.", 401)
                val body = JSONObject()
                    .put("call_id", callId)
                    .put("sender_id", userId)
                    .put("kind", kind)
                    .put("payload", payload)
                val response = executeAuthorized {
                    Request.Builder()
                        .url("$baseUrl/rest/v1/call_signals")
                        .addHeader("Prefer", "return=representation")
                        .post(body.toString().toRequestBody(jsonMediaType))
                }
                response.use {
                    val responseBody = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        throw CallApiException(parseError(responseBody, "Unable to send call signal."), it.code)
                    }
                    val array = JSONArray(responseBody)
                    if (array.length() == 0) throw CallApiException("Call signal was not saved.", 500)
                    CallSignal.fromJson(array.getJSONObject(0))
                }
            }
        }

    suspend fun fetchSignals(callId: String, afterId: Long = 0L): Result<List<CallSignal>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = executeAuthorized {
                    Request.Builder()
                        .url(
                            "$baseUrl/rest/v1/call_signals" +
                                "?call_id=eq.$callId&id=gt.$afterId&select=*&order=id.asc&limit=1000"
                        )
                        .get()
                }
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        throw CallApiException(parseError(body, "Unable to synchronize call signaling."), it.code)
                    }
                    val rows = JSONArray(body)
                    buildList {
                        for (index in 0 until rows.length()) add(CallSignal.fromJson(rows.getJSONObject(index)))
                    }
                }
            }
        }

    suspend fun dispatchPush(callId: String, event: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("call_id", callId).put("event", event)
            val response = executeAuthorized {
                Request.Builder()
                    .url("$baseUrl/functions/v1/send-call-notification")
                    .post(body.toString().toRequestBody(jsonMediaType))
            }
            response.use {
                val responseBody = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    throw CallApiException(parseError(responseBody, "Unable to notify the other caller."), it.code)
                }
                val json = runCatching { JSONObject(responseBody) }.getOrNull()
                if (json?.optBoolean("ok", true) == false) {
                    Log.w(TAG, "Call push returned ok=false: ${responseBody.take(400)}")
                }
            }
        }
    }

    suspend fun fetchRecentCalls(limit: Int = 50): Result<List<BlinkCall>> = withContext(Dispatchers.IO) {
        runCatching {
            val safeLimit = limit.coerceIn(1, 100)
            val response = executeAuthorized {
                Request.Builder()
                    .url("$baseUrl/rest/v1/calls?select=*&order=created_at.desc&limit=$safeLimit")
                    .get()
            }
            response.use {
                val body = it.body?.string().orEmpty()
                if (!it.isSuccessful) throw CallApiException(parseError(body, "Unable to load call history."), it.code)
                val rows = JSONArray(body)
                buildList {
                    for (index in 0 until rows.length()) add(BlinkCall.fromJson(rows.getJSONObject(index)))
                }
            }
        }
    }

    private suspend fun rpcCall(name: String, payload: JSONObject): Result<BlinkCall> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = executeAuthorized {
                    Request.Builder()
                        .url("$baseUrl/rest/v1/rpc/$name")
                        .post(payload.toString().toRequestBody(jsonMediaType))
                }
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (!it.isSuccessful) throw CallApiException(parseError(body, "Call request failed."), it.code)
                    parseCall(body)
                }
            }
        }

    private fun parseCall(body: String): BlinkCall {
        val trimmed = body.trim()
        if (trimmed.startsWith("[")) {
            val array = JSONArray(trimmed)
            if (array.length() == 0) throw CallApiException("Call was not returned by the server.", 500)
            return BlinkCall.fromJson(array.getJSONObject(0))
        }
        return BlinkCall.fromJson(JSONObject(trimmed))
    }

    private suspend fun executeAuthorized(builder: () -> Request.Builder): okhttp3.Response {
        val firstToken = SupabaseService.accessToken()?.takeIf { it.isNotBlank() }
            ?: throw CallApiException("Your session has expired. Sign in again before calling.", 401)
        var response = withContext(Dispatchers.IO) {
            client.newCall(
                builder()
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $firstToken")
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .build()
            ).execute()
        }
        if (response.code != 401) return response

        response.close()
        if (!refreshSession()) {
            throw CallApiException("Your session has expired. Sign in again before calling.", 401)
        }
        val refreshedToken = SupabaseService.accessToken()?.takeIf { it.isNotBlank() }
            ?: throw CallApiException("Your session has expired. Sign in again before calling.", 401)
        response = withContext(Dispatchers.IO) {
            client.newCall(
                builder()
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $refreshedToken")
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .build()
            ).execute()
        }
        return response
    }

    private fun refreshSession(): Boolean {
        val refreshToken = SupabaseService.refreshToken()?.takeIf { it.isNotBlank() } ?: return false
        return try {
            val body = JSONObject().put("refresh_token", refreshToken)
            val request = Request.Builder()
                .url("$baseUrl/auth/v1/token?grant_type=refresh_token")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val json = JSONObject(response.body?.string().orEmpty())
                val access = json.optString("access_token")
                val refresh = json.optString("refresh_token")
                if (access.isBlank()) return false
                SupabaseService.saveSession(access, refresh.ifBlank { refreshToken })
                true
            }
        } catch (error: Exception) {
            Log.w(TAG, "Unable to refresh call session", error)
            false
        }
    }

    private fun parseError(body: String, fallback: String): String {
        return try {
            val json = JSONObject(body)
            val raw = json.optString("message").ifBlank {
                json.optString("error_description").ifBlank { json.optString("error") }
            }
            when {
                raw.contains("USER_BUSY", true) -> "This person is already on another call."
                raw.contains("CALL_BLOCKED", true) -> "Calling is unavailable for this conversation."
                raw.contains("NOT_CONVERSATION_PARTICIPANT", true) -> "You can only call someone in an active conversation."
                raw.contains("CALL_NOT_RINGING", true) -> "This call is no longer ringing."
                raw.contains("AUTHENTICATION_REQUIRED", true) -> "Your session has expired. Sign in again."
                raw.isNotBlank() -> raw
                else -> fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }
}

class CallApiException(message: String, val statusCode: Int) : Exception(message)