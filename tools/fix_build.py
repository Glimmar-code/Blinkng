from pathlib import Path
import re


def replace_function(text: str, name: str, body: str) -> str:
    m = re.search(r'(?m)^\s*(?:(?:public|private|internal|protected)\s+)?(?:suspend\s+)?fun\s+' + re.escape(name) + r'\b', text)
    if not m:
        raise RuntimeError(f'missing function {name}')
    b = text.find('{', m.end())
    if b < 0:
        raise RuntimeError(f'missing body {name}')
    depth = 0
    for i in range(b, len(text)):
        if text[i] == '{': depth += 1
        elif text[i] == '}':
            depth -= 1
            if depth == 0:
                return text[:m.start()] + body.rstrip() + text[i + 1:]
    raise RuntimeError(f'unbalanced function {name}')

root = Path('.')

p = root / 'app/src/main/java/com/example/data/BlinkDemoData.kt'
s = p.read_text()
s = re.sub(r'(Comment(?:Reply)?\(\s*)(\d+)L?(?=\s*[,\)])', r'\1"\2"', s)
p.write_text(s)

p = root / 'app/src/main/java/com/example/ui/components/CommentSheet.kt'
s = p.read_text().replace('onToggleCommentLike: (Long) -> Unit', 'onToggleCommentLike: (String) -> Unit')
p.write_text(s)

p = root / 'app/src/main/java/com/example/viewmodel/BlinkViewModel.kt'
s = p.read_text()
s = re.sub(r'(fun\s+\w*[Cc]omment\w*\s*\([^)]*(?:commentId|id)\s*:\s*)Long', r'\1String', s)
s = s.replace('commentId.toLong()', 'commentId').replace('comment.id.toLong()', 'comment.id')
p.write_text(s)

p = root / 'app/build.gradle.kts'
s = p.read_text().replace('debug { signingConfig = signingConfigs.getByName("debugConfig") }', 'debug { }')
p.write_text(s)

p = root / 'app/src/main/java/com/example/data/supabase/SupabaseService.kt'
s = p.read_text()
s = replace_function(s, 'fetchActivities', '''    suspend fun fetchActivities(): Result<List<ActivityItem>> = withContext(Dispatchers.IO) {
        try {
            val uid = getCurrentUserId() ?: throw IllegalStateException("Not authenticated.")
            val request = newRequestBuilder(
                "/rest/v1/activities?recipient_id=eq.${encodeValue(uid)}&order=created_at.desc&limit=100",
                authenticated = true
            ).get().build()
            executeRequest(request).use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception(parseSupabaseError(raw, "Activity fetch failed.")))
                }
                val arr = JSONArray(if (raw.isBlank()) "[]" else raw)
                val result = buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val type = o.optString("activity_type").uppercase(Locale.US)
                        val category = when {
                            type.contains("COMMENT") || type.contains("REPLY") -> NotificationFilter.COMMENTS
                            type.contains("LIKE") || type.contains("BOOKMARK") || type.contains("SAVE") -> NotificationFilter.LIKES
                            type.contains("MARKET") || type.contains("SELLER") -> NotificationFilter.MARKET
                            else -> NotificationFilter.ALL
                        }
                        add(ActivityItem(
                            id = o.optString("id"),
                            user = o.optString("actor_id"),
                            avatar = "",
                            action = o.optString("message", type.lowercase(Locale.US).replace('_', ' ')),
                            time = formatTimeAgo(o.optString("created_at")),
                            rawTimestamp = o.optString("created_at"),
                            isUnread = !o.optBoolean("is_read", false),
                            category = category,
                            targetPostId = o.optString("entity_id").takeIf { o.optString("entity_type").equals("post", true) },
                            targetMarketId = o.optString("entity_id").takeIf { o.optString("entity_type").equals("market", true) },
                            targetUsername = o.optString("actor_username").takeIf { it.isNotBlank() },
                            targetType = o.optString("entity_type").takeIf { it.isNotBlank() },
                            previewText = o.optString("preview_text").takeIf { it.isNotBlank() }
                        ))
                    }
                }
                Result.success(result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchActivities exception", e)
            Result.failure(e)
        }
    }''')
p.write_text(s)
print('build blockers fixed')
