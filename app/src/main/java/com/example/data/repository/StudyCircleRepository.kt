package com.example.data.repository

import com.example.data.models.StudyCircleJoinRequest
import com.example.data.models.StudyCircleSummary
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

class StudyCircleRepository {
    private val jsonType = "application/json".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun fetchCircles(query: String = "", limit: Int = 40, offset: Int = 0): List<StudyCircleSummary> =
        withContext(Dispatchers.IO) {
            val raw = rpc(
                "get_study_circles_page",
                JSONObject()
                    .put("p_query", query.trim().ifBlank { JSONObject.NULL })
                    .put("p_limit", limit.coerceIn(1, 60))
                    .put("p_offset", offset.coerceAtLeast(0))
            )
            val rows = JSONArray(if (raw.isBlank()) "[]" else raw)
            buildList {
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    add(
                        StudyCircleSummary(
                            id = row.optString("id"),
                            ownerId = row.optString("owner_id"),
                            name = row.optString("name"),
                            description = row.optString("description"),
                            faculty = row.optString("faculty"),
                            course = row.optString("course"),
                            maxMembers = row.optInt("max_members", 50),
                            isPrivate = row.optBoolean("is_private", false),
                            memberCount = row.optInt("member_count", 0),
                            isMember = row.optBoolean("is_member", false),
                            isOwner = row.optBoolean("is_owner", false),
                            requestId = row.optString("request_id").takeIf { it.isNotBlank() && it != "null" },
                            requestStatus = row.optString("request_status").takeIf { it.isNotBlank() && it != "null" },
                            createdAt = row.optString("created_at")
                        )
                    )
                }
            }
        }

    suspend fun fetchOwnerRequests(limit: Int = 100): List<StudyCircleJoinRequest> = withContext(Dispatchers.IO) {
        val raw = rpc(
            "get_my_study_circle_join_requests",
            JSONObject().put("p_limit", limit.coerceIn(1, 200))
        )
        val rows = JSONArray(if (raw.isBlank()) "[]" else raw)
        buildList {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                add(
                    StudyCircleJoinRequest(
                        requestId = row.optString("request_id"),
                        circleId = row.optString("circle_id"),
                        circleName = row.optString("circle_name"),
                        requesterId = row.optString("requester_id"),
                        requesterUsername = row.optString("requester_username"),
                        requesterFullName = row.optString("requester_full_name"),
                        requesterAvatarUrl = row.optString("requester_avatar_url"),
                        status = row.optString("status"),
                        createdAt = row.optString("created_at")
                    )
                )
            }
        }
    }

    suspend fun createCircle(
        name: String,
        description: String,
        faculty: String,
        course: String,
        maxMembers: Int,
        isPrivate: Boolean
    ): String = rpc(
        "create_study_circle",
        JSONObject()
            .put("p_name", name.trim())
            .put("p_description", description.trim().ifBlank { JSONObject.NULL })
            .put("p_faculty", faculty.trim().ifBlank { JSONObject.NULL })
            .put("p_course", course.trim().ifBlank { JSONObject.NULL })
            .put("p_max_members", maxMembers.coerceIn(2, 200))
            .put("p_is_private", isPrivate)
    ).trim().removeSurrounding("\"")

    suspend fun joinOrRequest(circleId: String): String = rpc(
        "request_study_circle_join",
        JSONObject().put("p_circle_id", circleId)
    ).trim().removeSurrounding("\"")

    suspend fun leave(circleId: String): Boolean =
        rpc("leave_study_circle", JSONObject().put("p_circle_id", circleId)).let { true }

    suspend fun cancelRequest(requestId: String): Boolean =
        rpc("cancel_study_circle_request", JSONObject().put("p_request_id", requestId)).isNotBlank()

    suspend fun respondRequest(requestId: String, accept: Boolean): Boolean =
        rpc(
            "respond_study_circle_request",
            JSONObject().put("p_request_id", requestId).put("p_accept", accept)
        ).isNotBlank()

    private suspend fun rpc(name: String, body: JSONObject): String = withContext(Dispatchers.IO) {
        val token = SupabaseService.accessToken() ?: throw IllegalStateException("Please sign in again.")
        val request = Request.Builder()
            .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/$name")
            .addHeader("apikey", SupabaseConfig.anonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonType))
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(raw).optString("message") }.getOrNull().orEmpty()
                throw IllegalStateException(message.ifBlank { "$name failed (${response.code})." })
            }
            raw
        }
    }
}
