import re
with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    content = f.read()
if "put(\"hobbies\", hobbiesArray)" in content:
    print("Success")
else:
    print("Failed")
