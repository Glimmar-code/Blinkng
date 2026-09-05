from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel: str, text: str) -> None:
    (ROOT / rel).write_text(text, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


# -----------------------------------------------------------------------------
# 1) Compose splash: smaller complete B and almost no artificial delay.
# -----------------------------------------------------------------------------
auth_path = "app/src/main/java/com/example/ui/screens/AuthScreens.kt"
auth = read(auth_path)
if 'label = "splash_b_scale"' in auth and 'delay(120)' not in auth:
    pattern = re.compile(
        r"@Composable\nfun SplashScreen\(\n    onTimeout: \(\) -> Unit\n\) \{.*?\n\}\n\n// ================================================================\n// ONBOARDING",
        re.S,
    )
    replacement = '''@Composable
fun SplashScreen(
    onTimeout: () -> Unit
) {
    var started by remember { mutableStateOf(false) }

    val bScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.92f,
        animationSpec = tween(
            durationMillis = 90,
            easing = FastOutSlowInEasing
        ),
        label = "splash_b_scale"
    )

    val bAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(
            durationMillis = 70,
            easing = FastOutSlowInEasing
        ),
        label = "splash_b_alpha"
    )

    LaunchedEffect(Unit) {
        started = true
        // Android already shows the system launch mark. Keep the Compose hand-off tiny
        // so startup never waits on branding or network/session work.
        delay(120)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "B",
            color = BlinkCream,
            fontSize = 52.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1).sp,
            modifier = Modifier
                .alpha(bAlpha)
                .scale(bScale)
        )
    }
}

// ================================================================
// ONBOARDING'''
    auth, count = pattern.subn(replacement, auth, count=1)
    if count != 1:
        raise RuntimeError(f"SplashScreen replacement failed: {count}")
    write(auth_path, auth)


# -----------------------------------------------------------------------------
# 2) Android 12+ system splash vector: keep the whole B well inside the safe mask.
# -----------------------------------------------------------------------------
splash_vector_path = "app/src/main/res/drawable/ic_splash_b.xml"
splash_vector = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Compact Blink B kept inside Android 12+'s system splash safe area. -->
    <path
        android:fillColor="@color/blink_splash_mark"
        android:pathData="M39,32 L58,32 C69,32 75,38 75,48 C75,54 72,58 67,60 C73,62 77,68 77,75 C77,85 70,90 58,90 L39,90 Z" />

    <path
        android:fillColor="@color/blink_splash_background"
        android:pathData="M50,42 L58,42 C63,42 66,45 66,49 C66,53 63,56 58,56 L50,56 Z" />

    <path
        android:fillColor="@color/blink_splash_background"
        android:pathData="M50,65 L59,65 C64,65 67,68 67,73 C67,78 64,81 59,81 L50,81 Z" />
</vector>
'''
if read(splash_vector_path) != splash_vector:
    write(splash_vector_path, splash_vector)


# -----------------------------------------------------------------------------
# 3) MainActivity: never wait on Supabase from splash; persist route on stop;
#    do not force Home back to sub-tab 0 on every process restart.
# -----------------------------------------------------------------------------
main_path = "app/src/main/java/com/example/MainActivity.kt"
main = read(main_path)
main = replace_once(
    main,
    '''    override fun onStop() {
        stopPresenceHeartbeat(markOffline = true)
        super.onStop()
    }''',
    '''    override fun onStop() {
        // Save the exact navigation surface before Android backgrounds or kills the task.
        viewModel.persistResumePoint()
        stopPresenceHeartbeat(markOffline = true)
        super.onStop()
    }''',
    "MainActivity onStop resume persistence",
)
main = replace_once(
    main,
    '''                                    SplashScreen(
                                        onTimeout = {
                                            if (com.example.data.supabase.SupabaseService.accessToken().isNullOrBlank() &&
                                                com.example.data.supabase.SupabaseService.refreshToken().isNullOrBlank()) {
                                                viewModel.setDestination(AppDestination.ONBOARDING)
                                            }
                                        }
                                    )''',
    '''                                    SplashScreen(
                                        onTimeout = { viewModel.completeSplash() }
                                    )''',
    "MainActivity splash callback",
)
old_reset = '''    // Old builds persisted Home/Reel/Connect/Game as feedSubTab values.
    // Always enter the redesigned Home shell on a fresh MainAppContent session.
    LaunchedEffect(Unit) {
        if (uiState.selectedTab == MainTab.HOME && uiState.feedSubTab != 0) {
            viewModel.setFeedSubTab(0)
        }
    }

'''
if old_reset in main:
    main = main.replace(old_reset, "", 1)
write(main_path, main)


# -----------------------------------------------------------------------------
# 4) ViewModel: persist and restore the last visible surface from local storage.
#    Network refresh remains background-only and cannot hold the launch screen.
# -----------------------------------------------------------------------------
vm_path = "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"
vm = read(vm_path)

key_anchor = '        private const val KEY_FEED_SUB_TAB = "ui_feed_sub_tab"\n'
keys = '''        private const val KEY_FEED_SUB_TAB = "ui_feed_sub_tab"
        private const val KEY_RESUME_PROFILE_USERNAME = "ui_resume_profile_username"
        private const val KEY_RESUME_PRODUCT_ID = "ui_resume_product_id"
        private const val KEY_RESUME_COMMENTS_POST_ID = "ui_resume_comments_post_id"
        private const val KEY_RESUME_POST_OPTIONS_ID = "ui_resume_post_options_id"
        private const val KEY_RESUME_DEEP_LINK_POST_ID = "ui_resume_deep_link_post_id"
        private const val KEY_RESUME_CONVERSATION = "ui_resume_conversation"
        private const val KEY_RESUME_STORY_ID = "ui_resume_story_id"
        private const val KEY_RESUME_POST_ITEM = "ui_resume_post_item"
        private const val KEY_RESUME_BECOME_SELLER = "ui_resume_become_seller"
        private const val KEY_RESUME_SELLER_CONGRATS = "ui_resume_seller_congrats"
        private const val KEY_RESUME_EDIT_PROFILE = "ui_resume_edit_profile"
        private const val KEY_RESUME_ACTIVITY = "ui_resume_activity"
        private const val KEY_RESUME_MENU = "ui_resume_menu"
        private const val KEY_RESUME_VERIFIED = "ui_resume_verified"
        private const val KEY_RESUME_CREATE_POST = "ui_resume_create_post"
        private const val KEY_RESUME_CREATE_STORY = "ui_resume_create_story"
        private const val KEY_RESUME_CONVERSATION_FULLSCREEN = "ui_resume_conversation_fullscreen"
'''
if 'KEY_RESUME_PROFILE_USERNAME' not in vm:
    vm = replace_once(vm, key_anchor, keys, "resume keys")

# Apply saved route after the durable local snapshot has hydrated profile/product/post objects.
vm = replace_once(
    vm,
    '''        viewModelScope.launch {
            restoreCachedAppSnapshot()
            if (hasLocalSession && !_uiState.value.isOnline) {''',
    '''        viewModelScope.launch {
            restoreCachedAppSnapshot()
            restoreResumableRouteFromCurrentState()
            if (hasLocalSession && !_uiState.value.isOnline) {''',
    "restore saved route after cache",
)

# Splash completion is local-first. Existing tokens may refresh in the background, but
# must never keep the user on the B screen.
insert_anchor = '    fun handleDeepLink(link: AppDeepLink) {\n'
startup_methods = '''    fun completeSplash() {
        if (_uiState.value.destination != AppDestination.SPLASH) return

        when {
            hasLocalAuthenticatedProfile() -> {
                restoreLocalSession()
                viewModelScope.launch {
                    restoreCachedAppSnapshot()
                    restoreResumableRouteFromCurrentState()
                }
            }
            !SupabaseService.accessToken().isNullOrBlank() ||
                !SupabaseService.refreshToken().isNullOrBlank() -> {
                // A remote session restore is already running. Show a usable auth surface
                // instead of blocking startup on network latency.
                _uiState.value = _uiState.value.copy(destination = AppDestination.SIGN_IN)
            }
            else -> _uiState.value = _uiState.value.copy(destination = AppDestination.ONBOARDING)
        }
    }

    fun persistResumePoint() {
        if (_uiState.value.destination != AppDestination.MAIN) return
        persistUiPreferences()
        persistResumableRoute(_uiState.value)
    }

'''
if 'fun completeSplash()' not in vm:
    vm = replace_once(vm, insert_anchor, startup_methods + insert_anchor, "startup methods")

old_restore_prefs = '''    private fun restoreUiPreferences() {
        val tabName = prefs.getString(KEY_SELECTED_TAB, MainTab.HOME.name).orEmpty()
        val selected = MainTab.entries.firstOrNull { it.name == tabName } ?: MainTab.HOME
        _uiState.value = _uiState.value.copy(
            isDarkMode = prefs.getBoolean(KEY_DARK_MODE, true),
            selectedTab = selected,
            // Do not restore the retired Home/Reel/Connect/Game sub-tab state.
            // Home now always starts on the premium For You lane.
            feedSubTab = 0
        )
    }
'''
new_restore_prefs = '''    private fun restoreUiPreferences() {
        val tabName = prefs.getString(KEY_SELECTED_TAB, MainTab.HOME.name).orEmpty()
        val selected = MainTab.entries.firstOrNull { it.name == tabName } ?: MainTab.HOME
        _uiState.value = _uiState.value.copy(
            isDarkMode = prefs.getBoolean(KEY_DARK_MODE, true),
            selectedTab = selected,
            feedSubTab = prefs.getInt(KEY_FEED_SUB_TAB, 0).coerceIn(0, 3)
        )
    }
'''
vm = replace_once(vm, old_restore_prefs, new_restore_prefs, "restore UI preferences")

persist_anchor = '''    private fun persistUiPreferences() {
        val state = _uiState.value
        prefs.edit()
            .putBoolean(KEY_DARK_MODE, state.isDarkMode)
            .putString(KEY_SELECTED_TAB, state.selectedTab.name)
            .putInt(KEY_FEED_SUB_TAB, state.feedSubTab)
            .apply()
    }
'''
resume_helpers = persist_anchor + '''
    private fun persistResumableRoute(state: BlinkUiState) {
        val editor = prefs.edit()

        fun putOrRemove(key: String, value: String?) {
            if (value.isNullOrBlank()) editor.remove(key) else editor.putString(key, value)
        }

        putOrRemove(KEY_RESUME_PROFILE_USERNAME, state.viewingProfile?.username)
        putOrRemove(KEY_RESUME_PRODUCT_ID, state.viewingProduct?.id)
        putOrRemove(KEY_RESUME_COMMENTS_POST_ID, state.activeCommentsPostId)
        putOrRemove(KEY_RESUME_POST_OPTIONS_ID, state.activePostOptionsPost?.id)
        putOrRemove(KEY_RESUME_DEEP_LINK_POST_ID, state.deepLinkedPost?.id)
        putOrRemove(KEY_RESUME_CONVERSATION, state.activeConversationPartner)
        putOrRemove(KEY_RESUME_STORY_ID, state.activeViewingStory?.id)

        editor
            .putBoolean(KEY_RESUME_POST_ITEM, state.isPostItemOpen)
            .putBoolean(KEY_RESUME_BECOME_SELLER, state.isBecomeSellerOpen)
            .putBoolean(KEY_RESUME_SELLER_CONGRATS, state.showSellerCongratulationsDialog)
            .putBoolean(KEY_RESUME_EDIT_PROFILE, state.isEditProfileOpen)
            .putBoolean(KEY_RESUME_ACTIVITY, state.isActivityOpen)
            .putBoolean(KEY_RESUME_MENU, state.isMenuOpen)
            .putBoolean(KEY_RESUME_VERIFIED, state.isGetVerifiedOpen)
            .putBoolean(KEY_RESUME_CREATE_POST, state.isCreatePostOpen)
            .putBoolean(KEY_RESUME_CREATE_STORY, state.isCreateStoryOpen)
            .putBoolean(KEY_RESUME_CONVERSATION_FULLSCREEN, state.isConversationFullScreen)
            .apply()
    }

    private fun restoreResumableRouteFromCurrentState() {
        if (!hasLocalAuthenticatedProfile() || _uiState.value.destination != AppDestination.MAIN) return

        val state = _uiState.value
        val profileUsername = prefs.getString(KEY_RESUME_PROFILE_USERNAME, null)
            ?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }
        val productId = prefs.getString(KEY_RESUME_PRODUCT_ID, null)?.takeIf { it.isNotBlank() }
        val commentsPostId = prefs.getString(KEY_RESUME_COMMENTS_POST_ID, null)?.takeIf { it.isNotBlank() }
        val postOptionsId = prefs.getString(KEY_RESUME_POST_OPTIONS_ID, null)?.takeIf { it.isNotBlank() }
        val deepLinkPostId = prefs.getString(KEY_RESUME_DEEP_LINK_POST_ID, null)?.takeIf { it.isNotBlank() }
        val conversation = prefs.getString(KEY_RESUME_CONVERSATION, null)
            ?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }
        val storyId = prefs.getString(KEY_RESUME_STORY_ID, null)?.takeIf { it.isNotBlank() }

        val knownProfiles = listOf(state.myProfile) + state.profiles
        val knownPosts = state.posts + state.reels

        val restoredProfile = profileUsername?.let { username ->
            knownProfiles.firstOrNull { it.username.equals(username, ignoreCase = true) }
        }
        val restoredProduct = productId?.let { id -> state.marketItems.firstOrNull { it.id == id } }
        val restoredPostOptions = postOptionsId?.let { id -> knownPosts.firstOrNull { it.id == id } }
        val restoredDeepLink = deepLinkPostId?.let { id -> knownPosts.firstOrNull { it.id == id } }
        val restoredStory = storyId?.let { id -> state.stories.firstOrNull { it.id == id } }

        _uiState.value = state.copy(
            viewingProfile = restoredProfile,
            viewingProduct = restoredProduct,
            activePostOptionsPost = restoredPostOptions,
            deepLinkedPost = restoredDeepLink,
            activeConversationPartner = conversation,
            activeViewingStory = restoredStory,
            isConversationFullScreen = conversation != null &&
                prefs.getBoolean(KEY_RESUME_CONVERSATION_FULLSCREEN, false),
            isPostItemOpen = prefs.getBoolean(KEY_RESUME_POST_ITEM, false),
            isBecomeSellerOpen = prefs.getBoolean(KEY_RESUME_BECOME_SELLER, false),
            showSellerCongratulationsDialog = prefs.getBoolean(KEY_RESUME_SELLER_CONGRATS, false),
            isEditProfileOpen = prefs.getBoolean(KEY_RESUME_EDIT_PROFILE, false),
            isActivityOpen = prefs.getBoolean(KEY_RESUME_ACTIVITY, false),
            isMenuOpen = prefs.getBoolean(KEY_RESUME_MENU, false),
            isGetVerifiedOpen = prefs.getBoolean(KEY_RESUME_VERIFIED, false),
            isCreatePostOpen = prefs.getBoolean(KEY_RESUME_CREATE_POST, false),
            isCreateStoryOpen = prefs.getBoolean(KEY_RESUME_CREATE_STORY, false),
            activeCommentsPostId = commentsPostId,
            isCommentsLoading = commentsPostId != null
        )

        if (commentsPostId != null) {
            viewModelScope.launch {
                val result = runCatching { postRepository.fetchComments(commentsPostId) }
                if (_uiState.value.activeCommentsPostId != commentsPostId) return@launch
                _uiState.value = _uiState.value.copy(
                    comments = result.getOrDefault(emptyList()),
                    isCommentsLoading = false
                )
                result.exceptionOrNull()?.let { Log.w(TAG, "Resume comment hydration failed", it) }
            }
        }
    }
'''
if 'private fun persistResumableRoute' not in vm:
    vm = replace_once(vm, persist_anchor, resume_helpers, "resume helpers")

write(vm_path, vm)

print("Applied fast launch + exact resume-state patch")
