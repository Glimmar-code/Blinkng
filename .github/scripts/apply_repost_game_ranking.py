from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count} in {path}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# ---------------------------------------------------------------------------
# FeedPost model: repost state and distribution attribution.
# ---------------------------------------------------------------------------
post_model = ROOT / "app/src/main/java/com/example/data/models/PostModel.kt"
replace_once(
    post_model,
    """    var commentsCount: Int,\n    var sharesCount: Int,\n    var viewsCount: Int = 0,\n""",
    """    var commentsCount: Int,\n    var sharesCount: Int,\n    var repostsCount: Int = 0,\n    var isRepostedByMe: Boolean = false,\n    val repostId: String? = null,\n    val repostedById: String? = null,\n    val repostedByUsername: String? = null,\n    var viewsCount: Int = 0,\n""",
    "FeedPost repost fields",
)


# ---------------------------------------------------------------------------
# Supabase service: parse repost metadata, toggle repost RPC, game-only ranking.
# ---------------------------------------------------------------------------
supabase = ROOT / "app/src/main/java/com/example/data/supabase/SupabaseService.kt"
replace_once(
    supabase,
    """            commentsCount = obj.optInt(\"comments_count\", obj.optInt(\"comment_count\", 0)),\n            sharesCount = obj.optInt(\"shares_count\", obj.optInt(\"share_count\", 0)),\n            viewsCount = obj.optInt(\"views_count\", obj.optInt(\"view_count\", 0)),\n""",
    """            commentsCount = obj.optInt(\"comments_count\", obj.optInt(\"comment_count\", 0)),\n            sharesCount = obj.optInt(\"shares_count\", obj.optInt(\"share_count\", 0)),\n            repostsCount = obj.optInt(\"repost_count\", obj.optInt(\"reposts_count\", 0)),\n            isRepostedByMe = obj.optBoolean(\"is_reposted_by_me\", false),\n            repostId = obj.cleanString(\"repost_id\").takeIf { it.isNotBlank() },\n            repostedById = obj.cleanString(\"reposted_by_id\").takeIf { it.isNotBlank() },\n            repostedByUsername = obj.cleanString(\"reposted_by_username\").takeIf { it.isNotBlank() },\n            viewsCount = obj.optInt(\"views_count\", obj.optInt(\"view_count\", 0)),\n""",
    "parse repost metadata",
)

replace_once(
    supabase,
    """    suspend fun togglePostBookmark(postId: String, bookmarked: Boolean): Boolean = withContext(Dispatchers.IO) {\n""",
    """    suspend fun togglePostRepost(postId: String): Pair<Boolean, Int>? = withContext(Dispatchers.IO) {\n        try {\n            if (!isValidUuid(postId)) return@withContext null\n            val body = JSONObject().apply { put(\"p_post_id\", postId) }\n            val raw = executeRequest(\n                newRequestBuilder(\"/rest/v1/rpc/toggle_post_repost\", true)\n                    .addHeader(\"Content-Type\", \"application/json\")\n                    .post(body.toString().toRequestBody(jsonMediaType))\n                    .build()\n            ).use { resp ->\n                val responseBody = resp.body?.string().orEmpty()\n                if (!resp.isSuccessful) {\n                    throw IllegalStateException(parseSupabaseError(responseBody, \"Repost update failed.\"))\n                }\n                responseBody\n            }\n            val payload = raw.trim().ifBlank { \"{}\" }\n            val obj = if (payload.startsWith(\"{\")) {\n                JSONObject(payload)\n            } else {\n                JSONObject(payload.removeSurrounding(\"\\\"\").replace(\"\\\\\\\"\", \"\\\"\"))\n            }\n            obj.optBoolean(\"reposted\", false) to obj.optInt(\"repostCount\", 0)\n        } catch (e: Exception) {\n            Log.e(TAG, \"togglePostRepost failed\", e)\n            null\n        }\n    }\n\n    suspend fun togglePostBookmark(postId: String, bookmarked: Boolean): Boolean = withContext(Dispatchers.IO) {\n""",
    "toggle repost RPC",
)

replace_once(
    supabase,
    """    suspend fun fetchLeaderboard(): List<LeaderboardUser> = withContext(Dispatchers.IO) {\n""",
    """    suspend fun fetchGameLeaderboard(): List<LeaderboardUser> = withContext(Dispatchers.IO) {\n        try {\n            val raw = executeRequest(\n                newRequestBuilder(\"/rest/v1/game_rankings?select=*&order=score.desc,world_rank.asc&limit=50\", true)\n                    .get()\n                    .build()\n            ).use { resp ->\n                val body = resp.body?.string().orEmpty()\n                if (!resp.isSuccessful) return@withContext emptyList()\n                body\n            }\n            val arr = JSONArray(if (raw.isBlank()) \"[]\" else raw)\n            buildList {\n                for (i in 0 until arr.length()) {\n                    add(parseLeaderboardUser(arr.getJSONObject(i), i + 1))\n                }\n            }\n        } catch (e: Exception) {\n            Log.e(TAG, \"Game ranking fetch failed\", e)\n            emptyList()\n        }\n    }\n\n    suspend fun fetchLeaderboard(): List<LeaderboardUser> = withContext(Dispatchers.IO) {\n""",
    "game ranking fetch",
)


