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

# Existing visual demo fixtures must remain compilable, but are never promoted to cloud identity.
p = root / 'app/src/main/java/com/example/data/BlinkDemoData.kt'
s = p.read_text()
s = re.sub(r'(Comment(?:Reply)?\(\s*)(\d+)L?(?=\s*[,\)])', r'\1"\2"', s)
p.write_text(s)

# Comment IDs are UUID/string IDs in Supabase.
p = root / 'app/src/main/java/com/example/ui/components/CommentSheet.kt'
s = p.read_text().replace('onToggleCommentLike: (Long) -> Unit', 'onToggleCommentLike: (String) -> Unit')
p.write_text(s)

# Keep all ViewModel comment actions on string/UUID IDs.
p = root / 'app/src/main/java/com/example/viewmodel/BlinkViewModel.kt'
s = p.read_text()
s = re.sub(r'(fun\s+\w*[Cc]omment\w*\s*\([^)]*(?:commentId|id)\s*:\s*)Long', r'\1String', s)
s = s.replace('commentId.toLong()', 'commentId').replace('comment.id.toLong()', 'comment.id')
# Never promote a cached local profile to an authenticated MAIN session.
s = s.replace('''            /*\n             * Only fall back to local profile data when there is no\n             * valid Supabase session.\n             */\n            restoreLocalSession()''', '''            // A missing/expired Supabase session means the user is signed out.\n            SupabaseService.clearSession()\n            prefs.edit().clear().apply()\n            authRepository.signOut()''')
s = s.replace('''            restoreLocalSession()\n\n        } catch''', '''            SupabaseService.clearSession()\n            prefs.edit().clear().apply()\n            authRepository.signOut()\n\n        } catch''', 1)
# Cloud fetch must not permanently re-add local/demo post caches.
s = re.sub(r'''\n\s*val localPosts =\n\s*_uiState\.value\.posts\.filter \{\n\s*it\.id\.startsWith\("post_"\) \|\| it\.id\.startsWith\("local_"\)\n\s*\}\n\n\s*val localReels =\n\s*_uiState\.value\.reels\.filter \{\n\s*it\.id\.startsWith\("post_"\) \|\| it\.id\.startsWith\("local_"\)\n\s*\}\n\n\s*val mergedPosts =\n\s*\(localPosts \+ normalPosts\)\.distinctBy \{ it\.id \}\n\n\s*val mergedReels =\n\s*\(localReels \+ fetchedReels\)\.distinctBy \{ it\.id \}\n''', '''\n                // Supabase is authoritative. Pending optimistic items are reconciled\n                // by their real server IDs before this refresh, so nothing synthetic\n                // is merged back into the durable feed.\n                val mergedPosts = normalPosts\n                val mergedReels = fetchedReels\n''', s, count=1)
# A successful backend refresh is the only point at which local profile cache is updated.
p.write_text(s)

# Debug builds use the standard Android debug signing configuration.
p = root / 'app/build.gradle.kts'
s = p.read_text().replace('debug { signingConfig = signingConfigs.getByName("debugConfig") }', 'debug { }')
p.write_text(s)

# Replace the activity fetch implementation with a correctly typed Result contract.
p = root / 'app/src/main/java/com/example/data/supabase/SupabaseService.kt'
s = p.read_text()
s = replace_function(s, 'fetchActivities', '''    suspend fun fetchActivities(): Result<List<ActivityItem>> = withContext(Dispatchers.IO) {\n        try {\n            val uid = getCurrentUserId() ?: throw IllegalStateException("Not authenticated.")\n            val request = newRequestBuilder(\n                "/rest/v1/activities?recipient_id=eq.${encodeValue(uid)}&order=created_at.desc&limit=100",\n                authenticated = true\n            ).get().build()\n            executeRequest(request).use { response ->\n                val raw = response.body?.string().orEmpty()\n                if (!response.isSuccessful) {\n                    return@withContext Result.failure(Exception(parseSupabaseError(raw, "Activity fetch failed.")))\n                }\n                val arr = JSONArray(if (raw.isBlank()) "[]" else raw)\n                val result = buildList {\n                    for (i in 0 until arr.length()) {\n                        val o = arr.getJSONObject(i)\n                        val type = o.optString("activity_type").uppercase(Locale.US)\n                        val category = when {\n                            type.contains("COMMENT") || type.contains("REPLY") -> NotificationFilter.COMMENTS\n                            type.contains("LIKE") || type.contains("BOOKMARK") || type.contains("SAVE") -> NotificationFilter.LIKES\n                            type.contains("MARKET") || type.contains("SELLER") -> NotificationFilter.MARKET\n                            else -> NotificationFilter.ALL\n                        }\n                        add(ActivityItem(\n                            id = o.optString("id"),\n                            user = o.optString("actor_id"),\n                            avatar = "",\n                            action = o.optString("message", type.lowercase(Locale.US).replace('_', ' ')),\n                            time = formatTimeAgo(o.optString("created_at")),\n                            rawTimestamp = o.optString("created_at"),\n                            isUnread = !o.optBoolean("is_read", false),\n                            category = category,\n                            targetPostId = o.optString("entity_id").takeIf { o.optString("entity_type").equals("post", true) },\n                            targetMarketId = o.optString("entity_id").takeIf { o.optString("entity_type").equals("market", true) },\n                            targetUsername = o.optString("actor_username").takeIf { it.isNotBlank() },\n                            targetType = o.optString("entity_type").takeIf { it.isNotBlank() },\n                            previewText = o.optString("preview_text").takeIf { it.isNotBlank() }\n                        ))\n                    }\n                }\n                Result.success(result)\n            }\n        } catch (e: Exception) {\n            Log.e(TAG, "fetchActivities exception", e)\n            Result.failure(e)\n        }\n    }''')
p.write_text(s)
print('build and production blockers fixed')
