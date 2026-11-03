from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]

vm = ROOT / 'app/src/main/java/com/example/viewmodel/BlinkViewModel.kt'
s = vm.read_text()
s = s.replace('''                val normalPosts =\n                    posts.filter {\n                        !it.isReel\n                    }''', '''                val normalPosts =\n                    posts.filter {\n                        !it.isReel && it.videoUrl.isNullOrBlank()\n                    }''', 1)
s = s.replace('''                val fetchedReels =\n                    posts.filter {\n                        it.isReel ||\n                                !it.videoUrl.isNullOrBlank()\n                    }''', '''                val fetchedReels =\n                    posts.filter {\n                        it.isReel || !it.videoUrl.isNullOrBlank()\n                    }''', 1)
old_restore = re.compile(r'''    private suspend fun restoreSupabaseSession\(\) \{.*?\n    \}\n\n    private fun restoreLocalSession''', re.S)
new_restore = '''    private suspend fun restoreSupabaseSession() {\n        try {\n            if (!supabaseService.restoreSession()) throw IllegalStateException("No valid Supabase session.")\n            val uid = supabaseService.getCurrentUserId() ?: throw IllegalStateException("Session has no authenticated UUID.")\n            val profile = profileRepository.fetchById(uid) ?: throw IllegalStateException("Authenticated user has no profile row.")\n            _uiState.value = _uiState.value.copy(myProfile = profile, destination = AppDestination.MAIN)\n            saveLocalProfile(profile)\n            authRepository.markAuthenticated(profile)\n            fetchSupabaseData()\n        } catch (e: Exception) {\n            Log.e(TAG, "restoreSupabaseSession failed", e)\n            val hasSession = !SupabaseService.accessToken().isNullOrBlank() || !SupabaseService.refreshToken().isNullOrBlank()\n            if (hasSession) {\n                restoreLocalSession()\n                _uiState.value = _uiState.value.copy(destination = AppDestination.MAIN)\n                fetchSupabaseData()\n            } else {\n                prefs.edit().clear().apply()\n                SupabaseService.clearSession()\n                _uiState.value = _uiState.value.copy(destination = AppDestination.SIGN_IN)\n            }\n        }\n    }\n\n    private fun restoreLocalSession'''
s, n = old_restore.subn(new_restore, s, count=1)
if n != 1:
    raise SystemExit('restoreSupabaseSession block not found')
# Remove obsolete fake notification generator, if still present.
s = re.sub(r'''    // ============================================================\n    // TEST NOTIFICATION\n    // ============================================================.*?(?=    // ============================================================\n    // STORY INTERACTIONS)''', '', s, flags=re.S)
vm.write_text(s)

activity = ROOT / 'app/src/main/java/com/example/MainActivity.kt'
s = activity.read_text()
s = s.replace('onSimulateNotification = { viewModel.simulateBackgroundNotification(context) }', 'onSimulateNotification = { viewModel.showToast("Test notifications are disabled. Real notifications are used.") }', 1)
s = s.replace('currentUserId = "you"', 'currentUserId = uiState.myProfile.id.ifBlank { "you" }', 1)
activity.write_text(s)

# Leave no stale import created solely for the removed fake callback.
PY = None
print('Audit fixes applied.')
