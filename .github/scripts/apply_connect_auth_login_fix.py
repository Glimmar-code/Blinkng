from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel: str, text: str) -> None:
    (ROOT / rel).write_text(text, encoding="utf-8")


def replace_once(rel: str, old: str, new: str) -> None:
    text = read(rel)
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{rel}: expected one match, found {count}")
    write(rel, text.replace(old, new, 1))


def replace_between(rel: str, start: str, end: str, replacement: str) -> None:
    text = read(rel)
    i = text.find(start)
    if i < 0:
        raise RuntimeError(f"{rel}: start marker not found")
    j = text.find(end, i)
    if j < 0:
        raise RuntimeError(f"{rel}: end marker not found")
    write(rel, text[:i] + replacement + text[j:])


# -----------------------------------------------------------------------------
# Connect: remove the duplicate Connect title/header entirely.
# -----------------------------------------------------------------------------
replace_once(
    "app/src/main/java/com/example/ui/screens/ConnectSection.kt",
    '''        ConnectHeader(\n            userAvatar = userAvatar,\n            onMenuClick = onOpenMenu,\n            onNotificationClick = onOpenActivity,\n            onProfileClick = { onProfileClick("you") }\n        )\n\n        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)\n\n''',
    ""
)

# -----------------------------------------------------------------------------
# Feed hosts: Connect opens directly on content; Game no longer inherits Home/B.
# -----------------------------------------------------------------------------
premium = "app/src/main/java/com/example/ui/screens/PremiumFeedScreen.kt"
replace_between(
    premium,
    "@Composable\nprivate fun PremiumGameHost(",
    "@Composable\nprivate fun PremiumConnectHost(",
    '''@Composable
private fun PremiumGameHost(
    userAvatar: String,
    currentUsername: String,
    leaderboardUsers: List<LeaderboardUser>,
    connectHub: ConnectHubSnapshot,
    connectHubActions: ConnectHubActions,
    isDark: Boolean,
    hasUnreadNotifications: Boolean,
    onOpenMenu: () -> Unit,
    onOpenActivity: () -> Unit,
    onProfileClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onForYou: () -> Unit,
    onFollowing: () -> Unit,
    onReel: () -> Unit
) {
    val density = LocalDensity.current
    val threshold = with(density) { 64.dp.toPx() }
    var horizontalDrag by remember { mutableStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .background(FeedBackground)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        horizontalDrag += amount
                    },
                    onDragEnd = {
                        if (horizontalDrag >= threshold) onFollowing()
                        horizontalDrag = 0f
                    },
                    onDragCancel = { horizontalDrag = 0f }
                )
            }
    ) {
        GameSection(
            userAvatar = userAvatar,
            leaderboardUsers = leaderboardUsers,
            connectHub = connectHub,
            connectHubActions = connectHubActions,
            isDark = isDark,
            onOpenMenu = onOpenMenu,
            onOpenActivity = onOpenActivity,
            onProfileClick = onProfileClick,
            selectedTopTab = 3,
            onHomeClick = onForYou,
            onReelClick = onReel,
            onConnectClick = {},
            onGameClick = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

'''
)

replace_between(
    premium,
    "@Composable\nprivate fun PremiumConnectHost(",
    "/**\n * Lays the legacy section slightly taller",
    '''@Composable
private fun PremiumConnectHost(
    profiles: List<UserProfile>,
    currentUsername: String,
    userAvatar: String,
    isDark: Boolean,
    onOpenMenu: () -> Unit,
    onOpenActivity: () -> Unit,
    onProfileClick: (String) -> Unit,
    onDirectMessage: (partner: String, partnerName: String?, partnerAvatar: String?) -> Unit,
    connectHub: ConnectHubSnapshot,
    connectHubActions: ConnectHubActions,
    isConnectHubLoading: Boolean,
    onHomeClick: () -> Unit,
    onReelClick: () -> Unit,
    onGameClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(FeedBackground)
            .statusBarsPadding()
    ) {
        ConnectSection(
            profiles = profiles,
            currentUsername = currentUsername,
            userAvatar = userAvatar,
            isDark = isDark,
            onOpenMenu = onOpenMenu,
            onOpenActivity = onOpenActivity,
            onProfileClick = onProfileClick,
            onDirectMessage = onDirectMessage,
            connectHub = connectHub,
            connectHubActions = connectHubActions,
            isConnectHubLoading = isConnectHubLoading,
            selectedTopTab = 2,
            onHomeClick = onHomeClick,
            onReelClick = onReelClick,
            onConnectClick = {},
            onGameClick = onGameClick,
            modifier = Modifier.fillMaxSize()
        )
    }
}

'''
)