# ---------------------------------------------------------------------------
# Repository bridge.
# ---------------------------------------------------------------------------
post_repo = ROOT / "app/src/main/java/com/example/data/repository/PostRepository.kt"
replace_once(
    post_repo,
    """    suspend fun togglePostBookmark(postId: String, bookmarked: Boolean): Boolean = withContext(Dispatchers.IO) {\n        supabaseService.togglePostBookmark(postId, bookmarked)\n    }\n""",
    """    suspend fun togglePostRepost(postId: String): Pair<Boolean, Int>? = withContext(Dispatchers.IO) {\n        supabaseService.togglePostRepost(postId)\n    }\n\n    suspend fun togglePostBookmark(postId: String, bookmarked: Boolean): Boolean = withContext(Dispatchers.IO) {\n        supabaseService.togglePostBookmark(postId, bookmarked)\n    }\n""",
    "repository repost bridge",
)


# ---------------------------------------------------------------------------
# Post card: attribution + dedicated Repeat action, separate from Share.
# ---------------------------------------------------------------------------
post_card = ROOT / "app/src/main/java/com/example/ui/components/PostCard.kt"
replace_once(
    post_card,
    """    onBookmark: () -> Unit,\n    onShare: () -> Unit,\n""",
    """    onBookmark: () -> Unit,\n    onRepost: () -> Unit,\n    onShare: () -> Unit,\n""",
    "PostCard repost callback",
)

replace_once(
    post_card,
    """            Row(\n                Modifier\n                    .fillMaxWidth()\n                    .padding(14.dp),\n""",
    """            post.repostedByUsername?.takeIf { it.isNotBlank() }?.let { reposter ->\n                Row(\n                    modifier = Modifier\n                        .fillMaxWidth()\n                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),\n                    verticalAlignment = Alignment.CenterVertically\n                ) {\n                    Icon(\n                        Icons.Default.Repeat,\n                        contentDescription = null,\n                        tint = MaterialTheme.colorScheme.onSurfaceVariant,\n                        modifier = Modifier.size(15.dp)\n                    )\n                    Spacer(Modifier.width(6.dp))\n                    Text(\n                        \"@$reposter reposted\",\n                        color = MaterialTheme.colorScheme.onSurfaceVariant,\n                        fontWeight = FontWeight.SemiBold,\n                        fontSize = 11.sp\n                    )\n                }\n            }\n\n            Row(\n                Modifier\n                    .fillMaxWidth()\n                    .padding(14.dp),\n""",
    "PostCard repost attribution",
)

replace_once(
    post_card,
    """                TextButton(onClick = onBookmark) {\n                    Icon(\n                        imageVector = if (post.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,\n""",
    """                TextButton(onClick = onRepost) {\n                    Icon(\n                        Icons.Default.Repeat,\n                        contentDescription = \"Repost\",\n                        tint = if (post.isRepostedByMe) BlinkPurple else MaterialTheme.colorScheme.onSurfaceVariant,\n                        modifier = Modifier.size(18.dp)\n                    )\n                    Spacer(Modifier.width(3.dp))\n                    Text(\"${post.repostsCount}\", fontSize = 10.sp)\n                }\n\n                TextButton(onClick = onBookmark) {\n                    Icon(\n                        imageVector = if (post.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,\n""",
    "PostCard repost button",
)


# ---------------------------------------------------------------------------
# Feed screen: route the repost action into each PostCard.
# ---------------------------------------------------------------------------
feed_screen = ROOT / "app/src/main/java/com/example/ui/screens/FeedScreen.kt"
replace_once(
    feed_screen,
    """    onBookmarkPost: (String) -> Unit,\n    onSharePost: (String) -> Unit,\n""",
    """    onBookmarkPost: (String) -> Unit,\n    onRepostPost: (String) -> Unit,\n    onSharePost: (String) -> Unit,\n""",
    "FeedScreen repost callback",
)
replace_once(
    feed_screen,
    """                                        onBookmark = { onBookmarkPost(post.id) },\n                                        onShare = { onSharePost(post.id) },\n""",
    """                                        onBookmark = { onBookmarkPost(post.id) },\n                                        onRepost = { onRepostPost(post.id) },\n                                        onShare = { onSharePost(post.id) },\n""",
    "FeedScreen PostCard repost wiring",
)


# ---------------------------------------------------------------------------
# ViewModel: game ranking state/fetch + repost state updates.
# ---------------------------------------------------------------------------
view_model = ROOT / "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"
replace_once(
    view_model,
    """    val leaderboardUsers: List<LeaderboardUser> = emptyList(),\n""",
    """    val leaderboardUsers: List<LeaderboardUser> = emptyList(),\n    val gameLeaderboardUsers: List<LeaderboardUser> = emptyList(),\n""",
    "game ranking state",
)

