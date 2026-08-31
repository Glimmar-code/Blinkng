with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    content = f.read()

old_s = """            id =
                obj.optString(
                    "id",
                    "user_me"
                ),"""

new_s = """            id =
                obj.optString(
                    "id",
                    ""
                ),"""

content = content.replace(old_s, new_s)

with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'w') as f:
    f.write(content)

