with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    content = f.read()

content = content.replace(
    '?: profile.id.takeIf { it.isNotBlank() && it != "user_me" }',
    '?: profile.id.takeIf { it.isNotBlank() }'
)
content = content.replace(
    'val id = obj.optString("id", "user_me")',
    'val id = obj.optString("id", "")'
)

with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'w') as f:
    f.write(content)


with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'r') as f:
    content = f.read()
    
content = content.replace(
    '?: profile.id.takeIf { it.isNotBlank() && it != "user_me" }',
    '?: profile.id.takeIf { it.isNotBlank() }'
)

with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'w') as f:
    f.write(content)

