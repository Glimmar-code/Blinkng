package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.AccountSessionStore
import com.example.data.local.CachedAppSnapshot
import com.example.data.local.OfflineContentStore
import com.example.data.models.*
import com.example.data.network.NetworkMonitor
import com.example.data.repository.*
import com.example.data.supabase.RealtimeEvent
import com.example.data.supabase.SupabaseRealtimeManager
import com.example.data.supabase.SupabaseService
import com.example.data.supabase.MessageMediaService
import com.example.notification.BlinkNotificationHelper
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray

enum class AppDestination { SPLASH, ONBOARDING, SIGN_IN, SIGN_UP, PROFILE_SETUP, MAIN }

enum class MainTab(val index: Int, val title: String) { HOME(0, "Home"), SEARCH(1, "Search"), LEADERBOARD(2, "Leaderboard"), MARKET(3, "Market"), MESSAGES(4, "Messages") }

data class BlinkUiState(
    val destination: AppDestination = AppDestination.SPLASH,
    val selectedTab: MainTab = MainTab.HOME,
    val isDarkMode: Boolean = true,
    val myProfile: UserProfile = UserProfile(),
    val profiles: List<UserProfile> = emptyList(),
    val viewingProfile: UserProfile? = null,
    val viewingProduct: MarketItem? = null,
    val isPostItemOpen: Boolean = false,
    val isBecomeSellerOpen: Boolean = false,
    val showSellerCongratulationsDialog: Boolean = false,
    val isEditProfileOpen: Boolean = false,
    val isActivityOpen: Boolean = false,
    val isMenuOpen: Boolean = false,
    val isGetVerifiedOpen: Boolean = false,
    val isCreatePostOpen: Boolean = false,
    val isCreateStoryOpen: Boolean = false,
    val isCreatingStory: Boolean = false,
    val activeCommentsPostId: String? = null,
    val activePostOptionsPost: FeedPost? = null,
    val activeConversationPartner: String? = null,
    val activeViewingStory: Story? = null,
    val isConversationFullScreen: Boolean = false,
    val stories: List<Story> = listOf(Story(id = "story_me", username = "Your Story", avatar = "", hasUnseen = false, isUser = true)),
    val posts: List<FeedPost> = emptyList(),
    val reels: List<FeedPost> = emptyList(),
    val savedDrafts: List<PostDraft> = emptyList(),
    val scheduledPosts: List<ScheduledPost> = emptyList(),
    val marketItems: List<MarketItem> = emptyList(),
    val leaderboardUsers: List<LeaderboardUser> = emptyList(),
    val conversations: List<ChatConversation> = emptyList(),
    val activities: List<ActivityItem> = emptyList(),
    val activitiesLoading: Boolean = false,
    val activitiesError: String? = null,
    val connectHub: ConnectHubSnapshot = ConnectHubSnapshot(),
    val isConnectHubLoading: Boolean = false,
    val comments: List<Comment> = emptyList(),
    val mutedUsers: Set<String> = emptySet(),
    val feedSubTab: Int = 0,
    val isOnline: Boolean = true,
    val isLiveSupabaseConnected: Boolean = false,
    val isFeedLoading: Boolean = true,
    val isRefreshingContent: Boolean = false,
    val isSyncingContent: Boolean = false,
    val feedErrorMessage: String? = null,
    val isCreatingPost: Boolean = false,
    val pendingMessageCount: Int = 0,
    val discoverProfiles: List<UserProfile> = emptyList(),
    val discoverPosts: List<FeedPost> = emptyList(),
    val isDiscoverSearching: Boolean = false,
    val hasMorePosts: Boolean = true,
    val hasMoreReels: Boolean = true,
    val isLoadingMorePosts: Boolean = false,
    val isLoadingMoreReels: Boolean = false,
    val messageHistoryHasMore: Map<String, Boolean> = emptyMap(),
    val loadingOlderConversationId: String? = null
)

class BlinkViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "BlinkViewModel"
        private const val PREFS = "blink_user_session"
        private const val AUTH_PREFS = "blink_auth_prefs"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_EMAIL = "email"
        private const val KEY_FULL_NAME = "full_name"
        private const val KEY_USERNAME = "username"
        private const val KEY_FACULTY = "faculty"
        private const val KEY_UNIVERSITY = "university"
        private const val KEY_AVATAR = "avatar_url"
        private const val KEY_COVER = "cover_url"
        private const val KEY_VERIFICATION = "verification_badge"
        private const val KEY_SELLER_ACTIVE = "is_seller_active"
        private const val KEY_DARK_MODE = "ui_dark_mode"
        private const val KEY_SELECTED_TAB = "ui_selected_tab"
        private const val KEY_FEED_SUB_TAB = "ui_feed_sub_tab"
    }

    private val application = application
    private val appContext: Context = application.applicationContext
    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val authPrefs = application.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
    private val supabaseService = SupabaseService()
    val authRepository = AuthRepository(application, supabaseService)
    val profileRepository = ProfileRepository(supabaseService)
    val postRepository = PostRepository(supabaseService)
    val marketRepository = MarketRepository(supabaseService)
    val chatRepository = ChatRepository(supabaseService)
    private val connectHubRepository = ConnectHubRepository(supabaseService)
    val realtimeManager = SupabaseRealtimeManager.getInstance()
    private val offlineContentStore = OfflineContentStore(appContext)
    private val networkMonitor = NetworkMonitor(appContext)
    private val syncMutex = Mutex()
    private val cacheWriteMutex = Mutex()
    private var syncJob: Job? = null
    private var lastSuccessfulSyncAt = 0L
    private var discoverSearchJob: Job? = null
    private val _uiState = MutableStateFlow(BlinkUiState())
    val uiState: StateFlow<BlinkUiState> = _uiState.asStateFlow()
    private val _snackBarMessages = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val snackBarMessages: SharedFlow<String> = _snackBarMessages.asSharedFlow()

    init {
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
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { offlineContentStore.pruneOldCaches() }
                .onFailure { Log.w(TAG, "Offline cache pruning failed", it) }
        }
        viewModelScope.launch { realtimeManager.events.collect { handleRealtimeEvent(it) } }
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.authState.collect { authState ->
                when (authState) {
                    is AuthState.Authenticated -> {
                        val profile = authState.userProfile
                        _uiState.value = _uiState.value.copy(myProfile = profile, destination = when (_uiState.value.destination) {
                            AppDestination.SIGN_IN, AppDestination.SIGN_UP, AppDestination.ONBOARDING, AppDestination.SPLASH -> AppDestination.MAIN
                            else -> _uiState.value.destination
                        })
                        saveLocalProfile(profile)
                        refreshMyProfileFromSupabase(showErrorToast = false)
                        fetchSupabaseData()
                    }
                    is AuthState.Unauthenticated -> {
                        val recoverable = !SupabaseService.refreshToken().isNullOrBlank() || AccountSessionStore.list(appContext).isNotEmpty()
                        if (_uiState.value.destination == AppDestination.MAIN && !recoverable) _uiState.value = _uiState.value.copy(destination = AppDestination.SIGN_IN)
                    }
                    else -> Unit
                }
            }
        }
    }