# Game gets its own minimal top row: menu dots + profile only.
replace_between(
    "app/src/main/java/com/example/ui/screens/GameSection.kt",
    "@Composable\nprivate fun GameHeader(",
    "@Composable\nprivate fun TopNavigationRow(",
    '''@Composable
private fun GameHeader(
    userAvatar: String,
    onMenuClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 38.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = "Menu",
                modifier = Modifier.size(27.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable { onProfileClick() }
        ) {
            AsyncImage(
                model = userAvatar,
                contentDescription = "Profile",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(4.dp))
    }
}

'''
)

# -----------------------------------------------------------------------------
# Account persistence: preserve account list, but explicit sign-out must never
# auto-restore a saved refresh token. Remember only the identifier in app prefs;
# passwords remain with Android's password manager rather than app plaintext.
# -----------------------------------------------------------------------------
store = "app/src/main/java/com/example/auth/AccountSessionStore.kt"
replace_once(
    store,
    '    private const val KEY_ACCOUNTS = "accounts"\n',
    '    private const val KEY_ACCOUNTS = "accounts"\n    private const val KEY_LAST_IDENTIFIER = "last_identifier"\n    private const val KEY_REQUIRE_SIGN_IN = "require_sign_in"\n'
)
replace_once(
    store,
    '    fun list(context: Context): List<Account> = load(context).sortedByDescending { it.lastUsedAt }\n\n',
    '''    fun list(context: Context): List<Account> = load(context).sortedByDescending { it.lastUsedAt }

    fun rememberIdentifier(context: Context, identifier: String) {
        val clean = identifier.trim()
        if (clean.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_IDENTIFIER, clean)
            .apply()
    }

    fun lastIdentifier(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_IDENTIFIER, "")
            .orEmpty()

    fun setSignInRequired(context: Context, required: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_REQUIRE_SIGN_IN, required)
            .apply()
    }

    fun isSignInRequired(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_REQUIRE_SIGN_IN, false)

'''
)
replace_once(
    store,
    '''    fun switchTo(context: Context, account: Account, accessToken: String, refreshToken: String) {
        SupabaseService.saveSession(accessToken, refreshToken)
''',
    '''    fun switchTo(context: Context, account: Account, accessToken: String, refreshToken: String) {
        SupabaseService.saveSession(accessToken, refreshToken)
        rememberIdentifier(context, account.email.ifBlank { account.username })
        setSignInRequired(context, false)
'''
)

