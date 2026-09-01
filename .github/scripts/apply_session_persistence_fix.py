from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
VM = ROOT / "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"

text = VM.read_text(encoding="utf-8")

if 'private const val AUTH_PREFS = "blink_auth_prefs"' not in text:
    text = text.replace(
        'private const val PREFS = "blink_user_session"\n',
        'private const val PREFS = "blink_user_session"\n        private const val AUTH_PREFS = "blink_auth_prefs"\n',
        1,
    )

if 'private val authPrefs = application.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)' not in text:
    text = text.replace(
        'private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)\n',
        'private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)\n    private val authPrefs = application.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)\n',
        1,
    )

replacement = '''private suspend fun restoreSupabaseSession() {
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

    private fun restoreLocalSession() {
        if (!hasLocalAuthenticatedProfile()) return
        val savedEmail = prefs.getString(KEY_EMAIL, authPrefs.getString(KEY_EMAIL, "")).orEmpty()
        val savedName = prefs.getString(KEY_FULL_NAME, authPrefs.getString(KEY_FULL_NAME, "Campus Student")).orEmpty()
        val savedUsername = prefs.getString(KEY_USERNAME, authPrefs.getString(KEY_USERNAME, "student")).orEmpty()
        val savedFaculty = prefs.getString(KEY_FACULTY, authPrefs.getString(KEY_FACULTY, "")).orEmpty()
        val savedUniversity = prefs.getString(KEY_UNIVERSITY, authPrefs.getString(KEY_UNIVERSITY, "")).orEmpty()
        val savedAvatar = prefs.getString(KEY_AVATAR, authPrefs.getString(KEY_AVATAR, "")).orEmpty()
        val savedCover = prefs.getString(KEY_COVER, authPrefs.getString(KEY_COVER, "")).orEmpty()
'''

pattern = re.compile(
r'    private suspend fun restoreSupabaseSession\(\) \{.*?    private fun restoreLocalSession\(\) \{.*?        val savedCover = prefs\.getString\(KEY_COVER, ""\)\.orEmpty\(\)\n',
    re.S,
)

text, count = pattern.subn(replacement, text, count=1)
if count != 1:
    raise SystemExit("Could not locate the session restore block; refusing to modify the file")

VM.write_text(text, encoding="utf-8")
print("Applied session persistence/recovery fix")
