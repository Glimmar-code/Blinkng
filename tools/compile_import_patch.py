from pathlib import Path
import re

# SupabaseService feed reconciliation uses runBlocking.
p=Path('app/src/main/java/com/example/data/supabase/SupabaseService.kt')
s=p.read_text()
needle='import kotlinx.coroutines.withContext'
if 'import kotlinx.coroutines.runBlocking' not in s:
    s=s.replace(needle, needle+'\nimport kotlinx.coroutines.runBlocking', 1)
p.write_text(s)

# Compose 1.12.0 requires compileSdk 37. Keep targetSdk unchanged to avoid
# opting users into new runtime behavior as part of this backend repair.
p=Path('app/build.gradle.kts')
s=p.read_text()
s=re.sub(r'compileSdk\s*\{\s*version\s*=\s*release\(36\)\s*\{\s*minorApiLevel\s*=\s*1\s*\}\s*\}', 'compileSdk { version = release(37) }', s, count=1)
s=s.replace('sourceCompatibility = JavaVersion.VERSION_11','sourceCompatibility = JavaVersion.VERSION_17')
s=s.replace('targetCompatibility = JavaVersion.VERSION_11','targetCompatibility = JavaVersion.VERSION_17')
p.write_text(s)
print('compile sdk/toolchain patch applied')
