package com.blinkng.desktop.data

import com.blinkng.shared.BlinkSearchPhase3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

enum class DesktopDiscoveryType(val backend: String, val label: String) {
    PROFILE("profile", "People"), POST("post", "Posts"), REEL("reel", "Reels"),
    COMMUNITY("community", "Communities"), EVENT("event", "Events"), PAGE("page", "Pages"),
    MARKET_ITEM("market_item", "Market");

    companion object { fun from(value: String) = entries.firstOrNull { it.backend == value } }
}

enum class DesktopDiscoverySort(val backend: String, val label: String) {
    RELEVANT("relevant", "Most relevant"), RECENT("recent", "Most recent"),
    TRENDING("trending", "Trending now"), GROWING("growing", "Fastest growing"), DISTANCE("distance", "Closest");
}

data class DesktopSearchCapabilities(
    val ready: Boolean = false,
    val imageSimilarity: Boolean = false,
    val matchedMoments: Boolean = false,
    val autoplay: Boolean = false,
    val syncedHistory: Boolean = false,
    val saved: Boolean = false,
    val following: Boolean = false,
)

data class DesktopDiscoveryResult(
    val type: DesktopDiscoveryType,
    val id: String,
    val title: String,
    val subtitle: String,
    val body: String,
    val imageUrl: String?,
    val avatarUrl: String?,
    val username: String,
    val videoUrl: String?,
    val score: Double,
    val reason: String,
    val asOf: String,
    val mutualCount: Int,
    val distanceKm: Double?,
    val trendPercent: Double,
    val matchedMomentMs: Int?,
    val saved: Boolean,
    val following: Boolean,
    val verified: Boolean,
    val memberCount: Int,
    val attendeeCount: Int,
    val followerCount: Int,
    val price: Long?,
    val currency: String,
)

data class DesktopDiscoveryCursor(val score: Double, val type: String, val id: String, val asOf: String)
data class DesktopDiscoveryPage(val rows: List<DesktopDiscoveryResult>, val cursor: DesktopDiscoveryCursor?, val hasMore: Boolean)
data class DesktopSearchHistory(val id: String, val query: String, val category: String, val lastSearchedAt: String)

class DesktopSearchPhase3Repository(private val client: DesktopSupabaseClient) {
    private val baseUrl = DesktopBackendConfig.url.trimEnd('/')
    private val anonKey = DesktopBackendConfig.anonKey
    private val media = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun capabilities(): DesktopSearchCapabilities = withContext(Dispatchers.IO) {
        val obj = rpcObject(BlinkSearchPhase3.CAPABILITIES_RPC, JSONObject())
        DesktopSearchCapabilities(
            ready = obj.optBoolean("communities") && obj.optBoolean("events") && obj.optBoolean("pages_brands") && obj.optBoolean("marketplace") && obj.optBoolean("cursor_pagination"),
            imageSimilarity = obj.optBoolean("image_similarity"),
            matchedMoments = obj.optBoolean("reel_matched_moments"),
            autoplay = obj.optBoolean("autoplay_previews"),
            syncedHistory = obj.optBoolean("synced_history"),
            saved = obj.optBoolean("saved"),
            following = obj.optBoolean("following"),
        )
    }

    suspend fun search(
        query: String,
        type: DesktopDiscoveryType?,
        sort: DesktopDiscoverySort,
        followingOnly: Boolean,
        savedOnly: Boolean,
        cursor: DesktopDiscoveryCursor? = null,
        limit: Int = 30,
    ): DesktopDiscoveryPage = withContext(Dispatchers.IO) {
        val types = type?.let { listOf(it.backend) } ?: BlinkSearchPhase3.resultTypes
        val body = JSONObject()
            .put("p_query", query.trim())
            .put("p_types", JSONArray(types))
            .put("p_limit", limit.coerceIn(1, 60))
            .put("p_following_only", followingOnly)
            .put("p_saved_only", savedOnly)
            .put("p_sort", sort.backend)
            .put("p_lat", JSONObject.NULL)
            .put("p_lng", JSONObject.NULL)
            .put("p_cursor_score", cursor?.score ?: JSONObject.NULL)
            .put("p_cursor_type", cursor?.type ?: JSONObject.NULL)
            .put("p_cursor_id", cursor?.id ?: JSONObject.NULL)
            .put("p_as_of", cursor?.asOf ?: JSONObject.NULL)
        pageFrom(JSONArray(rpc(BlinkSearchPhase3.DISCOVERY_RPC, body)), limit, false)
    }

