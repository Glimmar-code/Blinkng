import re
with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    content = f.read()

new_createFeedPost = """    suspend fun createFeedPost(
        author: String,
        authorAvatar: String,
        facultyTag: String,
        text: String,
        imageUrl: String?,
        videoUrl: String? = null,
        tags: List<String> = emptyList(),
        mentions: List<String> = emptyList(),
        poll: PostPoll? = null,
        isReel: Boolean = false,
        audience: String = "Everyone",
        category: String = "Campus Life",
        location: String? = null,
        linkUrl: String? = null,
        allowComments: Boolean = true,
        hideLikes: Boolean = false,
        isPinned: Boolean = false,
        isDisappearing: Boolean = false,
        audioTitle: String? = null,
        altText: String? = null
    ): FeedPost? =
        withContext(Dispatchers.IO) {
            try {
                val userId =
                    getCurrentUserId()
                        ?: author.trim().lowercase(Locale.US).ifBlank { "user_student" }

                val json =
                    JSONObject().apply {
                        put("user_id", userId)
                        put("type", when {
                            isReel || !videoUrl.isNullOrBlank() -> "reel"
                            !imageUrl.isNullOrBlank() -> "photo"
                            else -> "text"
                        })
                        put("faculty", facultyTag.trim().ifBlank { "SIMME" })
                        put("text", text.trim())
                        put("content", text.trim())
                        
                        if (!imageUrl.isNullOrBlank()) {
                            put("image_url", imageUrl)
                            put("media_url", imageUrl)
                        }
                        if (!videoUrl.isNullOrBlank()) {
                            put("video_url", videoUrl)
                        }
                        
                        put("is_reel", isReel)
                        put("like_count", 0)
                        put("comment_count", 0)
                        put("share_count", 0)
                        put("view_count", 0)
                        put("username", author)
                        put("author", author)
                        put("avatar_url", authorAvatar)
                        put("author_avatar", authorAvatar)
                        
                        if (tags.isNotEmpty()) {
                            val tagsArray = JSONArray()
                            tags.forEach { tagsArray.put(it) }
                            put("tags", tagsArray)
                        }
                        if (mentions.isNotEmpty()) {
                            val mentionsArray = JSONArray()
                            mentions.forEach { mentionsArray.put(it) }
                            put("mentions", mentionsArray)
                        }
                        
                        if (poll != null) {
                            val pollObj = JSONObject()
                            pollObj.put("question", poll.question)
                            pollObj.put("total_votes", poll.totalVotes)
                            pollObj.put("has_voted", poll.hasVoted)
                            val optionsArray = JSONArray()
                            poll.options.forEach { opt ->
                                val optObj = JSONObject()
                                optObj.put("id", opt.id)
                                optObj.put("text", opt.text)
                                optObj.put("votes", opt.votes)
                                optObj.put("is_voted_by_me", opt.isVotedByMe)
                                optionsArray.put(optObj)
                            }
                            pollObj.put("options", optionsArray)
                            put("poll_data", pollObj)
                        }
                        
                        put("audience", audience)
                        put("category", category)
                        location?.let { put("location", it) }
                        linkUrl?.let { put("link_url", it) }
                        put("allow_comments", allowComments)
                        put("hide_likes", hideLikes)
                        put("is_pinned", isPinned)
                        put("is_disappearing", isDisappearing)
                        audioTitle?.let { put("audio_title", it) }
                        altText?.let { put("alt_text", it) }
                    }

                val request =
                    newRequestBuilder(
                        "/rest/v1/feed_posts",
                        authenticated = true
                    )
                        .addHeader("Prefer", "return=representation")
                        .post(
                            json.toString().toRequestBody(jsonMediaType)
                        )
                        .build()

                executeRequest(request).use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful && body.isNotBlank()) {
                        try {
                            val arr = JSONArray(body)
                            if (arr.length() > 0) {
                                return@withContext parseFeedPost(arr.getJSONObject(0))
                            }
                        } catch (e: Exception) {
                            // If it's not an array, maybe it's an object
                            try {
                                return@withContext parseFeedPost(JSONObject(body))
                            } catch (e2: Exception) {
                                Log.e(TAG, "Failed to parse created post", e2)
                            }
                        }
                    }
                    Log.e(TAG, "createFeedPost failed: ${response.code} $body")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "createFeedPost exception", e)
                null
            }
        }"""

pattern = r'    suspend fun createFeedPost\([\s\S]*?\} catch \(e: Exception\) \{\n\s*Log\.e\(TAG, "createFeedPost exception", e\)\n\s*false\n\s*\}\n\s*\}'
content = re.sub(pattern, new_createFeedPost, content)

with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'w') as f:
    f.write(content)
