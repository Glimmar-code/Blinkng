import re

with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    content = f.read()

new_methods = """
    // ============================================================
    // COMMENTS
    // ============================================================

    suspend fun fetchComments(postId: String): List<Comment> = withContext(Dispatchers.IO) {
        try {
            val req = newRequestBuilder("/rest/v1/feed_comments?post_id=eq.$postId&order=created_at.asc")
                .get()
                .build()
            val currentUserId = getCurrentUserId() ?: ""
            val currentUsername = appContext?.getSharedPreferences("blink_auth_prefs", android.content.Context.MODE_PRIVATE)?.getString("username", "") ?: ""

            var myLikedComments = setOf<Long>()
            val likeReq = newRequestBuilder("/rest/v1/comment_likes?select=comment_id" + 
                if (currentUserId.isNotBlank()) "&user_id=eq.$currentUserId" else if (currentUsername.isNotBlank()) "&username=eq.$currentUsername" else "")
                .get().build()
            executeRequest(likeReq).use { r ->
                if (r.isSuccessful) {
                    val body = r.body?.string().orEmpty()
                    if (body.isNotBlank() && body != "[]") {
                        val arr = JSONArray(body)
                        val set = mutableSetOf<Long>()
                        for (i in 0 until arr.length()) set.add(arr.getJSONObject(i).optLong("comment_id"))
                        myLikedComments = set
                    }
                }
            }

            executeRequest(req).use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@withContext emptyList()
                if (body.isBlank() || body == "[]") return@withContext emptyList()

                val arr = JSONArray(body)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optLong("id")
                        add(Comment(
                            id = id,
                            user = obj.optString("username", ""),
                            avatar = obj.optString("avatar_url", ""),
                            text = obj.optString("text", ""),
                            time = obj.optString("time_ago", "Recently"),
                            likes = obj.optInt("like_count", 0),
                            isLiked = myLikedComments.contains(id),
                            replies = emptyList() // If replies are supported, fetch them
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchComments failed", e)
            emptyList()
        }
    }

    suspend fun addComment(postId: String, text: String, replyToUser: String?): Comment? = withContext(Dispatchers.IO) {
        try {
            val currentUserId = getCurrentUserId() ?: ""
            val currentUsername = appContext?.getSharedPreferences("blink_auth_prefs", android.content.Context.MODE_PRIVATE)?.getString("username", "") ?: ""
            val currentUserAvatar = appContext?.getSharedPreferences("blink_auth_prefs", android.content.Context.MODE_PRIVATE)?.getString("avatar_url", "") ?: ""

            val obj = JSONObject().apply {
                put("post_id", postId)
                put("user_id", currentUserId)
                put("username", currentUsername)
                put("avatar_url", currentUserAvatar)
                put("text", text)
                if (replyToUser != null) put("reply_to_user", replyToUser)
            }
            val req = newRequestBuilder("/rest/v1/feed_comments", authenticated = true)
                .addHeader("Prefer", "return=representation")
                .post(obj.toString().toRequestBody(jsonMediaType))
                .build()

            executeRequest(req).use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful && body.isNotBlank()) {
                    val arr = JSONArray(body)
                    if (arr.length() > 0) {
                        val resObj = arr.getJSONObject(0)
                        return@withContext Comment(
                            id = resObj.optLong("id"),
                            user = resObj.optString("username", currentUsername),
                            avatar = resObj.optString("avatar_url", currentUserAvatar),
                            text = resObj.optString("text", text),
                            time = "Just now",
                            likes = 0,
                            isLiked = false
                        )
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "addComment failed", e)
            null
        }
    }

    suspend fun toggleCommentLike(commentId: Long, liked: Boolean, newLikeCount: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentUserId = getCurrentUserId() ?: ""
            val currentUsername = appContext?.getSharedPreferences("blink_auth_prefs", android.content.Context.MODE_PRIVATE)?.getString("username", "") ?: ""

            if (liked) {
                val obj = JSONObject().apply {
                    put("comment_id", commentId)
                    if (currentUserId.isNotBlank()) put("user_id", currentUserId)
                    if (currentUsername.isNotBlank()) put("username", currentUsername)
                }
                val req = newRequestBuilder("/rest/v1/comment_likes", authenticated = true)
                    .addHeader("Prefer", "resolution=merge-duplicates")
                    .post(obj.toString().toRequestBody(jsonMediaType)).build()
                executeRequest(req).use { it.isSuccessful }
            } else {
                val filter = when {
                    currentUserId.isNotBlank() -> "user_id=eq.$currentUserId"
                    currentUsername.isNotBlank() -> "username=eq.$currentUsername"
                    else -> ""
                }
                val url = if (filter.isNotBlank()) "/rest/v1/comment_likes?comment_id=eq.$commentId&$filter" else "/rest/v1/comment_likes?comment_id=eq.$commentId"
                val req = newRequestBuilder(url, authenticated = true).delete().build()
                executeRequest(req).use { it.isSuccessful }
            }

            val patchObj = JSONObject().apply { put("like_count", newLikeCount) }
            val patchReq = newRequestBuilder("/rest/v1/feed_comments?id=eq.$commentId", authenticated = true)
                .patch(patchObj.toString().toRequestBody(jsonMediaType)).build()
            executeRequest(patchReq).use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e(TAG, "toggleCommentLike failed", e)
            false
        }
    }
"""

idx = content.find('    // ============================================================')
# Wait, we want to append it before the end of the class, or after some section
idx = content.find('    suspend fun togglePostLike')
content = content[:idx] + new_methods + content[idx:]

# add import for Comment if needed.
# Supposedly, it's already there because `fetchFeedPosts` imports FeedPost, but what about Comment?
# Let's check imports.
if 'import com.example.data.models.Comment' not in content:
    content = content.replace('import com.example.data.models.VerificationBadge', 'import com.example.data.models.VerificationBadge\nimport com.example.data.models.Comment\nimport com.example.data.models.CommentReply')

with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'w') as f:
    f.write(content)
