import re

with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    content = f.read()

# Replace client.newCall( req ).execute() with executeRequest( req )
new_content = re.sub(
    r'client\.newCall\(\s*([a-zA-Z0-9_]+)\s*\)\.execute\(\)',
    r'executeRequest(\1)',
    content
)

with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'w') as f:
    f.write(new_content)

