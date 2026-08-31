import re

with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    content = f.read()

new_fetch_code = """    suspend fun fetchFeedPosts(): List<FeedPost> =
        withContext(Dispatchers.IO) {
            try {
                val currentUserId = getCurrentUserId() ?: ""
                val currentUsername = appContext
                    ?.getSharedPreferences("blink_auth_prefs", android.content.Context.MODE_PRIVATE)
                    ?.getString("username", "") ?: ""

                // 1. Fetch posts
                val request = newRequestBuilder(
                    "/rest/v1/feed_posts?select=*&order=created_at.desc&limit=100"
                ).get().build()

                val postsStr = executeRequest(request).use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        Log.e(TAG, "FEED_FETCH failed status=${response.code} body=$body")
                        return@withContext emptyList()
                    }
                    if (body.isBlank() || body == "[]") return@withContext emptyList()
                    body
                }

                // 2. Fetch my likes
                val myLikes = mutableSetOf<String>()
                if (currentUserId.isNotBlank() || currentUsername.isNotBlank()) {
                    val filter = when {
                        currentUserId.isNotBlank() -> "user_id=eq.$currentUserId"
                        currentUsername.isNotBlank() -> "username=eq.$currentUsername"
                        else -> ""
                    }
                    val likesReq = newRequestBuilder("/rest/v1/post_likes?select=post_id&$filter").get().build()
                    executeRequest(likesReq).use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string().orEmpty()
                            if (body.isNotBlank() && body != "[]") {
                                val arr = JSONArray(body)
                                for (i in 0 until arr.length()) {
                                    myLikes.add(arr.getJSONObject(i).optString("post_id"))
                                }
                            }
                        }
                    }
                }

                // 3. Fetch my bookmarks
                val myBookmarks = mutableSetOf<String>()
                if (currentUserId.isNotBlank() || currentUsername.isNotBlank()) {
                    val filter = when {
                        currentUserId.isNotBlank() -> "user_id=eq.$currentUserId"
                        currentUsername.isNotBlank() -> "username=eq.$currentUsername"
                        else -> ""
                    }
                    val bmkReq = newRequestBuilder("/rest/v1/post_bookmarks?select=post_id&$filter").get().build()
                    executeRequest(bmkReq).use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string().orEmpty()
                            if (body.isNotBlank() && body != "[]") {
                                val arr = JSONArray(body)
                                for (i in 0 until arr.length()) {
                                    myBookmarks.add(arr.getJSONObject(i).optString("post_id"))
                                }
                            }
                        }
                    }
                }

                // 4. Parse and map
                val json = JSONArray(postsStr)
                buildList {
                    for (i in 0 until json.length()) {
                        try {
                            val obj = json.getJSONObject(i)
                            var post = parseFeedPost(obj)
                            if (myLikes.contains(post.id)) {
                                post = post.copy(isLiked = true)
                            }
                            if (myBookmarks.contains(post.id)) {
                                post = post.copy(isBookmarked = true)
                            }
                            add(post)
                        } catch (e: Exception) {
                            Log.e(TAG, "FEED_FETCH item parse error", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "FEED_FETCH exception", e)
                emptyList()
            }
        }"""

content = re.sub(r'    suspend fun fetchFeedPosts\(\): List<FeedPost> =[\s\S]*?catch \(e: Exception\) \{\n\s*Log\.e\(\n\s*TAG,\n\s*"FEED_FETCH exception",\n\s*e\n\s*\)\n\s*emptyList\(\)\n\s*\}\n\s*\}', new_fetch_code, content)

with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'w') as f:
    f.write(content)
