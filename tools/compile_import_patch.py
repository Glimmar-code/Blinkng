from pathlib import Path
p=Path('app/src/main/java/com/example/data/supabase/SupabaseService.kt')
s=p.read_text()
needle='import kotlinx.coroutines.withContext'
if 'import kotlinx.coroutines.runBlocking' not in s:
    s=s.replace(needle, needle+'\nimport kotlinx.coroutines.runBlocking', 1)
p.write_text(s)
print('compile import patch applied')