private suspend fun restoreSupabaseSession() {
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
                        persistProfile(profile)
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
        val savedName = prefs.getString(KEY_FULL_NAME, authPrefs.getString(KEY_FULL_NAME, "")).orEmpty()
        val savedUsername = prefs.getString(KEY_USERNAME, authPrefs.getString(KEY_USERNAME, "")).orEmpty()
        if (savedName.isBlank() || savedUsername.isBlank()) return
        val savedFaculty = prefs.getString(KEY_FACULTY, authPrefs.getString(KEY_FACULTY, "")).orEmpty()
        val savedUniversity = prefs.getString(KEY_UNIVERSITY, authPrefs.getString(KEY_UNIVERSITY, "")).orEmpty()
        val savedAvatar = prefs.getString(KEY_AVATAR, authPrefs.getString(KEY_AVATAR, "")).orEmpty()
        val savedCover = prefs.getString(KEY_COVER, authPrefs.getString(KEY_COVER, "")).orEmpty()
        val badge = when (prefs.getString(KEY_VERIFICATION, "")?.uppercase()) {
            "GOLD" -> VerificationBadge.GOLD
            "BLUE" -> VerificationBadge.BLUE
            else -> VerificationBadge.NONE
        }
        _uiState.value = _uiState.value.copy(
            myProfile = UserProfile(
                fullName = savedName,
                username = savedUsername,
                email = ContactField(savedEmail, true),
                faculty = savedFaculty,
                university = savedUniversity,
                avatarUrl = savedAvatar,
                coverPhotoUrl = savedCover,
                verificationBadge = badge,
                isSellerActive = prefs.getBoolean(KEY_SELLER_ACTIVE, false)
            ),
            destination = AppDestination.MAIN
        )
    }

    private fun saveLocalProfile(profile: UserProfile) {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_EMAIL, profile.email.value)
            .putString(KEY_FULL_NAME, profile.fullName)
            .putString(KEY_USERNAME, profile.username)
            .putString(KEY_FACULTY, profile.faculty)
            .putString(KEY_UNIVERSITY, profile.university)
            .putString(KEY_AVATAR, profile.avatarUrl)
            .putString(KEY_COVER, profile.coverPhotoUrl)
            .putString(KEY_VERIFICATION, profile.verificationBadge.name)
            .putBoolean(KEY_SELLER_ACTIVE, profile.isSellerActive)
            .apply()
    }

    private fun saveSession(profile: UserProfile) = saveLocalProfile(profile)

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

    private fun observeCachedContent() {
        viewModelScope.launch {
            offlineContentStore.posts.collectLatest { cachedPosts ->
                if (cachedPosts.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        posts = cachedPosts,
                        isFeedLoading = false
                    )
                }
            }
        }
        viewModelScope.launch {
            offlineContentStore.reels.collectLatest { cachedReels ->
                if (cachedReels.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        reels = cachedReels,
                        isFeedLoading = false
                    )
                }
            }
        }
        viewModelScope.launch {
            offlineContentStore.profiles.collectLatest { cachedProfiles ->
                if (cachedProfiles.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(profiles = cachedProfiles)
                }
            }
        }
        viewModelScope.launch {
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
        viewModelScope.launch {
            offlineContentStore.pendingOutboxCount.collectLatest { count ->
                _uiState.value = _uiState.value.copy(pendingMessageCount = count)
            }
        }
    }

    private fun observeNetworkStatus() {
        viewModelScope.launch {
            var previousStatus: Boolean? = null
            networkMonitor.isOnline.collectLatest { online ->
                val connectionRestored = previousStatus == false && online
                previousStatus = online
                _uiState.value = _uiState.value.copy(
                    isOnline = online,
                    isLiveSupabaseConnected = if (online) {
                        _uiState.value.isLiveSupabaseConnected
                    } else {
                        false
                    },
                    isFeedLoading = if (online) {
                        _uiState.value.isFeedLoading
                    } else {
                        false
                    }
                )

                if (connectionRestored && _uiState.value.destination == AppDestination.MAIN) {
                    drainMessageOutbox()
                    fetchSupabaseData()
                }
            }
        }
    }

    private fun persistCurrentFeed() {
        val snapshot = _uiState.value
        viewModelScope.launch(Dispatchers.IO) {
            cacheWriteMutex.withLock {
                runCatching {
                    offlineContentStore.replaceFeed(snapshot.posts, snapshot.reels, snapshot.myProfile.username)
                }.onFailure { Log.w(TAG, "Unable to persist the current feed snapshot", it) }
            }
        }
    }

    private fun persistProfile(profile: UserProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            cacheWriteMutex.withLock {
                runCatching { offlineContentStore.upsertProfile(profile) }
                    .onFailure { Log.w(TAG, "Unable to persist profile ${profile.username}", it) }
            }
        }
    }

    private fun persistConversations() {
        val snapshot = _uiState.value.conversations
        viewModelScope.launch(Dispatchers.IO) {
            cacheWriteMutex.withLock {
                runCatching { offlineContentStore.replaceConversations(snapshot, _uiState.value.myProfile.username) }
                    .onFailure { Log.w(TAG, "Unable to persist conversations", it) }
            }
        }
    }

    fun refreshIfStale(maxAgeMillis: Long = 60_000L) {
        val lastSync = lastSuccessfulSyncAt
        val isFresh = lastSync != 0L && SystemClock.elapsedRealtime() - lastSync < maxAgeMillis
        if (!isFresh) fetchSupabaseData()
    }

    fun fetchSupabaseData(showRefreshIndicator: Boolean = false) {
        // Auth restore, onResume, reconnect and realtime can all request a refresh at once.
        // Coalesce those requests instead of queueing several full Supabase syncs back-to-back.
        if (syncJob?.isActive == true) return

        syncJob = viewModelScope.launch {
            if (!_uiState.value.isOnline) {
                _uiState.value = _uiState.value.copy(
                    isFeedLoading = false,
                    isRefreshingContent = false,
                    isSyncingContent = false,
                    isLiveSupabaseConnected = false
                )
                if (showRefreshIndicator) {
                    showToast("You're offline. Showing saved content.")
                }
                return@launch
            }

            syncMutex.withLock {
                val before = _uiState.value
                val hadFeed = before.posts.isNotEmpty() || before.reels.isNotEmpty()

                _uiState.value = before.copy(
                    isFeedLoading = !hadFeed && !showRefreshIndicator,
                    isRefreshingContent = showRefreshIndicator,
                    isSyncingContent = true,
                    feedErrorMessage = null
                )

                try {
                    runCatching { supabaseService.setMyPresence(true) }
                    val postsRequest = async {
                        runCatching { postRepository.fetchFeed(isReel = false) }
                            .onFailure { Log.e(TAG, "Post page fetch failed", it) }
                    }
                    val reelsRequest = async {
                        runCatching { postRepository.fetchFeed(isReel = true) }
                            .onFailure { Log.e(TAG, "Reel page fetch failed", it) }
                    }
                    val postsResult = postsRequest.await()
                    val reelsResult = reelsRequest.await()

                    val normalPosts = postsResult.getOrDefault(before.posts).distinctBy { it.id }
                    val fetchedReels = reelsResult.getOrDefault(before.reels).distinctBy { it.id }
                    val feedSucceeded = postsResult.isSuccess || reelsResult.isSuccess
                    if (feedSucceeded) lastSuccessfulSyncAt = SystemClock.elapsedRealtime()

                    _uiState.value = _uiState.value.copy(
                        posts = normalPosts,
                        reels = fetchedReels,
                        hasMorePosts = postsResult.getOrNull()?.size?.let { it >= 30 } ?: before.hasMorePosts,
                        hasMoreReels = reelsResult.getOrNull()?.size?.let { it >= 30 } ?: before.hasMoreReels,
                        isLiveSupabaseConnected = feedSucceeded,
                        isFeedLoading = false,
                        feedErrorMessage = if (!feedSucceeded) {
                            "Couldn't refresh live Supabase data. Check your connection and try again."
                        } else null
                    )

                    if (feedSucceeded) {
                        cacheWriteMutex.withLock {
                            runCatching {
                                offlineContentStore.replaceFeed(normalPosts, fetchedReels, _uiState.value.myProfile.username)
                            }.onFailure { Log.w(TAG, "Feed cache update failed", it) }
                        }
                    }

                    // These sections are independent. Fetching them together makes startup
                    // wait for the slowest request instead of the sum of every request.
                    val profilesRequest = async {
                        runCatching { supabaseService.fetchProfiles() }
                            .onFailure { Log.e(TAG, "Profiles fetch failed", it) }
                    }
                    val marketRequest = async {
                        runCatching { supabaseService.fetchMarketItems() }
                            .onFailure { Log.e(TAG, "Market fetch failed", it) }
                    }
                    val conversationsRequest = async {
                        runCatching { MessageMediaService.hydrateVideos(chatRepository.fetchConversations()) }
                            .onFailure { Log.e(TAG, "Message fetch failed", it) }
                    }
                    val leaderboardRequest = async {
                        runCatching { supabaseService.fetchLeaderboard() }
                            .onFailure { Log.e(TAG, "Leaderboard fetch failed", it) }
                    }
                    val connectHubRequest = async {
                        runCatching { connectHubRepository.fetchSnapshot() }
                            .onFailure { Log.e(TAG, "Connect Hub fetch failed", it) }
                    }
                    val storiesRequest = async {
                        runCatching { supabaseService.fetchStories() }
                            .onFailure { Log.e(TAG, "Stories fetch failed", it) }
                    }
                    val activitiesRequest = async {
                        runCatching { supabaseService.fetchActivities() }
                            .onFailure { Log.e(TAG, "Activities fetch failed", it) }
                    }

                    val profilesResult = profilesRequest.await()
                    val liveProfiles = profilesResult
                        .getOrDefault(before.profiles)
                        .filter { it.username.isNotBlank() }
                        .distinctBy { it.id.ifBlank { it.username.lowercase() } }

                    if (profilesResult.isSuccess) {
                        cacheWriteMutex.withLock {
                            runCatching { offlineContentStore.replaceProfiles(liveProfiles, _uiState.value.myProfile.username) }
                                .onFailure { Log.w(TAG, "Profile cache update failed", it) }
                        }
                    }

                    val market = marketRequest.await()
                        .getOrDefault(before.marketItems)

                    val conversationsResult = conversationsRequest.await()
                    val conversationSummaries = conversationsResult.getOrDefault(before.conversations)
                    val conversations = conversationSummaries.map { summary ->
                        val cached = before.conversations.firstOrNull {
                            it.id == summary.id || it.partnerUsername.equals(summary.partnerUsername, true)
                        }
                        summary.copy(messages = cached?.messages ?: mutableListOf())
                    }
                    if (conversationsResult.isSuccess) {
                        cacheWriteMutex.withLock {
                            runCatching { offlineContentStore.replaceConversations(conversations, _uiState.value.myProfile.username) }
                                .onFailure { Log.w(TAG, "Conversation cache update failed", it) }
                        }
                    }

                    val leaderboard = leaderboardRequest.await()
                        .getOrDefault(before.leaderboardUsers)

                    val connectHub = connectHubRequest.await()
                        .getOrDefault(before.connectHub)

                    val cloudStories = storiesRequest.await()
                        .getOrDefault(before.stories.filterNot { it.id == "story_me" })

                    val myProfile = _uiState.value.myProfile
                    val userStoryHeader = Story(
                        id = "story_me",
                        username = "Your Story",
                        avatar = myProfile.avatarUrl,
                        hasUnseen = false,
                        isUser = true
                    )

                    val mine = cloudStories.filter {
                        it.isUser || it.username.equals(myProfile.username, true)
                    }
                    val others = cloudStories.filter {
                        !it.isUser && !it.username.equals(myProfile.username, true)
                    }
                    val mergedStories = if (mine.isNotEmpty()) {
                        mine + others
                    } else {
                        listOf(userStoryHeader) + others
                    }

                    _uiState.value = _uiState.value.copy(
                        profiles = liveProfiles,
                        marketItems = market,
                        conversations = conversations,
                        leaderboardUsers = leaderboard,
                        connectHub = connectHub,
                        isConnectHubLoading = false,
                        stories = mergedStories,
                        activitiesLoading = true,
                        activitiesError = null
                    )

                    activitiesRequest.await()
                        .onSuccess { result ->
                            result.fold(
                                { activities ->
                                    _uiState.value = _uiState.value.copy(
                                        activities = activities,
                                        activitiesLoading = false
                                    )
                                },
                                { error ->
                                    _uiState.value = _uiState.value.copy(
                                        activitiesLoading = false,
                                        activitiesError = error.message
                                    )
                                }
                            )
                        }
                        .onFailure { error ->
                            _uiState.value = _uiState.value.copy(
                                activitiesLoading = false,
                                activitiesError = error.message
                            )
                        }

                    val curUser = supabaseService.getCurrentUsername() ?: myProfile.username
                    val curUid = supabaseService.getCurrentUserId() ?: ""
                    if (curUid.isNotBlank() && myProfile.username.isNotBlank()) {
                        AccountSessionStore.recordCurrentSession(appContext, curUid, myProfile.username, myProfile.fullName, myProfile.email.value, myProfile.avatarUrl)
                    }
                    if (curUser.isNotBlank() || curUid.isNotBlank()) {
                        realtimeManager.connect(curUser, curUid)
                    }
                } finally {
                    _uiState.value = _uiState.value.copy(
                        isFeedLoading = false,
                        isRefreshingContent = false,
                        isSyncingContent = false
                    )
                    persistExtendedCache()
                }
            }
        }
    }

    fun refreshContent() = fetchSupabaseData(showRefreshIndicator = true)

    fun refreshLeaderboard() {
        viewModelScope.launch {
            runCatching { supabaseService.fetchLeaderboard() }
                .onSuccess { live ->
                    _uiState.value = _uiState.value.copy(leaderboardUsers = live)
                    persistExtendedCache()
                    showToast("Leaderboard refreshed from Supabase.")
                }
                .onFailure {
                    Log.e(TAG, "Leaderboard refresh failed", it)
                    showToast("Couldn't refresh the leaderboard.")
                }
        }
    }



    fun searchDiscover(query: String) {
        discoverSearchJob?.cancel()
        val clean = query.trim().removePrefix("#")
        if (clean.isBlank()) {
            _uiState.value = _uiState.value.copy(
                discoverProfiles = emptyList(),
                discoverPosts = emptyList(),
                isDiscoverSearching = false
            )
            return
        }

        discoverSearchJob = viewModelScope.launch {
            delay(280)
            _uiState.value = _uiState.value.copy(isDiscoverSearching = true)
            try {
                val people = profileRepository.searchProfiles(clean)
                val posts = postRepository.searchPosts(clean, limit = 30)
                _uiState.value = _uiState.value.copy(
                    discoverProfiles = people.distinctBy { it.id.ifBlank { it.username.lowercase() } },
                    discoverPosts = posts.distinctBy { it.id },
                    isDiscoverSearching = false
                )
            } catch (e: Exception) {
                Log.e(TAG, "Discover search failed", e)
                _uiState.value = _uiState.value.copy(isDiscoverSearching = false)
            }
        }
    }

    fun loadMoreFeed(isReel: Boolean) {
        val state = _uiState.value
        if (!state.isOnline) return
        if (isReel && (state.isLoadingMoreReels || !state.hasMoreReels)) return
        if (!isReel && (state.isLoadingMorePosts || !state.hasMorePosts)) return

        val current = if (isReel) state.reels else state.posts
        val last = current.lastOrNull() ?: return
        if (last.createdAt.isBlank()) return

        _uiState.value = if (isReel) {
            state.copy(isLoadingMoreReels = true)
        } else {
            state.copy(isLoadingMorePosts = true)
        }

        viewModelScope.launch {
            runCatching {
                postRepository.fetchFeedPage(
                    isReel = isReel,
                    beforeCreatedAt = last.createdAt,
                    beforeId = last.id,
                    limit = 30
                )
            }.onSuccess { page ->
                val latest = _uiState.value
                if (isReel) {
                    val merged = (latest.reels + page).distinctBy { it.id }
                    _uiState.value = latest.copy(
                        reels = merged,
                        isLoadingMoreReels = false,
                        hasMoreReels = page.size >= 30
                    )
                } else {
                    val merged = (latest.posts + page).distinctBy { it.id }
                    _uiState.value = latest.copy(
                        posts = merged,
                        isLoadingMorePosts = false,
                        hasMorePosts = page.size >= 30
                    )
                }
                persistCurrentFeed()
            }.onFailure {
                Log.w(TAG, "Load more feed failed", it)
                _uiState.value = if (isReel) {
                    _uiState.value.copy(isLoadingMoreReels = false)
                } else {
                    _uiState.value.copy(isLoadingMorePosts = false)
                }
            }
        }
    }

    fun refreshConnectHub() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isConnectHubLoading = true)
            runCatching { connectHubRepository.fetchSnapshot() }
                .onSuccess { snapshot ->
                    _uiState.value = _uiState.value.copy(
                        connectHub = snapshot,
                        isConnectHubLoading = false
                    )
                }
                .onFailure {
                    Log.e(TAG, "Connect Hub refresh failed", it)
                    _uiState.value = _uiState.value.copy(isConnectHubLoading = false)
                    showToast(it.message ?: "Couldn't refresh Connect Hub.")
                }
        }
    }

    private fun runConnectAction(successMessage: String, action: suspend () -> Boolean) {
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { ok ->
                    if (ok) {
                        showToast(successMessage)
                        refreshConnectHub()
                    } else {
                        showToast("That request is no longer available.")
                    }
                }
                .onFailure {
                    Log.e(TAG, "Connect Hub action failed", it)
                    showToast(it.message ?: "Connect Hub action failed.")
                }
        }
    }

    fun publishRoommateProfile(
        title: String,
        description: String,
        location: String,
        budgetMin: Double?,
        budgetMax: Double?
    ) = runConnectAction("Roommate profile published.") {
        connectHubRepository.upsertRoommate(title, description, location, budgetMin, budgetMax)
    }

    fun applyForRoommate(profileId: String) =
        runConnectAction("Roommate request sent.") {
            connectHubRepository.applyRoommate(profileId)
        }

    fun publishMentorProfile(
        subjects: List<String>,
        headline: String,
        description: String,
        mode: String = "mentor"
    ) = runConnectAction("Mentor profile saved.") {
        connectHubRepository.upsertMentor(mode, subjects, headline, description)
    }

    fun requestMentor(profileId: String) =
        runConnectAction("Mentor request sent.") {
            connectHubRepository.requestMentor(profileId)
        }

    fun publishReadingMateProfile(
        courses: List<String>,
        studyStyle: String,
        preferredTimes: List<String>,
        location: String,
        description: String
    ) = runConnectAction("Reading-mate profile published.") {
        connectHubRepository.upsertReadingMate(courses, studyStyle, preferredTimes, location, description)
    }

    fun requestReadingMate(profileId: String) =
        runConnectAction("Reading-mate request sent.") {
            connectHubRepository.requestReadingMate(profileId)
        }

    fun applyAsHousingAgent(
        businessName: String,
        serviceAreas: List<String>,
        bio: String
    ) = runConnectAction("Housing-agent application submitted for verification.") {
        connectHubRepository.applyAsHousingAgent(businessName, serviceAreas, bio)
    }

    fun publishHousingRequest(
        title: String,
        location: String,
        budgetMin: Double?,
        budgetMax: Double?,
        description: String
    ) = runConnectAction("Housing request published.") {
        connectHubRepository.createHousingRequest(title, location, budgetMin, budgetMax, description)
    }

    fun applyToHousingRequest(requestId: String, message: String) =
        runConnectAction("Housing application sent.") {
            connectHubRepository.applyToHousingRequest(requestId, message)
        }

    fun challengeUser(
        userId: String,
        gameType: String = ChallengeGameType.GENERAL_KNOWLEDGE.apiName
    ) =
        runConnectAction("Game challenge sent.") {
            connectHubRepository.challengeUser(userId, gameType)
        }

    fun respondToGameChallenge(challengeId: String, accept: Boolean) {
        viewModelScope.launch {
            runCatching { connectHubRepository.respondToChallenge(challengeId, accept) }
                .onSuccess { updated ->
                    if (!updated) {
                        showToast("This challenge is no longer available.")
                        return@onSuccess
                    }
                    showToast(if (accept) "Challenge accepted — game on!" else "Challenge declined.")
                    if (accept) {
                        _uiState.value = _uiState.value.copy(feedSubTab = 3)
                    }
                    refreshConnectHub()
                }
                .onFailure { error ->
                    Log.e(TAG, "Game challenge response failed", error)
                    showToast(error.message ?: "Couldn't update the challenge.")
                }
        }
    }

    fun respondToConnectRequest(kind: String, requestId: String, accept: Boolean) =
        runConnectAction(if (accept) "Request accepted." else "Request declined.") {
            connectHubRepository.respondToConnectRequest(kind, requestId, accept)
        }

    fun submitChallengeScore(challengeId: String, score: Int) =
        runConnectAction("Challenge score submitted.") {
            connectHubRepository.submitChallengeScore(challengeId, score)
        }

    fun recordGameResult(gameType: String, score: Int) {
        runConnectAction("Game result synced.") {
            connectHubRepository.recordGameSession(gameType, score)
        }
    }

    suspend fun refreshMyProfileFromSupabase(showErrorToast: Boolean = true) {
        try {
            val userId = supabaseService.getCurrentUserId()
            if (userId.isNullOrBlank()) return
            profileRepository.fetchById(userId)?.let { profile ->
                _uiState.value = _uiState.value.copy(myProfile = profile)
                saveLocalProfile(profile)
                persistProfile(profile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshMyProfileFromSupabase failed", e)
            if (showErrorToast) showToast("Unable to refresh your profile.")
        }
    }

    fun signInWithCredentials(emailOrUsername: String, password: String, onResult: (Boolean, String?) -> Unit) {
        if (emailOrUsername.isBlank() || password.isBlank()) { onResult(false, "Please enter both email/username and password."); return }
        viewModelScope.launch {
            val result = authRepository.signInWithEmail(emailOrUsername, password)
            if (result.isSuccess && result.userProfile != null) {
                val profile = result.userProfile
                _uiState.value = _uiState.value.copy(myProfile = profile, destination = AppDestination.MAIN)
                saveLocalProfile(profile); fetchSupabaseData(); showToast("✨ Signed in as @${profile.username}"); onResult(true, null)
            } else { val msg = result.errorMessage ?: "Unable to sign in."; showToast(msg); onResult(false, msg) }
        }
    }

    fun loginWithGoogle(email: String = "") {
        viewModelScope.launch {
            try {
                showToast("🔐 Connecting to Google...")
                val result = authRepository.signInWithGoogle(email)
                if (result.errorMessage == "GOOGLE_OAUTH_STARTED") return@launch
                if (result.isSuccess && result.userProfile != null) {
                    val profile = result.userProfile
                    _uiState.value = _uiState.value.copy(myProfile = profile, destination = AppDestination.MAIN)
                    saveLocalProfile(profile); refreshMyProfileFromSupabase(false); fetchSupabaseData()
                    showToast("✨ Welcome back, @${_uiState.value.myProfile.username}")
                } else showToast(result.errorMessage ?: "Google authentication failed.")
            } catch (e: Exception) { Log.e(TAG, "loginWithGoogle failed", e); showToast(e.message ?: "Google authentication failed.") }
        }
    }

    fun sendPasswordReset(email: String, onResult: (Boolean, String) -> Unit) {
        if (email.isBlank() || !email.contains("@")) { onResult(false, "Please enter a valid university or Gmail address."); return }
        viewModelScope.launch {
            val success = authRepository.recoverPassword(email)
            val msg = if (success) "Password reset instructions sent to $email." else "Could not send password reset email."
            showToast(msg); onResult(success, msg)
        }
    }

    fun signUp(fullName: String, username: String, email: String, password: String = "", faculty: String = "") {
        if (fullName.isBlank()) { showToast("Please enter your real name."); return }
        if (username.isBlank()) { showToast("Please choose a username."); return }
        if (password.isBlank()) { showToast("Please create a password before continuing."); return }

        val cleanName = fullName.trim()
        val cleanUsername = username.trim().lowercase().removePrefix("@")
        val cleanEmail = email.trim().lowercase()

        if (cleanUsername.isBlank()) { showToast("Please choose a username."); return }
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) { showToast("Please enter a valid email address."); return }
        val initialProfile = _uiState.value.myProfile.copy(fullName = cleanName, username = cleanUsername, email = ContactField(cleanEmail, true), faculty = faculty.trim())
        _uiState.value = _uiState.value.copy(myProfile = initialProfile)
        saveLocalProfile(initialProfile)
        viewModelScope.launch {
            try {
                val result = authRepository.signUpWithEmail(cleanEmail, password, cleanUsername, cleanName, faculty.trim())
                if (result.isSuccess && result.userProfile != null) {
                    _uiState.value = _uiState.value.copy(myProfile = result.userProfile, destination = AppDestination.PROFILE_SETUP)
                    saveLocalProfile(result.userProfile); showToast("Account created! Set up your campus profile.")
                } else showToast(result.errorMessage ?: "Sign up failed.")
            } catch (e: Exception) { Log.e(TAG, "signUp failed", e); showToast(e.message ?: "Sign up failed.") }
        }
    }

    fun completeProfileOnboarding(university: String, department: String, academicLevel: String, bio: String, skills: List<String>, phone: String = "", whatsapp: String = "") {
        val current = _uiState.value.myProfile
        val updatedSkills = skills.filter { it.isNotBlank() }.map { SkillEndorsement(it, 1, true) }
        val completed = current.copy(
            university = university.trim(), department = department.trim(), academicLevel = academicLevel.trim(), bio = bio.trim(),
            skillEndorsements = if (updatedSkills.isNotEmpty()) updatedSkills.toMutableList() else current.skillEndorsements,
            phone = ContactField(if (phone.isNotBlank()) phone.trim() else current.phone.value, true),
            whatsapp = ContactField(if (whatsapp.isNotBlank()) whatsapp.trim() else current.whatsapp.value, true)
        )
        _uiState.value = _uiState.value.copy(myProfile = completed); saveLocalProfile(completed)
        viewModelScope.launch {
            val userId = supabaseService.getCurrentUserId()
            if (userId.isNullOrBlank()) { _uiState.value = _uiState.value.copy(destination = AppDestination.MAIN); showToast("Profile saved locally. Sign in to sync it with Supabase."); return@launch }
            if (supabaseService.updateProfile(completed)) { _uiState.value = _uiState.value.copy(myProfile = completed, destination = AppDestination.MAIN); showToast("🎉 Profile created and synced with Supabase.") }
            else { _uiState.value = _uiState.value.copy(destination = AppDestination.MAIN); showToast("Profile saved locally, but Supabase sync failed.") }
        }
    }

    fun setDestination(destination: AppDestination) { _uiState.value = _uiState.value.copy(destination = destination) }
    fun navigateTo(destination: AppDestination) = setDestination(destination)
    fun selectTab(tab: MainTab) {
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
    fun openMenu(open: Boolean) { _uiState.value = _uiState.value.copy(isMenuOpen = open) }
    fun openActivity(open: Boolean) { _uiState.value = _uiState.value.copy(isActivityOpen = open) }

    fun openCommentsForPost(postId: String?) {
        _uiState.value = _uiState.value.copy(activeCommentsPostId = postId)
        if (postId != null) viewModelScope.launch { _uiState.value = _uiState.value.copy(comments = postRepository.fetchComments(postId)) }
    }
    fun openPostOptions(post: FeedPost?) { _uiState.value = _uiState.value.copy(activePostOptionsPost = post) }
    fun openCreatePost(open: Boolean) { _uiState.value = _uiState.value.copy(isCreatePostOpen = open) }
    fun openCreateStory(open: Boolean) { _uiState.value = _uiState.value.copy(isCreateStoryOpen = open) }
    fun openPostItem(open: Boolean) { _uiState.value = _uiState.value.copy(isPostItemOpen = open) }
    fun openBecomeSeller(open: Boolean) { _uiState.value = _uiState.value.copy(isBecomeSellerOpen = open) }
    fun openEditProfile(open: Boolean) { _uiState.value = _uiState.value.copy(isEditProfileOpen = open) }
    fun openGetVerified(open: Boolean) { _uiState.value = _uiState.value.copy(isGetVerifiedOpen = open) }

    fun isMe(identifier: String?): Boolean {
        if (identifier.isNullOrBlank()) return false
        val clean = identifier.trim().removePrefix("@").trim()
        val myUser = _uiState.value.myProfile.username.trim().removePrefix("@").trim()
        val myName = _uiState.value.myProfile.fullName.trim()
        val myId = _uiState.value.myProfile.id.trim()
        return clean.equals("you", true) || clean.equals("me", true) || clean.equals(myUser, true) || clean.equals(myName, true) || clean.equals(myId, true) || clean.replace(" ", ".").equals(myUser, true) || myUser.replace(".", " ").equals(clean, true)
    }

    private fun cachedProfile(identifier: String): UserProfile? {
        val clean = identifier.trim().removePrefix("@")
        return _uiState.value.profiles.firstOrNull {
            it.username.equals(clean, true) ||
                it.id.equals(clean, true) ||
                it.fullName.equals(clean, true)
        }
    }

    fun openProfile(username: String) {
        if (isMe(username)) { _uiState.value = _uiState.value.copy(viewingProfile = _uiState.value.myProfile); return }
        val cached = cachedProfile(username)
        if (cached != null) {
            _uiState.value = _uiState.value.copy(viewingProfile = cached)
        }
        if (!_uiState.value.isOnline) {
            if (cached == null) showToast("That profile is not saved on this device yet.")
            return
        }
        viewModelScope.launch {
            val remoteProfile = profileRepository.fetchByUsername(username)
            if (remoteProfile != null) {
                _uiState.value = _uiState.value.copy(
                    viewingProfile = remoteProfile,
                    profiles = listOf(remoteProfile) + _uiState.value.profiles.filterNot {
                        it.id == remoteProfile.id || it.username.equals(remoteProfile.username, true)
                    }
                )
                persistProfile(remoteProfile)
                return@launch
            }
            if (cached == null) showToast("User @${username.removePrefix("@")} was not found.")
        }
    }

    fun openProfileFromChat(username: String) {
        if (isMe(username)) {
            _uiState.value = _uiState.value.copy(viewingProfile = _uiState.value.myProfile, isConversationFullScreen = false, activeConversationPartner = null)
            return
        }
        val cached = cachedProfile(username)
        if (cached != null) {
            _uiState.value = _uiState.value.copy(
                viewingProfile = cached,
                isConversationFullScreen = false,
                activeConversationPartner = null
            )
        }
        if (!_uiState.value.isOnline) {
            if (cached == null) showToast("That profile is not saved on this device yet.")
            return
        }
        viewModelScope.launch {
            val remote = profileRepository.fetchByUsername(username)
            if (remote != null) {
                _uiState.value = _uiState.value.copy(
                    viewingProfile = remote,
                    isConversationFullScreen = false,
                    activeConversationPartner = null,
                    profiles = listOf(remote) + _uiState.value.profiles.filterNot {
                        it.id == remote.id || it.username.equals(remote.username, true)
                    }
                )
                persistProfile(remote)
            } else if (cached == null) {
                showToast("User @${username.removePrefix("@")} was not found.")
            }
        }
    }
    fun closeProfile() { _uiState.value = _uiState.value.copy(viewingProfile = null) }

    fun updateProfile(updated: UserProfile) {
        viewModelScope.launch {
            try {
                val cleanUsername = updated.username.trim().lowercase().removePrefix("@")
                val cleanName = updated.fullName.trim()
                if (!cleanUsername.matches(Regex("^[a-z0-9][a-z0-9._-]{1,29}$"))) {
                    showToast("Use 2–30 lowercase letters, numbers, dots, dashes or underscores for your username.")
                    return@launch
                }
                if (cleanName.length !in 2..60 || cleanName.equals("Blink User", ignoreCase = true)) {
                    showToast("Please choose a display name between 2 and 60 characters.")
                    return@launch
                }
                val availability = supabaseService.checkProfileIdentity(
                    username = cleanUsername,
                    fullName = cleanName,
                    excludeCurrentUser = true
                )
                if (availability?.usernameAvailable == false) {
                    showToast("That username is already taken. Please choose another one.")
                    return@launch
                }
                if (availability?.fullNameAvailable == false) {
                    showToast("That display name is already in use. Add a middle name or another identifier.")
                    return@launch
                }
                val normalized = updated.copy(username = cleanUsername, fullName = cleanName)
                if (profileRepository.updateProfile(normalized)) {
                    val authoritative = profileRepository.fetchCurrent(normalized.username) ?: normalized
                    _uiState.value = _uiState.value.copy(myProfile = authoritative, isEditProfileOpen = false, viewingProfile = if (_uiState.value.viewingProfile?.username?.equals(authoritative.username, true) == true) authoritative else _uiState.value.viewingProfile)
                    saveLocalProfile(authoritative); persistProfile(authoritative); updateLocalAuthorData(authoritative); showToast("✅ Profile saved successfully.")
                } else showToast("❌ Failed to update profile.")
            } catch (e: Exception) { Log.e(TAG, "updateProfile error", e); showToast("❌ Failed to update profile: ${e.message}") }
        }
    }
    fun updateMyProfile(updated: UserProfile) = updateProfile(updated)

    private fun updateLocalAuthorData(profile: UserProfile) {
        val old = _uiState.value.myProfile
        val names = setOf(old.username.lowercase(), old.fullName.lowercase())
        _uiState.value = _uiState.value.copy(
            posts = _uiState.value.posts.map { if (it.author.lowercase() in names) it.copy(author = profile.username, authorAvatar = profile.avatarUrl) else it },
            reels = _uiState.value.reels.map { if (it.author.lowercase() in names) it.copy(author = profile.username, authorAvatar = profile.avatarUrl) else it },
            marketItems = _uiState.value.marketItems.map { if (it.sellerUsername.lowercase() in names) it.copy(sellerUsername = profile.username, sellerAvatar = profile.avatarUrl, sellerName = profile.fullName, sellerPhone = profile.phone.value, sellerWhatsapp = profile.whatsapp.value) else it }
        )
        persistCurrentFeed()
    }

    private fun loadDraftsFromPrefs() {
        try {
            val json = prefs.getString("blink_saved_drafts_data", null)
            if (!json.isNullOrBlank()) {
                val drafts = mutableListOf<PostDraft>()
                for (item in json.split(";;;DRAFT_DELIM;;;")) {
                    if (item.isBlank()) continue
                    val parts = item.split(":::FIELD:::")
                    if (parts.size >= 8) drafts.add(PostDraft(id = parts.getOrNull(0) ?: "draft_${System.currentTimeMillis()}", text = parts.getOrNull(1) ?: "", faculty = parts.getOrNull(2) ?: "SIMME", imageUri = parts.getOrNull(3)?.takeIf { it.isNotBlank() }, videoUri = parts.getOrNull(4)?.takeIf { it.isNotBlank() }, isReel = parts.getOrNull(5)?.toBoolean() ?: false, category = parts.getOrNull(6) ?: "Campus Life", audience = parts.getOrNull(7) ?: "Everyone", tags = parts.getOrNull(8)?.split(",")?.filter { it.isNotBlank() } ?: emptyList(), mentions = parts.getOrNull(9)?.split(",")?.filter { it.isNotBlank() } ?: emptyList(), savedAtTimestamp = parts.getOrNull(10)?.toLongOrNull() ?: System.currentTimeMillis()))
                }
                _uiState.value = _uiState.value.copy(savedDrafts = drafts)
            }
        } catch (e: Exception) { Log.e(TAG, "Failed to load drafts", e) }
    }
    private fun saveDraftsToPrefs(drafts: List<PostDraft>) { try { val serialized = drafts.joinToString(";;;DRAFT_DELIM;;;") { d -> listOf(d.id, d.text, d.faculty, d.imageUri ?: "", d.videoUri ?: "", d.isReel.toString(), d.category, d.audience, d.tags.joinToString(","), d.mentions.joinToString(","), d.savedAtTimestamp.toString()).joinToString(":::FIELD:::") }; prefs.edit().putString("blink_saved_drafts_data", serialized).apply() } catch (e: Exception) { Log.e(TAG, "Failed to save drafts", e) } }
    fun saveDraft(draft: PostDraft) { val updated = listOf(draft) + _uiState.value.savedDrafts.filter { it.id != draft.id }; _uiState.value = _uiState.value.copy(savedDrafts = updated); saveDraftsToPrefs(updated); showToast("💾 Draft saved to phone storage") }
    fun deleteDraft(draftId: String) { val updated = _uiState.value.savedDrafts.filter { it.id != draftId }; _uiState.value = _uiState.value.copy(savedDrafts = updated); saveDraftsToPrefs(updated); showToast("🗑️ Draft deleted") }

    fun schedulePost(post: FeedPost, timeMillis: Long, timeFormatted: String) { val sched = ScheduledPost("sched_${System.currentTimeMillis()}", post, timeMillis, timeFormatted); _uiState.value = _uiState.value.copy(scheduledPosts = listOf(sched) + _uiState.value.scheduledPosts, isCreatePostOpen = false); showToast("⏰ Post scheduled for $timeFormatted") }
    fun deleteScheduledPost(id: String) { _uiState.value = _uiState.value.copy(scheduledPosts = _uiState.value.scheduledPosts.filter { it.id != id }); showToast("🗑️ Scheduled post removed") }
    fun publishScheduledPostNow(id: String) {
        val sched = _uiState.value.scheduledPosts.find { it.id == id } ?: return
        val post = sched.post
        _uiState.value = _uiState.value.copy(
            scheduledPosts = _uiState.value.scheduledPosts.filter { it.id != id },
            posts = if (post.isReel || !post.videoUrl.isNullOrBlank()) _uiState.value.posts else listOf(post) + _uiState.value.posts,
            reels = if (post.isReel || !post.videoUrl.isNullOrBlank()) listOf(post) + _uiState.value.reels else _uiState.value.reels
        )
        persistCurrentFeed()
        showToast("✨ Post published")
    }

    fun addPost(
        text: String,
        faculty: String,
        imageUri: String?,
        videoUri: String? = null,
        tags: List<String> = emptyList(),
        mentions: List<String> = emptyList(),
        poll: PostPoll? = null,
        isReel: Boolean = false,
        audience: String = "Everyone",
        category: String = "Campus Life",
        location: String? = null,
        linkUrl: String? = null,
        allowComments: Boolean = true,
        hideLikes: Boolean = false,
        isPinned: Boolean = false,
        isDisappearing: Boolean = false,
        audioTitle: String? = null,
        altText: String? = null
    ) {
        if (_uiState.value.isCreatingPost) return

        val profile = _uiState.value.myProfile
        val userId = supabaseService.getCurrentUserId()
            ?: profile.id.takeIf { it.isNotBlank() }
            ?: "user_${profile.username}"

        _uiState.value = _uiState.value.copy(isCreatingPost = true)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val imageInputs = parseImagePayload(imageUri)
                val uploadedImageUrls = mutableListOf<String>()

                for (input in imageInputs) {
                    val uploaded = if (input.startsWith("content://")) {
                        uploadPostUri(userId, input, false)
                    } else {
                        input
                    }

                    if (uploaded.isNullOrBlank()) {
                        throw IllegalStateException("One of the selected images could not be uploaded.")
                    }
                    uploadedImageUrls += uploaded
                }

                val uploadedVideoUrl = if (!videoUri.isNullOrBlank()) {
                    if (videoUri.startsWith("content://")) uploadPostUri(userId, videoUri, true) else videoUri
                } else null

                if (!videoUri.isNullOrBlank() && uploadedVideoUrl.isNullOrBlank()) {
                    throw IllegalStateException("The selected video could not be uploaded.")
                }

                val imagePayload = when (uploadedImageUrls.size) {
                    0 -> null
                    1 -> uploadedImageUrls.first()
                    else -> JSONArray(uploadedImageUrls).toString()
                }

                val finalIsReel = isReel || !uploadedVideoUrl.isNullOrBlank()
                val resultPost = supabaseService.createFeedPost(
                    profile.username,
                    profile.avatarUrl,
                    faculty,
                    text,
                    imagePayload,
                    uploadedVideoUrl,
                    tags,
                    mentions,
                    poll,
                    finalIsReel,
                    audience,
                    category,
                    location,
                    linkUrl,
                    allowComments,
                    hideLikes,
                    isPinned,
                    isDisappearing,
                    audioTitle,
                    altText
                ) ?: throw IllegalStateException("The server did not save the post.")

                withContext(Dispatchers.Main) {
                    val current = _uiState.value
                    _uiState.value = current.copy(
                        posts = if (resultPost.isReel || !resultPost.videoUrl.isNullOrBlank()) current.posts else listOf(resultPost) + current.posts,
                        reels = if (resultPost.isReel || !resultPost.videoUrl.isNullOrBlank()) listOf(resultPost) + current.reels else current.reels,
                        isCreatePostOpen = false,
                        isCreatingPost = false
                    )
                    persistCurrentFeed()
                    showToast(if (finalIsReel) "Reel published." else "Post published.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Background sync for post creation failed", e)
                showToast(e.message ?: "Couldn't publish the post. Please try again.")
            } finally {
                _uiState.value = _uiState.value.copy(isCreatingPost = false)
            }
        }
    }

    private fun parseImagePayload(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val value = raw.trim()
        if (!value.startsWith("[")) return listOf(value)

        return runCatching {
            val array = JSONArray(value)
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index)
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?.let { add(it) }
                }
            }
        }.getOrElse { listOf(value) }
    }

    private suspend fun uploadPostUri(userId: String, uriString: String, isVideo: Boolean): String? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            val mimeType = appContext.contentResolver.getType(uri) ?: if (isVideo) "video/mp4" else "image/jpeg"
            val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            if (bytes.isEmpty()) return@withContext null
            supabaseService.uploadPostMedia(userId, bytes, mimeType, isVideo)
        } catch (e: Exception) { Log.e(TAG, "uploadPostUri failed", e); null }
    }

    fun togglePostLike(postId: String) {
        var nextLiked = false; var nextCount = 0
        val updatedPosts = _uiState.value.posts.map { if (it.id == postId) { nextLiked = !it.isLiked; nextCount = (it.likes + if (nextLiked) 1 else -1).coerceAtLeast(0); it.copy(isLiked = nextLiked, likes = nextCount) } else it }
        val updatedReels = _uiState.value.reels.map { if (it.id == postId) { nextLiked = !it.isLiked; nextCount = (it.likes + if (nextLiked) 1 else -1).coerceAtLeast(0); it.copy(isLiked = nextLiked, likes = nextCount) } else it }
        _uiState.value = _uiState.value.copy(posts = updatedPosts, reels = updatedReels)
        persistCurrentFeed()
        viewModelScope.launch {
            val success = runCatching { postRepository.togglePostLike(postId, nextLiked, nextCount) }.getOrDefault(false)
            if (success && nextLiked) {
                val target = (_uiState.value.posts + _uiState.value.reels).find { it.id == postId }
                if (target != null && target.author.isNotBlank()) supabaseService.recordActivity(target.author, "liked your post", NotificationFilter.LIKES, postId, targetType = "POST")
            } else if (!success) {
                _uiState.value = _uiState.value.copy(posts = _uiState.value.posts.map { if (it.id == postId) it.copy(isLiked = !nextLiked, likes = (it.likes + if (!nextLiked) 1 else -1).coerceAtLeast(0)) else it }, reels = _uiState.value.reels.map { if (it.id == postId) it.copy(isLiked = !nextLiked, likes = (it.likes + if (!nextLiked) 1 else -1).coerceAtLeast(0)) else it })
                persistCurrentFeed()
                showToast("Failed to update like.")
            }
        }
    }

    fun toggleBookmark(postId: String) {
        var next = false
        _uiState.value = _uiState.value.copy(posts = _uiState.value.posts.map { if (it.id == postId) { next = !it.isBookmarked; it.copy(isBookmarked = next) } else it }, reels = _uiState.value.reels.map { if (it.id == postId) { next = !it.isBookmarked; it.copy(isBookmarked = next) } else it })
        persistCurrentFeed()
        viewModelScope.launch { if (!runCatching { postRepository.togglePostBookmark(postId, next) }.getOrDefault(false)) { _uiState.value = _uiState.value.copy(posts = _uiState.value.posts.map { if (it.id == postId) it.copy(isBookmarked = !next) else it }, reels = _uiState.value.reels.map { if (it.id == postId) it.copy(isBookmarked = !next) else it }); persistCurrentFeed(); showToast("Failed to update bookmark.") } }
    }
    fun sharePost(postId: String) {
        viewModelScope.launch {
            if (supabaseService.sharePost(postId, "share")) {
                _uiState.value = _uiState.value.copy(
                    posts = _uiState.value.posts.map {
                        if (it.id == postId) it.copy(sharesCount = it.sharesCount + 1) else it
                    },
                    reels = _uiState.value.reels.map {
                        if (it.id == postId) it.copy(sharesCount = it.sharesCount + 1) else it
                    }
                )
                persistCurrentFeed()
                showToast("🔗 Post shared.")
            } else {
                showToast("Couldn't record the share. Please try again.")
            }
        }
    }
    fun deletePost(postId: String) {
        val state = _uiState.value
        val target = (state.posts + state.reels).firstOrNull { it.id == postId } ?: return
        val me = state.myProfile.username.trim()
        if (me.isBlank() || !target.author.equals(me, ignoreCase = true)) {
            _uiState.value = state.copy(activePostOptionsPost = null)
            showToast("You can only delete your own post or reel.")
            return
        }

        val postsBefore = state.posts
        val reelsBefore = state.reels
        _uiState.value = state.copy(
            posts = postsBefore.filterNot { it.id == postId },
            reels = reelsBefore.filterNot { it.id == postId },
            activePostOptionsPost = null
        )
        persistCurrentFeed()

        viewModelScope.launch {
            val deleted = runCatching { supabaseService.deleteFeedPost(postId) }.getOrDefault(false)
            if (deleted) {
                showToast(if (target.videoUrl.isNullOrBlank()) "Post deleted." else "Reel deleted.")
            } else {
                _uiState.value = _uiState.value.copy(posts = postsBefore, reels = reelsBefore)
                persistCurrentFeed()
                showToast("Delete failed. Only the owner can delete this content.")
            }
        }
    }
    fun reportPost(postId: String, reason: String) {
        _uiState.value = _uiState.value.copy(activePostOptionsPost = null)
        viewModelScope.launch {
            if (supabaseService.reportPost(postId, reason)) {
                showToast("🚨 Report submitted for moderation.")
            } else {
                showToast("Couldn't submit the report. Please try again.")
            }
        }
    }
    fun muteUser(username: String) {
        val clean = username.trim().removePrefix("@")
        if (clean.isBlank() || clean.equals("null", true)) return
        _uiState.value = _uiState.value.copy(activePostOptionsPost = null)
        viewModelScope.launch {
            if (supabaseService.muteUser(clean)) {
                val normalized = clean.lowercase()
                _uiState.value = _uiState.value.copy(
                    mutedUsers = _uiState.value.mutedUsers + normalized,
                    posts = _uiState.value.posts.filterNot { it.author.equals(clean, true) },
                    reels = _uiState.value.reels.filterNot { it.author.equals(clean, true) },
                    stories = _uiState.value.stories.filterNot {
                        !it.isUser && it.username.equals(clean, true)
                    }
                )
                persistCurrentFeed()
                showToast("🔇 @$clean muted.")
            } else {
                showToast("Couldn't mute @$clean.")
            }
        }
    }

    fun votePoll(postId: String, optionId: String) {
        val post = _uiState.value.posts.find { it.id == postId } ?: return
        val poll = post.poll ?: return
        if (poll.hasVoted || poll.options.any { it.isVotedByMe }) {
            showToast("You already voted in this poll.")
            return
        }

        val before = _uiState.value.posts
        val updated = before.map { item ->
            if (item.id != postId || item.poll == null) item
            else {
                val options = item.poll.options.map { option ->
                    if (option.id == optionId) {
                        option.copy(votes = option.votes + 1, isVotedByMe = true)
                    } else option
                }
                item.copy(
                    poll = item.poll.copy(
                        options = options,
                        totalVotes = item.poll.totalVotes + 1,
                        hasVoted = true
                    )
                )
            }
        }
        _uiState.value = _uiState.value.copy(posts = updated)
        persistCurrentFeed()

        val pollState = updated.find { it.id == postId }?.poll ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val success = runCatching {
                postRepository.votePoll(postId, optionId, pollState)
            }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                if (success) {
                    showToast("🗳️ Vote recorded.")
                } else {
                    _uiState.value = _uiState.value.copy(posts = before)
                    persistCurrentFeed()
                    showToast("Vote wasn't saved. Please try again.")
                }
            }
        }
    }

    fun addComment(postId: String, text: String, replyToUser: String? = null) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val newComment = postRepository.addComment(postId, text, replyToUser)
            if (newComment != null) {
                _uiState.value = _uiState.value.copy(comments = listOf(newComment) + _uiState.value.comments, posts = _uiState.value.posts.map { if (it.id == postId) it.copy(commentsCount = it.commentsCount + 1) else it }, reels = _uiState.value.reels.map { if (it.id == postId) it.copy(commentsCount = it.commentsCount + 1) else it })
                val target = (_uiState.value.posts + _uiState.value.reels).find { it.id == postId }
                if (target != null && target.author.isNotBlank()) supabaseService.recordActivity(target.author, if (replyToUser.isNullOrBlank()) "commented on your post" else "replied to comment", NotificationFilter.COMMENTS, postId, text, "POST")
                showToast(if (replyToUser.isNullOrBlank()) "💬 Comment posted." else "↩️ Reply posted.")
            } else showToast("Failed to post comment.")
        }
    }

    fun toggleCommentLike(commentId: String) {
        var next = false; var count = 0
        _uiState.value = _uiState.value.copy(comments = _uiState.value.comments.map { if (it.id == commentId) { next = !it.isLiked; count = (it.likes + if (next) 1 else -1).coerceAtLeast(0); it.copy(isLiked = next, likes = count) } else it })
        viewModelScope.launch {
            if (runCatching { postRepository.toggleCommentLike(commentId, next, count) }.getOrDefault(false)) {
                if (next) _uiState.value.comments.find { it.id == commentId }?.let { comment -> if (comment.user.isNotBlank()) supabaseService.recordActivity(comment.user, "liked your comment", NotificationFilter.LIKES, previewText = comment.text, targetType = "POST") }
            } else { _uiState.value = _uiState.value.copy(comments = _uiState.value.comments.map { if (it.id == commentId) it.copy(isLiked = !next, likes = (it.likes + if (!next) 1 else -1).coerceAtLeast(0)) else it }); showToast("Failed to update comment like.") }
        }
    }

    fun openChatWithUser(username: String, sellerName: String? = null, sellerAvatar: String? = null) {
        val clean = username.trim().removePrefix("@")
        if (clean.isBlank() || clean.equals("null", true)) {
            showToast("This profile can't receive messages.")
            return
        }
        val state = _uiState.value
        val existing = state.conversations.find { it.partnerUsername.equals(clean, true) }
        if (existing != null) {
            _uiState.value = state.copy(
                conversations = state.conversations.map {
                    if (it.partnerUsername.equals(clean, true)) it.copy(unreadCount = 0) else it
                },
                activeConversationPartner = clean,
                isConversationFullScreen = true
            )
            viewModelScope.launch {
                chatRepository.markConversationRead(clean)
                if (_uiState.value.isOnline && existing.id.isNotBlank() && !existing.id.startsWith("local_")) {
                    loadConversationHistory(existing.id, clean, older = false)
                }
            }
            return
        }

        viewModelScope.launch {
            val profile = supabaseService.fetchProfileByUsername(clean)
            if (profile == null) {
                showToast("User @$clean wasn't found.")
                return@launch
            }
            val latest = _uiState.value
            val convo = ChatConversation(
                id = "local_${UUID.randomUUID()}",
                partnerUsername = profile.username,
                partnerId = profile.id,
                partnerName = sellerName ?: profile.fullName.ifBlank { profile.username },
                partnerAvatar = sellerAvatar ?: profile.avatarUrl,
                isOnline = profile.onlineNow,
                lastMessageTime = "New",
                messages = mutableListOf()
            )
            _uiState.value = latest.copy(
                conversations = listOf(convo) + latest.conversations,
                activeConversationPartner = profile.username,
                isConversationFullScreen = true
            )
        }
    }
    fun loadOlderMessages(partnerUsername: String) {
        val conversation = _uiState.value.conversations.firstOrNull {
            it.partnerUsername.equals(partnerUsername, true)
        } ?: return
        viewModelScope.launch {
            loadConversationHistory(conversation.id, conversation.partnerUsername, older = true)
        }
    }

    private suspend fun loadConversationHistory(conversationId: String, partnerUsername: String, older: Boolean) {
        if (conversationId.isBlank() || conversationId.startsWith("local_")) return
        val state = _uiState.value
        if (state.loadingOlderConversationId == conversationId) return
        val current = state.conversations.firstOrNull { it.id == conversationId } ?: return
        val oldest = current.messages.minByOrNull { it.rawTimestamp.ifBlank { "9999" } }
        _uiState.value = state.copy(loadingOlderConversationId = conversationId)
        try {
            val page = chatRepository.fetchMessagePage(
                conversationId = conversationId,
                beforeCreatedAt = if (older) oldest?.rawTimestamp?.takeIf { it.isNotBlank() } else null,
                beforeId = if (older) oldest?.id?.takeIf { it.isNotBlank() } else null,
                limit = 40
            )
            val latest = _uiState.value
            val updated = latest.conversations.map { conversation ->
                if (conversation.id != conversationId) conversation
                else {
                    val merged = if (older) page + conversation.messages else page + conversation.messages.filter {
                        it.status != MessageStatus.SENT || it.id.startsWith("temp_")
                    }
                    conversation.copy(messages = merged.distinctBy { it.id }.sortedBy { it.rawTimestamp }.toMutableList())
                }
            }
            _uiState.value = latest.copy(
                conversations = updated,
                messageHistoryHasMore = latest.messageHistoryHasMore + (conversationId to (page.size >= 40)),
                loadingOlderConversationId = null
            )
            persistConversations()
        } catch (e: Exception) {
            Log.w(TAG, "Message history page failed", e)
            _uiState.value = _uiState.value.copy(loadingOlderConversationId = null)
        }
    }

    fun closeConversation() { _uiState.value = _uiState.value.copy(activeConversationPartner = null, isConversationFullScreen = false) }

    fun sendMessage(partnerUsername: String, text: String, isFromMe: Boolean = true) {
        val cleanText = text.trim()
        val cleanPartner = partnerUsername.trim().removePrefix("@")
        if (cleanText.isBlank() || cleanPartner.isBlank()) return

        val uid = supabaseService.getCurrentUserId() ?: "local_user"
        val currentUsername = supabaseService.getCurrentUsername() ?: _uiState.value.myProfile.username.ifBlank { "you" }
        val tempId = "temp_${UUID.randomUUID()}"
        val optimistic = ChatMessage(
            id = tempId,
            senderId = uid,
            senderUsername = currentUsername,
            receiverUsername = cleanPartner,
            text = cleanText,
            timestamp = if (_uiState.value.isOnline) "Sending..." else "Queued",
            rawTimestamp = java.time.Instant.now().toString(),
            isFromMe = true,
            isRead = false,
            status = MessageStatus.SENDING
        )
        appendMessageToState(cleanPartner, optimistic)
        persistConversations()

        viewModelScope.launch(Dispatchers.IO) {
            offlineContentStore.enqueueMessage(tempId, cleanPartner, cleanText)
            if (_uiState.value.isOnline) {
                drainMessageOutbox()
            } else {
                withContext(Dispatchers.Main) {
                    showToast("Message queued. Blink will send it when you're back online.")
                }
            }
        }
    }

    fun sendVideoMessage(partnerUsername: String, uri: Uri) {
        val cleanPartner = partnerUsername.trim()
        if (cleanPartner.isBlank()) return
        val tempId = "temp_video_${UUID.randomUUID()}"
        val uid = supabaseService.getCurrentUserId() ?: "local_user"
        appendMessageToState(cleanPartner, ChatMessage(id = tempId, senderId = uid, receiverUsername = cleanPartner, text = "Video", timestamp = "Sending...", isFromMe = true, isRead = false, status = MessageStatus.SENDING))
        viewModelScope.launch {
            MessageMediaService.sendVideoMessage(appContext, cleanPartner, uri).fold({ serverMsg ->
                replaceMessageInState(cleanPartner, tempId, serverMsg.copy(status = MessageStatus.SENT))
                fetchSupabaseData()
            }, {
                updateMessageStatusInState(cleanPartner, tempId, MessageStatus.FAILED)
                showToast("Failed to send video. Tap the message to retry.")
            })
        }
    }

    fun retrySendMessage(partnerUsername: String, failedMessage: ChatMessage) {
        if (failedMessage.text.isBlank()) return
        updateMessageStatusInState(partnerUsername, failedMessage.id, MessageStatus.SENDING)
        persistConversations()
        viewModelScope.launch(Dispatchers.IO) {
            val pending = offlineContentStore.pendingOutbox(100).firstOrNull { it.localId == failedMessage.id }
            if (pending == null) {
                offlineContentStore.enqueueMessage(failedMessage.id, partnerUsername.trim(), failedMessage.text.trim())
            } else {
                offlineContentStore.resetOutbox(failedMessage.id)
            }
            if (_uiState.value.isOnline) drainMessageOutbox()
        }
    }

    private suspend fun drainMessageOutbox() {
        if (!_uiState.value.isOnline || supabaseService.getCurrentUserId().isNullOrBlank()) return
        val pending = offlineContentStore.pendingOutbox(40)
        if (pending.isEmpty()) return

        for (item in pending) {
            chatRepository.sendMessage(item.receiverUsername, item.content).fold(
                onSuccess = { serverMsg ->
                    offlineContentStore.deleteOutbox(item.localId)
                    withContext(Dispatchers.Main) {
                        replaceMessageInState(
                            item.receiverUsername,
                            item.localId,
                            serverMsg.copy(
                                receiverUsername = item.receiverUsername,
                                status = MessageStatus.SENT
                            )
                        )
                        persistConversations()
                    }
                    runCatching {
                        supabaseService.recordActivity(
                            item.receiverUsername,
                            "sent you a direct message",
                            NotificationFilter.ALL,
                            targetUsername = supabaseService.getCurrentUsername().orEmpty(),
                            previewText = item.content,
                            targetType = "CHAT"
                        )
                    }
                    reconcileConversationSummary(item.receiverUsername)
                },
                onFailure = { error ->
                    offlineContentStore.markOutboxFailure(item, error.message ?: "Message send failed")
                    withContext(Dispatchers.Main) {
                        updateMessageStatusInState(item.receiverUsername, item.localId, MessageStatus.FAILED)
                        persistConversations()
                        showToast(error.message ?: "Message failed. Please try again.")
                    }
                }
            )
        }
    }


    private suspend fun reconcileConversationSummary(partnerUsername: String) {
        val server = runCatching { chatRepository.fetchConversations() }.getOrDefault(emptyList())
            .firstOrNull { it.partnerUsername.equals(partnerUsername, true) }
            ?: return
        withContext(Dispatchers.Main) {
            val latest = _uiState.value
            val index = latest.conversations.indexOfFirst { it.partnerUsername.equals(partnerUsername, true) }
            if (index < 0) return@withContext
            val local = latest.conversations[index]
            val merged = server.copy(messages = local.messages)
            val conversations = latest.conversations.toMutableList().apply { this[index] = merged }
            _uiState.value = latest.copy(conversations = conversations)
            persistConversations()
        }
    }

    private fun handleRealtimeEvent(event: RealtimeEvent) {
        when (event) {
            is RealtimeEvent.MessageEvent -> handleIncomingRealtimeMessage(event.message)
            is RealtimeEvent.ConversationEvent -> viewModelScope.launch { _uiState.value = _uiState.value.copy(conversations = chatRepository.fetchConversations()) }
            is RealtimeEvent.NotificationEvent -> fetchSupabaseData()
            is RealtimeEvent.ConnectHubEvent -> refreshConnectHub()
            is RealtimeEvent.FeedPostEvent -> viewModelScope.launch {
                val fresh = postRepository.fetchFeed()
                if (fresh.isNotEmpty()) {
                    val muted = _uiState.value.mutedUsers
                    _uiState.value = _uiState.value.copy(
                        posts = fresh.filter {
                            it.videoUrl.isNullOrBlank() &&
                                !it.isReel &&
                                it.author.lowercase() !in muted
                        },
                        reels = fresh.filter {
                            !it.videoUrl.isNullOrBlank() &&
                                it.author.lowercase() !in muted
                        }
                    )
                    persistCurrentFeed()
                }
            }
        }
    }

    private fun handleIncomingRealtimeMessage(msg: ChatMessage) {
        val myId = supabaseService.getCurrentUserId().orEmpty()
        if (msg.isFromMe || (myId.isNotBlank() && msg.senderId == myId)) return

        viewModelScope.launch {
            val initial = _uiState.value
            var senderProfile = initial.profiles.firstOrNull { it.id == msg.senderId }
            if (senderProfile == null && msg.senderId.isNotBlank()) {
                senderProfile = try {
                    profileRepository.fetchById(msg.senderId)
                } catch (_: Exception) {
                    null
                }
            }

            var serverSummary = initial.conversations.firstOrNull {
                msg.conversationId?.let { id -> id.isNotBlank() && it.id == id } == true
            }
            if (serverSummary == null) {
                val fresh = runCatching { chatRepository.fetchConversations() }.getOrDefault(emptyList())
                serverSummary = fresh.firstOrNull {
                    msg.conversationId?.let { id -> id.isNotBlank() && it.id == id } == true
                } ?: fresh.firstOrNull { it.partnerId == msg.senderId }
            }

            val partner = senderProfile?.username?.takeIf { it.isNotBlank() }
                ?: serverSummary?.partnerUsername?.takeIf { it.isNotBlank() }
                ?: msg.senderUsername.takeIf { it.isNotBlank() }
                ?: return@launch
            val displayName = senderProfile?.fullName?.takeIf { it.isNotBlank() }
                ?: serverSummary?.partnerName?.takeIf { it.isNotBlank() }
                ?: partner
            val avatar = senderProfile?.avatarUrl?.takeIf { it.isNotBlank() }
                ?: serverSummary?.partnerAvatar.orEmpty()
            val conversationId = msg.conversationId?.takeIf { it.isNotBlank() }
                ?: serverSummary?.id
                ?: "conv_$partner"
            val enriched = msg.copy(
                conversationId = conversationId,
                senderUsername = partner,
                isFromMe = false
            )

            val latest = _uiState.value
            val active = latest.activeConversationPartner?.equals(partner, true) == true
            val conversations = latest.conversations.toMutableList()
            val index = conversations.indexOfFirst {
                it.id == conversationId || it.partnerUsername.equals(partner, true)
            }
            if (index >= 0) {
                val old = conversations[index]
                val messages = old.messages.toMutableList()
                val existing = messages.indexOfFirst { it.id == enriched.id }
                if (existing >= 0) messages[existing] = enriched else messages.add(enriched)
                conversations[index] = old.copy(
                    id = if (old.id.startsWith("local_") && !conversationId.startsWith("local_")) conversationId else old.id,
                    partnerId = old.partnerId.ifBlank { msg.senderId },
                    partnerName = if (old.partnerName.isBlank() || old.partnerName.equals(old.partnerUsername, true)) displayName else old.partnerName,
                    partnerAvatar = old.partnerAvatar.ifBlank { avatar },
                    lastMessage = enriched.text,
                    lastMessageTime = enriched.timestamp,
                    lastMessageRawTime = enriched.rawTimestamp,
                    unreadCount = if (active) 0 else old.unreadCount + 1,
                    messages = messages.distinctBy { it.id }.sortedBy { it.rawTimestamp.ifBlank { it.timestamp } }.toMutableList()
                )
            } else {
                conversations.add(
                    0,
                    (serverSummary ?: ChatConversation(
                        id = conversationId,
                        partnerUsername = partner,
                        partnerId = msg.senderId,
                        partnerName = displayName,
                        partnerAvatar = avatar
                    )).copy(
                        id = conversationId,
                        partnerUsername = partner,
                        partnerId = msg.senderId,
                        partnerName = displayName,
                        partnerAvatar = avatar,
                        lastMessage = enriched.text,
                        lastMessageTime = enriched.timestamp,
                        lastMessageRawTime = enriched.rawTimestamp,
                        unreadCount = if (active) 0 else 1,
                        messages = mutableListOf(enriched)
                    )
                )
            }
            _uiState.value = latest.copy(conversations = conversations)
            persistConversations()

            if (active) {
                chatRepository.markConversationRead(partner)
            } else {
                _snackBarMessages.tryEmit("💬 $displayName: ${enriched.text.take(120)}")
                withContext(Dispatchers.IO) {
                    BlinkNotificationHelper.showChatMessageNotification(
                        appContext,
                        partner,
                        displayName,
                        enriched.text,
                        avatar
                    )
                }
            }
        }
    }

    private fun appendMessageToState(partnerUsername: String, message: ChatMessage) {
        val conversations = _uiState.value.conversations.toMutableList(); val index = conversations.indexOfFirst { it.partnerUsername.equals(partnerUsername, true) }
        if (index >= 0) { val old = conversations[index]; conversations[index] = old.copy(lastMessage = message.text, lastMessageTime = message.timestamp, lastMessageRawTime = message.rawTimestamp, messages = (old.messages + message).toMutableList()) }
        else conversations.add(0, ChatConversation("conv_$partnerUsername", partnerUsername, partnerName = partnerUsername.replace(".", " ").replace("_", " ").capitalizeWords(), partnerAvatar = "", lastMessage = message.text, lastMessageTime = message.timestamp, messages = mutableListOf(message)))
        _uiState.value = _uiState.value.copy(conversations = conversations)
        persistConversations()
    }
    private fun replaceMessageInState(partnerUsername: String, oldId: String, newMsg: ChatMessage) { val conversations = _uiState.value.conversations.toMutableList(); val index = conversations.indexOfFirst { it.partnerUsername.equals(partnerUsername, true) }; if (index >= 0) { val old = conversations[index]; conversations[index] = old.copy(lastMessage = newMsg.text, lastMessageTime = newMsg.timestamp, lastMessageRawTime = newMsg.rawTimestamp, messages = old.messages.map { if (it.id == oldId) newMsg else it }.toMutableList()); _uiState.value = _uiState.value.copy(conversations = conversations); persistConversations() } }
    private fun updateMessageStatusInState(partnerUsername: String, messageId: String, status: MessageStatus) { val conversations = _uiState.value.conversations.toMutableList(); val index = conversations.indexOfFirst { it.partnerUsername.equals(partnerUsername, true) }; if (index >= 0) { val old = conversations[index]; conversations[index] = old.copy(messages = old.messages.map { if (it.id == messageId) it.copy(status = status, timestamp = if (status == MessageStatus.SENDING) "Sending..." else it.timestamp) else it }.toMutableList()); _uiState.value = _uiState.value.copy(conversations = conversations); persistConversations() } }

    fun addMarketListing(item: MarketItem) {
        if (_uiState.value.isPostItemOpen.not()) return
        viewModelScope.launch {
            if (supabaseService.createMarketItem(item)) {
                val live = runCatching { supabaseService.fetchMarketItems() }
                    .getOrDefault(_uiState.value.marketItems)
                _uiState.value = _uiState.value.copy(
                    marketItems = live,
                    isPostItemOpen = false
                )
                showToast("🛍️ Product published to Aluta Market.")
            } else {
                showToast("Product wasn't published. Check your details and try again.")
            }
        }
    }

    fun addMarketItem(
        title: String,
        price: Long,
        category: String,
        condition: String,
        description: String,
        imageUrl: String?
    ) {
        val p = _uiState.value.myProfile
        addMarketListing(
            MarketItem(
                id = "",
                title = title,
                price = price,
                images = if (imageUrl.isNullOrBlank()) emptyList() else listOf(imageUrl),
                sellerUsername = p.username,
                sellerAvatar = p.avatarUrl,
                sellerName = p.fullName,
                sellerPhone = p.phone.value,
                sellerWhatsapp = p.whatsapp.value,
                sellerIsVerified = p.verificationBadge != VerificationBadge.NONE,
                verificationBadge = p.verificationBadge,
                university = p.university,
                location = p.currentCityState,
                category = category,
                condition = condition,
                description = description,
                postedTime = "Just now"
            )
        )
    }

    fun openProductDetail(item: MarketItem) {
        _uiState.value = _uiState.value.copy(viewingProduct = item)
    }

    fun closeProductDetail() {
        _uiState.value = _uiState.value.copy(viewingProduct = null)
    }

    fun activateSellerAccount(
        storeName: String,
        phone: String,
        whatsapp: String,
        state: String,
        city: String
    ) {
        val current = _uiState.value.myProfile
        val cleanStore = storeName.trim().ifBlank { current.fullName.ifBlank { "Campus Store" } }
        val profileUpdate = current.copy(
            sellerStoreName = cleanStore,
            phone = ContactField(phone.trim(), true),
            whatsapp = ContactField(whatsapp.trim(), true),
            currentCityState = listOf(city.trim(), state.trim()).filter { it.isNotBlank() }.joinToString(", ")
        )
        viewModelScope.launch {
            val profileSaved = profileRepository.updateProfile(profileUpdate)
            val sellerActivated = supabaseService.activateMarketplaceProfile(cleanStore)
            if (profileSaved && sellerActivated) {
                val activated = profileUpdate.copy(isSellerActive = true)
                _uiState.value = _uiState.value.copy(
                    myProfile = activated,
                    isBecomeSellerOpen = false,
                    showSellerCongratulationsDialog = true
                )
                saveLocalProfile(activated)
                showToast("🎉 Your seller profile is active.")
            } else {
                showToast("Seller activation failed. Please try again.")
            }
        }
    }

    fun dismissSellerCongratulations() {
        _uiState.value = _uiState.value.copy(showSellerCongratulationsDialog = false)
    }

    fun endorseSkill(skill: String) {
        val viewing = _uiState.value.viewingProfile
        val target = viewing ?: _uiState.value.myProfile
        val targetUsername = target.username
        if (targetUsername.isBlank()) return

        fun toggled(profile: UserProfile): UserProfile {
            return profile.copy(
                skillEndorsements = profile.skillEndorsements.map {
                    if (it.skill.equals(skill, true)) {
                        it.copy(
                            endorsedByMe = !it.endorsedByMe,
                            endorsements = (it.endorsements + if (!it.endorsedByMe) 1 else -1)
                                .coerceAtLeast(0)
                        )
                    } else it
                }.toMutableList()
            )
        }

        val beforeViewing = _uiState.value.viewingProfile
        val beforeMine = _uiState.value.myProfile
        if (viewing != null) {
            _uiState.value = _uiState.value.copy(viewingProfile = toggled(viewing))
        } else {
            _uiState.value = _uiState.value.copy(myProfile = toggled(beforeMine))
        }

        viewModelScope.launch {
            val success = supabaseService.recordSkillEndorsement(
                targetUsername,
                skill,
                _uiState.value.myProfile.username
            )
            if (success) {
                showToast("Endorsement updated for $skill.")
            } else {
                _uiState.value = _uiState.value.copy(
                    viewingProfile = beforeViewing,
                    myProfile = beforeMine
                )
                showToast("Couldn't update the endorsement.")
            }
        }
    }

    fun applyVerification(
        tier: VerificationBadge,
        paymentReference: String = "",
        amount: Int = if (tier == VerificationBadge.GOLD) 2500 else 800
    ) {
        if (tier == VerificationBadge.NONE) return
        viewModelScope.launch {
            val reference = paymentReference.trim()
            val success = supabaseService.submitVerificationRequest(
                if (tier == VerificationBadge.GOLD) "GOLD" else "BLUE",
                reference,
                amount
            )
            if (success) {
                _uiState.value = _uiState.value.copy(isGetVerifiedOpen = false)
                showToast("Verification request submitted. Your badge will activate only after approval.")
            } else {
                showToast("Couldn't submit the verification request.")
            }
        }
    }

    suspend fun recordTriviaResult(questionId: String, correct: Boolean): GameActionResult? {
        val result = supabaseService.recordTriviaResult(questionId, correct)
        if (result != null) {
            val live = runCatching { supabaseService.fetchLeaderboard() }
                .getOrDefault(_uiState.value.leaderboardUsers)
            _uiState.value = _uiState.value.copy(leaderboardUsers = live)
        }
        return result
    }

    fun recordPostView(postId: String) {
        viewModelScope.launch {
            val views = supabaseService.recordPostView(postId, _uiState.value.myProfile.username)
            if (views <= 0) return@launch
            _uiState.value = _uiState.value.copy(posts = _uiState.value.posts.map { if (it.id == postId) it.copy(viewsCount = maxOf(it.viewsCount, views)) else it }, reels = _uiState.value.reels.map { if (it.id == postId) it.copy(viewsCount = maxOf(it.viewsCount, views)) else it })
            persistCurrentFeed()
        }
    }

    fun handleNotificationClick(activity: ActivityItem) {
        _uiState.value = _uiState.value.copy(
            isActivityOpen = false,
            activities = _uiState.value.activities.map {
                if (it.id == activity.id) it.copy(isUnread = false) else it
            }
        )
        viewModelScope.launch {
            runCatching { supabaseService.markActivityRead(activity.id) }
        }

        activity.targetPostId?.let { postId ->
            val target = (_uiState.value.posts + _uiState.value.reels).find { it.id == postId }
            if (target != null) {
                _uiState.value = _uiState.value.copy(
                    selectedTab = MainTab.HOME,
                    feedSubTab = if (target.isReel) 1 else 0
                )
                if (activity.category == NotificationFilter.COMMENTS) {
                    openCommentsForPost(target.id)
                }
            }
            return
        }

        activity.targetMarketId?.let { marketId ->
            _uiState.value.marketItems.find { it.id == marketId }?.let { openProductDetail(it) }
            return
        }

        openProfile(activity.user)
    }

    fun markAllActivitiesRead() {
        if (_uiState.value.activities.none { it.isUnread }) return
        _uiState.value = _uiState.value.copy(
            activities = _uiState.value.activities.map { it.copy(isUnread = false) }
        )
        viewModelScope.launch {
            val success = runCatching { supabaseService.markAllActivitiesRead() }.getOrDefault(false)
            if (!success) {
                showToast("Couldn't sync notification read status.")
            }
        }
    }

    fun logout() {
        realtimeManager.disconnect()
        viewModelScope.launch {
            runCatching { supabaseService.setMyPresence(false) }
            runCatching { authRepository.signOut() }
            SupabaseService.clearSession(); prefs.edit().clear().apply()
            _uiState.value = BlinkUiState(destination = AppDestination.SIGN_IN, isDarkMode = _uiState.value.isDarkMode)
            showToast("Logged out successfully.")
        }
    }

    fun updatePresence(online: Boolean) {
        viewModelScope.launch {
            runCatching { supabaseService.setMyPresence(online) }
        }
    }

    fun openStory(story: Story) { val updated = _uiState.value.stories.map { if (it.id == story.id) it.copy(hasUnseen = false) else it }; _uiState.value = _uiState.value.copy(stories = updated, activeViewingStory = updated.find { it.id == story.id } ?: story.copy(hasUnseen = false)); markStoryViewed(story.id) }
    fun closeStory() { _uiState.value = _uiState.value.copy(activeViewingStory = null) }
    fun createStory(storyImage: String, caption: String, faculty: String = "") {
        val p=_uiState.value.myProfile
        val s=Story(UUID.randomUUID().toString(),p.username,p.avatarUrl,false,true,storyImage,caption,"Just now",faculty.ifBlank{p.faculty},p.university,0,false,p.verificationBadge)
        viewModelScope.launch(Dispatchers.IO){
            if(postRepository.createStory(s,false)){
                _uiState.value=_uiState.value.copy(stories=listOf(s)+_uiState.value.stories.filter{it.id!="story_me"&&it.id!=s.id})
                showToast("✨ Story published!")
            }else showToast("Failed to persist story to Supabase.")
        }
    }

    fun publishStory(uriString:String,caption:String,isVideo:Boolean){
        if(_uiState.value.isCreatingStory)return
        _uiState.value=_uiState.value.copy(isCreatingStory=true)
        viewModelScope.launch{
            try{
                val uid=supabaseService.getCurrentUserId()?:throw IllegalStateException("Please sign in again.")
                val uri=Uri.parse(uriString)
                val mime=appContext.contentResolver.getType(uri)?:if(isVideo)"video/mp4" else "image/jpeg"
                val bytes=withContext(Dispatchers.IO){appContext.contentResolver.openInputStream(uri)?.use{it.readBytes()}}?:throw IllegalStateException("Unable to read selected media.")
                val url=postRepository.uploadStoryMedia(uid,bytes,mime,isVideo)?:throw IllegalStateException("Story upload failed.")
                val p=_uiState.value.myProfile
                val story=Story(UUID.randomUUID().toString(),p.username,p.avatarUrl,false,true,url,caption.trim(),"Just now",p.faculty,p.university,0,false,p.verificationBadge)
                if(!postRepository.createStory(story,isVideo))throw IllegalStateException("Story save failed.")
                _uiState.value=_uiState.value.copy(stories=listOf(story)+_uiState.value.stories.filter{it.id!="story_me"&&it.id!=story.id},isCreateStoryOpen=false)
                showToast("Story shared.")
            }catch(e:Exception){
                Log.e(TAG,"publishStory failed",e)
                showToast(e.message?:"Unable to share story.")
            }finally{
                _uiState.value=_uiState.value.copy(isCreatingStory=false)
            }
        }
    }
    fun markStoryViewed(storyId: String) { _uiState.value = _uiState.value.copy(stories = _uiState.value.stories.map { if (it.id == storyId) it.copy(hasUnseen = false) else it }); if (storyId != "story_me") viewModelScope.launch(Dispatchers.IO) { postRepository.markStoryViewed(storyId) } }
    fun toggleStoryLike(storyId: String) {
        var next = false
        var count = 0
        val before = _uiState.value.stories
        val updated = before.map {
            if (it.id == storyId) {
                next = !it.isLiked
                count = (it.likesCount + if (next) 1 else -1).coerceAtLeast(0)
                it.copy(isLiked = next, likesCount = count)
            } else it
        }
        _uiState.value = _uiState.value.copy(
            stories = updated,
            activeViewingStory = updated.find { it.id == storyId } ?: _uiState.value.activeViewingStory
        )
        viewModelScope.launch(Dispatchers.IO) {
            val success = runCatching {
                postRepository.toggleStoryLike(storyId, next, count)
            }.getOrDefault(false)
            if (!success) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        stories = before,
                        activeViewingStory = before.find { it.id == storyId } ?: _uiState.value.activeViewingStory
                    )
                    showToast("Couldn't update the story like.")
                }
            }
        }
    }

    fun reactToStory(storyId: String, emoji: String) {
        viewModelScope.launch {
            val success = runCatching { postRepository.reactToStory(storyId, emoji) }.getOrDefault(false)
            if (success) showToast("Reacted $emoji")
            else showToast("Reaction wasn't sent.")
        }
    }

    fun replyToStory(storyUsername: String, replyText: String) {
        val cleanText = replyText.trim()
        val cleanUser = storyUsername.trim().removePrefix("@")
        if (cleanText.isBlank() || cleanUser.isBlank() || cleanUser.equals("null", true)) return
        viewModelScope.launch {
            chatRepository.sendMessage(cleanUser, cleanText).fold(
                onSuccess = {
                    showToast("💬 Message sent to @$cleanUser")
                    supabaseService.recordActivity(
                        cleanUser,
                        "replied to your story",
                        NotificationFilter.ALL,
                        targetUsername = _uiState.value.myProfile.username,
                        targetType = "CHAT"
                    )
                },
                onFailure = { showToast("Story reply wasn't sent.") }
            )
        }
    }

    fun showToast(message: String) { _snackBarMessages.tryEmit(message) }
}

private fun String.capitalizeWords(): String = split(" ").filter { it.isNotBlank() }.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
