from pathlib import Path
import re

SERVICE = Path("app/src/main/java/com/example/data/supabase/SupabaseService.kt")
REPOSITORY = Path("app/src/main/java/com/example/data/repository/PostRepository.kt")

service = SERVICE.read_text()

cursor_anchor = "    private val refreshMutex = Mutex()\n"
cursor_fields = """    private val refreshMutex = Mutex()
    private val discoveryCursorMutex = Mutex()
    private val discoveryOffsets = mutableMapOf<String, Int>()
    private val discoveryAsOf = mutableMapOf<String, String>()
"""
if "private val discoveryCursorMutex = Mutex()" not in service:
    if cursor_anchor not in service:
        raise SystemExit("SupabaseService cursor anchor not found")
    service = service.replace(cursor_anchor, cursor_fields, 1)

pattern = re.compile(
    r'''        val rpcName = if \(searchQuery\.isNullOrBlank\(\)\) "get_feed_page" else "search_feed_page"\n'''
    r'''        val rpcBody = JSONObject\(\)\.apply \{.*?'''
    r'''        val postsRaw = executeRequest\(.*?'''
    r'''            JSONArray\(if \(raw\.isBlank\(\)\) "\[\]" else raw\)\n'''
    r'''        \}\n\n        val userIds = buildSet \{''',
    re.S,
)

replacement = r'''        val normalizedFeedType = feedType
            .removePrefix("ranked_")
            .lowercase(Locale.US)
            .takeIf { it == "posts" || it == "reels" || it == "all" }
            ?: "posts"
        val useRankedDiscovery = searchQuery.isNullOrBlank() && feedType.startsWith("ranked_")

        val postsRaw: JSONArray = if (useRankedDiscovery) {
            val isInitialPage = beforeCreatedAt.isNullOrBlank() && beforeId.isNullOrBlank()
            val cursorKey = normalizedFeedType
            val (offset, asOf) = discoveryCursorMutex.withLock {
                if (isInitialPage) {
                    discoveryOffsets[cursorKey] = 0
                    discoveryAsOf.remove(cursorKey)
                }
                (discoveryOffsets[cursorKey] ?: 0) to discoveryAsOf[cursorKey]
            }

            val rpcBody = JSONObject().apply {
                put("p_limit", limit.coerceIn(1, 60))
                put("p_offset", offset)
                put("p_as_of", asOf ?: JSONObject.NULL)
                put("p_feed_type", normalizedFeedType)
            }

            val rankedRows = executeRequest(
                newRequestBuilder(
                    "/rest/v1/rpc/get_ranked_feed_page",
                    authenticated = true
                )
                    .addHeader("Content-Type", "application/json")
                    .post(rpcBody.toString().toRequestBody(jsonMediaType))
                    .build()
            ).use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(
                        parseSupabaseError(raw, "Ranked discovery feed fetch failed.")
                    )
                }
                JSONArray(if (raw.isBlank()) "[]" else raw)
            }

            val items = JSONArray()
            var resolvedAsOf = asOf
            var nextOffset = offset
            for (index in 0 until rankedRows.length()) {
                val ranked = rankedRows.optJSONObject(index) ?: continue
                ranked.optJSONObject("item")?.let(items::put)
                ranked.cleanString("as_of")
                    .takeIf { it.isNotBlank() }
                    ?.let { resolvedAsOf = it }
                nextOffset = maxOf(nextOffset, ranked.optInt("next_offset", nextOffset))
            }

            discoveryCursorMutex.withLock {
                discoveryOffsets[cursorKey] = nextOffset
                resolvedAsOf
                    ?.takeIf { it.isNotBlank() }
                    ?.let { discoveryAsOf[cursorKey] = it }
            }
            items
        } else {
            val rpcName = if (searchQuery.isNullOrBlank()) "get_feed_page" else "search_feed_page"
            val rpcBody = JSONObject().apply {
                put("p_limit", limit.coerceIn(1, 60))
                put("p_before", beforeCreatedAt ?: JSONObject.NULL)
                put("p_before_id", beforeId ?: JSONObject.NULL)
                if (searchQuery.isNullOrBlank()) {
                    put("p_feed_type", normalizedFeedType)
                } else {
                    put("p_query", searchQuery.trim())
                }
            }

            executeRequest(
                newRequestBuilder(
                    "/rest/v1/rpc/$rpcName",
                    authenticated = true
                )
                    .addHeader("Content-Type", "application/json")
                    .post(rpcBody.toString().toRequestBody(jsonMediaType))
                    .build()
            ).use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(parseSupabaseError(raw, "Feed page fetch failed."))
                }
                JSONArray(if (raw.isBlank()) "[]" else raw)
            }
        }

        val userIds = buildSet {'''

if "get_ranked_feed_page" not in service:
    service, count = pattern.subn(replacement, service, count=1)
    if count != 1:
        raise SystemExit(f"SupabaseService feed block replacement count={count}")

SERVICE.write_text(service)

repo = REPOSITORY.read_text()
replacements = {
    'true -> supabaseService.fetchFeedPage(limit = 30, feedType = "reels")':
        'true -> supabaseService.fetchFeedPage(limit = 30, feedType = "ranked_reels")',
    'false -> supabaseService.fetchFeedPage(limit = 30, feedType = "posts")':
        'false -> supabaseService.fetchFeedPage(limit = 30, feedType = "ranked_posts")',
    'null -> supabaseService.fetchFeedPosts()':
        'null -> supabaseService.fetchFeedPage(limit = 60, feedType = "ranked_all")',
    'feedType = if (isReel) "reels" else "posts"':
        'feedType = if (isReel) "ranked_reels" else "ranked_posts"',
}

for old, new in replacements.items():
    if old in repo:
        repo = repo.replace(old, new, 1)
    elif new not in repo:
        raise SystemExit(f"PostRepository anchor missing: {old}")

REPOSITORY.write_text(repo)

print("Weighted discovery feed is wired into initial and paginated Home/Reels loads.")
