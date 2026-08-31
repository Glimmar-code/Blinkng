import re

with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    content = f.read()

# Check if helpers exist, if not insert before STORIES section
helpers_code = '''
    // ============================================================
    // POLL & STORY HELPERS
    // ============================================================

    private suspend fun fetchUserStoryViews(username: String): Set<String> = withContext(Dispatchers.IO) {
        try {
            val req = newRequestBuilder(
                "/rest/v1/story_views?viewer_username=eq.${URLEncoder.encode(username, "UTF-8")}&select=story_id",
                authenticated = true
            ).get().build()
            executeRequest(req).use { resp ->
                if (!resp.isSuccessful) return@withContext emptySet()
                val body = resp.body?.string().orEmpty()
                if (body.isBlank() || body == "[]") return@withContext emptySet()
                val arr = JSONArray(body)
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    val sid = arr.optJSONObject(i)?.optString("story_id", "")
                    if (!sid.isNullOrBlank()) set.add(sid)
                }
                set
            }
        } catch (e: Exception) {
            emptySet()
        }
    }

    private suspend fun fetchUserStoryLikes(username: String): Set<String> = withContext(Dispatchers.IO) {
        try {
            val req = newRequestBuilder(
                "/rest/v1/story_likes?username=eq.${URLEncoder.encode(username, "UTF-8")}&select=story_id",
                authenticated = true
            ).get().build()
            executeRequest(req).use { resp ->
                if (!resp.isSuccessful) return@withContext emptySet()
                val body = resp.body?.string().orEmpty()
                if (body.isBlank() || body == "[]") return@withContext emptySet()
                val arr = JSONArray(body)
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    val sid = arr.optJSONObject(i)?.optString("story_id", "")
                    if (!sid.isNullOrBlank()) set.add(sid)
                }
                set
            }
        } catch (e: Exception) {
            emptySet()
        }
    }

    private suspend fun fetchUserPollVotes(username: String): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            val req = newRequestBuilder(
                "/rest/v1/poll_votes?username=eq.${URLEncoder.encode(username, "UTF-8")}&select=post_id,option_id",
                authenticated = true
            ).get().build()
            executeRequest(req).use { resp ->
                if (!resp.isSuccessful) return@withContext emptyMap()
                val body = resp.body?.string().orEmpty()
                if (body.isBlank() || body == "[]") return@withContext emptyMap()
                val arr = JSONArray(body)
                val map = mutableMapOf<String, String>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val pid = obj.optString("post_id", "")
                    val optId = obj.optString("option_id", "")
                    if (pid.isNotBlank() && optId.isNotBlank()) {
                        map[pid] = optId
                    }
                }
                map
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }
'''