replace_once(
    view_model,
    """                    val leaderboardRequest = async {\n                        runCatching { supabaseService.fetchLeaderboard() }\n                            .onFailure { Log.e(TAG, \"Leaderboard fetch failed\", it) }\n                    }\n""",
    """                    val leaderboardRequest = async {\n                        runCatching { supabaseService.fetchLeaderboard() }\n                            .onFailure { Log.e(TAG, \"Leaderboard fetch failed\", it) }\n                    }\n                    val gameLeaderboardRequest = async {\n                        runCatching { supabaseService.fetchGameLeaderboard() }\n                            .onFailure { Log.e(TAG, \"Game ranking fetch failed\", it) }\n                    }\n""",
    "game ranking async fetch",
)

replace_once(
    view_model,
    """                    val leaderboard = leaderboardRequest.await()\n                        .getOrDefault(before.leaderboardUsers)\n\n                    val connectHub = connectHubRequest.await()\n""",
    """                    val leaderboard = leaderboardRequest.await()\n                        .getOrDefault(before.leaderboardUsers)\n                    val gameLeaderboard = gameLeaderboardRequest.await()\n                        .getOrDefault(before.gameLeaderboardUsers)\n\n                    val connectHub = connectHubRequest.await()\n""",
    "game ranking await",
)

replace_once(
    view_model,
    """                        leaderboardUsers = leaderboard,\n                        connectHub = connectHub,\n""",
    """                        leaderboardUsers = leaderboard,\n                        gameLeaderboardUsers = gameLeaderboard,\n                        connectHub = connectHub,\n""",
    "game ranking state assignment",
)

replace_once(
    view_model,
    """    fun toggleBookmark(postId: String) {\n""",
    """    fun toggleRepost(postId: String) {\n        viewModelScope.launch {\n            val result = postRepository.togglePostRepost(postId)\n            if (result == null) {\n                showToast(\"Couldn't update repost.\")\n                return@launch\n            }\n            val (reposted, count) = result\n            fun update(items: List<FeedPost>): List<FeedPost> = items.map { post ->\n                if (post.id == postId) {\n                    post.copy(isRepostedByMe = reposted, repostsCount = count)\n                } else {\n                    post\n                }\n            }\n            val state = _uiState.value\n            _uiState.value = state.copy(\n                posts = update(state.posts),\n                reels = update(state.reels),\n                discoverPosts = update(state.discoverPosts)\n            )\n            persistCurrentFeed()\n            showToast(if (reposted) \"Reposted to your people.\" else \"Repost removed.\")\n        }\n    }\n\n    fun toggleBookmark(postId: String) {\n""",
    "ViewModel repost action",
)

replace_once(
    view_model,
    """            val live = runCatching { supabaseService.fetchLeaderboard() }\n                .getOrDefault(_uiState.value.leaderboardUsers)\n            _uiState.value = _uiState.value.copy(leaderboardUsers = live)\n""",
    """            val live = runCatching { supabaseService.fetchGameLeaderboard() }\n                .getOrDefault(_uiState.value.gameLeaderboardUsers)\n            _uiState.value = _uiState.value.copy(gameLeaderboardUsers = live)\n""",
    "trivia refreshes game ranking",
)

replace_once(
    view_model,
    """    fun recordGameResult(gameType: String, score: Int) {\n        runConnectAction(\"Game result synced.\") {\n            connectHubRepository.recordGameSession(gameType, score)\n        }\n    }\n""",
    """    fun recordGameResult(gameType: String, score: Int) {\n        runConnectAction(\"Game result synced.\") {\n            connectHubRepository.recordGameSession(gameType, score)\n            val live = runCatching { supabaseService.fetchGameLeaderboard() }\n                .getOrDefault(_uiState.value.gameLeaderboardUsers)\n            _uiState.value = _uiState.value.copy(gameLeaderboardUsers = live)\n        }\n    }\n""",
    "game result refreshes game ranking",
)


# ---------------------------------------------------------------------------
# Activity: Game tab gets its own ranking, feed gets repost callback.
# ---------------------------------------------------------------------------
main_activity = ROOT / "app/src/main/java/com/example/MainActivity.kt"
replace_once(
    main_activity,
    """                        leaderboardUsers = uiState.leaderboardUsers,\n""",
    """                        leaderboardUsers = uiState.gameLeaderboardUsers,\n""",
    "FeedScreen uses game ranking",
)
replace_once(
    main_activity,
    """                        onBookmarkPost = { viewModel.toggleBookmark(it) },\n                        onSharePost = { sharePostOrReel(it) },\n                        onOptionsClick = { viewModel.openPostOptions(it) },\n""",
    """                        onBookmarkPost = { viewModel.toggleBookmark(it) },\n                        onRepostPost = { viewModel.toggleRepost(it) },\n                        onSharePost = { sharePostOrReel(it) },\n                        onOptionsClick = { viewModel.openPostOptions(it) },\n""",
    "MainActivity repost wiring",
)

print("Repost distribution UI and game-only ranking source changes applied.")
