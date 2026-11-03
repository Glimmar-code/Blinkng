with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    lines = f.readlines()

open_braces = 0
for i, line in enumerate(lines):
    open_braces += line.count('{')
    open_braces -= line.count('}')
    if open_braces == 0 and i > 100:
        print(f"Class ended at {i+1}")
