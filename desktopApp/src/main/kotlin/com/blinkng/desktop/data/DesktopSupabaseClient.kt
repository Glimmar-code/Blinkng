package com.blinkng.desktop.data

import com.blinkng.shared.BlinkBackendDefaults
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class DesktopSupabaseClient(
    private val sessionStore: DesktopSessionStore = DesktopSessionStore(),
) {
    private val baseUrl = BlinkBackendDefaults.resolve(
        System.getenv("SUPABASE_URL") ?: System.getenv("VITE_SUPABASE_URL"),
        BlinkBackendDefaults.SUPABASE_URL,
    ).trimEnd('/')
    private val anonKey = BlinkBackendDefaults.resolve(
        System.getenv("SUPABASE_ANON_KEY") ?: System.getenv("VITE_SUPABASE_ANON_KEY"),
        BlinkBackendDefaults.SUPABASE_ANON_KEY,
    )
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()
    private val profileCache = mutableMapOf<String, DesktopProfile>()

    @Volatile
    var session: DesktopSession? = sessionStore.load()
        private set

    suspend fun restoreSession(): DesktopSession? = withContext(Dispatchers.IO) {
        val stored = session ?: return@withContext null
        runCatching {
            ensureFreshSession()
            fetchProfile(requireSession().userId)
            session
        }.getOrElse {
            clearSession()
            null
        }
    }

    suspend fun signIn(email: String, password: String): DesktopSession = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        require(cleanEmail.isNotBlank()) { "Email is required." }
        require(password.isNotBlank()) { "Password is required." }
        val response = authPost(
            "/auth/v1/token?grant_type=password",
            JSONObject().put("email", cleanEmail).put("password", password),
        )
        saveAuthResponse(response, cleanEmail)
    }

    suspend fun signUp(
        email: String,
        password: String,
        username: String,
        fullName: String,
    ): DesktopSession = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val cleanUsername = username.trim().lowercase().removePrefix("@").replace(" ", "_")
        require(cleanEmail.contains("@")) { "Enter a valid email address." }
        require(cleanUsername.length >= 3) { "Username must be at least 3 characters." }
        require(password.length >= 8 && password.any(Char::isUpperCase) && password.any(Char::isLowerCase) && password.any(Char::isDigit)) {
            "Use at least 8 characters with uppercase, lowercase and a number."
        }
        val body = JSONObject()
            .put("email", cleanEmail)
            .put("password", password)
            .put(
                "data",
                JSONObject()
                    .put("username", cleanUsername)
                    .put("full_name", fullName.trim().ifBlank { cleanUsername })
                    .put("name", fullName.trim().ifBlank { cleanUsername }),
            )
        val response = authPost("/auth/v1/signup", body)
        if (response.optString("access_token").isBlank()) {
            throw IllegalStateException("Account created. Check your email to confirm it, then sign in.")
        }
        saveAuthResponse(response, cleanEmail)
    }

    suspend fun sendPasswordReset(email: String) = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        require(cleanEmail.contains("@")) { "Enter a valid email address." }
        val body = JSONObject().put("email", cleanEmail)
        System.getenv("BLINK_RESET_REDIRECT_URL")
            ?.trim()
            ?.takeIf { it.startsWith("https://") || it.startsWith("http://127.0.0.1") }
            ?.let { body.put("redirect_to", it) }
        authPost("/auth/v1/recover", body)
    }

    /** Google OAuth using a localhost PKCE callback; no client secret is stored in the app. */
    suspend fun signInWithGoogle(): DesktopSession = withContext(Dispatchers.IO) {
        require(Desktop.isDesktopSupported()) { "The default browser is unavailable." }
        val verifierBytes = ByteArray(64).also(SecureRandom()::nextBytes)
        val verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes)
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII)),
        )
        val codeFuture = CompletableFuture<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/callback") { exchange ->
            val params = parseQuery(exchange.requestURI.rawQuery.orEmpty())
            val code = params["code"]
            val error = params["error_description"] ?: params["error"]
            val success = !code.isNullOrBlank()
            val html = if (success) {
                "<html><body><h2>Blinkng sign-in complete</h2><p>You can return to Blinkng.</p></body></html>"
            } else {
                "<html><body><h2>Blinkng sign-in failed</h2><p>${escapeHtml(error ?: "No authorization code returned.")}</p></body></html>"
            }
            val bytes = html.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(if (success) 200 else 400, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            if (success) codeFuture.complete(code) else codeFuture.completeExceptionally(IllegalStateException(error ?: "Google sign-in failed."))
        }
        server.start()
        try {
            val redirect = "http://127.0.0.1:${server.address.port}/callback"
            val authorizeUrl = buildString {
                append("$baseUrl/auth/v1/authorize?provider=google")
                append("&redirect_to=${encode(redirect)}")
                append("&code_challenge=${encode(challenge)}")
                append("&code_challenge_method=s256")
            }
            Desktop.getDesktop().browse(URI(authorizeUrl))
            val code = codeFuture.get(3, TimeUnit.MINUTES)
            val response = authPost(
                "/auth/v1/token?grant_type=pkce",
                JSONObject().put("auth_code", code).put("code_verifier", verifier),
            )
            saveAuthResponse(response, response.optJSONObject("user")?.optString("email").orEmpty())
        } finally {
            server.stop(0)
        }
    }

    fun signOut() {
        clearSession()
    }

    suspend fun fetchProfile(userId: String = requireSession().userId): DesktopProfile = withContext(Dispatchers.IO) {
        profileCache[userId]?.let { return@withContext it }
        val rows = getArray(
            "/rest/v1/profiles?id=eq.${encode(userId)}&select=id,full_name,name,username,handle,avatar_url,university,faculty,department,bio,is_verified,verification_tier,follower_count,following_count,posts_count,current_wallet_balance,online_now,is_online,last_seen_at&limit=1",
        )
        val row = rows.optJSONObject(0) ?: throw IllegalStateException("Profile was not found.")
        parseProfile(row).also { profileCache[userId] = it }
    }

    suspend fun fetchFeed(reelsOnly: Boolean = false, search: String? = null): List<DesktopFeedPost> = withContext(Dispatchers.IO) {
        val filter = buildString {
            append("/rest/v1/feed_posts?select=id,user_id,text,caption,image_url,video_url,images,hashtags,like_count,comment_count,share_count,view_count,is_reel,created_at")
            append("&is_active=eq.true")
            if (reelsOnly) append("&is_reel=eq.true")
            search?.trim()?.takeIf { it.isNotBlank() }?.let { q ->
                append("&or=${encode("(text.ilike.*$q*,caption.ilike.*$q*)")}")
            }
            append("&order=created_at.desc&limit=60")
        }
        val rows = getArray(filter)
        val likedIds = fetchMyLikedPostIds()
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val userId = row.optString("user_id")
            val profile = runCatching { fetchProfile(userId) }.getOrNull()
            parseFeedPost(row, profile, likedIds.contains(row.optString("id")))
        }
    }

    suspend fun createPost(text: String, isReel: Boolean = false, videoUrl: String? = null): DesktopFeedPost = withContext(Dispatchers.IO) {
        val active = requireSession()
        val clean = text.trim()
        require(clean.isNotBlank() || !videoUrl.isNullOrBlank()) { "Write something or attach media." }
        val last = getArray(
            "/rest/v1/feed_posts?user_id=eq.${encode(active.userId)}&select=creator_post_number&order=creator_post_number.desc&limit=1",
        )
        val nextNumber = (last.optJSONObject(0)?.optInt("creator_post_number", 0) ?: 0) + 1
        val body = JSONObject()
            .put("user_id", active.userId)
            .put("text", clean.ifBlank { JSONObject.NULL })
            .put("caption", clean.ifBlank { JSONObject.NULL })
            .put("is_reel", isReel)
            .put("type", if (isReel) "video" else "text")
            .put("creator_post_number", nextNumber)
        if (!videoUrl.isNullOrBlank()) body.put("video_url", videoUrl)
        val created = postArray("/rest/v1/feed_posts", body, prefer = "return=representation")
            .optJSONObject(0) ?: throw IllegalStateException("Post was created but no row was returned.")
        parseFeedPost(created, fetchProfile(active.userId), false)
    }

    suspend fun toggleLike(postId: String): Boolean = withContext(Dispatchers.IO) {
        val active = requireSession()
        val query = "/rest/v1/post_likes?post_id=eq.${encode(postId)}&user_id=eq.${encode(active.userId)}&select=id&limit=1"
        val existing = getArray(query)
        if (existing.length() > 0) {
            delete("/rest/v1/post_likes?post_id=eq.${encode(postId)}&user_id=eq.${encode(active.userId)}")
            false
        } else {
            postArray(
                "/rest/v1/post_likes",
                JSONObject().put("post_id", postId).put("user_id", active.userId),
                prefer = "return=minimal",
            )
            true
        }
    }

    suspend fun fetchComments(postId: String): List<DesktopComment> = withContext(Dispatchers.IO) {
        val rows = getArray(
            "/rest/v1/comments?post_id=eq.${encode(postId)}&parent_comment_id=is.null&select=id,post_id,author_id,content,likes_count,created_at&order=created_at.asc&limit=200",
        )
        (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            val author = runCatching { fetchProfile(row.optString("author_id")) }.getOrNull()
            DesktopComment(
                id = row.optString("id"),
                postId = row.optString("post_id"),
                authorId = row.optString("author_id"),
                authorName = author?.fullName ?: author?.username ?: "Blink user",
                authorVerified = author?.isVerified == true,
                content = row.optString("content"),
                likesCount = row.optInt("likes_count"),
                createdAt = row.optString("created_at"),
            )
        }
    }

    suspend fun addComment(postId: String, content: String) = withContext(Dispatchers.IO) {
        require(content.trim().isNotBlank()) { "Comment cannot be empty." }
        postArray(
            "/rest/v1/comments",
            JSONObject().put("post_id", postId).put("content", content.trim()),
            prefer = "return=minimal",
        )
    }

    suspend fun search(query: String): DesktopSearchResults = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext DesktopSearchResults(emptyList(), emptyList())
        val encodedPattern = encode("*$clean*")
        val profiles = getArray(
            "/rest/v1/profiles?or=${encode("(full_name.ilike.*$clean*,username.ilike.*$clean*,handle.ilike.*$clean*)")}&select=id,full_name,name,username,handle,avatar_url,university,faculty,department,bio,is_verified,verification_tier,follower_count,following_count,posts_count,current_wallet_balance,online_now,is_online,last_seen_at&limit=30",
        )
        DesktopSearchResults(
            profiles = (0 until profiles.length()).mapNotNull { profiles.optJSONObject(it)?.let(::parseProfile) },
            posts = fetchFeed(search = clean).take(30),
        )
    }

    suspend fun fetchNotifications(): List<DesktopNotification> = withContext(Dispatchers.IO) {
        val userId = requireSession().userId
        val rows = getArray(
            "/rest/v1/notifications?user_id=eq.${encode(userId)}&select=id,type,text,sub_text,post_id,is_read,actor_is_vip,vip_priority,created_at&order=created_at.desc&limit=100",
        )
        (0 until rows.length()).mapNotNull { i ->
            rows.optJSONObject(i)?.let { row ->
                DesktopNotification(
                    id = row.optString("id"),
                    type = row.optString("type"),
                    text = row.optString("text").ifBlank { row.optString("type") },
                    subText = row.optNullableString("sub_text"),
                    postId = row.optNullableString("post_id"),
                    isRead = row.optBoolean("is_read"),
                    actorIsVip = row.optBoolean("actor_is_vip"),
                    vipPriority = row.optBoolean("vip_priority"),
                    createdAt = row.optString("created_at"),
                )
            }
        }
    }

    suspend fun markNotificationRead(notificationId: String) = withContext(Dispatchers.IO) {
        patch(
            "/rest/v1/notifications?id=eq.${encode(notificationId)}",
            JSONObject().put("is_read", true),
        )
    }

    suspend fun fetchConversations(): List<DesktopConversation> = withContext(Dispatchers.IO) {
        val summaryResponse = runCatching {
            postObject(
                "/rest/v1/rpc/get_conversation_summaries_page",
                JSONObject()
                    .put("p_limit", 100)
                    .put("p_before", JSONObject.NULL)
                    .put("p_before_id", JSONObject.NULL),
            )
        }.getOrNull()
        val summaryRows = summaryResponse as? JSONArray
        if (summaryRows != null) {
            return@withContext (0 until summaryRows.length()).mapNotNull { i ->
                summaryRows.optJSONObject(i)?.let { row ->
                    DesktopConversation(
                        id = row.optString("conversation_id"),
                        title = row.optString("partner_name").ifBlank {
                            row.optString("partner_username").ifBlank { "Conversation" }
                        },
                        avatarUrl = row.optNullableString("partner_avatar"),
                        isGroup = row.optBoolean("is_group", false),
                        lastMessageAt = row.optNullableString("last_message_at"),
                        isOnline = row.optBoolean("partner_online", false),
                        lastSeenAt = row.optNullableString("partner_last_seen"),
                    )
                }
            }
        }

        val rows = getArray(
            "/rest/v1/conversations?select=id,title,avatar_url,is_group,last_message_at&order=last_message_at.desc.nullslast&limit=100",
        )
        (0 until rows.length()).mapNotNull { i ->
            rows.optJSONObject(i)?.let { row ->
                DesktopConversation(
                    id = row.optString("id"),
                    title = row.optString("title").ifBlank { if (row.optBoolean("is_group")) "Group chat" else "Conversation" },
                    avatarUrl = row.optNullableString("avatar_url"),
                    isGroup = row.optBoolean("is_group"),
                    lastMessageAt = row.optNullableString("last_message_at"),
                )
            }
        }
    }

    suspend fun fetchMessages(conversationId: String): List<DesktopMessage> = withContext(Dispatchers.IO) {
        val rows = getArray(
            "/rest/v1/messages?conversation_id=eq.${encode(conversationId)}&deleted_for_everyone=eq.false&select=id,conversation_id,sender_id,content,media_url,message_type,is_read,delivered_at,read_at,created_at&order=created_at.asc&limit=300",
        )
        (0 until rows.length()).mapNotNull { i -> rows.optJSONObject(i)?.let(::parseMessage) }
    }

    suspend fun sendMessage(conversationId: String, content: String): DesktopMessage = withContext(Dispatchers.IO) {
        val clean = content.trim()
        require(clean.isNotBlank()) { "Message cannot be empty." }
        val created = postArray(
            "/rest/v1/messages",
            JSONObject()
                .put("conversation_id", conversationId)
                .put("sender_id", requireSession().userId)
                .put("content", clean),
            prefer = "return=representation",
        ).optJSONObject(0) ?: throw IllegalStateException("Message was not returned by the server.")
        parseMessage(created)
    }

    suspend fun fetchMarketplace(): List<DesktopMarketItem> = withContext(Dispatchers.IO) {
        val rows = getArray(
            "/rest/v1/market_items?status=eq.active&select=id,title,price,currency,category,condition,description,image_url,seller_id,seller_name,seller_username,seller_is_verified,university,location,is_featured,is_sold&order=is_featured.desc,created_at.desc&limit=100",
        )
        (0 until rows.length()).mapNotNull { i -> rows.optJSONObject(i)?.let(::parseMarketItem) }
    }

    suspend fun createMarketplaceItem(title: String, description: String, price: Long, category: String): DesktopMarketItem = withContext(Dispatchers.IO) {
        require(title.trim().isNotBlank()) { "Title is required." }
        require(price >= 0) { "Price cannot be negative." }
        val profile = fetchProfile()
        val body = JSONObject()
            .put("title", title.trim())
            .put("description", description.trim())
            .put("price", price)
            .put("category", category.ifBlank { "Other" })
            .put("seller_id", profile.id)
            .put("seller_name", profile.fullName)
            .put("seller_username", profile.username)
            .put("seller_is_verified", profile.isVerified)
            .put("university", profile.university ?: "Campus")
            .put("location", profile.university ?: "Campus")
        val created = postArray("/rest/v1/market_items", body, prefer = "return=representation")
            .optJSONObject(0) ?: throw IllegalStateException("Listing was not returned by the server.")
        parseMarketItem(created)
    }

    suspend fun fetchConnectListings(): List<DesktopConnectListing> = withContext(Dispatchers.IO) {
        val rows = getArray(
            "/rest/v1/connect_listings?is_active=eq.true&select=id,user_id,listing_type,title,description,university,department,academic_level,location,tags,created_at&order=created_at.desc&limit=100",
        )
        (0 until rows.length()).mapNotNull { i -> rows.optJSONObject(i)?.let(::parseConnectListing) }
    }

    suspend fun createConnectListing(type: String, title: String, description: String): DesktopConnectListing = withContext(Dispatchers.IO) {
        require(title.trim().isNotBlank()) { "Title is required." }
        val profile = fetchProfile()
        val body = JSONObject()
            .put("listing_type", type.ifBlank { "community" })
            .put("title", title.trim())
            .put("description", description.trim())
            .put("university", profile.university ?: JSONObject.NULL)
            .put("department", profile.department ?: JSONObject.NULL)
        val created = postArray("/rest/v1/connect_listings", body, prefer = "return=representation")
            .optJSONObject(0) ?: throw IllegalStateException("Connect listing was not returned.")
        parseConnectListing(created)
    }

    suspend fun fetchStore(): Pair<List<DesktopStoreItem>, List<DesktopInventoryItem>> = withContext(Dispatchers.IO) {
        val catalogRows = getArray(
            "/rest/v1/blink_store_catalog?is_active=eq.true&select=id,name,description,category,price,item_type,target_type,duration_seconds,vip_only,boost_multipliers&order=sort_order.asc",
        )
        val inventoryRows = getArray(
            "/rest/v1/blink_inventory?user_id=eq.${encode(requireSession().userId)}&select=id,catalog_id,quantity,status,purchased_at,activated_at,expires_at,target_type,target_id,boost_multiplier&order=purchased_at.desc",
        )
        val catalog = (0 until catalogRows.length()).mapNotNull { i ->
            catalogRows.optJSONObject(i)?.let { row ->
                DesktopStoreItem(
                    id = row.optString("id"),
                    name = row.optString("name"),
                    description = row.optString("description"),
                    category = row.optString("category"),
                    price = row.optInt("price"),
                    itemType = row.optString("item_type"),
                    targetType = row.optString("target_type"),
                    durationSeconds = row.optNullableLong("duration_seconds"),
                    vipOnly = row.optBoolean("vip_only"),
                    boostMultipliers = row.optIntList("boost_multipliers"),
                )
            }
        }
        val inventory = (0 until inventoryRows.length()).mapNotNull { i ->
            inventoryRows.optJSONObject(i)?.let { row ->
                DesktopInventoryItem(
                    id = row.optString("id"),
                    catalogId = row.optString("catalog_id"),
                    quantity = row.optInt("quantity"),
                    status = row.optString("status"),
                    purchasedAt = row.optString("purchased_at"),
                    activatedAt = row.optNullableString("activated_at"),
                    expiresAt = row.optNullableString("expires_at"),
                    targetType = row.optNullableString("target_type"),
                    targetId = row.optNullableString("target_id"),
                    boostMultiplier = row.optNullableInt("boost_multiplier"),
                )
            }
        }
        catalog to inventory
    }

    suspend fun fetchCoinBalance(): Long = withContext(Dispatchers.IO) {
        val rows = getArray(
            "/rest/v1/user_balances?user_id=eq.${encode(requireSession().userId)}&select=spendable_coin_balance&limit=1",
        )
        rows.optJSONObject(0)?.optDouble("spendable_coin_balance", 0.0)?.toLong() ?: 0L
    }

    suspend fun fetchLeaderboard(): List<DesktopLeaderboardEntry> = withContext(Dispatchers.IO) {
        val rows = getArray(
            "/rest/v1/leaderboard_snapshots?select=user_id,name,handle,university,verification_tier,world_score,world_rank,campus_score,campus_rank&order=world_rank.asc.nullslast&limit=200",
        )
        (0 until rows.length()).mapNotNull { i ->
            rows.optJSONObject(i)?.let { row ->
                DesktopLeaderboardEntry(
                    userId = row.optString("user_id"),
                    name = row.optString("name"),
                    handle = row.optString("handle"),
                    university = row.optNullableString("university"),
                    verificationTier = row.optString("verification_tier"),
                    worldScore = row.optLong("world_score"),
                    worldRank = row.optNullableInt("world_rank"),
                    campusScore = row.optLong("campus_score"),
                    campusRank = row.optNullableInt("campus_rank"),
                )
            }
        }
    }

    suspend fun fetchSettings(): DesktopUserSettings = withContext(Dispatchers.IO) {
        val rows = getArray(
            "/rest/v1/user_settings?user_id=eq.${encode(requireSession().userId)}&select=theme,language,push_notifs_enabled,email_notifs_enabled,dm_privacy,private_account,show_online_status,read_receipts,autoplay_videos,data_saver,reduce_motion&limit=1",
        )
        rows.optJSONObject(0)?.let(::parseSettings) ?: DesktopUserSettings(
            theme = "system",
            language = "en",
            pushNotificationsEnabled = true,
            emailNotificationsEnabled = true,
            dmPrivacy = "everyone",
            privateAccount = false,
            showOnlineStatus = true,
            readReceipts = true,
            autoplayVideos = true,
            dataSaver = false,
            reduceMotion = false,
        )
    }

    suspend fun saveSettings(settings: DesktopUserSettings) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("user_id", requireSession().userId)
            .put("theme", settings.theme)
            .put("language", settings.language)
            .put("push_notifs_enabled", settings.pushNotificationsEnabled)
            .put("email_notifs_enabled", settings.emailNotificationsEnabled)
            .put("dm_privacy", settings.dmPrivacy)
            .put("private_account", settings.privateAccount)
            .put("show_online_status", settings.showOnlineStatus)
            .put("read_receipts", settings.readReceipts)
            .put("autoplay_videos", settings.autoplayVideos)
            .put("data_saver", settings.dataSaver)
            .put("reduce_motion", settings.reduceMotion)
        postArray(
            "/rest/v1/user_settings?on_conflict=user_id",
            body,
            prefer = "resolution=merge-duplicates,return=minimal",
        )
    }

    suspend fun fetchAdminCapability(): DesktopAdminCapability = withContext(Dispatchers.IO) {
        runCatching {
            val response = postObject("/rest/v1/rpc/admin_get_capability", JSONObject())
            val obj = when (response) {
                is JSONObject -> response
                is JSONArray -> response.optJSONObject(0) ?: JSONObject()
                else -> JSONObject()
            }
            val allowed = obj.optBoolean("allowed", obj.optBoolean("is_admin", obj.optBoolean("admin", false)))
            DesktopAdminCapability(
                allowed = allowed,
                role = obj.optNullableString("role") ?: obj.optNullableString("admin_role"),
                isOwner = obj.optBoolean("is_owner", false),
            )
        }.getOrElse { DesktopAdminCapability(false, null, false) }
    }

    suspend fun fetchGameSummary(): Pair<Int, Long> = withContext(Dispatchers.IO) {
        val challenges = runCatching { getArray("/rest/v1/game_challenges?select=id&limit=100").length() }.getOrDefault(0)
        challenges to fetchCoinBalance()
    }

    private suspend fun fetchMyLikedPostIds(): Set<String> = withContext(Dispatchers.IO) {
        val active = session ?: return@withContext emptySet()
        val rows = runCatching {
            getArray("/rest/v1/post_likes?user_id=eq.${encode(active.userId)}&select=post_id&limit=1000")
        }.getOrNull() ?: return@withContext emptySet()
        buildSet {
            for (i in 0 until rows.length()) rows.optJSONObject(i)?.optString("post_id")?.takeIf(String::isNotBlank)?.let(::add)
        }
    }

    private fun parseProfile(row: JSONObject): DesktopProfile = DesktopProfile(
        id = row.optString("id"),
        fullName = row.optString("full_name").ifBlank { row.optString("name").ifBlank { row.optString("username") } },
        username = row.optString("username").ifBlank { row.optString("handle") },
        avatarUrl = row.optNullableString("avatar_url"),
        university = row.optNullableString("university"),
        faculty = row.optNullableString("faculty"),
        department = row.optNullableString("department"),
        bio = row.optNullableString("bio"),
        isVerified = row.optBoolean("is_verified"),
        verificationTier = row.optString("verification_tier"),
        followerCount = row.optInt("follower_count"),
        followingCount = row.optInt("following_count"),
        postsCount = row.optInt("posts_count"),
        coinBalance = row.optLong("current_wallet_balance"),
        isOnline = row.optBoolean("online_now", row.optBoolean("is_online")),
        lastSeenAt = row.optNullableString("last_seen_at"),
    )

    private fun parseFeedPost(row: JSONObject, profile: DesktopProfile?, liked: Boolean) = DesktopFeedPost(
        id = row.optString("id"),
        userId = row.optString("user_id"),
        authorName = profile?.fullName ?: profile?.username ?: "Blink user",
        authorUsername = profile?.username ?: "user",
        authorVerified = profile?.isVerified == true,
        authorVerificationTier = profile?.verificationTier.orEmpty(),
        authorOnline = profile?.isOnline == true,
        text = row.optNullableString("text"),
        caption = row.optNullableString("caption"),
        imageUrl = row.optNullableString("image_url"),
        videoUrl = row.optNullableString("video_url"),
        images = row.optStringList("images"),
        hashtags = row.optStringList("hashtags"),
        likeCount = row.optInt("like_count"),
        commentCount = row.optInt("comment_count"),
        shareCount = row.optInt("share_count"),
        viewCount = row.optInt("view_count"),
        isReel = row.optBoolean("is_reel"),
        createdAt = row.optString("created_at"),
        isLiked = liked,
    )

    private fun parseMessage(row: JSONObject) = DesktopMessage(
        id = row.optString("id"),
        conversationId = row.optString("conversation_id"),
        senderId = row.optString("sender_id"),
        content = row.optString("content"),
        mediaUrl = row.optNullableString("media_url"),
        messageType = row.optString("message_type").ifBlank { "text" },
        isRead = row.optBoolean("is_read"),
        deliveredAt = row.optNullableString("delivered_at"),
        readAt = row.optNullableString("read_at"),
        createdAt = row.optString("created_at"),
    )

    private fun parseMarketItem(row: JSONObject) = DesktopMarketItem(
        id = row.optString("id"),
        title = row.optString("title"),
        price = row.optLong("price"),
        currency = row.optString("currency").ifBlank { "NGN" },
        category = row.optString("category"),
        condition = row.optString("condition"),
        description = row.optString("description"),
        imageUrl = row.optNullableString("image_url"),
        sellerId = row.optNullableString("seller_id"),
        sellerName = row.optString("seller_name"),
        sellerUsername = row.optString("seller_username"),
        sellerVerified = row.optBoolean("seller_is_verified"),
        university = row.optString("university"),
        location = row.optString("location"),
        isFeatured = row.optBoolean("is_featured"),
        isSold = row.optBoolean("is_sold"),
    )

    private fun parseConnectListing(row: JSONObject) = DesktopConnectListing(
        id = row.optString("id"),
        userId = row.optString("user_id"),
        listingType = row.optString("listing_type"),
        title = row.optString("title"),
        description = row.optString("description"),
        university = row.optNullableString("university"),
        department = row.optNullableString("department"),
        academicLevel = row.optNullableString("academic_level"),
        location = row.optNullableString("location"),
        tags = row.optStringList("tags"),
        createdAt = row.optString("created_at"),
    )

    private fun parseSettings(row: JSONObject) = DesktopUserSettings(
        theme = row.optString("theme").ifBlank { "system" },
        language = row.optString("language").ifBlank { "en" },
        pushNotificationsEnabled = row.optBoolean("push_notifs_enabled", true),
        emailNotificationsEnabled = row.optBoolean("email_notifs_enabled", true),
        dmPrivacy = row.optString("dm_privacy").ifBlank { "everyone" },
        privateAccount = row.optBoolean("private_account"),
        showOnlineStatus = row.optBoolean("show_online_status", true),
        readReceipts = row.optBoolean("read_receipts", true),
        autoplayVideos = row.optBoolean("autoplay_videos", true),
        dataSaver = row.optBoolean("data_saver"),
        reduceMotion = row.optBoolean("reduce_motion"),
    )

    private fun clearSession() {
        session = null
        profileCache.clear()
        sessionStore.clear()
    }

    private fun requireSession(): DesktopSession = session ?: throw IllegalStateException("Sign in to continue.")

    private fun ensureFreshSession() {
        val active = requireSession()
        if (active.expiresAtEpochSeconds - Instant.now().epochSecond > 90) return
        val refreshed = authPostBlocking(
            "/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", active.refreshToken),
        )
        saveAuthResponseBlocking(refreshed, active.email)
    }

    private fun authPostBlocking(path: String, body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        return execute(request).let(::JSONObject)
    }

    private suspend fun authPost(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        authPostBlocking(path, body)
    }

    private fun saveAuthResponseBlocking(response: JSONObject, fallbackEmail: String): DesktopSession {
        val token = response.optString("access_token")
        val refresh = response.optString("refresh_token")
        val user = response.optJSONObject("user") ?: JSONObject()
        val userId = user.optString("id")
        require(token.isNotBlank() && refresh.isNotBlank() && userId.isNotBlank()) { "Supabase did not return a complete session." }
        val result = DesktopSession(
            accessToken = token,
            refreshToken = refresh,
            userId = userId,
            email = user.optString("email").ifBlank { fallbackEmail },
            expiresAtEpochSeconds = response.optLong("expires_at", Instant.now().epochSecond + response.optLong("expires_in", 3600L)),
        )
        session = result
        sessionStore.save(result)
        return result
    }

    private fun saveAuthResponse(response: JSONObject, fallbackEmail: String) = saveAuthResponseBlocking(response, fallbackEmail)

    private fun authenticatedRequest(path: String): Request.Builder {
        ensureFreshSession()
        return Request.Builder()
            .url("$baseUrl$path")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer ${requireSession().accessToken}")
            .header("Accept", "application/json")
    }

    private fun getArray(path: String): JSONArray {
        val text = execute(authenticatedRequest(path).get().build())
        return if (text.isBlank()) JSONArray() else JSONArray(text)
    }

    private fun postArray(path: String, body: JSONObject, prefer: String): JSONArray {
        val builder = authenticatedRequest(path)
            .header("Prefer", prefer)
            .post(body.toString().toRequestBody(jsonMedia))
        val text = execute(builder.build())
        return if (text.isBlank()) JSONArray() else if (text.trimStart().startsWith("[")) JSONArray(text) else JSONArray().put(JSONObject(text))
    }

    private fun postObject(path: String, body: JSONObject): Any {
        val text = execute(
            authenticatedRequest(path)
                .header("Prefer", "return=representation")
                .post(body.toString().toRequestBody(jsonMedia))
                .build(),
        )
        val trimmed = text.trim()
        return when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> JSONObject(trimmed)
            else -> JSONObject().put("value", trimmed)
        }
    }

    private fun patch(path: String, body: JSONObject) {
        execute(
            authenticatedRequest(path)
                .header("Prefer", "return=minimal")
                .patch(body.toString().toRequestBody(jsonMedia))
                .build(),
        )
    }

    private fun delete(path: String) {
        execute(
            authenticatedRequest(path)
                .header("Prefer", "return=minimal")
                .delete()
                .build(),
        )
    }

    private fun execute(request: Request): String {
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    val json = JSONObject(body)
                    json.optString("msg").ifBlank {
                        json.optString("message").ifBlank { json.optString("error_description").ifBlank { json.optString("error") } }
                    }
                }.getOrNull().orEmpty()
                throw IllegalStateException(message.ifBlank { "Blinkng server request failed (${response.code})." })
            }
            return body
        }
    }

    private fun parseQuery(raw: String): Map<String, String> = raw.split('&')
        .mapNotNull { part ->
            val pieces = part.split('=', limit = 2)
            if (pieces.isEmpty() || pieces[0].isBlank()) null
            else URLDecoder.decode(pieces[0], StandardCharsets.UTF_8) to URLDecoder.decode(pieces.getOrElse(1) { "" }, StandardCharsets.UTF_8)
        }
        .toMap()

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun JSONObject.optNullableString(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }
    private fun JSONObject.optNullableInt(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)
    private fun JSONObject.optNullableLong(key: String): Long? = if (isNull(key) || !has(key)) null else optLong(key)
    private fun JSONObject.optStringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }
    private fun JSONObject.optIntList(key: String): List<Int> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { array.optInt(it) }
    }
}
