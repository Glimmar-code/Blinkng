package com.example.data.repository

import com.example.data.models.DiscoveryCapabilities
import com.example.data.models.DiscoveryCursor
import com.example.data.models.DiscoveryResult
import com.example.data.models.DiscoveryResultType
import com.example.data.models.DiscoverySearchPage
import com.example.data.models.DiscoverySearchRequest
import com.example.data.models.SearchHistoryEntry
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

/**
 * Isolated Phase 3 Search gateway.
 *
 * It intentionally talks only to search-owned RPC contracts. The existing feed/profile
 * repositories stay untouched, so Testlab can fall back to Phase 2 if the migration has
 * not yet been deployed to its backend.
 */
class SearchDiscoveryRepository {
    private val baseUrl = SupabaseConfig.url.trimEnd('/')
    private val anonKey = SupabaseConfig.anonKey
    private val json = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun fetchCapabilities(): Result<DiscoveryCapabilities> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = rpc("search_capabilities_v2", JSONObject())
            val obj = when {
                raw.trim().startsWith("{") -> JSONObject(raw)
                raw.trim().startsWith("[") -> JSONArray(raw).optJSONObject(0) ?: JSONObject()
                else -> JSONObject()
            }
            DiscoveryCapabilities(
                communities = obj.optBoolean("communities"),
                events = obj.optBoolean("events"),
                pagesBrands = obj.optBoolean("pages_brands"),
                marketplace = obj.optBoolean("marketplace"),
                saved = obj.optBoolean("saved"),
                following = obj.optBoolean("following"),
                syncedHistory = obj.optBoolean("synced_history"),
                mutualRanking = obj.optBoolean("mutual_ranking"),
                distanceSort = obj.optBoolean("distance_sort"),
                growthMetrics = obj.optBoolean("growth_metrics"),
                trendMetrics = obj.optBoolean("trend_metrics"),
                cursorPagination = obj.optBoolean("cursor_pagination"),
                reelMatchedMoments = obj.optBoolean("reel_matched_moments"),
                // A vector table alone is not enough: Blink still needs a real client/server
                // embedding producer. Keep UI capability-gated until that producer ships.
                imageSimilarity = false,
                autoplayPreviews = obj.optBoolean("autoplay_previews"),
                heroTransitions = obj.optBoolean("hero_transitions"),
            )
        }
    }

    suspend fun search(request: DiscoverySearchRequest): Result<DiscoverySearchPage> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("p_query", request.query.trim())
                put("p_types", JSONArray(request.types.map { it.backendValue }))
                put("p_limit", request.limit.coerceIn(1, 60))
                put("p_following_only", request.followingOnly)
                put("p_saved_only", request.savedOnly)
                put("p_sort", request.sort.backendValue)
                putNullable("p_lat", request.latitude)
                putNullable("p_lng", request.longitude)
                val cursor = request.cursor
                putNullable("p_cursor_score", cursor?.score)
                putNullable("p_cursor_type", cursor?.type)
                putNullable("p_cursor_id", cursor?.id)
                putNullable("p_as_of", cursor?.asOf)
            }
            val rows = JSONArray(rpc("search_discovery_v2", body))
            val results = buildList {
                for (index in 0 until rows.length()) {
                    parseResult(rows.optJSONObject(index) ?: continue)?.let(::add)
                }
            }
            val last = results.lastOrNull()
            val nextCursor = last?.let {
                DiscoveryCursor(
                    score = it.score,
                    type = it.type.backendValue,
                    id = it.id,
                    asOf = it.asOf,
                )
            }
            DiscoverySearchPage(
                results = results,
                nextCursor = nextCursor,
                hasMore = results.size >= request.limit.coerceIn(1, 60) && nextCursor != null,
            )
        }
    }

    suspend fun fetchHistory(limit: Int = 20): Result<List<SearchHistoryEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val rows = JSONArray(rpc("get_search_history_v2", JSONObject().put("p_limit", limit.coerceIn(1, 50))))
            buildList {
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: continue
                    add(
                        SearchHistoryEntry(
                            id = row.optString("id"),
                            query = row.optString("query"),
                            category = row.optString("category", "all"),
                            searchCount = row.optInt("search_count", 1),
                            pinned = row.optBoolean("pinned"),
                            lastSearchedAt = row.optString("last_searched_at"),
                        )
                    )
                }
            }
        }
    }

    suspend fun saveHistory(
        query: String,
        category: String,
        filters: JSONObject = JSONObject(),
        privateSearch: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (query.isBlank() || privateSearch) return@runCatching Unit
            rpc(
                "upsert_search_history_v2",
                JSONObject()
                    .put("p_query", query.trim())
                    .put("p_category", category.ifBlank { "all" })
                    .put("p_filters", filters)
                    .put("p_private", privateSearch),
            )
            Unit
        }
    }

    suspend fun toggleEntity(result: DiscoveryResult): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val (rpcName, key) = when (result.type) {
                DiscoveryResultType.COMMUNITY -> "toggle_community_membership_v2" to "p_community_id"
                DiscoveryResultType.EVENT -> "toggle_event_attendance_v2" to "p_event_id"
                DiscoveryResultType.PAGE -> "toggle_page_follow_v2" to "p_page_id"
                else -> error("No Phase 3 toggle is defined for ${result.type}")
            }
            val raw = rpc(rpcName, JSONObject().put(key, result.id)).trim()
            raw.equals("true", ignoreCase = true) || raw == "1" || raw.trim('"').equals("true", ignoreCase = true)
        }
    }

    /** Contract for the future visual model. UI does not call this until a real embedding producer exists. */
    suspend fun searchByImageEmbedding(embedding: FloatArray, limit: Int = 24): Result<List<DiscoveryResult>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject()
                    .put("p_embedding", JSONArray(embedding.map { it.toDouble() }))
                    .put("p_limit", limit.coerceIn(1, 60))
                val rows = JSONArray(rpc("search_image_similarity_v2", body))
                buildList {
                    for (index in 0 until rows.length()) {
                        val row = rows.optJSONObject(index) ?: continue
                        val type = DiscoveryResultType.fromBackend(row.optString("result_type")) ?: continue
                        add(
                            DiscoveryResult(
                                type = type,
                                id = row.optString("result_id"),
                                title = "Visual match",
                                imageUrl = row.optString("image_url").takeIf(String::isNotBlank),
                                score = row.optDouble("similarity", 0.0),
                                reason = "Visual similarity",
                            )
                        )
                    }
                }
            }
        }

    private fun parseResult(row: JSONObject): DiscoveryResult? {
        val type = DiscoveryResultType.fromBackend(row.optString("result_type")) ?: return null
        val payload = row.optJSONObject("payload") ?: JSONObject()
        val author = payload.optJSONObject("author_profile") ?: payload.optJSONObject("author") ?: JSONObject()

        val username = when (type) {
            DiscoveryResultType.PROFILE -> payload.optString("username")
            DiscoveryResultType.MARKET_ITEM -> payload.optString("seller_username")
            else -> author.optString("username")
        }
        val title = when (type) {
            DiscoveryResultType.PROFILE -> payload.optString("full_name").ifBlank { username }
            DiscoveryResultType.POST, DiscoveryResultType.REEL -> author.optString("full_name").ifBlank { username }
            DiscoveryResultType.COMMUNITY, DiscoveryResultType.PAGE -> payload.optString("name")
            DiscoveryResultType.EVENT, DiscoveryResultType.MARKET_ITEM -> payload.optString("title")
        }
        val subtitle = when (type) {
            DiscoveryResultType.PROFILE -> listOf(payload.optString("department"), payload.optString("university")).filter(String::isNotBlank).joinToString(" · ")
            DiscoveryResultType.POST, DiscoveryResultType.REEL -> payload.optString("caption").ifBlank { payload.optString("text") }
            DiscoveryResultType.COMMUNITY -> listOf(payload.optString("category"), payload.optString("university")).filter(String::isNotBlank).joinToString(" · ")
            DiscoveryResultType.EVENT -> listOf(payload.optString("venue"), payload.optString("location_label")).filter(String::isNotBlank).joinToString(" · ")
            DiscoveryResultType.PAGE -> "@${payload.optString("handle").removePrefix("@")}"
            DiscoveryResultType.MARKET_ITEM -> payload.optString("category")
        }
        val body = when (type) {
            DiscoveryResultType.PROFILE -> payload.optString("bio")
            DiscoveryResultType.POST, DiscoveryResultType.REEL -> payload.optString("text").ifBlank { payload.optString("caption") }
            else -> payload.optString("description")
        }
        val imageUrl = when (type) {
            DiscoveryResultType.PROFILE -> null
            DiscoveryResultType.POST, DiscoveryResultType.REEL -> firstImage(payload)
            DiscoveryResultType.COMMUNITY, DiscoveryResultType.PAGE -> payload.optString("cover_url").takeIf(String::isNotBlank)
            DiscoveryResultType.EVENT -> payload.optString("banner_url").takeIf(String::isNotBlank)
            DiscoveryResultType.MARKET_ITEM -> firstMarketImage(payload)
        }
        val avatarUrl = when (type) {
            DiscoveryResultType.PROFILE -> payload.optString("avatar_url")
            DiscoveryResultType.POST, DiscoveryResultType.REEL -> author.optString("avatar_url")
            DiscoveryResultType.COMMUNITY, DiscoveryResultType.PAGE -> payload.optString("avatar_url")
            DiscoveryResultType.MARKET_ITEM -> payload.optString("seller_avatar")
            DiscoveryResultType.EVENT -> ""
        }.takeIf(String::isNotBlank)

        return DiscoveryResult(
            type = type,
            id = row.optString("result_id"),
            title = title,
            subtitle = subtitle,
            body = body,
            imageUrl = imageUrl,
            avatarUrl = avatarUrl,
            username = username,
            videoUrl = payload.optString("video_url").takeIf(String::isNotBlank),
            score = row.optDouble("relevance_score", 0.0),
            reason = row.optString("ranking_reason"),
            asOf = row.optString("as_of"),
            mutualCount = row.optInt("mutual_count", payload.optInt("mutual_count", 0)),
            distanceKm = row.optNullableDouble("distance_km") ?: payload.optNullableDouble("distance_km"),
            trendPercent = row.optDouble("trend_percent", payload.optDouble("trend_percent", 0.0)),
            matchedMomentMs = row.optNullableInt("matched_moment_ms") ?: payload.optNullableInt("matched_moment_ms"),
            saved = row.optBoolean("is_saved", payload.optBoolean("is_saved", payload.optBoolean("saved"))),
            following = row.optBoolean("is_following", payload.optBoolean("is_following", payload.optBoolean("following", payload.optBoolean("joined", payload.optBoolean("attending"))))),
            verified = payload.optBoolean("is_verified") || payload.optBoolean("seller_is_verified"),
            memberCount = payload.optInt("member_count", 0),
            attendeeCount = payload.optInt("attendee_count", 0),
            followerCount = payload.optInt("follower_count", 0),
            price = payload.optLong("price").takeIf { payload.has("price") && !payload.isNull("price") },
            currency = payload.optString("currency", "NGN"),
            location = payload.optString("location_label").ifBlank { payload.optString("location") },
            startTime = payload.optString("starts_at"),
            category = payload.optString("category"),
            likeCount = payload.optInt("like_count", 0),
            commentCount = payload.optInt("comment_count", 0),
            shareCount = payload.optInt("share_count", 0),
            viewCount = payload.optInt("view_count", 0),
        )
    }

    private fun firstImage(payload: JSONObject): String? {
        payload.optJSONArray("images")?.let { array ->
            for (index in 0 until array.length()) {
                array.optString(index).takeIf(String::isNotBlank)?.let { return it }
            }
        }
        return payload.optString("image_url").takeIf(String::isNotBlank)
    }

    private fun firstMarketImage(payload: JSONObject): String? {
        payload.optJSONArray("image_urls")?.let { array ->
            for (index in 0 until array.length()) {
                array.optString(index).takeIf(String::isNotBlank)?.let { return it }
            }
        }
        return payload.optString("image_url").takeIf(String::isNotBlank)
    }

    private fun rpc(name: String, body: JSONObject): String {
        val token = SupabaseService.accessToken()?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Search requires an authenticated Blink session.")
        val request = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/$name")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(json))
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(raw).optString("message") }.getOrNull().orEmpty()
                throw SearchBackendUnavailableException(
                    statusCode = response.code,
                    message = message.ifBlank { "Blink Search backend returned HTTP ${response.code}." },
                )
            }
            return raw
        }
    }
}

class SearchBackendUnavailableException(
    val statusCode: Int,
    message: String,
) : IllegalStateException(message)

private fun JSONObject.putNullable(key: String, value: Any?) {
    put(key, value ?: JSONObject.NULL)
}

private fun JSONObject.optNullableDouble(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

private fun JSONObject.optNullableInt(key: String): Int? =
    if (!has(key) || isNull(key)) null else optInt(key)