    suspend fun history(limit: Int = 20): List<DesktopSearchHistory> = withContext(Dispatchers.IO) {
        val rows = JSONArray(rpc(BlinkSearchPhase3.HISTORY_GET_RPC, JSONObject().put("p_limit", limit)))
        (0 until rows.length()).mapNotNull { index ->
            rows.optJSONObject(index)?.let {
                DesktopSearchHistory(it.optString("id"), it.optString("query"), it.optString("category", "all"), it.optString("last_searched_at"))
            }
        }
    }

    suspend fun saveHistory(query: String, category: String, followingOnly: Boolean, savedOnly: Boolean, sort: DesktopDiscoverySort, privateMode: Boolean) = withContext(Dispatchers.IO) {
        if (query.isBlank() || privateMode) return@withContext
        rpc(
            BlinkSearchPhase3.HISTORY_UPSERT_RPC,
            JSONObject()
                .put("p_query", query.trim())
                .put("p_category", category)
                .put("p_filters", JSONObject().put("following_only", followingOnly).put("saved_only", savedOnly).put("sort", sort.backend))
                .put("p_private", false),
        )
    }

    suspend fun visualSearch(file: File, limit: Int = 30): DesktopDiscoveryPage = withContext(Dispatchers.IO) {
        val embedding = visualDescriptor(file)
        require(embedding.size == BlinkSearchPhase3.VISUAL_DESCRIPTOR_DIMENSIONS) { "Blink could not read that image." }
        val rows = JSONArray(
            rpc(
                BlinkSearchPhase3.IMAGE_SEARCH_RPC,
                JSONObject().put("p_embedding", JSONArray(embedding.map { it.toDouble() })).put("p_limit", limit),
            )
        )
        pageFrom(rows, limit, true)
    }

    suspend fun kickIndexers() = withContext(Dispatchers.IO) {
        runCatching { edge(BlinkSearchPhase3.MEDIA_INDEXER_FUNCTION, JSONObject().put("limit", 6)) }
        runCatching { edge(BlinkSearchPhase3.REEL_INDEXER_FUNCTION, JSONObject().put("limit", 1)) }
    }

    private fun visualDescriptor(file: File): FloatArray {
        val original = ImageIO.read(file) ?: return FloatArray(0)
        val scaled = BufferedImage(16, 8, BufferedImage.TYPE_INT_RGB)
        val graphics = scaled.createGraphics()
        try { graphics.drawImage(original.getScaledInstance(16, 8, Image.SCALE_SMOOTH), 0, 0, null) } finally { graphics.dispose() }
        val values = FloatArray(BlinkSearchPhase3.VISUAL_DESCRIPTOR_DIMENSIONS)
        var offset = 0
        for (y in 0 until 8) for (x in 0 until 16) {
            val rgb = scaled.getRGB(x, y)
            val r = ((rgb shr 16) and 0xff) / 255f
            val g = ((rgb shr 8) and 0xff) / 255f
            val b = (rgb and 0xff) / 255f
            values[offset++] = r
            values[offset++] = g
            values[offset++] = b
            values[offset++] = 0.2126f * r + 0.7152f * g + 0.0722f * b
        }
        return BlinkSearchPhase3.normalizeDescriptor(values)
    }

    private fun pageFrom(rows: JSONArray, limit: Int, visual: Boolean): DesktopDiscoveryPage {
        val parsed = (0 until rows.length()).mapNotNull { parse(rows.optJSONObject(it)) }
        val last = parsed.lastOrNull()
        val cursor = if (visual || last == null || last.asOf.isBlank()) null else DesktopDiscoveryCursor(last.score, last.type.backend, last.id, last.asOf)
        return DesktopDiscoveryPage(parsed, cursor, !visual && parsed.size >= limit && cursor != null)
    }

