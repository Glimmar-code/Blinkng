from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VM = ROOT / "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"
text = VM.read_text(encoding="utf-8")

if 'import com.example.auth.AccountSessionStore' not in text:
    text = text.replace('import com.example.data.models.*\n', 'import com.example.auth.AccountSessionStore\nimport com.example.data.models.*\n', 1)

text = text.replace(
    'private const val PREFS = "blink_user_session"\n',
    'private const val PREFS = "blink_user_session"\n        private const val AUTH_PREFS = "blink_auth_prefs"\n',
    1,
) if 'private const val AUTH_PREFS = "blink_auth_prefs"' not in text else text

if 'private val authPrefs = application.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)' not in text:
    text = text.replace(
        'private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)\n',
        'private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)\n    private val authPrefs = application.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)\n',
        1,
    )

start_marker = '    private suspend fun restoreSupabaseSession() {'
end_marker = '    private fun restoreLocalSession() {'
start = text.find(start_marker)
end = text.find(end_marker, start + len(start_marker))
if start < 0 or end < 0:
    raise SystemExit('Could not locate session restoration methods; refusing to modify the file')

new_restore = '''    private suspend fun restoreSupabaseSession() {
        try {
            var restored = supabaseService.restoreSession()

            // Recover the encrypted refresh token saved by AccountSessionStore if the
            // primary session preference was lost or was not written by an older build.
            if (!restored) {
                val recentAccount = AccountSessionStore.list(appContext).firstOrNull()
                if (recentAccount != null && recentAccount.refreshToken.isNotBlank()) {
                    SupabaseService.saveSession(recentAccount.accessToken, recentAccount.refreshToken)
                    restored = supabaseService.restoreSession()
                }
            }

            if (restored) {
                val uid = supabaseService.getCurrentUserId()
                if (!uid.isNullOrBlank()) {
                    val profile = profileRepository.fetchById(uid)
                    if (profile != null) {
                        _uiState.value = _uiState.value.copy(myProfile = profile, destination = AppDestination.MAIN)
                        saveLocalProfile(profile)
                        authRepository.markAuthenticated(profile)
                        fetchSupabaseData()
                        return
                    }
                }

                // A temporary profile/API failure must not turn a valid login into a logout.
                if (hasLocalAuthenticatedProfile()) {
                    restoreLocalSession()
                    authRepository.markAuthenticated(_uiState.value.myProfile)
                    _uiState.value = _uiState.value.copy(destination = AppDestination.MAIN)
                    fetchSupabaseData()
                    return
                }
            }

            if (hasLocalAuthenticatedProfile() &&
                (SupabaseService.accessToken() != null || AccountSessionStore.list(appContext).isNotEmpty())) {
                restoreLocalSession()
                _uiState.value = _uiState.value.copy(destination = AppDestination.MAIN)
                fetchSupabaseData()
            } else {
                _uiState.value = _uiState.value.copy(destination = AppDestination.SIGN_IN)
            }
        } catch (e: Exception) {
            Log.w(TAG, "restoreSupabaseSession notice: ${e.message}")
            if (hasLocalAuthenticatedProfile() &&
                (SupabaseService.accessToken() != null || AccountSessionStore.list(appContext).isNotEmpty())) {
                restoreLocalSession()
                _uiState.value = _uiState.value.copy(destination = AppDestination.MAIN)
                fetchSupabaseData()
            } else {
                _uiState.value = _uiState.value.copy(destination = AppDestination.SIGN_IN)
            }
        }
    }

    private fun hasLocalAuthenticatedProfile(): Boolean =
        prefs.getBoolean(KEY_IS_LOGGED_IN, false) || authPrefs.getBoolean(KEY_IS_LOGGED_IN, false)

'''
text = text[:start] + new_restore + text[end:]

# Make the local-profile fallback read both historical preference stores.
text = text.replace(
    '        if (!prefs.getBoolean(KEY_IS_LOGGED_IN, false)) return\n',
    '        if (!hasLocalAuthenticatedProfile()) return\n',
    1,
)
text = text.replace(
    'prefs.getString(KEY_EMAIL, "")',
    'prefs.getString(KEY_EMAIL, authPrefs.getString(KEY_EMAIL, ""))',
    1,
)
text = text.replace(
    'prefs.getString(KEY_FULL_NAME, "Campus Student")',
    'prefs.getString(KEY_FULL_NAME, authPrefs.getString(KEY_FULL_NAME, "Campus Student"))',
    1,
)
text = text.replace(
    'prefs.getString(KEY_USERNAME, "student")',
    'prefs.getString(KEY_USERNAME, authPrefs.getString(KEY_USERNAME, "student"))',
    1,
)
text = text.replace(
    'prefs.getString(KEY_FACULTY, "")',
    'prefs.getString(KEY_FACULTY, authPrefs.getString(KEY_FACULTY, ""))',
    1,
)
text = text.replace(
    'prefs.getString(KEY_UNIVERSITY, "")',
    'prefs.getString(KEY_UNIVERSITY, authPrefs.getString(KEY_UNIVERSITY, ""))',
    1,
)
text = text.replace(
    'prefs.getString(KEY_AVATAR, "")',
    'prefs.getString(KEY_AVATAR, authPrefs.getString(KEY_AVATAR, ""))',
    1,
)
text = text.replace(
    'prefs.getString(KEY_COVER, "")',
    'prefs.getString(KEY_COVER, authPrefs.getString(KEY_COVER, ""))',
    1,
)

VM.write_text(text, encoding="utf-8")
print("Applied robust session persistence/recovery fix")
