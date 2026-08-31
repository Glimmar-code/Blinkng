with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    content = f.read()

# Fix conflicting imports
lines = content.split('\n')
seen_imports = set()
new_lines = []
for line in lines:
    if line.startswith('import '):
        if line in seen_imports:
            continue
        seen_imports.add(line)
    new_lines.append(line)
content = '\n'.join(new_lines)

# Fix parseLeaderboardUser definition
content = content.replace(
    'private fun parseLeaderboardUser(obj: JSONObject, rank: Int): LeaderboardUser {',
    'private fun parseLeaderboardUser(obj: JSONObject): LeaderboardUser {\n        val rank = obj.optInt("rank", 0)'
)

# And fix parseLeaderboardUser caller if it passes rank
# We don't know what it passes, let's just make the signature take rank with a default
content = content.replace(
    'private fun parseLeaderboardUser(obj: JSONObject): LeaderboardUser {',
    'private fun parseLeaderboardUser(obj: JSONObject, rank: Int = obj.optInt("rank", 0)): LeaderboardUser {'
)

# Also fix the duplicate val rank
content = content.replace(
    'private fun parseLeaderboardUser(obj: JSONObject, rank: Int = obj.optInt("rank", 0)): LeaderboardUser {\n        val rank = obj.optInt("rank", 0)',
    'private fun parseLeaderboardUser(obj: JSONObject, rank: Int = obj.optInt("rank", 0)): LeaderboardUser {'
)

with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'w') as f:
    f.write(content)

