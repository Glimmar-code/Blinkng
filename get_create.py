with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    content = f.read()

idx = content.find('suspend fun createFeedPost(')
idx2 = content.find('suspend fun', idx + 20)
print(content[idx:idx2])
