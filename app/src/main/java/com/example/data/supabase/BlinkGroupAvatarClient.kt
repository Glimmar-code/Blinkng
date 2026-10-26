package com.example.data.supabase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Server-enforced group image permissions; the client cannot appoint an admin. */
internal object BlinkGroupAvatarClient {
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun setAvatar(conversationId: String, avatarUrl: String): Boolean =
        withContext(Dispatchers.IO) {
            val groupId = runCatching { UUID.fromString(conversationId).toString() }
                .getOrNull() ?: return@withContext false
            val token = SupabaseService.accessToken()?.takeIf(String::isNotBlank)
                ?: return@withContext false
            val request = Request.Builder()
                .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/blink_set_group_avatar")
                .header("apikey", SupabaseConfig.anonKey)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .post(JSONObject()
                    .put("p_conversation_id", groupId)
                    .put("p_avatar_url", avatarUrl)
                    .toString().toRequestBody(mediaType))
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful &&
                    response.body?.string()?.trim()?.equals("true", true) == true
            }
        }
}