# Auth success clears the explicit sign-in gate. Logout sets it before token
# revocation so the ViewModel cannot silently resurrect a saved account.
auth = "app/src/main/java/com/example/data/repository/AuthRepository.kt"
replace_once(
    auth,
    '''    suspend fun signOut() {
        try { supabaseService.revokeCurrentSupabaseSession() } catch (e: Exception) { Log.w("AuthRepository", "Supabase logout failed", e); SupabaseService.clearSession() }
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }.onFailure { Log.w("AuthRepository", "Unable to clear Google credential state", it) }
        prefs.edit().clear().apply()
        _authState.value = AuthState.Unauthenticated()
    }
''',
    '''    suspend fun signOut() {
        val rememberedIdentifier = prefs.getString("email", "").orEmpty()
            .ifBlank { prefs.getString("username", "").orEmpty() }
        AccountSessionStore.rememberIdentifier(context.applicationContext, rememberedIdentifier)
        AccountSessionStore.setSignInRequired(context.applicationContext, true)

        try {
            supabaseService.revokeCurrentSupabaseSession()
        } catch (e: Exception) {
            Log.w("AuthRepository", "Supabase logout failed", e)
        } finally {
            SupabaseService.clearSession()
        }

        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }.onFailure { Log.w("AuthRepository", "Unable to clear Google credential state", it) }

        prefs.edit().clear().apply()
        context.getSharedPreferences("blink_user_session", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_logged_in", false)
            .apply()
        _authState.value = AuthState.Unauthenticated()
    }
'''
)
replace_once(
    auth,
    '''    private fun persistSession(profile: UserProfile) {
        prefs.edit().apply { putBoolean("is_logged_in", true); putString("email", profile.email.value); putString("full_name", profile.fullName); putString("username", profile.username); putString("faculty", profile.faculty); putString("university", profile.university); putString("avatar_url", profile.avatarUrl); putString("cover_url", profile.coverPhotoUrl); apply() }
        AccountSessionStore.recordCurrentSession(context.applicationContext, profile.id, profile.username, profile.fullName, profile.email.value, profile.avatarUrl)
        BlinkFirebaseMessagingService.syncCurrentToken(context.applicationContext)
    }
''',
    '''    private fun persistSession(profile: UserProfile) {
        prefs.edit().apply { putBoolean("is_logged_in", true); putString("email", profile.email.value); putString("full_name", profile.fullName); putString("username", profile.username); putString("faculty", profile.faculty); putString("university", profile.university); putString("avatar_url", profile.avatarUrl); putString("cover_url", profile.coverPhotoUrl); apply() }
        AccountSessionStore.rememberIdentifier(
            context.applicationContext,
            profile.email.value.ifBlank { profile.username }
        )
        AccountSessionStore.setSignInRequired(context.applicationContext, false)
        AccountSessionStore.recordCurrentSession(context.applicationContext, profile.id, profile.username, profile.fullName, profile.email.value, profile.avatarUrl)
        BlinkFirebaseMessagingService.syncCurrentToken(context.applicationContext)
    }
'''
)

# ViewModel respects explicit logout/add-account even when encrypted accounts remain.
vm = "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"
replace_once(
    vm,
    '''        val hasLocalSession = hasLocalAuthenticatedProfile()
        if (hasLocalSession) restoreLocalSession()
''',
    '''        val explicitSignInRequired = AccountSessionStore.isSignInRequired(appContext)
        val hasLocalSession = !explicitSignInRequired && hasLocalAuthenticatedProfile()
        if (hasLocalSession) restoreLocalSession()
'''
)
replace_once(
    vm,
    '''                        val recoverable = !SupabaseService.refreshToken().isNullOrBlank() || AccountSessionStore.list(appContext).isNotEmpty()
                        if (_uiState.value.destination == AppDestination.MAIN && !recoverable) _uiState.value = _uiState.value.copy(destination = AppDestination.SIGN_IN)
''',
    '''                        val recoverable = !AccountSessionStore.isSignInRequired(appContext) &&
                            (!SupabaseService.refreshToken().isNullOrBlank() || AccountSessionStore.list(appContext).isNotEmpty())
                        if (_uiState.value.destination == AppDestination.MAIN && !recoverable) {
                            _uiState.value = _uiState.value.copy(destination = AppDestination.SIGN_IN)
                        }
'''
)
replace_once(
    vm,
    '''private suspend fun restoreSupabaseSession() {
        try {
''',
    '''private suspend fun restoreSupabaseSession() {
        try {
            if (AccountSessionStore.isSignInRequired(appContext)) {
                SupabaseService.clearSession()
                _uiState.value = _uiState.value.copy(destination = AppDestination.SIGN_IN)
                return
            }
'''
)

