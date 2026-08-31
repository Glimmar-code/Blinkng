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

# Existing visual demo fixtures must remain compilable, but must use the UUID/String model.
p = root / 'app/src/main/java/com/example/data/BlinkDemoData.kt'
s = p.read_text()
s = re.sub(r'(\b(?:id\s*=\s*|(?:Comment|CommentReply)\()\s*)(\d+)L?\b', r'\1"\2"', s)
p.write_text(s)

# Comment IDs are UUID/string IDs in Supabase.
p = root / 'app/src/main/java/com/example/ui/components/CommentSheet.kt'
s = p.read_text().replace('onToggleCommentLike: (Long) -> Unit', 'onToggleCommentLike: (String) -> Unit')
p.write_text(s)

# Keep all ViewModel comment actions on string/UUID IDs and never restore a local-auth session.
p = root / 'app/src/main/java/com/example/viewmodel/BlinkViewModel.kt'
s = p.read_text()
s = re.sub(r'(fun\s+\w*[Cc]omment\w*\s*\([^)]*(?:commentId|id)\s*:\s*)Long', r'\1String', s)
s = s.replace('commentId.toLong()', 'commentId').replace('comment.id.toLong()', 'comment.id')
s = s.replace('''            /*\n             * Only fall back to local profile data when there is no\n             * valid Supabase session.\n             */\n            restoreLocalSession()''', '''            // A missing/expired Supabase session means the user is signed out.\n            SupabaseService.clearSession()\n            prefs.edit().clear().apply()\n            authRepository.signOut()''')
s = re.sub(r'\n\s*restoreLocalSession\(\)\n\s*\n\s*\}\s*catch', '\n            SupabaseService.clearSession()\n            prefs.edit().clear().apply()\n            authRepository.signOut()\n\n        } catch', s, count=1)
# Never re-add synthetic/demo feed objects after the authoritative Supabase fetch.
s = re.sub(r'''\n\s*val localPosts =\n\s*_uiState\.value\.posts\.filter \{\n\s*it\.id\.startsWith\("post_"\) \|\| it\.id\.startsWith\("local_"\)\n\s*\}\n\n\s*val localReels =\n\s*_uiState\.value\.reels\.filter \{\n\s*it\.id\.startsWith\("post_"\) \|\| it\.id\.startsWith\("local_"\)\n\s*\}\n\n\s*val mergedPosts =\n\s*\(localPosts \+ normalPosts\)\.distinctBy \{ it\.id \}\n\n\s*val mergedReels =\n\s*\(localReels \+ fetchedReels\)\.distinctBy \{ it\.id \}\n''', '''\n                // Supabase is authoritative. Pending optimistic items are reconciled\n                // before this refresh and are never persisted as durable feed rows.\n                val mergedPosts = normalPosts\n                val mergedReels = fetchedReels\n''', s, count=1)
p.write_text(s)

# Debug builds use Android's standard generated debug signing configuration.
p = root / 'app/build.gradle.kts'
s = p.read_text().replace('debug { signingConfig = signingConfigs.getByName("debugConfig") }', 'debug { }')
p.write_text(s)

# IMPORTANT: this is the final repair pass, so it must own fetchActivities after all schema-finalization edits.
p = root / 'app/src/main/java/com/example/data/supabase/SupabaseService.kt'
s = p.read_text()
s = replace_function(s, 'fetchActivities', '''    suspend fun fetchActivities(): Result<List<ActivityItem>> = withContext(Dispatchers.IO) {\n        try {\n            val uid = getCurrentUserId() ?: throw IllegalStateException("Not authenticated.")\n            val request = newRequestBuilder(\n                "/rest/v1/activities?recipient_id=eq.${encodeValue(uid)}&order=created_at.desc&limit=100",\n                authenticated = true\n            ).get().build()\n            executeRequest(request).use { response ->\n                val raw = response.body?.string().orEmpty()\n                if (!response.isSuccessful) {\n                    return@withContext Result.failure(Exception(parseSupabaseError(raw, "Activity fetch failed.")))\n                }\n                val array = JSONArray(if (raw.isBlank()) "[]" else raw)\n                val items = buildList {\n                    for (i in 0 until array.length()) {\n                        val o = array.getJSONObject(i)\n                        val type = o.optString("activity_type")\n                        add(ActivityItem(\n                            id = o.optString("id"),\n                            user = o.optString("actor_id"),\n                            avatar = "",\n                            action = o.optString("message").ifBlank { type.replace('_', ' ') },\n                            time = formatTimeAgo(o.optString("created_at")),\n                            rawTimestamp = o.optString("created_at"),\n                            isUnread = !o.optBoolean("is_read", false),\n                            targetPostId = o.optString("entity_id").takeIf { o.optString("entity_type").equals("post", true) },\n                            targetMarketId = o.optString("entity_id").takeIf { o.optString("entity_type").equals("market", true) },\n                            targetType = o.optString("entity_type").takeIf { it.isNotBlank() }\n                        ))\n                    }\n                }\n                Result.success(items)\n            }\n        } catch (e: Exception) {\n            Log.e(TAG, "fetchActivities exception", e)\n            Result.failure(e)\n        }\n    }''')
p.write_text(s)
print('final compile and production repair applied')
