from pathlib import Path

script_path = Path('.github/scripts/apply_auth_professionalization.py')
source = script_path.read_text()

old_google = '''if 'override fun onDestroy()' not in text:
    marker = '    private fun generateSecureRandomNonce(byteLength: Int = 32): String {'
    insertion = ''' + "'''" + '''    override fun onDestroy() {
        if (isFinishing) GoogleAuthLaunchGate.end()
        super.onDestroy()
    }

''' + "'''" + '''
    text = replace_once(text, marker, insertion + marker, 'Google callback onDestroy')
'''

new_google = '''if 'override fun onDestroy()' not in text:
    insertion = ''' + "'''" + '''
    override fun onDestroy() {
        if (isFinishing) GoogleAuthLaunchGate.end()
        super.onDestroy()
    }
''' + "'''" + '''
    idx = text.rfind('\\n}')
    if idx < 0:
        raise RuntimeError('Google callback final class marker not found')
    text = text[:idx] + insertion + text[idx:]
'''

if old_google not in source:
    raise RuntimeError('Expected Google callback patch block was not found')
source = source.replace(old_google, new_google, 1)

old_supabase = '''    idx = text.rfind('\\n}')
    if idx < 0:
        raise RuntimeError('SupabaseService final class marker not found')
    text = text[:idx] + method + text[idx:]
'''

new_supabase = '''    class_end = '\\n}\\n\\nenum class ProfileMediaType'
    idx = text.find(class_end)
    if idx < 0:
        raise RuntimeError('SupabaseService class boundary not found')
    text = text[:idx] + method + text[idx:]
'''

if old_supabase not in source:
    raise RuntimeError('Expected SupabaseService insertion block was not found')
source = source.replace(old_supabase, new_supabase, 1)

exec(compile(source, str(script_path), 'exec'))
