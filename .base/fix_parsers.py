with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    content = f.read()

# Add missing imports
if "import com.example.data.models.SkillEndorsement" not in content:
    content = content.replace("import com.example.data.models.UserProfile", "import com.example.data.models.UserProfile\nimport com.example.data.models.SkillEndorsement\nimport com.example.data.models.AvailabilityStatus\nimport com.example.data.models.AchievementBadge")

content = content.replace("private fun parseLeaderboardUser(obj: JSONObject, rank: Int): LeaderboardUser {", "private fun parseLeaderboardUser(obj: JSONObject): LeaderboardUser {\n        val rank = obj.optInt(\"rank\", 0)")

with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'w') as f:
    f.write(content)