# Prefer the explicitly remembered email/username on the sign-in screen.
main = "app/src/main/java/com/example/MainActivity.kt"
replace_once(
    main,
    '''                                AppDestination.SIGN_IN -> {
                                    val recent = remember { AccountSessionStore.list(this@MainActivity).firstOrNull() }
                                    SignInScreen(
                                        initialIdentifier = recent?.email?.takeIf { it.isNotBlank() } ?: recent?.username.orEmpty(),
''',
    '''                                AppDestination.SIGN_IN -> {
                                    val initialIdentifier = remember {
                                        AccountSessionStore.lastIdentifier(this@MainActivity).ifBlank {
                                            val recent = AccountSessionStore.list(this@MainActivity).firstOrNull()
                                            recent?.email?.takeIf { it.isNotBlank() } ?: recent?.username.orEmpty()
                                        }
                                    }
                                    SignInScreen(
                                        initialIdentifier = initialIdentifier,
'''
)

# Switch-account screen gets a first-class Add account action that opens a fresh
# sign-in flow without deleting other encrypted saved account sessions.
switcher = "app/src/main/java/com/example/auth/AccountSwitcherActivity.kt"
replace_once(
    switcher,
    '''                    OutlinedButton(
                        onClick = {
                            AccountSessionStore.clear(this@AccountSwitcherActivity)
''',
    '''                    Button(
                        onClick = {
                            val recent = accounts.firstOrNull()
                            AccountSessionStore.rememberIdentifier(
                                this@AccountSwitcherActivity,
                                recent?.email?.takeIf { it.isNotBlank() } ?: recent?.username.orEmpty()
                            )
                            AccountSessionStore.setSignInRequired(this@AccountSwitcherActivity, true)
                            SupabaseService.clearSession()
                            getSharedPreferences("blink_auth_prefs", MODE_PRIVATE).edit().clear().apply()
                            getSharedPreferences("blink_user_session", MODE_PRIVATE)
                                .edit()
                                .putBoolean("is_logged_in", false)
                                .apply()
                            startActivity(Intent(this@AccountSwitcherActivity, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                            finish()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add account") }

                    OutlinedButton(
                        onClick = {
                            AccountSessionStore.clear(this@AccountSwitcherActivity)
'''
)

# Google: use Supabase/Android's current native ID-token option and show real errors
# instead of silently closing for every Credential Manager failure.
google = "app/src/main/java/com/example/auth/GoogleAuthCallbackActivity.kt"
replace_once(
    google,
    'import androidx.credentials.exceptions.GetCredentialException\n',
    'import androidx.credentials.exceptions.GetCredentialCancellationException\nimport androidx.credentials.exceptions.GetCredentialException\n'
)
replace_once(
    google,
    'import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption\n',
    'import com.google.android.libraries.identity.googleid.GetGoogleIdOption\n'
)
replace_once(
    google,
    '''        val googleOption = GetSignInWithGoogleOption.Builder(webClientId)
            .setNonce(hashedNonce)
            .build()
''',
    '''        val googleOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setNonce(hashedNonce)
            .build()
'''
)
replace_once(
    google,
    '''        } catch (error: GetCredentialException) {
            Log.i(TAG, "Google credential flow closed: ${error.type}")
            // Cancellation is normal; return silently to Blink. For other credential
            // errors the user can tap Continue with Google again.
            finish()
        } catch (error: Exception) {
''',
    '''        } catch (error: GetCredentialCancellationException) {
            Log.i(TAG, "Google credential flow cancelled by user")
            finish()
        } catch (error: GetCredentialException) {
            Log.e(TAG, "Google Credential Manager failed: ${error.type}", error)
            failAndFinish("Google sign-in could not start. Check Google Play services and try again.")
        } catch (error: Exception) {
'''
)

print("Applied Connect/Game/Auth/Google sign-in fixes successfully.")
