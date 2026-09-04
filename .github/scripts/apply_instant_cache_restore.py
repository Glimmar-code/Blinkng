from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_once(path: Path, old: str, new: str):
    text = path.read_text()
    if old not in text:
        raise SystemExit(f"Missing expected block in {path}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1))


store = ROOT / "app/src/main/java/com/example/data/local/OfflineContentStore.kt"
text = store.read_text()
text = text.replace(
    "import com.example.data.models.ChatConversation\nimport com.example.data.models.ChatMessage\nimport com.example.data.models.FeedPost\nimport com.example.data.models.UserProfile\n",
    "import com.example.data.models.ActivityItem\nimport com.example.data.models.ChatConversation\nimport com.example.data.models.ChatMessage\nimport com.example.data.models.ConnectHubSnapshot\nimport com.example.data.models.FeedPost\nimport com.example.data.models.LeaderboardUser\nimport com.example.data.models.MarketItem\nimport com.example.data.models.Story\nimport com.example.data.models.UserProfile\n"
)
text = text.replace(
    "import kotlinx.coroutines.Dispatchers\n",
    "import java.io.File\nimport kotlinx.coroutines.Dispatchers\n"
)
text = text.replace(
    "import kotlinx.coroutines.flow.map\n",
    "import kotlinx.coroutines.flow.map\nimport kotlinx.coroutines.withContext\n"
)
text = text.replace(
    "class OfflineContentStore(context: Context) {\n",
    '''data class CachedAppSnapshot(
    val ownerUsername: String = "",
    val myProfile: UserProfile = UserProfile(),
    val stories: List<Story> = emptyList(),
    val marketItems: List<MarketItem> = emptyList(),
    val leaderboardUsers: List<LeaderboardUser> = emptyList(),
    val activities: List<ActivityItem> = emptyList(),
    val connectHub: ConnectHubSnapshot = ConnectHubSnapshot(),
    val mutedUsers: Set<String> = emptySet(),
    val cachedAt: Long = 0L
)

class OfflineContentStore(context: Context) {
'''
)
text = text.replace(
    "private const val DEFAULT_CACHE_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L",
    "private const val DEFAULT_CACHE_MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L"
)
text = text.replace(
    "    private val dao = BlinkDatabase.getInstance(context).cachedContentDao()\n    private val codec = OfflineContentCodec()\n",
    '''    private val dao = BlinkDatabase.getInstance(context).cachedContentDao()
    private val codec = OfflineContentCodec()
    private val snapshotFile = File(context.noBackupFilesDir, "blink_main_snapshot.json")
    private val metadataPrefs = context.getSharedPreferences("blink_offline_cache_meta", Context.MODE_PRIVATE)

    fun cachedOwnerUsername(): String = metadataPrefs.getString("owner_username", "").orEmpty()

    private fun rememberOwner(username: String) {
        if (username.isNotBlank()) metadataPrefs.edit().putString("owner_username", username.lowercase()).apply()
    }
'''
)
text = text.replace(
    "    suspend fun replaceFeed(posts: List<FeedPost>, reels: List<FeedPost>) {\n",
    "    suspend fun replaceFeed(posts: List<FeedPost>, reels: List<FeedPost>, ownerUsername: String = \"\") {\n        rememberOwner(ownerUsername)\n"
)
text = text.replace(
    "    suspend fun replaceProfiles(profiles: List<UserProfile>) {\n",
    "    suspend fun replaceProfiles(profiles: List<UserProfile>, ownerUsername: String = \"\") {\n        rememberOwner(ownerUsername)\n"
)
text = text.replace(
    "    suspend fun replaceConversations(conversations: List<ChatConversation>) {\n",
    "    suspend fun replaceConversations(conversations: List<ChatConversation>, ownerUsername: String = \"\") {\n        rememberOwner(ownerUsername)\n"
)
needle = "    suspend fun resetOutbox(localId: String) = dao.resetOutbox(localId)\n"
insert = '''    suspend fun loadAppSnapshot(): CachedAppSnapshot? = withContext(Dispatchers.IO) {
        if (!snapshotFile.exists()) return@withContext null
        runCatching { codec.decodeAppSnapshot(snapshotFile.readText()) }
            .onFailure { Log.w(TAG, "Unable to read cached app snapshot", it) }
            .getOrNull()
    }

    suspend fun saveAppSnapshot(snapshot: CachedAppSnapshot) = withContext(Dispatchers.IO) {
        val normalized = snapshot.copy(cachedAt = System.currentTimeMillis())
        val json = codec.encodeAppSnapshot(normalized) ?: return@withContext
        rememberOwner(normalized.ownerUsername)
        val temp = File(snapshotFile.parentFile, "${snapshotFile.name}.tmp")
        runCatching {
            temp.writeText(json)
            if (!temp.renameTo(snapshotFile)) {
                snapshotFile.writeText(json)
                temp.delete()
            }
        }.onFailure { Log.w(TAG, "Unable to save cached app snapshot", it) }
    }

'''
if needle not in text:
    raise SystemExit("Could not find OfflineContentStore insertion point")
text = text.replace(needle, insert + needle, 1)
text = text.replace(
    "    private val messageAdapter: JsonAdapter<ChatMessage> = moshi.adapter(ChatMessage::class.java)\n",
    "    private val messageAdapter: JsonAdapter<ChatMessage> = moshi.adapter(ChatMessage::class.java)\n    private val appSnapshotAdapter: JsonAdapter<CachedAppSnapshot> = moshi.adapter(CachedAppSnapshot::class.java)\n"
)
text = text.replace(
    "    fun decodeMessage(json: String): ChatMessage? = runCatching { messageAdapter.fromJson(json) }\n        .onFailure { Log.w(\"OfflineContentCodec\", \"Ignoring an unreadable cached message\", it) }\n        .getOrNull()\n",
    '''    fun decodeMessage(json: String): ChatMessage? = runCatching { messageAdapter.fromJson(json) }
        .onFailure { Log.w("OfflineContentCodec", "Ignoring an unreadable cached message", it) }
        .getOrNull()

    fun encodeAppSnapshot(snapshot: CachedAppSnapshot): String? = runCatching { appSnapshotAdapter.toJson(snapshot) }
        .onFailure { Log.w("OfflineContentCodec", "Unable to encode app snapshot", it) }
        .getOrNull()

    fun decodeAppSnapshot(json: String): CachedAppSnapshot? = runCatching { appSnapshotAdapter.fromJson(json) }
        .onFailure { Log.w("OfflineContentCodec", "Ignoring an unreadable app snapshot", it) }
        .getOrNull()
'''
)
store.write_text(text)

vm = ROOT / "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"
text = vm.read_text()
text = text.replace(
    "import com.example.data.local.OfflineContentStore\n",
    "import com.example.data.local.CachedAppSnapshot\nimport com.example.data.local.OfflineContentStore\n"
)
text = text.replace(
    "        private const val KEY_SELLER_ACTIVE = \"is_seller_active\"\n",
    '''        private const val KEY_SELLER_ACTIVE = "is_seller_active"
        private const val KEY_DARK_MODE = "ui_dark_mode"
        private const val KEY_SELECTED_TAB = "ui_selected_tab"
        private const val KEY_FEED_SUB_TAB = "ui_feed_sub_tab"
'''
)
old_init = '''    init {
        SupabaseService.initialize(appContext)
        _uiState.value = _uiState.value.copy(isOnline = networkMonitor.isCurrentlyOnline())
        observeCachedContent()
        observeNetworkStatus()
        observeAuthState()
        viewModelScope.launch { restoreSupabaseSession() }
        loadDraftsFromPrefs()
'''
new_init = '''    init {
        SupabaseService.initialize(appContext)
        restoreUiPreferences()
        _uiState.value = _uiState.value.copy(isOnline = networkMonitor.isCurrentlyOnline())

        // Render the last authenticated account immediately. Supabase verification and
        // refresh continue in the background instead of blocking the first usable frame.
        val recoverableLocalSession = hasLocalAuthenticatedProfile() && (
            !SupabaseService.accessToken().isNullOrBlank() ||
                !SupabaseService.refreshToken().isNullOrBlank() ||
                AccountSessionStore.list(appContext).isNotEmpty()
            )
        if (recoverableLocalSession) restoreLocalSession()

        observeCachedContent()
        viewModelScope.launch { restoreCachedAppSnapshot() }
        observeNetworkStatus()
        observeAuthState()
        viewModelScope.launch { restoreSupabaseSession() }
        loadDraftsFromPrefs()
'''
if old_init not in text:
    raise SystemExit("Could not find ViewModel init block")
text = text.replace(old_init, new_init, 1)

old_restore_start = '''private suspend fun restoreSupabaseSession() {
        try {
            var restored = supabaseService.restoreSession()
'''
new_restore_start = '''private suspend fun restoreSupabaseSession() {
        try {
            // Local state is already usable when available; this call only validates and
            // refreshes the cloud session. Never blank the cached UI while it runs.
            if (_uiState.value.destination != AppDestination.MAIN && hasLocalAuthenticatedProfile()) {
                val recoverable = !SupabaseService.accessToken().isNullOrBlank() ||
                    !SupabaseService.refreshToken().isNullOrBlank() ||
                    AccountSessionStore.list(appContext).isNotEmpty()
                if (recoverable) restoreLocalSession()
            }
            var restored = supabaseService.restoreSession()
'''
if old_restore_start not in text:
    raise SystemExit("Could not find restoreSupabaseSession start")
text = text.replace(old_restore_start, new_restore_start, 1)

anchor = "    private fun saveSession(profile: UserProfile) = saveLocalProfile(profile)\n\n"
helpers = '''    private fun saveSession(profile: UserProfile) = saveLocalProfile(profile)

    private fun restoreUiPreferences() {
        val tabName = prefs.getString(KEY_SELECTED_TAB, MainTab.HOME.name).orEmpty()
        val selected = MainTab.entries.firstOrNull { it.name == tabName } ?: MainTab.HOME
        _uiState.value = _uiState.value.copy(
            isDarkMode = prefs.getBoolean(KEY_DARK_MODE, true),
            selectedTab = selected,
            feedSubTab = prefs.getInt(KEY_FEED_SUB_TAB, 0).coerceIn(0, 3)
        )
    }

    private fun persistUiPreferences() {
        val state = _uiState.value
        prefs.edit()
            .putBoolean(KEY_DARK_MODE, state.isDarkMode)
            .putString(KEY_SELECTED_TAB, state.selectedTab.name)
            .putInt(KEY_FEED_SUB_TAB, state.feedSubTab)
            .apply()
    }

    private suspend fun restoreCachedAppSnapshot() {
        val cached = offlineContentStore.loadAppSnapshot() ?: return
        val current = _uiState.value
        val activeUsername = current.myProfile.username
        if (activeUsername.isBlank() || !cached.ownerUsername.equals(activeUsername, true)) return

        _uiState.value = current.copy(
            myProfile = cached.myProfile.takeIf { it.username.equals(activeUsername, true) } ?: current.myProfile,
            stories = cached.stories.ifEmpty { current.stories },
            marketItems = cached.marketItems,
            leaderboardUsers = cached.leaderboardUsers,
            activities = cached.activities,
            connectHub = cached.connectHub,
            mutedUsers = cached.mutedUsers,
            isConnectHubLoading = false,
            activitiesLoading = false,
            isFeedLoading = false
        )
    }

    private fun persistExtendedCache() {
        val snapshot = _uiState.value
        if (snapshot.myProfile.username.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            cacheWriteMutex.withLock {
                runCatching {
                    offlineContentStore.saveAppSnapshot(
                        CachedAppSnapshot(
                            ownerUsername = snapshot.myProfile.username,
                            myProfile = snapshot.myProfile,
                            stories = snapshot.stories,
                            marketItems = snapshot.marketItems,
                            leaderboardUsers = snapshot.leaderboardUsers,
                            activities = snapshot.activities,
                            connectHub = snapshot.connectHub,
                            mutedUsers = snapshot.mutedUsers
                        )
                    )
                }.onFailure { Log.w(TAG, "Unable to persist extended app cache", it) }
            }
        }
    }

'''
if anchor not in text:
    raise SystemExit("Could not find saveSession anchor")
text = text.replace(anchor, helpers, 1)

# Only restore cached personal content when it belongs to the active account.
text = text.replace(
    '''        viewModelScope.launch {
            offlineContentStore.conversations.collectLatest { cachedConversations ->
                if (cachedConversations.isNotEmpty() && _uiState.value.conversations.isEmpty()) {
                    _uiState.value = _uiState.value.copy(conversations = cachedConversations)
                }
            }
        }
''',
    '''        viewModelScope.launch {
            offlineContentStore.conversations.collectLatest { cachedConversations ->
                val owner = offlineContentStore.cachedOwnerUsername()
                val activeUsername = _uiState.value.myProfile.username
                if (
                    owner.isNotBlank() && owner.equals(activeUsername, true) &&
                    cachedConversations.isNotEmpty() && _uiState.value.conversations.isEmpty()
                ) {
                    _uiState.value = _uiState.value.copy(conversations = cachedConversations)
                }
            }
        }
''',
    1
)

text = text.replace(
    "offlineContentStore.replaceFeed(snapshot.posts, snapshot.reels)",
    "offlineContentStore.replaceFeed(snapshot.posts, snapshot.reels, snapshot.myProfile.username)"
)
text = text.replace(
    "offlineContentStore.replaceConversations(snapshot)",
    "offlineContentStore.replaceConversations(snapshot, _uiState.value.myProfile.username)"
)
text = text.replace(
    "offlineContentStore.replaceFeed(normalPosts, fetchedReels)",
    "offlineContentStore.replaceFeed(normalPosts, fetchedReels, _uiState.value.myProfile.username)"
)
text = text.replace(
    "offlineContentStore.replaceProfiles(liveProfiles)",
    "offlineContentStore.replaceProfiles(liveProfiles, _uiState.value.myProfile.username)"
)
text = text.replace(
    "offlineContentStore.replaceConversations(conversations)",
    "offlineContentStore.replaceConversations(conversations, _uiState.value.myProfile.username)"
)

# Persist the fully hydrated non-feed sections after every successful/partial background sync.
old_finally = '''                } finally {
                    _uiState.value = _uiState.value.copy(
                        isFeedLoading = false,
                        isRefreshingContent = false,
                        isSyncingContent = false
                    )
                }
'''
new_finally = '''                } finally {
                    _uiState.value = _uiState.value.copy(
                        isFeedLoading = false,
                        isRefreshingContent = false,
                        isSyncingContent = false
                    )
                    persistExtendedCache()
                }
'''
if old_finally not in text:
    raise SystemExit("Could not find sync finally block")
text = text.replace(old_finally, new_finally, 1)

old_nav = '''    fun selectTab(tab: MainTab) { _uiState.value = _uiState.value.copy(selectedTab = tab, viewingProfile = null, viewingProduct = null, isConversationFullScreen = false) }
    fun setTab(tab: MainTab) = selectTab(tab)
    fun setFeedSubTab(tab: Int) { _uiState.value = _uiState.value.copy(feedSubTab = tab.coerceIn(0, 3)) }
    fun toggleDarkMode() { _uiState.value = _uiState.value.copy(isDarkMode = !_uiState.value.isDarkMode) }
'''
new_nav = '''    fun selectTab(tab: MainTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab, viewingProfile = null, viewingProduct = null, isConversationFullScreen = false)
        persistUiPreferences()
    }
    fun setTab(tab: MainTab) = selectTab(tab)
    fun setFeedSubTab(tab: Int) {
        _uiState.value = _uiState.value.copy(feedSubTab = tab.coerceIn(0, 3))
        persistUiPreferences()
    }
    fun toggleDarkMode() {
        _uiState.value = _uiState.value.copy(isDarkMode = !_uiState.value.isDarkMode)
        persistUiPreferences()
    }
'''
if old_nav not in text:
    raise SystemExit("Could not find navigation/theme methods")
text = text.replace(old_nav, new_nav, 1)

# Any explicit leaderboard refresh should update the reusable startup snapshot too.
text = text.replace(
    '''                .onSuccess { live ->
                    _uiState.value = _uiState.value.copy(leaderboardUsers = live)
                    showToast("Leaderboard refreshed from Supabase.")
''',
    '''                .onSuccess { live ->
                    _uiState.value = _uiState.value.copy(leaderboardUsers = live)
                    persistExtendedCache()
                    showToast("Leaderboard refreshed from Supabase.")
''',
    1
)

vm.write_text(text)
print("Applied instant cache restore changes")
