with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    lines = f.readlines()
for line in lines[:50]:
    if line.startswith('import '):
        print(line.strip())
