from pathlib import Path

script_path = Path('.github/scripts/apply_auth_professionalization.py')
source = script_path.read_text()

old = '''if 'override fun onDestroy()' not in text:
    marker = '    private fun generateSecureRandomNonce(byteLength: Int = 32): String {'
    insertion = ''' + "'''" + '''    override fun onDestroy() {
        if (isFinishing) GoogleAuthLaunchGate.end()
        super.onDestroy()
    }

''' + "'''" + '''
    text = replace_once(text, marker, insertion + marker, 'Google callback onDestroy')
'''

new = '''if 'override fun onDestroy()' not in text:
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

if old not in source:
    raise RuntimeError('Expected Google callback patch block was not found')

patched_source = source.replace(old, new, 1)
exec(compile(patched_source, str(script_path), 'exec'))
