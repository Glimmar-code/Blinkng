import re

with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    content = f.read()

new_bookmark_code = """
    suspend fun togglePostBookmark(
        postId: String,
        bookmarked: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentUserId = getCurrentUserId() ?: ""
            val currentUsername = appContext
                ?.getSharedPreferences("blink_auth_prefs", android.content.Context.MODE_PRIVATE)
                ?.getString("username", "") ?: ""

            if (bookmarked) {
                val obj = JSONObject().apply {
                    put("post_id", postId)
                    if (currentUserId.isNotBlank()) put("user_id", currentUserId)
                    if (currentUsername.isNotBlank()) put("username", currentUsername)
                }
                val req = newRequestBuilder("/rest/v1/post_bookmarks", authenticated = true)
                    .addHeader("Prefer", "resolution=merge-duplicates")
                    .post(obj.toString().toRequestBody(jsonMediaType))
                    .build()
                executeRequest(req).use { it.isSuccessful }
            } else {
                val filter = when {
                    currentUserId.isNotBlank() -> "user_id=eq.$currentUserId"
                    currentUsername.isNotBlank() -> "username=eq.$currentUsername"
                    else -> ""
                }
                val url = if (filter.isNotBlank()) "/rest/v1/post_bookmarks?post_id=eq.$postId&$filter" else "/rest/v1/post_bookmarks?post_id=eq.$postId"
                val req = newRequestBuilder(url, authenticated = true).delete().build()
                executeRequest(req).use { it.isSuccessful }
            }
        } catch (e: Exception) {
            Log.e(TAG, "togglePostBookmark failed", e)
            false
        }
    }
"""

idx = content.find('suspend fun togglePostLike')
content = content[:idx] + new_bookmark_code + content[idx:]

with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'w') as f:
    f.write(content)
