with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    lines = f.readlines()

open_b = 0
for i, l in enumerate(lines):
    open_b += l.count('{')
    open_b -= l.count('}')
    if open_b == 0 and i > 50:
        print(f"Brace closed at line {i+1}")
