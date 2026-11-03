with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    content = f.read()

idx1 = content.find('suspend fun updateProfile')
idx2 = content.find('suspend fun', idx1 + 10)
if idx2 == -1: idx2 = len(content)

print(content[idx1:idx2])
