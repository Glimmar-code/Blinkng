import re
with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    content = f.read()

idx_start = content.find('private fun parseUserProfile(')
idx_end = content.find('    // ============================================================', idx_start)
print(idx_start, idx_end)
