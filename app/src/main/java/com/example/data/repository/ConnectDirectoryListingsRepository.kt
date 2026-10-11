package com.example.data.repository

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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class DirectoryConnectListing(
    val id: String,
    val userId: String,
    val title: String,
    val description: String
)

data class DirectoryConnectApplication(
    val id: String,
    val listingId: String,
    val applicantId: String,
    val message: String,
    val status: String
)

/**
 * Specialist Connect categories persist separately, using the existing secure
 * connect_listings/connect_applications tables and a category_slug column.
 * The backend defaults user_id/applicant_id to auth.uid(); the app never supplies
 * or overrides either identity on insert.
 */
class ConnectDirectoryListingsRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = SupabaseConfig.url.trimEnd('/')

    suspend fun listings(slug: String): List<DirectoryConnectListing> = withContext(Dispatchers.IO) {
        val rows = read(
            "/rest/v1/connect_listings?select=id,user_id,title,description" +
                "&category_slug=eq.${encode(slug)}&is_active=eq.true&order=created_at.desc&limit=80"
        )
        buildList {
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                add(
                    DirectoryConnectListing(
                        id = row.optString("id"),
                        userId = row.optString("user_id"),
                        title = row.optString("title"),
                        description = row.optString("description")
                    )
                )
            }
        }
    }

    suspend fun applicationsForOwnListings(listings: List<DirectoryConnectListing>, userId: String):
        List<DirectoryConnectApplication> = withContext(Dispatchers.IO) {
        val ownIds = listings.filter { it.userId == userId }.map { it.id }
        if (ownIds.isEmpty()) return@withContext emptyList()
        val rows = read(
            "/rest/v1/connect_applications?select=id,listing_id,applicant_id,message,status" +
                "&listing_id=in.(${ownIds.joinToString(",")})&order=created_at.desc&limit=80"
        )
        buildList {
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                add(
                    DirectoryConnectApplication(
                        id = row.optString("id"),
                        listingId = row.optString("listing_id"),
                        applicantId = row.optString("applicant_id"),
                        message = row.optString("message"),
                        status = row.optString("status")
                    )
                )
            }
        }
    }

    suspend fun create(categorySlug: String, title: String, description: String) = withContext(Dispatchers.IO) {
        require(title.trim().length in 3..90) { "Title must be 3–90 characters." }
        require(description.length <= 800) { "Description is too long." }
        val listingType = when (categorySlug) {
            "study_partners", "research_partners", "accountability_partners" -> "study_mate"
            "project_teammates", "founders_builders", "freelance_collaborators",
            "event_partners" -> "project_partner"
            "skill_exchange" -> "skill_swap"
            "campus_communities" -> "campus_guide"
            "relocation_support" -> "housing_need"
            else -> "mentor_available"
        }
        mutate(
            "/rest/v1/connect_listings",
            JSONObject()
                .put("category_slug", categorySlug)
                .put("listing_type", listingType)
                .put("title", title.trim())
                .put("description", description.trim())
                .put("is_active", true),
            "POST"
        )
    }

    suspend fun apply(listingId: String, message: String) = withContext(Dispatchers.IO) {
        mutate(
            "/rest/v1/connect_applications",
            JSONObject()
                .put("listing_id", listingId)
                .put("message", message.take(500)),
            "POST"
        )
    }

    suspend fun respond(applicationId: String, accept: Boolean) = withContext(Dispatchers.IO) {
        mutate(
            "/rest/v1/connect_applications?id=eq.${encode(applicationId)}",
            JSONObject().put("status", if (accept) "accepted" else "declined"),
            "PATCH"
        )
    }

    private fun read(path: String): JSONArray {
        client.newCall(authenticatedRequest(path).get().build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(serverMessage(response.code, raw))
            return if (raw.isBlank()) JSONArray() else JSONArray(raw)
        }
    }

    private fun mutate(path: String, body: JSONObject, method: String) {
        val builder = authenticatedRequest(path)
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=minimal")
        val requestBody = body.toString().toRequestBody(jsonType)
        val request = if (method == "PATCH") builder.patch(requestBody).build()
                      else builder.post(requestBody).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(serverMessage(response.code, raw))
        }
    }

    private fun authenticatedRequest(path: String): Request.Builder {
        val token = SupabaseService.accessToken()
            ?: throw IllegalStateException("Please sign in again.")
        return Request.Builder()
            .url(baseUrl + path)
            .addHeader("apikey", SupabaseConfig.anonKey)
            .addHeader("Authorization", "Bearer $token")
    }

    private fun serverMessage(code: Int, raw: String): String {
        val postgresCode = runCatching { JSONObject(raw).optString("code") }.getOrNull()
        return when {
            code == 401 -> "Your session expired. Please sign in again."
            postgresCode == "23505" || code == 409 -> "You already requested this listing."
            postgresCode == "42501" || code == 403 ->
                "This request is not allowed. You cannot apply to your own or inactive listing."
            postgresCode == "42703" -> "Connect directory is being updated. Please try again later."
            else -> "Could not save your Connect request. Please retry."
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
