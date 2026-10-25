package com.example.data.supabase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Membership is always server-owned; selecting a student sends an invitation, not a forced join. */
data class BlinkPendingGroupInvite(
    val groupId: String,
    val groupTitle: String,
    val inviterUsername: String,
)

object BlinkGroupInvitationClient {
    private val http = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private suspend fun rpc(name: String, body: JSONObject): String =
        withContext(Dispatchers.IO) {
            val token = SupabaseService.accessToken()?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Sign in to manage group invitations.")
            val request = Request.Builder()
                .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/$name")
                .header("apikey", SupabaseConfig.anonKey)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .post(body.toString().toRequestBody(jsonType))
                .build()
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful)
                    throw IllegalStateException("Group invitations are temporarily unavailable (HTTP ${response.code}).")
                text
            }
        }

    suspend fun createGroup(title: String, studentIds: List<String>): String {
        require(title.trim().length in 2..80) { "Enter a group name of 2–80 characters." }
        require(studentIds.distinct().size in 2..30) { "Choose 2–30 different students." }
        require(studentIds.all { runCatching { UUID.fromString(it) }.isSuccess }) {
            "Invalid student selection."
        }
        val result = rpc("blink_create_invite_only_group",
            JSONObject().put("p_title", title.trim())
                .put("p_member_ids", JSONArray(studentIds.distinct())))
        // PostgREST serializes uuid scalar returns as a JSON string.
        return result.trim().trim('"').takeIf { runCatching { UUID.fromString(it) }.isSuccess }
            ?: throw IllegalStateException("The server did not confirm group creation.")
    }

    suspend fun pendingInvitations(): List<BlinkPendingGroupInvite> {
        val rows = JSONArray(rpc("blink_my_group_invitations", JSONObject().put("p_limit", 20)))
        return (0 until rows.length()).mapNotNull { position ->
            val entry = rows.optJSONObject(position) ?: return@mapNotNull null
            val id = entry.optString("group_id")
            if (runCatching { UUID.fromString(id) }.isFailure) return@mapNotNull null
            BlinkPendingGroupInvite(
                groupId = id,
                groupTitle = entry.optString("group_title"),
                inviterUsername = entry.optString("inviter_username"),
            )
        }
    }

    suspend fun respond(groupId: String, accept: Boolean): Boolean {
        if (runCatching { UUID.fromString(groupId) }.isFailure) return false
        return rpc("blink_respond_to_group_invite",
            JSONObject().put("p_group_id", groupId).put("p_accept", accept)).trim() == "true"
    }
}