new_stories_section = '''    // ============================================================
    // STORIES & POLLS
    // ============================================================

    suspend fun fetchStories(): List<Story> = withContext(Dispatchers.IO) {
        try {
            val currentUsername = getCurrentUsername() ?: ""
            val viewedIds = fetchUserStoryViews(currentUsername)
            val likedIds = fetchUserStoryLikes(currentUsername)

            val request = newRequestBuilder(
                "/rest/v1/stories?select=*&order=created_at.desc&limit=50",
                authenticated = true
            )
                .get()
                .build()

            executeRequest(request).use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank() || body == "[]") {
                    return@withContext emptyList()
                }

                val array = JSONArray(body)
                val list = mutableListOf<Story>()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id", UUID.randomUUID().toString())
                    val username = obj.optString("username", "Student")
                    val avatar = obj.optString("avatar", obj.optString("avatar_url", ""))
                    val storyImage = obj.optString("story_image", obj.optString("image_url", ""))
                    val caption = obj.optString("caption", "")
                    val faculty = obj.optString("faculty", "SIMME")
                    val university = obj.optString("university", "University of Lagos")
                    val likesCount = obj.optInt("likes_count", obj.optInt("likes", 0))
                    val isLiked = likedIds.contains(id) || obj.optBoolean("is_liked", false)
                    val hasUnseen = !viewedIds.contains(id)

                    list.add(
                        Story(
                            id = id,
                            username = username,
                            avatar = avatar.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300&h=300&fit=crop" },
                            hasUnseen = hasUnseen,
                            isUser = username.equals(currentUsername, ignoreCase = true),
                            storyImage = storyImage,
                            caption = caption,
                            faculty = faculty,
                            university = university,
                            likesCount = likesCount,
                            isLiked = isLiked
                        )
                    )
                }
                list
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchStories exception", e)
            emptyList()
        }
    }

    suspend fun createStory(story: Story): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("id", story.id)
                put("username", story.username)
                put("avatar", story.avatar)
                put("story_image", story.storyImage)
                put("caption", story.caption)
                put("faculty", story.faculty)
                put("university", story.university)
                put("likes_count", story.likesCount)
                put("created_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date()))
            }
            val request = newRequestBuilder(
                "/rest/v1/stories",
                authenticated = true
            )
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            executeRequest(request).use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "createStory exception", e)
            false
        }
    }

    suspend fun markStoryViewed(storyId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername() ?: "user"
            val userId = getCurrentUserId() ?: "user"

            val json = JSONObject().apply {
                put("story_id", storyId)
                put("viewer_id", userId)
                put("viewer_username", username)
                put("created_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date()))
            }
            val request = newRequestBuilder(
                "/rest/v1/story_views",
                authenticated = true
            )
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            executeRequest(request).use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "markStoryViewed exception", e)
            false
        }
    }

    suspend fun toggleStoryLike(storyId: String, nextLiked: Boolean, newCount: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername() ?: "user"
            val userId = getCurrentUserId() ?: "user"

            if (nextLiked) {
                val json = JSONObject().apply {
                    put("story_id", storyId)
                    put("user_id", userId)
                    put("username", username)
                    put("created_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.format(Date()))
                }
                val req = newRequestBuilder("/rest/v1/story_likes", authenticated = true)
                    .post(json.toString().toRequestBody(jsonMediaType))
                    .build()
                executeRequest(req).close()
            } else {
                val req = newRequestBuilder(
                    "/rest/v1/story_likes?story_id=eq.${URLEncoder.encode(storyId, "UTF-8")}&username=eq.${URLEncoder.encode(username, "UTF-8")}",
                    authenticated = true
                )
                    .delete()
                    .build()
                executeRequest(req).close()
            }

            val patchJson = JSONObject().apply { put("likes_count", newCount) }
            val patchReq = newRequestBuilder(
                "/rest/v1/stories?id=eq.${URLEncoder.encode(storyId, "UTF-8")}",
                authenticated = true
            )
                .patch(patchJson.toString().toRequestBody(jsonMediaType))
                .build()
            executeRequest(patchReq).use { resp -> resp.isSuccessful }
        } catch (e: Exception) {
            Log.e(TAG, "toggleStoryLike exception", e)
            false
        }
    }

    suspend fun reactToStory(storyId: String, emoji: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername() ?: "user"
            val userId = getCurrentUserId() ?: "user"

            val json = JSONObject().apply {
                put("story_id", storyId)
                put("user_id", userId)
                put("username", username)
                put("emoji", emoji)
                put("created_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date()))
            }
            val request = newRequestBuilder(
                "/rest/v1/story_reactions",
                authenticated = true
            )
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            executeRequest(request).use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "reactToStory exception", e)
            false
        }
    }

    suspend fun replyToStory(storyId: String, recipientUsername: String, replyText: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername() ?: "user"
            val userId = getCurrentUserId() ?: "user"

            val json = JSONObject().apply {
                put("story_id", storyId)
                put("sender_id", userId)
                put("sender_username", username)
                put("recipient_username", recipientUsername)
                put("reply_text", replyText)
                put("created_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date()))
            }
            val request = newRequestBuilder(
                "/rest/v1/story_replies",
                authenticated = true
            )
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            executeRequest(request).use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "replyToStory exception", e)
            false
        }
    }

    suspend fun votePoll(postId: String, optionId: String, updatedPoll: PostPoll): Boolean = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername() ?: "user"
            val userId = getCurrentUserId() ?: "user"

            val voteJson = JSONObject().apply {
                put("post_id", postId)
                put("option_id", optionId)
                put("user_id", userId)
                put("username", username)
                put("created_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date()))
            }
            val voteReq = newRequestBuilder(
                "/rest/v1/poll_votes",
                authenticated = true
            )
                .post(voteJson.toString().toRequestBody(jsonMediaType))
                .build()
            try { executeRequest(voteReq).close() } catch (e: Exception) { Log.w(TAG, "poll_votes error", e) }

            val pollObj = JSONObject().apply {
                put("question", updatedPoll.question)
                put("total_votes", updatedPoll.totalVotes)
                put("has_voted", true)
                val optionsArray = JSONArray()
                updatedPoll.options.forEach { opt ->
                    val optObj = JSONObject().apply {
                        put("id", opt.id)
                        put("text", opt.text)
                        put("votes", opt.votes)
                        put("is_voted_by_me", opt.isVotedByMe)
                    }
                    optionsArray.put(optObj)
                }
                put("options", optionsArray)
            }

            val patchJson = JSONObject().apply {
                put("poll_data", pollObj)
            }

            val patchReq = newRequestBuilder(
                "/rest/v1/feed_posts?id=eq.${URLEncoder.encode(postId, "UTF-8")}",
                authenticated = true
            )
                .patch(patchJson.toString().toRequestBody(jsonMediaType))
                .build()

            executeRequest(patchReq).use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "votePoll exception", e)
            false
        }
    }
'''

# Find existing STORIES section in SupabaseService.kt
start_idx = content.find('    // ============================================================')
story_idx = content.find('    // STORIES', start_idx)
if story_idx != -1:
    end_idx = content.find('    // ============================================================', story_idx + 20)
    # replace existing STORIES section with helpers_code + new_stories_section
    content = content[:story_idx] + helpers_code + "\n\n" + new_stories_section + "\n\n" + content[end_idx:]

with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'w') as f:
    f.write(content)

print("Updated SupabaseService.kt")