    private fun parse(row: JSONObject?): DesktopDiscoveryResult? {
        row ?: return null
        val type = DesktopDiscoveryType.from(row.optString("result_type")) ?: return null
        val payload = row.optJSONObject("payload") ?: JSONObject()
        val author = payload.optJSONObject("author_profile") ?: payload.optJSONObject("author") ?: JSONObject()
        val username = when (type) {
            DesktopDiscoveryType.PROFILE -> payload.optString("username")
            DesktopDiscoveryType.MARKET_ITEM -> payload.optString("seller_username")
            else -> author.optString("username")
        }
        val title = when (type) {
            DesktopDiscoveryType.PROFILE -> payload.optString("full_name").ifBlank { username }
            DesktopDiscoveryType.POST, DesktopDiscoveryType.REEL -> author.optString("full_name").ifBlank { username }
            DesktopDiscoveryType.COMMUNITY, DesktopDiscoveryType.PAGE -> payload.optString("name")
            DesktopDiscoveryType.EVENT, DesktopDiscoveryType.MARKET_ITEM -> payload.optString("title")
        }
        val subtitle = when (type) {
            DesktopDiscoveryType.PROFILE -> listOf(payload.optString("department"), payload.optString("university")).filter(String::isNotBlank).joinToString(" · ")
            DesktopDiscoveryType.POST, DesktopDiscoveryType.REEL -> payload.optString("caption").ifBlank { payload.optString("text") }
            DesktopDiscoveryType.PAGE -> "@${payload.optString("handle").removePrefix("@")}"
            DesktopDiscoveryType.EVENT -> payload.optString("venue").ifBlank { payload.optString("location_label") }
            else -> payload.optString("category")
        }
        val image = when (type) {
            DesktopDiscoveryType.POST, DesktopDiscoveryType.REEL -> first(payload, "images", "image_url")
            DesktopDiscoveryType.MARKET_ITEM -> first(payload, "image_urls", "image_url")
            DesktopDiscoveryType.COMMUNITY, DesktopDiscoveryType.PAGE -> payload.optString("cover_url").takeIf(String::isNotBlank)
            DesktopDiscoveryType.EVENT -> payload.optString("banner_url").takeIf(String::isNotBlank)
            else -> null
        }
        val avatar = when (type) {
            DesktopDiscoveryType.PROFILE -> payload.optString("avatar_url")
            DesktopDiscoveryType.POST, DesktopDiscoveryType.REEL -> author.optString("avatar_url")
            DesktopDiscoveryType.COMMUNITY, DesktopDiscoveryType.PAGE -> payload.optString("avatar_url")
            DesktopDiscoveryType.MARKET_ITEM -> payload.optString("seller_avatar")
            else -> ""
        }.takeIf(String::isNotBlank)
        return DesktopDiscoveryResult(
            type, row.optString("result_id"), title, subtitle,
            when (type) { DesktopDiscoveryType.PROFILE -> payload.optString("bio"); DesktopDiscoveryType.POST, DesktopDiscoveryType.REEL -> payload.optString("text").ifBlank { payload.optString("caption") }; else -> payload.optString("description") },
            image, avatar, username, payload.optString("video_url").takeIf(String::isNotBlank),
            row.optDouble("relevance_score"), row.optString("ranking_reason"), row.optString("as_of"), row.optInt("mutual_count"),
            if (row.has("distance_km") && !row.isNull("distance_km")) row.optDouble("distance_km") else null,
            row.optDouble("trend_percent", payload.optDouble("growth_percent")),
            if (row.has("matched_moment_ms") && !row.isNull("matched_moment_ms")) row.optInt("matched_moment_ms") else null,
            row.optBoolean("is_saved", payload.optBoolean("saved")),
            row.optBoolean("is_following", payload.optBoolean("following", payload.optBoolean("joined", payload.optBoolean("attending")))),
            payload.optBoolean("is_verified") || payload.optBoolean("seller_is_verified"),
            payload.optInt("member_count"), payload.optInt("attendee_count"), payload.optInt("follower_count"),
            payload.optLong("price").takeIf { payload.has("price") && !payload.isNull("price") }, payload.optString("currency", "NGN"),
        )
    }

    private fun first(obj: JSONObject, arrayKey: String, scalarKey: String): String? {
        obj.optJSONArray(arrayKey)?.let { a -> for (i in 0 until a.length()) a.optString(i).takeIf(String::isNotBlank)?.let { return it } }
        return obj.optString(scalarKey).takeIf(String::isNotBlank)
    }

    private suspend fun activeToken(): String = client.restoreSession()?.accessToken ?: client.session?.accessToken ?: error("Search requires a signed-in Blink account.")

    private suspend fun rpcObject(name: String, body: JSONObject): JSONObject {
        val raw = rpc(name, body).trim()
        return if (raw.startsWith("[")) JSONArray(raw).optJSONObject(0) ?: JSONObject() else JSONObject(raw.ifBlank { "{}" })
    }

    private suspend fun rpc(name: String, body: JSONObject): String {
        val token = activeToken()
        val request = Request.Builder().url("$baseUrl/rest/v1/rpc/$name")
            .addHeader("apikey", anonKey).addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json").post(body.toString().toRequestBody(media)).build()
        return withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException(runCatching { JSONObject(raw).optString("message") }.getOrDefault("").ifBlank { "Blink Search returned HTTP ${response.code}." })
                raw
            }
        }
    }

    private suspend fun edge(name: String, body: JSONObject) {
        val token = activeToken()
        val request = Request.Builder().url("$baseUrl/functions/v1/$name")
            .addHeader("apikey", anonKey).addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json").post(body.toString().toRequestBody(media)).build()
        withContext(Dispatchers.IO) { http.newCall(request).execute().use { } }
    }
}
