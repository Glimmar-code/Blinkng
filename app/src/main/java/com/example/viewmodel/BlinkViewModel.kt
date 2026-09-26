package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.AccountSessionStore
import com.example.auth.AuthErrorMapper
import com.example.auth.PasswordRecoveryLinkParser
import com.example.call.CallRepository
import com.example.call.CallType
import com.example.call.IncomingCallNotification
import com.example.data.local.CachedAppSnapshot
import com.example.data.local.OfflineContentStore
import com.example.data.models.*
import com.example.data.network.NetworkMonitor
import com.example.data.repository.*
import com.example.data.supabase.BlinkEconomyService
import com.example.data.supabase.RealtimeEvent
import com.example.data.supabase.SupabaseRealtimeManager
import com.example.data.supabase.SupabaseService
import com.example.data.supabase.MessageMediaService
import com.example.notification.BlinkNotificationHelper
import com.example.notification.BlinkInAppNotification
import com.example.notification.BlinkInAppNotificationCenter
import com.example.notification.BlinkInAppNotificationDestination
import com.example.notification.BlinkNotificationType
import com.example.notification.NotificationPreferenceStore
import com.example.sharing.AppDeepLink
import com.example.sharing.ShareContentType
import com.example.util.safeBoolean
import com.example.util.safeInt
import com.example.util.safeString
import com.blinkng.shared.BlinkCoinPack
import com.blinkng.shared.BlinkDailyMission
import com.blinkng.shared.BlinkEconomyDefaults
import com.blinkng.shared.BlinkEconomyPolicy
import com.blinkng.shared.BlinkRewardMilestone
import com.blinkng.shared.BlinkOnboardingPolicy
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
import org.json.JSONObject

enum class AppDestination { SPLASH, ONBOARDING, SIGN_IN, SIGN_UP, RESET_PASSWORD, PROFILE_SETUP, MAIN }

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
    val deepLinkedPost: FeedPost? = null,
    val activeConversationPartner: String? = null,
    val activeViewingStory: Story? = null,
    val isConversationFullScreen: Boolean = false,
    val stories: List<Story> = listOf(Story(id = "story_me", username = "Your Story", avatar = "", hasUnseen = false, isUser = true)),
    val posts: List<FeedPost> = emptyList(),
    val followingPosts: List<FeedPost> = emptyList(),
    val reels: List<FeedPost> = emptyList(),
    val savedDrafts: List<PostDraft> = emptyList(),
    val scheduledPosts: List<ScheduledPost> = emptyList(),
    val marketItems: List<MarketItem> = emptyList(),
    val leaderboardUsers: List<LeaderboardUser> = emptyList(),
    val gameLeaderboardUsers: List<LeaderboardUser> = emptyList(),
    val conversations: List<ChatConversation> = emptyList(),
    val isConversationsLoading: Boolean = false,
    val activities: List<ActivityItem> = emptyList(),
    val activitiesLoading: Boolean = false,
    val activitiesError: String? = null,
    val connectHub: ConnectHubSnapshot = ConnectHubSnapshot(),
    val isConnectHubLoading: Boolean = false,
    val comments: List<Comment> = emptyList(),
    val isCommentsLoading: Boolean = false,
    val isPostingComment: Boolean = false,
    val mutedUsers: Set<String> = emptySet(),
    val feedSubTab: Int = 0,
    val routedReelId: String? = null,
    val isOnline: Boolean = true,
    val isLiveSupabaseConnected: Boolean = false,
    val isMessagingRealtimeConnected: Boolean = false,
    val isFeedLoading: Boolean = true,
    val isRefreshingContent: Boolean = false,
    val isSyncingContent: Boolean = false,
    val feedErrorMessage: String? = null,
    val isCreatingPost: Boolean = false,
    val pendingMessageCount: Int = 0,
    val blinkCoinBalance: Long = 0L,
    val economyPolicy: BlinkEconomyPolicy = BlinkEconomyDefaults.policy,
    val rewardedAdsToday: Int = 0,
    val rewardedCoinsToday: Int = 0,
    val dailyMissions: List<BlinkDailyMission> = emptyList(),
    val isDailyMissionsLoading: Boolean = false,
    val isCoinPackPickerOpen: Boolean = false,
    val discoverProfiles: List<UserProfile> = emptyList(),
    val discoverPosts: List<FeedPost> = emptyList(),
    val isDiscoverSearching: Boolean = false,
    val hasMorePosts: Boolean = true,
    val hasMoreFollowingPosts: Boolean = true,
    val hasMoreReels: Boolean = true,
    val isLoadingMorePosts: Boolean = false,
    val isLoadingMoreFollowingPosts: Boolean = false,
    val isLoadingMoreReels: Boolean = false,
    val messageHistoryHasMore: Map<String, Boolean> = emptyMap(),
    val loadingOlderConversationId: String? = null,
    val loadingInitialConversationId: String? = null
)

class BlinkViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "BlinkViewModel"
        private const val PREFS = "blink_user_session"
        private const val AUTH_PREFS = "blink_auth_prefs"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_FULL_NAME = "full_name"
        private const val KEY_USERNAME = "username"
        private const val KEY_FACULTY = "faculty"
        private const val KEY_UNIVERSITY = "university"
        private const val KEY_DEPARTMENT = "department"
        private const val KEY_ACADEMIC_LEVEL = "academic_level"
        private const val KEY_GENDER = "gender"
        private const val KEY_BIRTH_DATE = "birth_date"
        private const val KEY_INTERESTS = "interests"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_ONBOARDING_STEP = "onboarding_step"
        private const val KEY_AVATAR = "avatar_url"
        private const val KEY_COVER = "cover_url"
        private const val KEY_VERIFICATION = "verification_badge"
        private const val KEY_SELLER_ACTIVE = "is_seller_active"
        private const val KEY_DARK_MODE = "ui_dark_mode"
        private const val KEY_SELECTED_TAB = "ui_selected_tab"
        private const val KEY_FEED_SUB_TAB = "ui_feed_sub_tab"
        private const val KEY_PENDING_PAYSTACK_ORDER = "pending_paystack_order_id"
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
    private val scheduledPostRepository = ScheduledPostRepository()
    private val blinkEconomyService = BlinkEconomyService()
    val realtimeManager = SupabaseRealtimeManager.getInstance()
    private val offlineContentStore = OfflineContentStore(appContext)
    private val networkMonitor = NetworkMonitor(appContext)
    private val syncMutex = Mutex()
    private val cacheWriteMutex = Mutex()
    // CHAT_CACHE_RECONCILIATION_V1: stale async snapshots must never write after newer ones.
    private val conversationCacheRevision = java.util.concurrent.atomic.AtomicLong(0L)
    private val messageOutboxMutex = Mutex()
    private val activeOutboxIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val pendingReplyTargets = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var syncJob: Job? = null
    private var lastSuccessfulSyncAt = 0L
    private var discoverSearchJob: Job? = null
    private var addAccountMode = AccountSessionStore.consumeAddAccountRequest(appContext)
    private var recoveryPreviousSession: SupabaseService.SessionSnapshot? = null
    private var recoveryHadExistingLogin: Boolean = false
    private var pendingDeepLink: AppDeepLink? = null
    private val _uiState = MutableStateFlow(BlinkUiState())
    val uiState: StateFlow<BlinkUiState> = _uiState.asStateFlow()
    private val _snackBarMessages = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val snackBarMessages: SharedFlow<String> = _snackBarMessages.asSharedFlow()

    init {
        SupabaseService.initialize(appContext)
        restoreUiPreferences()
        _uiState.value = _uiState.value.copy(isOnline = networkMonitor.isCurrentlyOnline())

        // Local-first startup: a previously authenticated account remains usable with
        // airplane mode / mobile data off. Cloud session verification happens afterwards.
        val explicitSignInRequired = AccountSessionStore.isSignInRequired(appContext)
        val hasLocalSession = !explicitSignInRequired && !addAccountMode && hasLocalAuthenticatedProfile()
        if (addAccountMode) {
            // "Add account" is an auth surface only. Keep the previous account/token/cache
            // untouched until a replacement login has fully succeeded.
            _uiState.value = _uiState.value.copy(destination = AppDestination.SIGN_IN)
        } else if (hasLocalSession) {
            restoreLocalSession()
        }

        observeCachedContent()
        viewModelScope.launch {
            restoreCachedAppSnapshot()
            restoreResumableRouteFromCurrentState()
            if (hasLocalSession && !_uiState.value.isOnline) {
                _uiState.value = _uiState.value.copy(
                    destination = AppDestination.MAIN,
                    isFeedLoading = false,
                    isRefreshingContent = false,
                    isSyncingContent = false,
                    isLiveSupabaseConnected = false
                )
            }
        }
        observeNetworkStatus()
        observeAuthState()
        if (!addAccountMode) {
            viewModelScope.launch { restoreSupabaseSession() }
        }
        loadDraftsFromPrefs()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { offlineContentStore.pruneOldCaches() }
                .onFailure { Log.w(TAG, "Offline cache pruning failed", it) }
        }
        viewModelScope.launch { realtimeManager.events.collect { handleRealtimeEvent(it) } }
        // MESSAGING_RELIABILITY_AUDIT_V3: expose real messages-channel readiness, not feed REST health.
        viewModelScope.launch {
            realtimeManager.messagesSubscribed.collectLatest { subscribed ->
                _uiState.value = _uiState.value.copy(isMessagingRealtimeConnected = subscribed)
                if (subscribed) {
                    viewModelScope.launch(Dispatchers.IO) {
                        runCatching { chatRepository.ackPendingDeliveries() }
                    }
                }
            }
        }
        viewModelScope.launch {
            _uiState.collectLatest { state ->
                val link = pendingDeepLink
                if (link != null && state.destination == AppDestination.MAIN && state.myProfile.id.isNotBlank()) {
                    pendingDeepLink = null
                    routeDeepLink(link)
                }
            }
        }
    }

    fun completeSplash() {
        if (_uiState.value.destination != AppDestination.SPLASH) return

        when {
            AccountSessionStore.isSignInRequired(appContext) -> {
                _uiState.value = _uiState.value.copy(destination = AppDestination.ONBOARDING)
            }
            hasLocalAuthenticatedProfile() -> {
                restoreLocalSession()
                viewModelScope.launch {
                    restoreCachedAppSnapshot()
                    restoreResumableRouteFromCurrentState()
                }
            }
            !SupabaseService.accessToken().isNullOrBlank() ||
                !SupabaseService.refreshToken().isNullOrBlank() -> {
                // A recoverable cloud session is already restoring. Keep the short splash
                // instead of flashing SIGN_IN and making a valid session look logged out.
                Unit
            }
            else -> _uiState.value = _uiState.value.copy(destination = AppDestination.ONBOARDING)
        }
    }

    fun persistResumePoint() {
        if (_uiState.value.destination != AppDestination.MAIN) return
        persistUiPreferences()
        persistResumableRoute(_uiState.value)
    }

    fun handleDeepLink(link: AppDeepLink) {
        pendingDeepLink = link
        val state = _uiState.value
        if (state.destination == AppDestination.MAIN && state.myProfile.id.isNotBlank()) {
            pendingDeepLink = null
            routeDeepLink(link)
        }
    }

    /** Returns true when the incoming URI is an authentication/recovery route. */
    fun handleAuthDeepLink(uri: Uri?): Boolean {
        val recovery = PasswordRecoveryLinkParser.parse(uri?.toString()) ?: return false
        if (!recovery.error.isNullOrBlank() || recovery.accessToken.isBlank()) {
            // An old/broken recovery link is not an instruction to sign the current user out.
            if (!AccountSessionStore.isSignInRequired(appContext) && hasLocalAuthenticatedProfile()) {
                restoreLocalSession()
                _uiState.value = _uiState.value.copy(destination = AppDestination.MAIN)
            } else {
                _uiState.value = _uiState.value.copy(destination = AppDestination.SIGN_IN)
            }
            showToast(
                if (!recovery.error.isNullOrBlank()) {
                    "That password reset link is invalid or expired. Request a new one."
                } else {
                    "That password reset link is incomplete or expired. Request a new one."
                }
            )
            return true
        }

        recoveryPreviousSession = SupabaseService.sessionSnapshot()
        recoveryHadExistingLogin =
            !AccountSessionStore.isSignInRequired(appContext) &&
                (hasLocalAuthenticatedProfile() ||
                    !recoveryPreviousSession?.accessToken.isNullOrBlank() ||
                    !recoveryPreviousSession?.refreshToken.isNullOrBlank())

        // Recovery credentials are temporary. Preserve the pre-recovery session so cancel
        // or completion can restore it instead of turning recovery into a logout.
        SupabaseService.replaceSession(
            accessToken = recovery.accessToken,
            refreshToken = recovery.refreshToken.ifBlank { null }
        )
        _uiState.value = _uiState.value.copy(destination = AppDestination.RESET_PASSWORD)
        return true
    }

    fun updateRecoveredPassword(newPassword: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = supabaseService.updatePassword(newPassword)
            if (result.isSuccess) {
                finishPasswordRecovery()
                val message = if (_uiState.value.destination == AppDestination.MAIN) {
                    "Password updated."
                } else {
                    "Password updated. Sign in with your new password."
                }
                showToast(message)
                onResult(true, message)
            } else {
                val message = AuthErrorMapper.friendly(
                    result.exceptionOrNull()?.message,
                    "Unable to update password. Request a new reset link and try again."
                )
                onResult(false, message)
            }
        }
    }

    fun cancelPasswordRecovery() {
        finishPasswordRecovery()
    }

    private fun finishPasswordRecovery() {
        val previous = recoveryPreviousSession
        recoveryPreviousSession = null

        if (recoveryHadExistingLogin && previous != null) {
            SupabaseService.restoreSessionSnapshot(previous)
            AccountSessionStore.setSignInRequired(appContext, false)
            if (hasLocalAuthenticatedProfile()) {
                restoreLocalSession()
                _uiState.value = _uiState.value.copy(destination = AppDestination.MAIN)
            } else {
                _uiState.value = _uiState.value.copy(destination = AppDestination.SIGN_IN)
            }
        } else {
            // Never persist a password-recovery token as the normal application session.
            SupabaseService.clearSession()
            AccountSessionStore.setSignInRequired(appContext, true)
            prefs.edit().putBoolean(KEY_IS_LOGGED_IN, false).apply()
            authPrefs.edit().putBoolean(KEY_IS_LOGGED_IN, false).apply()
            _uiState.value = _uiState.value.copy(destination = AppDestination.SIGN_IN)
        }

        recoveryHadExistingLogin = false
    }

    private fun routeDeepLink(link: AppDeepLink) {
        viewModelScope.launch {
            when (link.type) {
                ShareContentType.PROFILE -> {
                    val profile = runCatching { supabaseService.fetchProfileById(link.id) }.getOrNull()
                    if (profile == null) {
                        showToast("This profile is unavailable.")
                    } else {
                        val state = _uiState.value
                        _uiState.value = state.copy(
                            selectedTab = MainTab.HOME,
                            routedReelId = null,
                            viewingProfile = profile,
                            deepLinkedPost = null,
                            activePostOptionsPost = null,
                            activeCommentsPostId = null
                        )
                        persistProfile(profile)
                    }
                }

                ShareContentType.POST, ShareContentType.REEL -> {
                    val post = runCatching { postRepository.fetchPostById(link.id) }.getOrNull()
                    val expectsReel = link.type == ShareContentType.REEL
                    if (post == null || post.isReel != expectsReel) {
                        showToast(if (expectsReel) "This reel is unavailable." else "This post is unavailable.")
                        return@launch
                    }

                    val state = _uiState.value
                    if (expectsReel) {
                        _uiState.value = state.copy(
                            selectedTab = MainTab.HOME,
                            feedSubTab = 1,
                            routedReelId = post.id,
                            reels = listOf(post) + state.reels.filterNot { it.id == post.id },
                            viewingProfile = null,
                            deepLinkedPost = null,
                            activePostOptionsPost = null,
                            activeCommentsPostId = null
                        )
                    } else {
                        _uiState.value = state.copy(
                            selectedTab = MainTab.HOME,
                            feedSubTab = 0,
                            routedReelId = null,
                            posts = listOf(post) + state.posts.filterNot { it.id == post.id },
                            viewingProfile = null,
                            deepLinkedPost = post,
                            activePostOptionsPost = null,
                            activeCommentsPostId = null
                        )
                    }
                    persistCurrentFeed()
                }
            }
        }
    }

    fun closeDeepLinkedPost() {
        _uiState.value = _uiState.value.copy(deepLinkedPost = null)
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.authState.collect { authState ->
                when (authState) {
                    is AuthState.Authenticated -> {
                        addAccountMode = false
                        val profile = authState.userProfile
                        _uiState.value = _uiState.value.copy(
                            myProfile = profile,
                            destination = when (_uiState.value.destination) {
                                AppDestination.SIGN_IN,
                                AppDestination.SIGN_UP,
                                AppDestination.ONBOARDING,
                                AppDestination.SPLASH -> authenticatedDestination(profile)
                                else -> _uiState.value.destination
                            }
                        )
                        saveLocalProfile(profile)
                        refreshMyProfileFromSupabase(showErrorToast = false)
                        fetchSupabaseData()
                    }
                    is AuthState.Unauthenticated -> {
                        val recoverable = !AccountSessionStore.isSignInRequired(appContext) &&
                            (!SupabaseService.refreshToken().isNullOrBlank() || AccountSessionStore.list(appContext).isNotEmpty())
                        if (
                            (_uiState.value.destination == AppDestination.MAIN ||
                                _uiState.value.destination == AppDestination.PROFILE_SETUP) &&
                            !recoverable
                        ) {
                            _uiState.value = _uiState.value.copy(destination = AppDestination.ONBOARDING)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

private suspend fun restoreSupabaseSession() {
        try {
            if (addAccountMode) {
                _uiState.value = _uiState.value.copy(destination = AppDestination.SIGN_IN)
                return
            }
            if (AccountSessionStore.isSignInRequired(appContext)) {
                SupabaseService.clearSession()
                _uiState.value = _uiState.value.copy(destination = AppDestination.ONBOARDING)
                return
            }
            // Never make an offline cold start wait on Supabase. The last signed-in account
            // and its durable cache are the source of truth until connectivity returns.
            if (!_uiState.value.isOnline && hasLocalAuthenticatedProfile()) {
                restoreLocalSession()
                restoreCachedAppSnapshot()
                _uiState.value = _uiState.value.copy(
                    destination = authenticatedDestination(),
                    isFeedLoading = false,
                    isRefreshingContent = false,
                    isSyncingContent = false,
                    isLiveSupabaseConnected = false
                )
                return
            }

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
                        _uiState.value = _uiState.value.copy(myProfile = profile, destination = authenticatedDestination(profile))
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
                    _uiState.value = _uiState.value.copy(destination = authenticatedDestination())
                    fetchSupabaseData()
                    return
                }
            }

            if (hasLocalAuthenticatedProfile() &&
                (SupabaseService.accessToken() != null || AccountSessionStore.list(appContext).isNotEmpty())) {
                restoreLocalSession()
                _uiState.value = _uiState.value.copy(destination = authenticatedDestination())
                fetchSupabaseData()
            } else {
                _uiState.value = _uiState.value.copy(destination = AppDestination.ONBOARDING)
            }
        } catch (e: Exception) {
            Log.w(TAG, "restoreSupabaseSession notice: ${e.message}")
            if (hasLocalAuthenticatedProfile()) {
                restoreLocalSession()
                restoreCachedAppSnapshot()
                _uiState.value = _uiState.value.copy(
                    destination = authenticatedDestination(),
                    isFeedLoading = false,
                    isRefreshingContent = false,
                    isSyncingContent = false,
                    isLiveSupabaseConnected = false
                )
                // A temporary network/Supabase failure must not erase the offline app.
                // Reconnect handling will retry the cloud sync automatically.
            } else {
                _uiState.value = _uiState.value.copy(destination = AppDestination.ONBOARDING)
            }
        }
    }

    private fun hasLocalAuthenticatedProfile(): Boolean =
        prefs.safeBoolean(KEY_IS_LOGGED_IN, false) || authPrefs.safeBoolean(KEY_IS_LOGGED_IN, false)

    private fun authenticatedDestination(profile: UserProfile = _uiState.value.myProfile): AppDestination =
        if (profile.onboardingCompleted) AppDestination.MAIN else AppDestination.PROFILE_SETUP

    private fun restoreLocalSession() {
        if (!hasLocalAuthenticatedProfile()) return
        val savedId = prefs.safeString(KEY_USER_ID, "").orEmpty()
        val savedEmail = prefs.safeString(KEY_EMAIL, authPrefs.safeString(KEY_EMAIL, "")).orEmpty()
        val savedName = prefs.safeString(KEY_FULL_NAME, authPrefs.safeString(KEY_FULL_NAME, "")).orEmpty()
        val savedUsername = prefs.safeString(KEY_USERNAME, authPrefs.safeString(KEY_USERNAME, "")).orEmpty()
        if (savedName.isBlank() || savedUsername.isBlank()) return
        val savedFaculty = prefs.safeString(KEY_FACULTY, authPrefs.safeString(KEY_FACULTY, "")).orEmpty()
        val savedUniversity = prefs.safeString(KEY_UNIVERSITY, authPrefs.safeString(KEY_UNIVERSITY, "")).orEmpty()
        val savedDepartment = prefs.safeString(KEY_DEPARTMENT, "").orEmpty()
        val savedAcademicLevel = prefs.safeString(KEY_ACADEMIC_LEVEL, "").orEmpty()
        val savedGender = prefs.safeString(KEY_GENDER, "").orEmpty()
        val savedBirthDate = prefs.safeString(KEY_BIRTH_DATE, "").orEmpty()
        val savedInterests = runCatching {
            val raw = prefs.safeString(KEY_INTERESTS, "[]").orEmpty()
            val array = JSONArray(if (raw.isBlank()) "[]" else raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
        val savedOnboardingCompleted = prefs.safeBoolean(KEY_ONBOARDING_COMPLETED, true)
        val savedOnboardingStep = prefs.safeInt(
            KEY_ONBOARDING_STEP,
            if (savedOnboardingCompleted) 4 else 0
        ).coerceIn(0, 4)
        val savedAvatar = prefs.safeString(KEY_AVATAR, authPrefs.safeString(KEY_AVATAR, "")).orEmpty()
        val savedCover = prefs.safeString(KEY_COVER, authPrefs.safeString(KEY_COVER, "")).orEmpty()
        val badge = when (prefs.safeString(KEY_VERIFICATION, "")?.uppercase()) {
            "GOLD" -> VerificationBadge.GOLD
            "BLUE" -> VerificationBadge.BLUE
            else -> VerificationBadge.NONE
        }
        val previousUsername = _uiState.value.myProfile.username
        val accountChanged = previousUsername.isNotBlank() && !previousUsername.equals(savedUsername, true)
        offlineContentStore.setActiveOwner(savedUsername)
        _uiState.value = _uiState.value.copy(
            myProfile = UserProfile(
                id = savedId,
                fullName = savedName,
                username = savedUsername,
                email = ContactField(savedEmail, true),
                faculty = savedFaculty,
                university = savedUniversity,
                department = savedDepartment,
                academicLevel = savedAcademicLevel,
                gender = savedGender,
                birthDate = savedBirthDate,
                interests = savedInterests,
                onboardingCompleted = savedOnboardingCompleted,
                onboardingStep = savedOnboardingStep,
                avatarUrl = savedAvatar,
                coverPhotoUrl = savedCover,
                verificationBadge = badge,
                isSellerActive = prefs.safeBoolean(KEY_SELLER_ACTIVE, false)
            ),
            // Never carry account A's in-memory chats into account B. The account-scoped
            // Room/snapshot cache below restores B's own conversations immediately.
            conversations = if (accountChanged) emptyList() else _uiState.value.conversations,
            destination = authenticatedDestination(UserProfile(onboardingCompleted = savedOnboardingCompleted))
        )
    }

    private fun saveLocalProfile(profile: UserProfile) {
        offlineContentStore.setActiveOwner(profile.username)
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_USER_ID, profile.id)
            .putString(KEY_EMAIL, profile.email.value)
            .putString(KEY_FULL_NAME, profile.fullName)
            .putString(KEY_USERNAME, profile.username)
            .putString(KEY_FACULTY, profile.faculty)
            .putString(KEY_UNIVERSITY, profile.university)
            .putString(KEY_DEPARTMENT, profile.department)
            .putString(KEY_ACADEMIC_LEVEL, profile.academicLevel)
            .putString(KEY_GENDER, profile.gender)
            .putString(KEY_BIRTH_DATE, profile.birthDate)
            .putString(KEY_INTERESTS, JSONArray(profile.interests).toString())
            .putBoolean(KEY_ONBOARDING_COMPLETED, profile.onboardingCompleted)
            .putInt(KEY_ONBOARDING_STEP, profile.onboardingStep.coerceIn(0, 4))
            .putString(KEY_AVATAR, profile.avatarUrl)
            .putString(KEY_COVER, profile.coverPhotoUrl)
            .putString(KEY_VERIFICATION, profile.verificationBadge.name)
            .putBoolean(KEY_SELLER_ACTIVE, profile.isSellerActive)
            .apply()
    }

    private fun saveSession(profile: UserProfile) = saveLocalProfile(profile)

    private fun restoreUiPreferences() {
        val tabName = prefs.safeString(KEY_SELECTED_TAB, MainTab.HOME.name).orEmpty()
        val selected = MainTab.entries.firstOrNull { it.name == tabName } ?: MainTab.HOME
        _uiState.value = _uiState.value.copy(
            isDarkMode = prefs.safeBoolean(KEY_DARK_MODE, true),
            selectedTab = selected,
            feedSubTab = prefs.safeInt(KEY_FEED_SUB_TAB, 0).coerceIn(0, 3)
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
        val profileUsername = prefs.safeString(KEY_RESUME_PROFILE_USERNAME, null)
            ?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }
        val productId = prefs.safeString(KEY_RESUME_PRODUCT_ID, null)?.takeIf { it.isNotBlank() }
        val commentsPostId = prefs.safeString(KEY_RESUME_COMMENTS_POST_ID, null)?.takeIf { it.isNotBlank() }
        val postOptionsId = prefs.safeString(KEY_RESUME_POST_OPTIONS_ID, null)?.takeIf { it.isNotBlank() }
        val deepLinkPostId = prefs.safeString(KEY_RESUME_DEEP_LINK_POST_ID, null)?.takeIf { it.isNotBlank() }
        val conversation = prefs.safeString(KEY_RESUME_CONVERSATION, null)
            ?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }
        val storyId = prefs.safeString(KEY_RESUME_STORY_ID, null)?.takeIf { it.isNotBlank() }

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
                prefs.safeBoolean(KEY_RESUME_CONVERSATION_FULLSCREEN, false),
            isPostItemOpen = prefs.safeBoolean(KEY_RESUME_POST_ITEM, false),
            isBecomeSellerOpen = prefs.safeBoolean(KEY_RESUME_BECOME_SELLER, false),
            showSellerCongratulationsDialog = prefs.safeBoolean(KEY_RESUME_SELLER_CONGRATS, false),
            isEditProfileOpen = prefs.safeBoolean(KEY_RESUME_EDIT_PROFILE, false),
            isActivityOpen = prefs.safeBoolean(KEY_RESUME_ACTIVITY, false),
            isMenuOpen = prefs.safeBoolean(KEY_RESUME_MENU, false),
            isGetVerifiedOpen = prefs.safeBoolean(KEY_RESUME_VERIFIED, false),
            isCreatePostOpen = prefs.safeBoolean(KEY_RESUME_CREATE_POST, false),
            isCreateStoryOpen = prefs.safeBoolean(KEY_RESUME_CREATE_STORY, false),
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

    private suspend fun restoreCachedAppSnapshot() {
        val cached = offlineContentStore.loadAppSnapshot() ?: return
        val current = _uiState.value
        val activeUsername = current.myProfile.username.ifBlank { offlineContentStore.cachedOwnerUsername() }
        if (activeUsername.isBlank() || !cached.ownerUsername.equals(activeUsername, true)) return

        val cachedProfile = cached.myProfile.takeIf { it.username.equals(activeUsername, true) }
        val restoredPosts = current.posts.ifEmpty { cached.posts }
        val restoredReels = current.reels.ifEmpty { cached.reels }
        val hasCachedFeed = restoredPosts.isNotEmpty() || restoredReels.isNotEmpty()

        _uiState.value = current.copy(
            myProfile = cachedProfile ?: current.myProfile,
            posts = restoredPosts,
            reels = restoredReels,
            profiles = current.profiles.ifEmpty { cached.profiles },
            conversations = current.conversations.ifEmpty { cached.conversations },
            stories = cached.stories.ifEmpty { current.stories },
            marketItems = current.marketItems,
            leaderboardUsers = current.leaderboardUsers.ifEmpty { cached.leaderboardUsers },
            gameLeaderboardUsers = current.gameLeaderboardUsers.ifEmpty { cached.gameLeaderboardUsers },
            activities = current.activities.ifEmpty { cached.activities },
            connectHub = if (current.connectHub == ConnectHubSnapshot()) cached.connectHub else current.connectHub,
            mutedUsers = if (current.mutedUsers.isEmpty()) cached.mutedUsers else current.mutedUsers,
            blinkCoinBalance = if (current.blinkCoinBalance == 0L) cached.blinkCoinBalance else current.blinkCoinBalance,
            isConnectHubLoading = false,
            activitiesLoading = false,
            isFeedLoading = if (!current.isOnline || hasCachedFeed) false else current.isFeedLoading
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
                            posts = snapshot.posts,
                            reels = snapshot.reels,
                            profiles = snapshot.profiles,
                            conversations = snapshot.conversations,
                            stories = snapshot.stories,
                            marketItems = emptyList(),
                            leaderboardUsers = snapshot.leaderboardUsers,
                            gameLeaderboardUsers = snapshot.gameLeaderboardUsers,
                            activities = snapshot.activities,
                            connectHub = snapshot.connectHub,
                            mutedUsers = snapshot.mutedUsers,
                            blinkCoinBalance = snapshot.blinkCoinBalance
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

    private suspend fun persistConversationsNow(
        snapshot: List<ChatConversation> = _uiState.value.conversations
    ) {
        val owner = _uiState.value.myProfile.username
        val revision = conversationCacheRevision.incrementAndGet()
        cacheWriteMutex.withLock {
            // A newer chat-state write may have been scheduled while this critical write waited.
            // Persist the newest in-memory state in that case, never an older snapshot.
            val currentSnapshot = if (revision == conversationCacheRevision.get()) {
                snapshot
            } else {
                _uiState.value.conversations
            }
            offlineContentStore.replaceConversations(currentSnapshot, _uiState.value.myProfile.username.ifBlank { owner })
        }
    }

    private fun persistConversations() {
        val snapshot = _uiState.value.conversations
        val owner = _uiState.value.myProfile.username
        val revision = conversationCacheRevision.incrementAndGet()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                cacheWriteMutex.withLock {
                    // Skip a snapshot that became stale before it reached disk.
                    if (revision != conversationCacheRevision.get()) return@withLock
                    offlineContentStore.replaceConversations(snapshot, owner)
                }
            }.onFailure { Log.w(TAG, "Unable to persist conversations", it) }
        }
    }

    /**
     * Server conversation summaries intentionally do not contain message pages.
     * Never replace the local list outright: doing so erases visible chat history
     * and optimistic/offline messages whenever a realtime summary event arrives.
     */
    private fun mergeConversationSummaries(
        summaries: List<ChatConversation>,
        local: List<ChatConversation>
    ): List<ChatConversation> {
        val server = summaries
            .distinctBy { it.id.ifBlank { it.partnerUsername.lowercase() } }
            .map { summary ->
                val cached = local.firstOrNull {
                    it.id == summary.id || it.partnerUsername.equals(summary.partnerUsername, true)
                }
                summary.copy(messages = cached?.messages?.toMutableList() ?: mutableListOf())
            }

        val localOnly = local.filter { cached ->
            server.none {
                it.id == cached.id || it.partnerUsername.equals(cached.partnerUsername, true)
            }
        }

        // Local-only entries are pending/new chats and should stay visible at the top
        // until Supabase returns their real conversation id.
        return localOnly + server
    }


    fun refreshProfileRewards() {
        val before = _uiState.value
        if (!before.isOnline || before.myProfile.id.isBlank()) return

        _uiState.value = before.copy(isDailyMissionsLoading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val streak = runCatching { supabaseService.touchDailyStreak() }
                .onFailure { Log.w(TAG, "Daily streak refresh failed", it) }
                .getOrNull()
            val economy = blinkEconomyService.economyStatus()
                .onFailure { Log.w(TAG, "Blink economy refresh failed", it) }
                .getOrNull()
            val balance = economy?.optLong("balance", before.blinkCoinBalance)
                ?: runCatching { supabaseService.fetchMyBlinkCoinBalance() }
                    .onFailure { Log.w(TAG, "Blink Coin balance refresh failed", it) }
                    .getOrDefault(before.blinkCoinBalance)
            val policy = economy?.let(::parseEconomyPolicy) ?: before.economyPolicy
            val adsToday = economy?.optInt("ads_today", before.rewardedAdsToday) ?: before.rewardedAdsToday
            val coinsToday = economy?.optInt("coins_earned_from_ads_today", before.rewardedCoinsToday)
                ?: before.rewardedCoinsToday
            val missionsPayload = blinkEconomyService.dailyMissions()
                .onFailure { Log.w(TAG, "Daily missions refresh failed", it) }
                .getOrNull()
            val missions = missionsPayload?.let(::parseDailyMissions) ?: before.dailyMissions

            withContext(Dispatchers.Main) {
                val latest = _uiState.value
                val currentMe = latest.myProfile
                if (currentMe.id.isBlank()) return@withContext

                val updatedMe = streak?.let { currentMe.copy(dailyStreak = it) } ?: currentMe
                val updatedProfiles = latest.profiles.map { candidate ->
                    if (
                        candidate.id == updatedMe.id ||
                        candidate.username.equals(updatedMe.username, ignoreCase = true)
                    ) updatedMe else candidate
                }
                val updatedViewing = latest.viewingProfile?.let { candidate ->
                    if (
                        candidate.id == updatedMe.id ||
                        candidate.username.equals(updatedMe.username, ignoreCase = true)
                    ) updatedMe else candidate
                }

                _uiState.value = latest.copy(
                    myProfile = updatedMe,
                    profiles = updatedProfiles,
                    viewingProfile = updatedViewing,
                    blinkCoinBalance = balance,
                    economyPolicy = policy,
                    rewardedAdsToday = adsToday.coerceIn(0, policy.rewardedAdDailyLimit),
                    rewardedCoinsToday = coinsToday.coerceAtLeast(0),
                    dailyMissions = missions,
                    isDailyMissionsLoading = false
                )
                saveLocalProfile(updatedMe)
                persistProfile(updatedMe)
                persistExtendedCache()
            }
        }
    }

    private fun parseDailyMissions(payload: JSONObject): List<BlinkDailyMission> {
        val array = payload.optJSONArray("missions") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: continue
                val key = row.optString("key").trim()
                val title = row.optString("title").trim()
                val target = row.optInt("target", 0)
                if (key.isBlank() || title.isBlank() || target <= 0) continue
                add(
                    BlinkDailyMission(
                        key = key,
                        title = title,
                        description = row.optString("description").trim(),
                        progress = row.optInt("progress", 0).coerceAtLeast(0),
                        target = target,
                        coinReward = row.optInt("coin_reward", 0).coerceAtLeast(0),
                        xpReward = row.optInt("xp_reward", 0).coerceAtLeast(0),
                        claimed = row.optBoolean("claimed", false)
                    )
                )
            }
        }
    }

    private fun parseEconomyPolicy(payload: JSONObject): BlinkEconomyPolicy {
        val fallback = BlinkEconomyDefaults.policy
        val milestones = payload.optJSONArray("rewarded_milestones")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val row = array.optJSONObject(index) ?: continue
                    val ads = row.optInt("ads", 0)
                    val total = row.optInt("total_coins", 0)
                    if (ads > 0 && total > 0) add(BlinkRewardMilestone(ads, total))
                }
            }.sortedBy { it.ads }
        }.orEmpty().ifEmpty { fallback.rewardedMilestones }

        val packs = payload.optJSONArray("coin_packs")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val row = array.optJSONObject(index) ?: continue
                    val id = row.optString("id").trim()
                    val price = row.optInt("price_ngn", 0)
                    val coins = row.optInt("coins", 0)
                    if (id.isNotBlank() && price > 0 && coins > 0) add(BlinkCoinPack(id, price, coins))
                }
            }
        }.orEmpty().ifEmpty { fallback.coinPacks }

        return BlinkEconomyPolicy(
            rewardedAdBaseCoins = payload.optInt("rewarded_ad_base_coins", fallback.rewardedAdBaseCoins).coerceAtLeast(1),
            rewardedAdDailyLimit = payload.optInt("rewarded_ad_daily_limit", fallback.rewardedAdDailyLimit).coerceIn(1, 100),
            rewardedMilestones = milestones,
            blueVerificationCashNgn = payload.optInt("blue_verification_cash_ngn", fallback.blueVerificationCashNgn).coerceAtLeast(1),
            blueVerificationCoinCost = payload.optInt("blue_verification_coin_cost", fallback.blueVerificationCoinCost).coerceAtLeast(1),
            blueVerificationValidDays = payload.optInt("blue_verification_valid_days", fallback.blueVerificationValidDays).coerceIn(1, 366),
            coinPacks = packs,
            cashCheckoutEnabled = payload.optBoolean("cash_checkout_enabled", fallback.cashCheckoutEnabled)
        )
    }

    suspend fun beginRewardedAdClaim(): String? {
        val state = _uiState.value
        if (!state.economyPolicy.canWatchRewardedAd(state.rewardedAdsToday)) {
            showToast("You've reached today's ${state.economyPolicy.rewardedAdDailyLimit}-ad rewarded limit.")
            return null
        }
        if (!state.isOnline || state.myProfile.id.isBlank()) {
            showToast("Connect to the internet and sign in before watching a rewarded ad.")
            return null
        }

        val result = blinkEconomyService.beginRewardedAdClaim()
        return result.fold(
            onSuccess = { payload ->
                payload.optString("claim_id").takeIf { it.isNotBlank() }
                    ?: run {
                        showToast("Unable to prepare the ad reward. Please try again.")
                        null
                    }
            },
            onFailure = { error ->
                Log.w(TAG, "Rewarded ad claim start failed", error)
                showToast(error.message ?: "Unable to prepare the ad reward. Please try again.")
                null
            }
        )
    }

    fun completeRewardedAdClaim(claimId: String) {
        if (claimId.isBlank()) return

        viewModelScope.launch {
            val result = blinkEconomyService.completeRewardedAdClaim(claimId)
            result.fold(
                onSuccess = { payload ->
                    val latest = _uiState.value
                    val reward = payload.optInt("reward_amount", latest.economyPolicy.rewardedAdBaseCoins)
                    val bonus = payload.optInt("milestone_bonus", 0)
                    val adsToday = payload.optInt("ads_today", latest.rewardedAdsToday + 1)
                    val balance = payload.optLong("balance", latest.blinkCoinBalance + reward)
                    _uiState.value = latest.copy(
                        blinkCoinBalance = balance,
                        rewardedAdsToday = adsToday.coerceIn(0, latest.economyPolicy.rewardedAdDailyLimit),
                        rewardedCoinsToday = latest.rewardedCoinsToday + reward
                    )
                    persistExtendedCache()
                    showToast(
                        if (bonus > 0) "+$reward Blink Coins • +$bonus milestone bonus"
                        else "+$reward Blink Coins"
                    )
                },
                onFailure = { error ->
                    Log.w(TAG, "Rewarded ad coin credit failed", error)
                    showToast(error.message ?: "Your ad finished, but the coin reward could not be credited. Please try again.")
                    refreshProfileRewards()
                }
            )
        }
    }

    fun rewardedAdUnavailable(message: String) {
        showToast(message)
    }

    fun claimDailyMission(missionKey: String) {
        if (missionKey.isBlank()) return
        viewModelScope.launch {
            blinkEconomyService.claimDailyMission(missionKey).fold(
                onSuccess = { payload ->
                    val latest = _uiState.value
                    val balance = payload.optLong("balance", latest.blinkCoinBalance)
                    val totalXp = payload.optLong("total_xp", latest.myProfile.totalXp).coerceAtLeast(0L)
                    val xpLevel = payload.optInt("xp_level", latest.myProfile.xpLevel).coerceIn(1, 100)
                    val updatedMe = latest.myProfile.copy(totalXp = totalXp, xpLevel = xpLevel)
                    val coinReward = payload.optInt("coin_reward", 0)
                    val xpReward = payload.optInt("xp_reward", 0)
                    _uiState.value = latest.copy(
                        myProfile = updatedMe,
                        profiles = latest.profiles.map {
                            if (it.id == updatedMe.id || it.username.equals(updatedMe.username, true)) updatedMe else it
                        },
                        viewingProfile = latest.viewingProfile?.let {
                            if (it.id == updatedMe.id || it.username.equals(updatedMe.username, true)) updatedMe else it
                        },
                        blinkCoinBalance = balance
                    )
                    saveLocalProfile(updatedMe)
                    persistProfile(updatedMe)
                    persistExtendedCache()
                    showToast(
                        when {
                            coinReward > 0 && xpReward > 0 -> "+$coinReward Blink Coins • +$xpReward XP"
                            coinReward > 0 -> "+$coinReward Blink Coins"
                            xpReward > 0 -> "+$xpReward XP"
                            else -> "Mission already claimed."
                        }
                    )
                    refreshProfileRewards()
                },
                onFailure = { error ->
                    Log.w(TAG, "Daily mission claim failed", error)
                    showToast(error.message ?: "Couldn't claim this mission.")
                    refreshProfileRewards()
                }
            )
        }
    }

    fun buyBlinkCoins() {
        val policy = _uiState.value.economyPolicy
        if (!policy.cashCheckoutEnabled) {
            val packs = policy.coinPacks.joinToString(" • ") { "₦${it.priceNgn}→${it.coins}" }
            showToast("Blink Coin packs are ready ($packs). Secure cash checkout is not enabled yet.")
            return
        }
        _uiState.value = _uiState.value.copy(isCoinPackPickerOpen = true)
    }

    fun closeCoinPackPicker() {
        _uiState.value = _uiState.value.copy(isCoinPackPickerOpen = false)
    }

    fun startPaystackCoinCheckout(packId: String) {
        if (packId.isBlank()) return
        _uiState.value = _uiState.value.copy(isCoinPackPickerOpen = false)
        viewModelScope.launch {
            blinkEconomyService.initializePaystackCoinCheckout(packId).fold(
                onSuccess = ::openPaystackCheckout,
                onFailure = { error ->
                    Log.w(TAG, "Paystack coin checkout failed to initialize", error)
                    showToast(error.message ?: "Couldn't start secure checkout.")
                }
            )
        }
    }

    fun startPaystackVerificationCheckout() {
        val current = _uiState.value
        if (!current.economyPolicy.cashCheckoutEnabled) {
            showToast("Secure cash checkout is not enabled yet.")
            return
        }
        if (current.myProfile.verificationBadge == VerificationBadge.GOLD) {
            showToast("Gold verification is already active on this account.")
            return
        }
        viewModelScope.launch {
            blinkEconomyService.initializePaystackVerificationCheckout().fold(
                onSuccess = ::openPaystackCheckout,
                onFailure = { error ->
                    Log.w(TAG, "Paystack verification checkout failed to initialize", error)
                    showToast(error.message ?: "Couldn't start secure checkout.")
                }
            )
        }
    }

    private fun openPaystackCheckout(payload: JSONObject) {
        val orderId = payload.optString("order_id").trim()
        val url = payload.optString("authorization_url").trim()
        if (orderId.isBlank() || url.isBlank()) {
            showToast("Paystack did not return a complete checkout session.")
            return
        }

        prefs.edit().putString(KEY_PENDING_PAYSTACK_ORDER, orderId).apply()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
            .onFailure {
                Log.w(TAG, "Unable to open Paystack hosted checkout", it)
                showToast("Couldn't open the secure payment page.")
            }
    }

    fun verifyPendingPaystackCheckout() {
        val orderId = prefs.safeString(KEY_PENDING_PAYSTACK_ORDER, null)?.trim().orEmpty()
        if (orderId.isBlank() || !_uiState.value.isOnline) return

        viewModelScope.launch {
            blinkEconomyService.verifyPaystackCashOrder(orderId).fold(
                onSuccess = { payload ->
                    if (payload.optString("status").equals("fulfilled", ignoreCase = true)) {
                        prefs.edit().remove(KEY_PENDING_PAYSTACK_ORDER).apply()
                        refreshProfileRewards()
                        fetchSupabaseData()
                        showToast("Payment confirmed. Your BLINK purchase is ready.")
                    }
                },
                onFailure = { error ->
                    Log.d(TAG, "Pending Paystack order is not fulfilled yet: ${error.message}")
                }
            )
        }
    }

    fun verifyBlueWithCoins() {
        val current = _uiState.value
        if (current.myProfile.verificationBadge != VerificationBadge.NONE) {
            showToast("BLINK Verified is already active on your account.")
            return
        }
        val remaining = current.economyPolicy.verificationCoinsRemaining(current.blinkCoinBalance)
        if (remaining > 0) {
            showToast("You need $remaining more Blink Coins for BLINK Verified.")
            return
        }

        viewModelScope.launch {
            blinkEconomyService.verifyBlueWithCoins().fold(
                onSuccess = { payload ->
                    val balance = payload.optLong("balance", _uiState.value.blinkCoinBalance)
                    val refreshed = runCatching { profileRepository.fetchById(_uiState.value.myProfile.id) }.getOrNull()
                    val latest = _uiState.value
                    val updatedMe = refreshed ?: latest.myProfile.copy(verificationBadge = VerificationBadge.BLUE)
                    _uiState.value = latest.copy(
                        myProfile = updatedMe,
                        profiles = latest.profiles.map {
                            if (it.id == updatedMe.id || it.username.equals(updatedMe.username, true)) updatedMe else it
                        },
                        viewingProfile = latest.viewingProfile?.let {
                            if (it.id == updatedMe.id || it.username.equals(updatedMe.username, true)) updatedMe else it
                        },
                        blinkCoinBalance = balance,
                        isGetVerifiedOpen = false
                    )
                    saveLocalProfile(updatedMe)
                    persistProfile(updatedMe)
                    persistExtendedCache()
                    showToast("BLINK Verified activated for ${payload.optInt("cost", current.economyPolicy.blueVerificationCoinCost)} coins.")
                },
                onFailure = { error ->
                    Log.w(TAG, "Blink Coin verification purchase failed", error)
                    showToast(error.message ?: "Couldn't activate BLINK Verified.")
                    refreshProfileRewards()
                }
            )
        }
    }

    fun refreshOnboardingSuggestions() {
        fetchSupabaseData()
        viewModelScope.launch {
            val featured = runCatching {
                supabaseService.fetchProfileByUsername(BlinkOnboardingPolicy.PINNED_CREATOR_USERNAME)
            }.getOrNull() ?: return@launch

            if (featured.id == _uiState.value.myProfile.id) return@launch
            val current = _uiState.value
            _uiState.value = current.copy(
                profiles = (listOf(featured) + current.profiles)
                    .distinctBy { it.id.ifBlank { it.username.lowercase() } }
            )
        }
    }

    fun refreshIfStale(maxAgeMillis: Long = 60_000L) {
        val lastSync = lastSuccessfulSyncAt
        val isFresh = lastSync != 0L && SystemClock.elapsedRealtime() - lastSync < maxAgeMillis
        if (!isFresh) fetchSupabaseData()
    }

    private fun reconcileRefreshedFeed(
        previous: List<FeedPost>,
        refreshed: List<FeedPost>
    ): List<FeedPost> {
        if (refreshed.isEmpty()) return previous
        val fresh = refreshed.distinctBy { it.id }
        val freshIds = fresh.asSequence().map { it.id }.toHashSet()
        return fresh + previous.filter { it.id !in freshIds }
    }

    fun fetchSupabaseData(showRefreshIndicator: Boolean = false) {
        // Auth restore, onResume, reconnect and realtime can all request a refresh at once.
        // Coalesce those requests instead of queueing several full Supabase syncs back-to-back.
        if (syncJob?.isActive == true) return

        syncJob = viewModelScope.launch {
            if (_uiState.value.isOnline) runCatching { chatRepository.ackPendingDeliveries() }
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
                    isConversationsLoading = before.conversations.isEmpty(),
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
                    val followingRequest = async {
                        runCatching { postRepository.fetchFollowingFeed(limit = 30) }
                            .onFailure { Log.e(TAG, "Following feed fetch failed", it) }
                    }
                    val postsResult = postsRequest.await()
                    val reelsResult = reelsRequest.await()
                    val followingResult = followingRequest.await()

                    val normalPosts = postsResult.getOrNull()
                        ?.let { reconcileRefreshedFeed(before.posts, it) }
                        ?: before.posts
                    val fetchedReels = reelsResult.getOrNull()
                        ?.let { reconcileRefreshedFeed(before.reels, it) }
                        ?: before.reels
                    val fetchedFollowing = followingResult.getOrNull()
                        ?.let { reconcileRefreshedFeed(before.followingPosts, it) }
                        ?: before.followingPosts
                    val feedSucceeded = postsResult.isSuccess || reelsResult.isSuccess
                    if (feedSucceeded) lastSuccessfulSyncAt = SystemClock.elapsedRealtime()

                    _uiState.value = _uiState.value.copy(
                        posts = normalPosts,
                        followingPosts = fetchedFollowing,
                        reels = fetchedReels,
                        hasMorePosts = postsResult.getOrNull()?.size?.let { it >= 30 } ?: before.hasMorePosts,
                        hasMoreFollowingPosts = followingResult.getOrNull()?.size?.let { it >= 30 } ?: before.hasMoreFollowingPosts,
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
                    val gameLeaderboardRequest = async {
                        runCatching { supabaseService.fetchGameLeaderboard() }
                            .onFailure { Log.e(TAG, "Game ranking fetch failed", it) }
                    }
                    val connectHubRequest = async {
                        runCatching { connectHubRepository.fetchSnapshot() }
                            .onFailure { Log.e(TAG, "Connect Hub fetch failed", it) }
                    }
                    val storiesRequest = async {
                        runCatching { supabaseService.fetchStories() }
                            .onFailure { Log.e(TAG, "Stories fetch failed", it) }
                    }
                    val scheduledPostsRequest = async {
                        runCatching { scheduledPostRepository.fetchMine() }
                            .onFailure { Log.e(TAG, "Scheduled posts fetch failed", it) }
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
                    val conversations = mergeConversationSummaries(
                        summaries = conversationSummaries,
                        local = before.conversations
                    )
                    if (conversationsResult.isSuccess) {
                        cacheWriteMutex.withLock {
                            runCatching { offlineContentStore.replaceConversations(conversations, _uiState.value.myProfile.username) }
                                .onFailure { Log.w(TAG, "Conversation cache update failed", it) }
                        }
                    }

                    val leaderboard = leaderboardRequest.await()
                        .getOrDefault(before.leaderboardUsers)
                    val gameLeaderboard = gameLeaderboardRequest.await()
                        .getOrDefault(before.gameLeaderboardUsers)

                    val connectHub = connectHubRequest.await()
                        .getOrDefault(before.connectHub)

                    val scheduledPosts = scheduledPostsRequest.await()
                        .getOrDefault(before.scheduledPosts)

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
                        gameLeaderboardUsers = gameLeaderboard,
                        connectHub = connectHub,
                        scheduledPosts = scheduledPosts,
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
                        isSyncingContent = false,
                        isConversationsLoading = false
                    )
                    persistExtendedCache()
                }
            }
        }
    }

    fun refreshContent() = fetchSupabaseData(showRefreshIndicator = true)

    fun refreshProgressState() {
        viewModelScope.launch {
            refreshMyProfileFromSupabase(showErrorToast = false)
            refreshProfileRewards()
            runCatching { supabaseService.fetchLeaderboard() }
                .onSuccess { live ->
                    _uiState.value = _uiState.value.copy(leaderboardUsers = live)
                }
                .onFailure { Log.w(TAG, "Progress leaderboard refresh failed", it) }
        }
    }

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

    fun loadMoreFollowingFeed() {
        val state = _uiState.value
        if (!state.isOnline || state.isLoadingMoreFollowingPosts || !state.hasMoreFollowingPosts) return
        val last = state.followingPosts.lastOrNull() ?: return
        if (last.createdAt.isBlank()) return

        _uiState.value = state.copy(isLoadingMoreFollowingPosts = true)
        viewModelScope.launch {
            runCatching {
                postRepository.fetchFollowingFeedPage(
                    beforeCreatedAt = last.createdAt,
                    beforeId = last.id,
                    limit = 30
                )
            }.onSuccess { page ->
                val latest = _uiState.value
                _uiState.value = latest.copy(
                    followingPosts = (latest.followingPosts + page).distinctBy { it.id },
                    isLoadingMoreFollowingPosts = false,
                    hasMoreFollowingPosts = page.size >= 30
                )
            }.onFailure {
                Log.w(TAG, "Load more following feed failed", it)
                _uiState.value = _uiState.value.copy(isLoadingMoreFollowingPosts = false)
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
                    persistExtendedCache()
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
            val synced = connectHubRepository.recordGameSession(gameType, score)
            if (synced) {
                val live = runCatching { supabaseService.fetchGameLeaderboard() }
                    .getOrDefault(_uiState.value.gameLeaderboardUsers)
                _uiState.value = _uiState.value.copy(gameLeaderboardUsers = live)
            }
            synced
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
                _uiState.value = _uiState.value.copy(
                    myProfile = profile,
                    destination = authenticatedDestination(profile)
                )
                saveLocalProfile(profile)
                fetchSupabaseData()
                showToast(
                    if (profile.onboardingCompleted) "✨ Signed in as @${profile.username}"
                    else "Complete your BLINK profile to continue."
                )
                onResult(true, null)
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
                    _uiState.value = _uiState.value.copy(
                        myProfile = profile,
                        destination = authenticatedDestination(profile)
                    )
                    saveLocalProfile(profile)
                    refreshMyProfileFromSupabase(false)
                    fetchSupabaseData()
                    showToast(
                        if (profile.onboardingCompleted) "✨ Welcome back, @${_uiState.value.myProfile.username}"
                        else "Choose your BLINK username to continue."
                    )
                } else showToast(result.errorMessage ?: "Google authentication failed.")
            } catch (e: Exception) { Log.e(TAG, "loginWithGoogle failed", e); showToast(e.message ?: "Google authentication failed.") }
        }
    }

    fun sendPasswordReset(email: String, onResult: (Boolean, String) -> Unit) {
        if (email.isBlank() || !email.contains("@")) { onResult(false, "Please enter a valid university or Gmail address."); return }
        viewModelScope.launch {
            val success = authRepository.recoverPassword(email)
            val msg = if (success) "If an account exists for that email, a password reset link has been sent." else "Could not send the password reset email. Check your connection and try again."
            showToast(msg); onResult(success, msg)
        }
    }

    fun signUp(fullName: String, email: String, password: String) {
        val temporaryUsername = "blink_" + UUID.randomUUID()
            .toString()
            .replace("-", "")
            .take(12)

        signUp(
            fullName = fullName,
            username = temporaryUsername,
            email = email,
            password = password,
            faculty = ""
        )
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
        // Keep signup input in memory only. Mark the user logged in locally only after
        // Supabase Auth and BLINK profile initialization have both succeeded.
        viewModelScope.launch {
            try {
                val result = authRepository.signUpWithEmail(cleanEmail, password, cleanUsername, cleanName, faculty.trim())
                if (result.isSuccess && result.userProfile != null) {
                    val profile = result.userProfile
                    _uiState.value = _uiState.value.copy(
                        myProfile = profile,
                        destination = authenticatedDestination(profile)
                    )
                    saveLocalProfile(profile)
                    showToast(
                        if (profile.onboardingCompleted) "Welcome back, @${profile.username}."
                        else "Account created! Set up your BLINK profile."
                    )
                } else showToast(result.errorMessage ?: "Sign up failed.")
            } catch (e: Exception) { Log.e(TAG, "signUp failed", e); showToast(e.message ?: "Sign up failed.") }
        }
    }

    fun checkOnboardingUsername(
        username: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val clean = BlinkOnboardingPolicy.normalizeUsername(username)
        BlinkOnboardingPolicy.usernameValidationMessage(clean)?.let { validationMessage ->
            onResult(false, validationMessage)
            return
        }

        viewModelScope.launch {
            val existing = runCatching {
                supabaseService.fetchProfileByUsername(clean)
            }.getOrNull()
            val currentId = _uiState.value.myProfile.id
            val available = existing == null || (
                currentId.isNotBlank() &&
                    existing.id.equals(currentId, ignoreCase = true)
                )
            onResult(
                available,
                if (available) "Username available" else "Username already taken."
            )
        }
    }

    fun saveOnboardingUsername(
        username: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        checkOnboardingUsername(username) { available, message ->
            if (!available) {
                onResult(false, message)
                return@checkOnboardingUsername
            }

            val clean = BlinkOnboardingPolicy.normalizeUsername(username)
            viewModelScope.launch {
                val current = _uiState.value.myProfile
                val updated = current.copy(
                    username = clean,
                    onboardingCompleted = false,
                    onboardingStep = maxOf(current.onboardingStep, 1)
                )
                val saved = runCatching { supabaseService.updateProfile(updated) }
                    .getOrDefault(false)
                if (saved) {
                    _uiState.value = _uiState.value.copy(myProfile = updated)
                    saveLocalProfile(updated)
                    onResult(true, null)
                } else {
                    onResult(false, "Unable to save your username. Check your connection and try again.")
                }
            }
        }
    }

    fun saveOnboardingBasics(
        university: String,
        department: String,
        level: String,
        gender: String,
        birthDate: String,
        avatarUrl: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val cleanUniversity = university.trim()
        val cleanDepartment = department.trim()
        val cleanLevel = level.trim()
        val cleanGender = gender.trim()
        val cleanBirthDate = birthDate.trim()

        when {
            cleanUniversity.isBlank() -> {
                onResult(false, "Choose your university.")
                return
            }
            cleanDepartment.isBlank() -> {
                onResult(false, "Choose your department.")
                return
            }
            cleanGender !in BlinkOnboardingCatalog.genders -> {
                onResult(false, "Choose a valid gender option.")
                return
            }
            cleanBirthDate.isNotBlank() &&
                !cleanBirthDate.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")) -> {
                onResult(false, "Use YYYY-MM-DD for your birthday, or leave it blank.")
                return
            }
        }

        viewModelScope.launch {
            val current = _uiState.value.myProfile
            val updated = current.copy(
                university = cleanUniversity,
                department = cleanDepartment,
                academicLevel = cleanLevel,
                gender = cleanGender,
                birthDate = cleanBirthDate,
                avatarUrl = avatarUrl.trim(),
                onboardingCompleted = false,
                onboardingStep = maxOf(current.onboardingStep, 2)
            )

            val privateSaved = runCatching {
                supabaseService.updatePrivateBirthDate(cleanBirthDate)
            }.getOrDefault(false)
            if (!privateSaved) {
                onResult(false, "Unable to save your private birthday setting. Check your connection and try again.")
                return@launch
            }

            val saved = runCatching { supabaseService.updateProfile(updated) }
                .getOrDefault(false)
            if (saved) {
                _uiState.value = _uiState.value.copy(myProfile = updated)
                saveLocalProfile(updated)
                onResult(true, null)
            } else {
                onResult(false, "Unable to save your profile. Check your connection and try again.")
            }
        }
    }

    fun saveOnboardingInterests(
        interests: List<String>,
        onResult: (Boolean, String?) -> Unit
    ) {
        val cleanInterests = interests
            .map { it.trim() }
            .filter { it.isNotBlank() && it in BlinkOnboardingCatalog.allInterests }
            .distinct()

        if (cleanInterests.isEmpty()) {
            onResult(false, "Choose at least one interest.")
            return
        }

        viewModelScope.launch {
            val current = _uiState.value.myProfile
            val updated = current.copy(
                interests = cleanInterests,
                onboardingCompleted = false,
                onboardingStep = maxOf(current.onboardingStep, 3)
            )
            val saved = runCatching { supabaseService.updateProfile(updated) }
                .getOrDefault(false)
            if (saved) {
                _uiState.value = _uiState.value.copy(myProfile = updated)
                saveLocalProfile(updated)
                onResult(true, null)
            } else {
                onResult(false, "Unable to save your interests. Check your connection and try again.")
            }
        }
    }

    fun finishAccountOnboarding(
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val following = runCatching { FollowStateStore.refresh() }
                .getOrDefault(emptySet())

            if (following.size < BlinkOnboardingPolicy.REQUIRED_FOLLOWS) {
                onResult(false, "Follow at least ${BlinkOnboardingPolicy.REQUIRED_FOLLOWS} people to continue.")
                return@launch
            }

            val current = _uiState.value.myProfile
            if (
                current.username.isBlank() ||
                current.university.isBlank() ||
                current.department.isBlank() ||
                current.gender.isBlank() ||
                current.interests.isEmpty()
            ) {
                onResult(false, "Complete the required onboarding details before continuing.")
                return@launch
            }

            val completed = current.copy(
                onboardingCompleted = true,
                onboardingStep = 4
            )
            val saved = runCatching { supabaseService.updateProfile(completed) }
                .getOrDefault(false)

            if (saved) {
                _uiState.value = _uiState.value.copy(
                    myProfile = completed,
                    destination = AppDestination.MAIN
                )
                saveLocalProfile(completed)
                fetchSupabaseData()
                showToast("Welcome to BLINK, @${completed.username} ✨")
                onResult(true, null)
            } else {
                onResult(false, "Unable to finish onboarding. Check your connection and try again.")
            }
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
            if (userId.isNullOrBlank()) { _uiState.value = _uiState.value.copy(destination = authenticatedDestination()); showToast("Profile saved locally. Sign in to sync it with Supabase."); return@launch }
            if (supabaseService.updateProfile(completed)) { _uiState.value = _uiState.value.copy(myProfile = completed, destination = AppDestination.MAIN); showToast("🎉 Profile created and synced with Supabase.") }
            else { _uiState.value = _uiState.value.copy(destination = authenticatedDestination()); showToast("Profile saved locally, but Supabase sync failed.") }
        }
    }

    fun setDestination(destination: AppDestination) { _uiState.value = _uiState.value.copy(destination = destination) }
    fun navigateTo(destination: AppDestination) = setDestination(destination)
    fun selectTab(tab: MainTab) {
        val nextFeedSubTab = if (tab == MainTab.HOME) 0 else _uiState.value.feedSubTab
        _uiState.value = _uiState.value.copy(
            selectedTab = tab,
            feedSubTab = nextFeedSubTab,
            routedReelId = null,
            viewingProfile = null,
            viewingProduct = null,
            isConversationFullScreen = false,
            isConversationsLoading = if (
                tab == MainTab.MESSAGES &&
                _uiState.value.conversations.isEmpty() &&
                _uiState.value.isOnline
            ) true else _uiState.value.isConversationsLoading
        )
        persistUiPreferences()
        if (tab == MainTab.MESSAGES && _uiState.value.conversations.isEmpty() && _uiState.value.isOnline) {
            fetchSupabaseData()
        }
    }
    fun setTab(tab: MainTab) = selectTab(tab)
    fun setFeedSubTab(tab: Int) {
        _uiState.value = _uiState.value.copy(
            feedSubTab = tab.coerceIn(0, 3),
            routedReelId = null
        )
        persistUiPreferences()
    }
    fun toggleDarkMode() {
        _uiState.value = _uiState.value.copy(isDarkMode = !_uiState.value.isDarkMode)
        persistUiPreferences()
    }
    fun openMenu(open: Boolean) { _uiState.value = _uiState.value.copy(isMenuOpen = open) }
    fun openActivity(open: Boolean) { _uiState.value = _uiState.value.copy(isActivityOpen = open) }

    fun openCommentsForPost(postId: String?) {
        if (postId == null) {
            _uiState.value = _uiState.value.copy(
                activeCommentsPostId = null,
                comments = emptyList(),
                isCommentsLoading = false,
                isPostingComment = false
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            activeCommentsPostId = postId,
            comments = emptyList(),
            isCommentsLoading = true
        )
        viewModelScope.launch {
            val result = runCatching { postRepository.fetchComments(postId) }
            if (_uiState.value.activeCommentsPostId != postId) return@launch
            _uiState.value = _uiState.value.copy(
                comments = result.getOrDefault(emptyList()),
                isCommentsLoading = false
            )
            if (result.isFailure) showToast("Couldn't load comments. Please try again.")
        }
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
            val cleanIdentifier = username.trim().removePrefix("@")
            val remoteProfile = if (runCatching { UUID.fromString(cleanIdentifier) }.isSuccess) {
                supabaseService.fetchProfileById(cleanIdentifier)
            } else {
                profileRepository.fetchByUsername(cleanIdentifier)
            }
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
            val json = prefs.safeString("blink_saved_drafts_data", null)
            if (!json.isNullOrBlank()) {
                val drafts = mutableListOf<PostDraft>()
                for (item in json.split(";;;DRAFT_DELIM;;;")) {
                    if (item.isBlank()) continue
                    val parts = item.split(":::FIELD:::")
                    if (parts.size >= 8) drafts.add(PostDraft(id = parts.getOrNull(0) ?: "draft_${System.currentTimeMillis()}", text = parts.getOrNull(1) ?: "", faculty = parts.getOrNull(2) ?: "SIMME", imageUri = parts.getOrNull(3)?.takeIf { it.isNotBlank() }, videoUri = parts.getOrNull(4)?.takeIf { it.isNotBlank() }, isReel = parts.getOrNull(5)?.toBoolean() ?: false, category = parts.getOrNull(6) ?: "Campus Life", audience = parts.getOrNull(7) ?: "Everyone", tags = parts.getOrNull(8)?.split(",")?.filter { it.isNotBlank() } ?: emptyList(), mentions = parts.getOrNull(9)?.split(",")?.filter { it.isNotBlank() } ?: emptyList(), savedAtTimestamp = parts.getOrNull(10)?.toLongOrNull() ?: System.currentTimeMillis(), textStyle = parts.getOrNull(11)?.takeIf { it.isNotBlank() }))
                }
                _uiState.value = _uiState.value.copy(savedDrafts = drafts)
            }
        } catch (e: Exception) { Log.e(TAG, "Failed to load drafts", e) }
    }
    private fun saveDraftsToPrefs(drafts: List<PostDraft>) { try { val serialized = drafts.joinToString(";;;DRAFT_DELIM;;;") { d -> listOf(d.id, d.text, d.faculty, d.imageUri ?: "", d.videoUri ?: "", d.isReel.toString(), d.category, d.audience, d.tags.joinToString(","), d.mentions.joinToString(","), d.savedAtTimestamp.toString(), d.textStyle ?: "").joinToString(":::FIELD:::") }; prefs.edit().putString("blink_saved_drafts_data", serialized).apply() } catch (e: Exception) { Log.e(TAG, "Failed to save drafts", e) } }
    fun saveDraft(draft: PostDraft) { val updated = listOf(draft) + _uiState.value.savedDrafts.filter { it.id != draft.id }; _uiState.value = _uiState.value.copy(savedDrafts = updated); saveDraftsToPrefs(updated); showToast("💾 Draft saved to phone storage") }
    fun deleteDraft(draftId: String) { val updated = _uiState.value.savedDrafts.filter { it.id != draftId }; _uiState.value = _uiState.value.copy(savedDrafts = updated); saveDraftsToPrefs(updated); showToast("🗑️ Draft deleted") }

    fun refreshScheduledPosts() {
        if (!_uiState.value.isOnline) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val remote = scheduledPostRepository.fetchMine()
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(scheduledPosts = remote)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Scheduled posts refresh failed", e)
            }
        }
    }

    fun schedulePost(post: FeedPost, timeMillis: Long, timeFormatted: String) {
        if (_uiState.value.isCreatingPost) return
        if (timeMillis < System.currentTimeMillis() + 60_000L) {
            showToast("Choose a time at least one minute from now.")
            return
        }

        val profile = _uiState.value.myProfile
        val userId = supabaseService.getCurrentUserId()
            ?: profile.id.takeIf { it.isNotBlank() }
        if (userId.isNullOrBlank()) {
            showToast("Please sign in again before scheduling a post.")
            return
        }

        _uiState.value = _uiState.value.copy(isCreatingPost = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uploadedImages = mutableListOf<String>()
                for (input in post.images) {
                    val uploaded = if (input.startsWith("content://")) {
                        uploadPostUri(userId, input, false)
                    } else input
                    if (uploaded.isNullOrBlank()) {
                        throw IllegalStateException("One of the selected images could not be uploaded.")
                    }
                    uploadedImages += uploaded
                }

                val uploadedVideo = post.videoUrl?.let { input ->
                    if (input.startsWith("content://")) uploadPostUri(userId, input, true) else input
                }
                if (!post.videoUrl.isNullOrBlank() && uploadedVideo.isNullOrBlank()) {
                    throw IllegalStateException("The selected video could not be uploaded.")
                }

                val remotePost = post.copy(
                    author = profile.username,
                    authorAvatar = profile.avatarUrl,
                    images = uploadedImages,
                    videoUrl = uploadedVideo,
                    isReel = post.isReel || !uploadedVideo.isNullOrBlank()
                )
                val scheduleId = scheduledPostRepository.schedule(remotePost, timeMillis)
                if (scheduleId.isBlank()) throw IllegalStateException("Supabase did not save the schedule.")
                val latest = scheduledPostRepository.fetchMine()

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        scheduledPosts = latest,
                        isCreatePostOpen = false,
                        isCreatingPost = false
                    )
                    showToast("Post scheduled for $timeFormatted")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Schedule post failed", e)
                withContext(Dispatchers.Main) {
                    showToast(e.message ?: "Couldn't schedule the post.")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isCreatingPost = false)
                }
            }
        }
    }

    fun deleteScheduledPost(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                scheduledPostRepository.cancel(id)
                val latest = scheduledPostRepository.fetchMine()
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(scheduledPosts = latest)
                    showToast("Scheduled post cancelled.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cancel scheduled post failed", e)
                withContext(Dispatchers.Main) { showToast(e.message ?: "Couldn't cancel scheduled post.") }
            }
        }
    }

    fun publishScheduledPostNow(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                scheduledPostRepository.publishNow(id)
                val latest = scheduledPostRepository.fetchMine()
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(scheduledPosts = latest)
                    showToast("Scheduled post published.")
                    fetchSupabaseData()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Publish scheduled post failed", e)
                withContext(Dispatchers.Main) { showToast(e.message ?: "Couldn't publish scheduled post.") }
            }
        }
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
        altText: String? = null,
        textStyle: String? = null
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
                    altText,
                    textStyle
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
        val state = _uiState.value
        val target = (state.posts + state.followingPosts + state.reels + state.discoverPosts)
            .firstOrNull { it.id == postId } ?: return
        val nextLiked = !target.isLiked
        val nextCount = (target.likes + if (nextLiked) 1 else -1).coerceAtLeast(0)
        fun update(items: List<FeedPost>, liked: Boolean, count: Int): List<FeedPost> =
            items.map { if (it.id == postId) it.copy(isLiked = liked, likes = count) else it }

        _uiState.value = state.copy(
            posts = update(state.posts, nextLiked, nextCount),
            followingPosts = update(state.followingPosts, nextLiked, nextCount),
            reels = update(state.reels, nextLiked, nextCount),
            discoverPosts = update(state.discoverPosts, nextLiked, nextCount)
        )
        persistCurrentFeed()

        viewModelScope.launch {
            val success = runCatching {
                postRepository.togglePostLike(postId, nextLiked, nextCount)
            }.getOrDefault(false)
            if (success && nextLiked) {
                if (target.author.isNotBlank()) {
                    supabaseService.recordActivity(
                        target.author,
                        "liked your post",
                        NotificationFilter.LIKES,
                        postId,
                        targetType = "POST"
                    )
                }
            } else if (!success) {
                val latest = _uiState.value
                _uiState.value = latest.copy(
                    posts = update(latest.posts, target.isLiked, target.likes),
                    followingPosts = update(latest.followingPosts, target.isLiked, target.likes),
                    reels = update(latest.reels, target.isLiked, target.likes),
                    discoverPosts = update(latest.discoverPosts, target.isLiked, target.likes)
                )
                persistCurrentFeed()
                showToast("Failed to update like.")
            }
        }
    }
    fun toggleRepost(postId: String) {
        viewModelScope.launch {
            val result = postRepository.togglePostRepost(postId)
            if (result == null) {
                showToast("Couldn't update repost.")
                return@launch
            }
            val (reposted, count) = result
            fun update(items: List<FeedPost>): List<FeedPost> = items.map { post ->
                if (post.id == postId) post.copy(isRepostedByMe = reposted, repostsCount = count) else post
            }
            val state = _uiState.value
            _uiState.value = state.copy(
                posts = update(state.posts),
                followingPosts = update(state.followingPosts),
                reels = update(state.reels),
                discoverPosts = update(state.discoverPosts)
            )
            persistCurrentFeed()
            showToast(if (reposted) "Reposted to your people." else "Repost removed.")
        }
    }
    fun toggleBookmark(postId: String) {
        val state = _uiState.value
        val target = (state.posts + state.followingPosts + state.reels + state.discoverPosts)
            .firstOrNull { it.id == postId } ?: return
        val next = !target.isBookmarked
        fun update(items: List<FeedPost>, value: Boolean): List<FeedPost> =
            items.map { if (it.id == postId) it.copy(isBookmarked = value) else it }

        _uiState.value = state.copy(
            posts = update(state.posts, next),
            followingPosts = update(state.followingPosts, next),
            reels = update(state.reels, next),
            discoverPosts = update(state.discoverPosts, next)
        )
        persistCurrentFeed()

        viewModelScope.launch {
            if (!runCatching { postRepository.togglePostBookmark(postId, next) }.getOrDefault(false)) {
                val latest = _uiState.value
                _uiState.value = latest.copy(
                    posts = update(latest.posts, target.isBookmarked),
                    followingPosts = update(latest.followingPosts, target.isBookmarked),
                    reels = update(latest.reels, target.isBookmarked),
                    discoverPosts = update(latest.discoverPosts, target.isBookmarked)
                )
                persistCurrentFeed()
                showToast("Failed to update bookmark.")
            }
        }
    }
    fun sharePost(postId: String) {
        viewModelScope.launch {
            if (supabaseService.sharePost(postId, "share")) {
                fun update(items: List<FeedPost>): List<FeedPost> = items.map {
                    if (it.id == postId) it.copy(sharesCount = it.sharesCount + 1) else it
                }
                val state = _uiState.value
                _uiState.value = state.copy(
                    posts = update(state.posts),
                    followingPosts = update(state.followingPosts),
                    reels = update(state.reels),
                    discoverPosts = update(state.discoverPosts)
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
        val target = (state.posts + state.followingPosts + state.reels)
            .firstOrNull { it.id == postId } ?: return
        val me = state.myProfile.username.trim()
        if (me.isBlank() || !target.author.equals(me, ignoreCase = true)) {
            _uiState.value = state.copy(activePostOptionsPost = null)
            showToast("You can only delete your own post or reel.")
            return
        }

        val postsBefore = state.posts
        val followingBefore = state.followingPosts
        val reelsBefore = state.reels
        _uiState.value = state.copy(
            posts = postsBefore.filterNot { it.id == postId },
            followingPosts = followingBefore.filterNot { it.id == postId },
            reels = reelsBefore.filterNot { it.id == postId },
            activePostOptionsPost = null
        )
        persistCurrentFeed()

        viewModelScope.launch {
            val deleted = runCatching { supabaseService.deleteFeedPost(postId) }.getOrDefault(false)
            if (deleted) {
                showToast(if (target.videoUrl.isNullOrBlank()) "Post deleted." else "Reel deleted.")
            } else {
                _uiState.value = _uiState.value.copy(
                    posts = postsBefore,
                    followingPosts = followingBefore,
                    reels = reelsBefore
                )
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
                val state = _uiState.value
                _uiState.value = state.copy(
                    mutedUsers = state.mutedUsers + normalized,
                    posts = state.posts.filterNot { it.author.equals(clean, true) },
                    followingPosts = state.followingPosts.filterNot { it.author.equals(clean, true) },
                    reels = state.reels.filterNot { it.author.equals(clean, true) },
                    discoverPosts = state.discoverPosts.filterNot { it.author.equals(clean, true) },
                    stories = state.stories.filterNot {
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
        val state = _uiState.value
        val target = (state.posts + state.followingPosts + state.reels)
            .firstOrNull { it.id == postId } ?: return
        val poll = target.poll ?: return
        if (poll.hasVoted || poll.options.any { it.isVotedByMe }) {
            showToast("You already voted in this poll.")
            return
        }

        fun update(items: List<FeedPost>): List<FeedPost> = items.map { item ->
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

        val updatedPosts = update(state.posts)
        val updatedFollowing = update(state.followingPosts)
        val updatedReels = update(state.reels)
        _uiState.value = state.copy(
            posts = updatedPosts,
            followingPosts = updatedFollowing,
            reels = updatedReels
        )
        persistCurrentFeed()

        val pollState = (updatedPosts + updatedFollowing + updatedReels)
            .firstOrNull { it.id == postId }?.poll ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val success = runCatching {
                postRepository.votePoll(postId, optionId, pollState)
            }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                if (success) {
                    showToast("🗳️ Vote recorded.")
                } else {
                    _uiState.value = _uiState.value.copy(
                        posts = state.posts,
                        followingPosts = state.followingPosts,
                        reels = state.reels
                    )
                    persistCurrentFeed()
                    showToast("Vote wasn't saved. Please try again.")
                }
            }
        }
    }
    fun addComment(postId: String, text: String, parentCommentId: String? = null) {
        val cleanText = text.trim()
        if (cleanText.isBlank() || _uiState.value.isPostingComment) return
        if (cleanText.length > 2_000) {
            showToast("Comments can be up to 2,000 characters.")
            return
        }
        _uiState.value = _uiState.value.copy(isPostingComment = true)
        viewModelScope.launch {
            val posted = postRepository.addComment(postId, cleanText, parentCommentId)
            if (!posted) {
                _uiState.value = _uiState.value.copy(isPostingComment = false)
                showToast("Failed to post comment.")
                return@launch
            }

            val refreshed = runCatching { postRepository.fetchComments(postId) }.getOrNull()
            val state = _uiState.value
            _uiState.value = state.copy(
                comments = if (state.activeCommentsPostId == postId && refreshed != null) {
                    refreshed
                } else {
                    state.comments
                },
                posts = state.posts.map {
                    if (it.id == postId) it.copy(commentsCount = it.commentsCount + 1) else it
                },
                followingPosts = state.followingPosts.map {
                    if (it.id == postId) it.copy(commentsCount = it.commentsCount + 1) else it
                },
                reels = state.reels.map {
                    if (it.id == postId) it.copy(commentsCount = it.commentsCount + 1) else it
                },
                isPostingComment = false
            )
            showToast(if (parentCommentId == null) "💬 Comment posted." else "↩️ Reply posted.")
        }
    }

    fun toggleCommentLike(commentId: String) {
        val before = _uiState.value.comments
        var found = false
        var next = false
        var targetUsername = ""
        var targetText = ""
        var targetPostId = _uiState.value.activeCommentsPostId.orEmpty()

        val updated = before.map { comment ->
            if (comment.id == commentId) {
                found = true
                next = !comment.isLiked
                targetUsername = comment.user
                targetText = comment.text
                targetPostId = comment.postId.ifBlank { targetPostId }
                comment.copy(
                    isLiked = next,
                    likes = (comment.likes + if (next) 1 else -1).coerceAtLeast(0)
                )
            } else {
                val updatedReplies = comment.replies.map { reply ->
                    if (reply.id == commentId) {
                        found = true
                        next = !reply.isLiked
                        targetUsername = reply.user
                        targetText = reply.text
                        targetPostId = reply.postId.ifBlank { comment.postId.ifBlank { targetPostId } }
                        reply.copy(
                            isLiked = next,
                            likes = (reply.likes + if (next) 1 else -1).coerceAtLeast(0)
                        )
                    } else {
                        reply
                    }
                }
                if (updatedReplies != comment.replies) comment.copy(replies = updatedReplies) else comment
            }
        }
        if (!found) return

        _uiState.value = _uiState.value.copy(comments = updated)
        viewModelScope.launch {
            if (runCatching { postRepository.toggleCommentLike(commentId, next) }.getOrDefault(false)) {
                if (next && targetUsername.isNotBlank() && !isMe(targetUsername)) {
                    supabaseService.recordActivity(
                        recipientUsername = targetUsername,
                        action = "liked your comment",
                        category = NotificationFilter.LIKES,
                        targetPostId = targetPostId.takeIf { it.isNotBlank() },
                        previewText = targetText,
                        targetType = "POST"
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(comments = before)
                showToast("Failed to update comment like.")
            }
        }
    }

    fun reportComment(commentId: String, reason: String) {
        if (commentId.isBlank() || reason.isBlank()) return
        viewModelScope.launch {
            if (postRepository.reportComment(commentId, reason)) {
                showToast("🚨 Comment reported for moderation.")
            } else {
                showToast("Couldn't submit the report. Please try again.")
            }
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
                selectedTab = MainTab.MESSAGES,
                viewingProfile = null,
                viewingProduct = null,
                conversations = state.conversations.map {
                    if (it.partnerUsername.equals(clean, true)) it.copy(unreadCount = 0) else it
                },
                activeConversationPartner = clean,
                isConversationFullScreen = false
            )
            persistUiPreferences()
            viewModelScope.launch {
                if (_uiState.value.isOnline && existing.id.isNotBlank() && !existing.id.startsWith("local_")) {
                    loadConversationHistory(existing.id, clean, older = false)
                }
            }
            return
        }

        _uiState.value = state.copy(
            selectedTab = MainTab.MESSAGES,
            viewingProfile = null,
            viewingProduct = null,
            activeConversationPartner = clean,
            isConversationFullScreen = false
        )
        persistUiPreferences()

        viewModelScope.launch {
            val profile = supabaseService.fetchProfileByUsername(clean)
            if (profile == null) {
                if (_uiState.value.activeConversationPartner.equals(clean, true)) {
                    _uiState.value = _uiState.value.copy(
                        activeConversationPartner = null,
                        isConversationFullScreen = false
                    )
                }
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
                selectedTab = MainTab.MESSAGES,
                viewingProfile = null,
                viewingProduct = null,
                conversations = listOf(convo) + latest.conversations.filterNot {
                    it.partnerUsername.equals(profile.username, true)
                },
                activeConversationPartner = profile.username,
                isConversationFullScreen = false
            )
            persistUiPreferences()
            persistConversations()
            // CHAT_OPEN_HISTORY_EDGE_V1: every chat entry path resolves an existing server chat
            // and hydrates its latest 50 messages, even if summaries have not synced yet.
            if (_uiState.value.isOnline) {
                loadConversationHistory(convo.id, profile.username, older = false)
            }
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

    private suspend fun loadConversationHistory(
        conversationId: String,
        partnerUsername: String,
        older: Boolean
    ) {
        if (conversationId.isBlank()) return

        var resolvedConversationId = conversationId
        if (resolvedConversationId.startsWith("local_") && _uiState.value.isOnline) {
            val server = runCatching { chatRepository.fetchConversations() }.getOrDefault(emptyList())
                .firstOrNull { it.partnerUsername.equals(partnerUsername, true) }
            if (server != null && server.id.isNotBlank() && !server.id.startsWith("local_")) {
                withContext(Dispatchers.Main) {
                    val latest = _uiState.value
                    val conversations = latest.conversations.toMutableList()
                    val index = conversations.indexOfFirst { it.partnerUsername.equals(partnerUsername, true) }
                    if (index >= 0) {
                        val local = conversations[index]
                        conversations[index] = server.copy(
                            messages = local.messages.toMutableList(),
                            isMuted = local.isMuted
                        )
                        val historyMap = latest.messageHistoryHasMore.toMutableMap().apply {
                            remove(local.id)
                            put(server.id, get(server.id) ?: true)
                        }
                        _uiState.value = latest.copy(
                            conversations = conversations,
                            messageHistoryHasMore = historyMap
                        )
                    }
                }
                persistConversationsNow()
                resolvedConversationId = server.id
            } else {
                return
            }
        }
        if (resolvedConversationId.startsWith("local_")) return

        val state = _uiState.value
        val current = state.conversations.firstOrNull {
            it.id == resolvedConversationId || it.partnerUsername.equals(partnerUsername, true)
        } ?: return
        if (older && state.loadingOlderConversationId == resolvedConversationId) return
        if (!older && state.loadingInitialConversationId == resolvedConversationId) return

        val oldest = current.messages
            .filter { it.rawTimestamp.isNotBlank() && !it.id.startsWith("temp_") }
            .minWithOrNull(compareBy<ChatMessage> { it.rawTimestamp }.thenBy { it.id })
        val beforeAt = if (older) oldest?.rawTimestamp else null
        val beforeId = if (older) oldest?.id else null

        _uiState.value = if (older) {
            state.copy(loadingOlderConversationId = resolvedConversationId)
        } else {
            state.copy(loadingInitialConversationId = resolvedConversationId)
        }

        try {
            val page = chatRepository.fetchMessagePage(
                conversationId = resolvedConversationId,
                beforeCreatedAt = beforeAt,
                beforeId = beforeId,
                limit = 50
            )

            withContext(Dispatchers.Main) {
                val latest = _uiState.value
                val conversations = latest.conversations.toMutableList()
                val index = conversations.indexOfFirst {
                    it.id == resolvedConversationId || it.partnerUsername.equals(partnerUsername, true)
                }
                if (index >= 0) {
                    val old = conversations[index]
                    val merged = LinkedHashMap<String, ChatMessage>()
                    old.messages.forEach { message ->
                        val key = message.id.ifBlank { "local:${message.rawTimestamp}:${message.text}" }
                        merged[key] = message
                    }
                    // Server rows win for matching IDs so delivery/read status is refreshed.
                    page.forEach { message ->
                        val key = message.id.ifBlank { "server:${message.rawTimestamp}:${message.text}" }
                        merged[key] = message.copy(conversationId = resolvedConversationId)
                    }
                    val messages = merged.values
                        .sortedWith(compareBy<ChatMessage> { it.rawTimestamp.ifBlank { "9999" } }.thenBy { it.id })
                        .toMutableList()
                    val newest = messages.lastOrNull()
                    conversations[index] = old.copy(
                        id = resolvedConversationId,
                        messages = messages,
                        lastMessage = newest?.text ?: old.lastMessage,
                        lastMessageTime = newest?.timestamp ?: old.lastMessageTime,
                        lastMessageRawTime = newest?.rawTimestamp ?: old.lastMessageRawTime
                    )
                    _uiState.value = latest.copy(
                        conversations = conversations,
                        messageHistoryHasMore = latest.messageHistoryHasMore + (resolvedConversationId to (page.size >= 50))
                    )
                }
            }

            // Make the merged page durable before acknowledging it to the server.
            persistConversationsNow()
            runCatching { chatRepository.ackPendingDeliveries() }
            if (!older) runCatching { chatRepository.markConversationRead(partnerUsername) }
        } catch (e: Exception) {
            Log.w(TAG, "Message history hydration failed for @$partnerUsername", e)
        } finally {
            val latest = _uiState.value
            _uiState.value = if (older) {
                latest.copy(loadingOlderConversationId = null)
            } else {
                latest.copy(loadingInitialConversationId = null)
            }
        }
    }

    fun setConversationFullScreen(fullScreen: Boolean) {
        val state = _uiState.value
        if (state.activeConversationPartner == null && fullScreen) return
        _uiState.value = state.copy(isConversationFullScreen = fullScreen)
    }

    fun closeConversation() { _uiState.value = _uiState.value.copy(activeConversationPartner = null, isConversationFullScreen = false) }

    fun sendMessage(
        partnerUsername: String,
        text: String,
        isFromMe: Boolean = true,
        replyToMessageId: String? = null
    ) {
        val cleanText = text.trim()
        val cleanPartner = partnerUsername.trim().removePrefix("@")
        if (cleanText.isBlank() || cleanPartner.isBlank()) return

        val uid = supabaseService.getCurrentUserId() ?: "local_user"
        val currentUsername = supabaseService.getCurrentUsername()
            ?: _uiState.value.myProfile.username.ifBlank { "you" }
        val tempId = "temp_${UUID.randomUUID()}"
        val currentConversationId = _uiState.value.conversations
            .firstOrNull { it.partnerUsername.equals(cleanPartner, true) }
            ?.id
            ?.takeUnless { it.startsWith("local_") }
        val optimistic = ChatMessage(
            id = tempId,
            conversationId = currentConversationId,
            senderId = uid,
            senderUsername = currentUsername,
            receiverUsername = cleanPartner,
            text = cleanText,
            rawTimestamp = java.time.Instant.now().toString(),
            timestamp = "Sending...",
            isFromMe = isFromMe,
            isRead = false,
            status = MessageStatus.SENDING,
            replyToMessageId = replyToMessageId
        )
        if (!replyToMessageId.isNullOrBlank()) pendingReplyTargets[tempId] = replyToMessageId
        appendMessageToState(cleanPartner, optimistic)

        viewModelScope.launch(Dispatchers.IO) {
            offlineContentStore.enqueueMessage(tempId, cleanPartner, cleanText)
            if (!activeOutboxIds.add(tempId)) return@launch
            try {
                chatRepository.sendMessage(cleanPartner, cleanText, replyToMessageId).fold(
                    onSuccess = { serverMsg ->
                        withContext(Dispatchers.Main) {
                            replaceMessageInState(
                                cleanPartner,
                                tempId,
                                serverMsg.copy(receiverUsername = cleanPartner, status = MessageStatus.SENT)
                            )
                        }
                        // Confirmed server identity must reach Room before the outbox row is removed.
                        persistConversationsNow()
                        offlineContentStore.deleteOutbox(tempId)
                        pendingReplyTargets.remove(tempId)
                        chatRepository.triggerMessagePushBestEffort(serverMsg.id)
                    },
                    onFailure = { error ->
                        withContext(Dispatchers.Main) {
                            updateMessageStatusInState(cleanPartner, tempId, MessageStatus.FAILED)
                            showToast(error.message ?: "Message couldn't be sent. It will retry when you're online.")
                        }
                    }
                )
            } finally {
                activeOutboxIds.remove(tempId)
            }
        }
    }

    fun sendVideoMessage(partnerUsername: String, uri: Uri) {
        val cleanPartner = partnerUsername.trim().removePrefix("@")
        if (cleanPartner.isBlank()) return
        val tempId = "temp_video_${UUID.randomUUID()}"
        val uid = supabaseService.getCurrentUserId() ?: "local_user"
        val existingConversationId = _uiState.value.conversations
            .firstOrNull { it.partnerUsername.equals(cleanPartner, true) }
            ?.id
            ?.takeUnless { it.startsWith("local_") }
        appendMessageToState(
            cleanPartner,
            ChatMessage(
                id = tempId,
                conversationId = existingConversationId,
                senderId = uid,
                receiverUsername = cleanPartner,
                text = "Video",
                rawTimestamp = java.time.Instant.now().toString(),
                timestamp = "Sending...",
                isFromMe = true,
                isRead = false,
                status = MessageStatus.SENDING
            )
        )
        viewModelScope.launch(Dispatchers.IO) {
            MessageMediaService.sendVideoMessage(appContext, cleanPartner, uri).fold(
                onSuccess = { serverMsg ->
                    withContext(Dispatchers.Main) {
                        replaceMessageInState(cleanPartner, tempId, serverMsg.copy(status = MessageStatus.SENT))
                    }
                    // Confirmed media messages receive the same durable Room ordering as text.
                    persistConversationsNow()
                    withContext(Dispatchers.Main) { fetchSupabaseData() }
                },
                onFailure = {
                    withContext(Dispatchers.Main) {
                        updateMessageStatusInState(cleanPartner, tempId, MessageStatus.FAILED)
                        showToast("Failed to send video. Tap the message to retry.")
                    }
                }
            )
        }
    }

    fun retrySendMessage(partnerUsername: String, failedMessage: ChatMessage) {
        if (failedMessage.text.isBlank()) return
        updateMessageStatusInState(partnerUsername, failedMessage.id, MessageStatus.SENDING)
        persistConversations()
        viewModelScope.launch(Dispatchers.IO) {
            val pending = offlineContentStore.pendingOutbox(100).firstOrNull { it.localId == failedMessage.id }
            failedMessage.replyToMessageId?.takeIf { it.isNotBlank() }?.let { pendingReplyTargets[failedMessage.id] = it }
            if (pending == null) {
                offlineContentStore.enqueueMessage(failedMessage.id, partnerUsername.trim(), failedMessage.text.trim())
            } else {
                offlineContentStore.resetOutbox(failedMessage.id)
            }
            drainMessageOutbox()
        }
    }

    private suspend fun drainMessageOutbox() {
        if (supabaseService.getCurrentUserId().isNullOrBlank() || !_uiState.value.isOnline) return
        val pending = messageOutboxMutex.withLock { offlineContentStore.pendingOutbox(100) }
        if (pending.isEmpty()) return

        for (item in pending) {
            if (!activeOutboxIds.add(item.localId)) continue
            try {
                chatRepository.sendMessage(
                    item.receiverUsername,
                    item.content,
                    pendingReplyTargets[item.localId]
                ).fold(
                    onSuccess = { serverMsg ->
                        withContext(Dispatchers.Main) {
                            replaceMessageInState(
                                item.receiverUsername,
                                item.localId,
                                serverMsg.copy(receiverUsername = item.receiverUsername, status = MessageStatus.SENT)
                            )
                        }
                        persistConversationsNow()
                        offlineContentStore.deleteOutbox(item.localId)
                        pendingReplyTargets.remove(item.localId)
                        chatRepository.triggerMessagePushBestEffort(serverMsg.id)
                    },
                    onFailure = { error ->
                        offlineContentStore.markOutboxFailure(item, error.message ?: "Send failed")
                        withContext(Dispatchers.Main) {
                            updateMessageStatusInState(item.receiverUsername, item.localId, MessageStatus.FAILED)
                        }
                    }
                )
            } finally {
                activeOutboxIds.remove(item.localId)
            }
        }
    }

    private fun mutateChatMessage(
        partnerUsername: String,
        messageId: String,
        transform: (ChatMessage) -> ChatMessage
    ) {
        val state = _uiState.value
        val conversations = state.conversations.map { conversation ->
            if (!conversation.partnerUsername.equals(partnerUsername, true)) conversation
            else conversation.copy(
                messages = conversation.messages.map { message ->
                    if (message.id == messageId) transform(message) else message
                }.toMutableList()
            )
        }
        _uiState.value = state.copy(conversations = conversations)
        persistConversations()
    }

    fun toggleMessageReaction(partnerUsername: String, message: ChatMessage, emoji: String) {
        if (emoji.isBlank() || message.id.startsWith("temp_")) return
        val wasMine = emoji in message.myReactions
        val active = !wasMine
        val before = message
        mutateChatMessage(partnerUsername, message.id) { current ->
            val counts = current.reactionCounts.toMutableMap()
            val next = (counts[emoji] ?: 0) + if (active) 1 else -1
            if (next <= 0) counts.remove(emoji) else counts[emoji] = next
            current.copy(
                reactionCounts = counts,
                myReactions = if (active) current.myReactions + emoji else current.myReactions - emoji
            )
        }
        viewModelScope.launch {
            if (!chatRepository.setMessageReaction(message.id, emoji, active)) {
                mutateChatMessage(partnerUsername, message.id) { before }
                showToast("Couldn't update reaction.")
            }
        }
    }

    fun editChatMessage(partnerUsername: String, message: ChatMessage, newText: String) {
        val clean = newText.trim()
        if (!message.isFromMe || message.id.startsWith("temp_") || clean.isBlank()) return
        val before = message
        mutateChatMessage(partnerUsername, message.id) {
            it.copy(text = clean, editedAt = java.time.Instant.now().toString())
        }
        viewModelScope.launch {
            if (!chatRepository.editMessage(message.id, clean)) {
                mutateChatMessage(partnerUsername, message.id) { before }
                showToast("Couldn't edit message.")
            }
        }
    }

    fun deleteChatMessageForMe(partnerUsername: String, message: ChatMessage) {
        val state = _uiState.value
        val updated = state.conversations.map { conversation ->
            if (!conversation.partnerUsername.equals(partnerUsername, true)) conversation
            else conversation.copy(messages = conversation.messages.filterNot { it.id == message.id }.toMutableList())
        }
        _uiState.value = state.copy(conversations = updated)
        persistConversations()
        viewModelScope.launch(Dispatchers.IO) {
            if (message.id.startsWith("temp_")) {
                offlineContentStore.deleteOutbox(message.id)
                pendingReplyTargets.remove(message.id)
                return@launch
            }
            if (!chatRepository.hideMessageForMe(message.id)) {
                withContext(Dispatchers.Main) {
                    // Restore only the affected message into the newest state. Never restore
                    // an old whole-list snapshot because messages may have arrived meanwhile.
                    val latest = _uiState.value
                    val restored = latest.conversations.map { conversation ->
                        if (!conversation.partnerUsername.equals(partnerUsername, true) ||
                            conversation.messages.any { it.id == message.id }
                        ) {
                            conversation
                        } else {
                            val messages = (conversation.messages + message)
                                .distinctBy { it.id }
                                .sortedBy { it.rawTimestamp.ifBlank { it.timestamp } }
                                .toMutableList()
                            conversation.copy(messages = messages)
                        }
                    }
                    _uiState.value = latest.copy(conversations = restored)
                    persistConversations()
                    showToast("Couldn't delete message for you.")
                }
            }
        }
    }

    fun deleteChatMessageForEveryone(partnerUsername: String, message: ChatMessage) {
        if (!message.isFromMe || message.id.startsWith("temp_")) return
        val before = message
        mutateChatMessage(partnerUsername, message.id) {
            it.copy(
                text = "",
                attachedImageUrl = null,
                attachedVideoUrl = null,
                deletedForEveryone = true
            )
        }
        viewModelScope.launch {
            if (!chatRepository.deleteMessageForEveryone(message.id)) {
                mutateChatMessage(partnerUsername, message.id) { before }
                showToast("Couldn't delete message for everyone.")
            }
        }
    }

    fun toggleChatMessageStar(partnerUsername: String, message: ChatMessage) {
        if (message.id.startsWith("temp_")) return
        val before = message
        val next = !message.isStarred
        mutateChatMessage(partnerUsername, message.id) { it.copy(isStarred = next) }
        viewModelScope.launch {
            if (!chatRepository.setMessageStarred(message.id, next)) {
                mutateChatMessage(partnerUsername, message.id) { before }
                showToast("Couldn't update starred message.")
            }
        }
    }

    fun toggleChatMessagePin(partnerUsername: String, message: ChatMessage) {
        if (message.id.startsWith("temp_")) return
        val before = message
        val next = !message.isPinned
        mutateChatMessage(partnerUsername, message.id) { it.copy(isPinned = next) }
        viewModelScope.launch {
            if (!chatRepository.setMessagePinned(message.id, next)) {
                mutateChatMessage(partnerUsername, message.id) { before }
                showToast("Couldn't update pinned message.")
            }
        }
    }

    fun reportChatMessage(message: ChatMessage, reason: String) {
        if (message.id.startsWith("temp_")) return
        viewModelScope.launch {
            if (chatRepository.reportMessage(message.id, reason)) showToast("Message reported.")
            else showToast("Couldn't report message.")
        }
    }

    fun clearConversationForMe(conversation: ChatConversation) {
        val state = _uiState.value
        val originalIndex = state.conversations.indexOfFirst { it.id == conversation.id }
        _uiState.value = state.copy(
            conversations = state.conversations.filterNot { it.id == conversation.id },
            activeConversationPartner = if (state.activeConversationPartner.equals(conversation.partnerUsername, true)) null else state.activeConversationPartner,
            isConversationFullScreen = false
        )
        persistConversations()
        if (conversation.id.startsWith("local_")) return
        viewModelScope.launch {
            if (!chatRepository.clearConversationForMe(conversation.id)) {
                // Roll back only this conversation into the newest state. This preserves any
                // conversations/messages that appeared while the server request was running.
                val latest = _uiState.value
                if (latest.conversations.none { it.id == conversation.id }) {
                    val restored = latest.conversations.toMutableList()
                    restored.add(originalIndex.coerceIn(0, restored.size), conversation)
                    _uiState.value = latest.copy(conversations = restored)
                    persistConversations()
                }
                showToast("Couldn't clear chat.")
            }
        }
    }

    fun setConversationMuted(conversation: ChatConversation, muted: Boolean) {
        val before = conversation.isMuted
        val state = _uiState.value
        _uiState.value = state.copy(conversations = state.conversations.map {
            if (it.id == conversation.id) it.copy(isMuted = muted) else it
        })
        persistConversations()
        if (conversation.id.startsWith("local_")) return
        viewModelScope.launch {
            if (!chatRepository.setConversationMuted(conversation.id, muted)) {
                val latest = _uiState.value
                _uiState.value = latest.copy(conversations = latest.conversations.map {
                    if (it.id == conversation.id) it.copy(isMuted = before) else it
                })
                persistConversations()
                showToast("Couldn't update chat notifications.")
            }
        }
    }

    fun reportConversation(conversation: ChatConversation, reason: String) {
        if (conversation.id.startsWith("local_")) return
        viewModelScope.launch {
            if (chatRepository.reportConversation(conversation.id, reason)) showToast("Conversation reported.")
            else showToast("Couldn't report conversation.")
        }
    }

    private suspend fun reconcileConversationSummary(partnerUsername: String) {
        val server = runCatching { chatRepository.fetchConversations() }.getOrDefault(emptyList())
            .firstOrNull { it.partnerUsername.equals(partnerUsername, true) }
            ?: return
        withContext(Dispatchers.Main) {
            val latest = _uiState.value
            val index = latest.conversations.indexOfFirst {
                it.partnerUsername.equals(partnerUsername, true)
            }
            val conversations = latest.conversations.toMutableList()
            if (index >= 0) {
                val local = conversations[index]
                conversations[index] = server.copy(messages = local.messages.toMutableList())
            } else {
                conversations.add(0, server)
            }
            _uiState.value = latest.copy(conversations = conversations)
            persistConversations()
        }
    }

    private fun handleRealtimeEvent(event: RealtimeEvent) {
        when (event) {
            is RealtimeEvent.MessageEvent -> handleIncomingRealtimeMessage(event.message)
            is RealtimeEvent.ConversationEvent -> viewModelScope.launch {
                runCatching { chatRepository.fetchConversations() }
                    .onSuccess { summaries ->
                        // Read state after the network call completes. A message may have been
                        // sent/received while this request was in flight, so a pre-request
                        // snapshot must never be written back over newer chat state.
                        val latest = _uiState.value
                        _uiState.value = latest.copy(
                            conversations = mergeConversationSummaries(
                                summaries = summaries,
                                local = latest.conversations
                            )
                        )
                        persistConversations()
                    }
                    .onFailure { Log.w(TAG, "Conversation summary refresh failed", it) }
            }
            is RealtimeEvent.ActivityEvent -> handleIncomingRealtimeActivity(event)
            is RealtimeEvent.NotificationEvent -> handleIncomingRealtimeNotification(event)
            is RealtimeEvent.IncomingCallEvent -> viewModelScope.launch {
                val currentUserId = _uiState.value.myProfile.id
                if (
                    currentUserId.isBlank() ||
                    event.calleeId != currentUserId ||
                    event.callId.isBlank() ||
                    event.callerId.isBlank()
                ) return@launch

                val peer = CallRepository().fetchPeer(event.callerId).getOrNull()
                IncomingCallNotification.showIncoming(
                    context = appContext,
                    callId = event.callId,
                    callType = CallType.fromWire(event.callType),
                    peerId = event.callerId,
                    peerUsername = peer?.username.orEmpty(),
                    peerName = peer?.name.orEmpty().ifBlank { "Blink user" },
                    peerAvatar = peer?.avatar.orEmpty(),
                    conversationId = event.conversationId
                )
            }
            is RealtimeEvent.ConnectHubEvent -> refreshConnectHub()
            is RealtimeEvent.FeedPostEvent -> viewModelScope.launch {
                val current = _uiState.value
                if (event.eventType.equals("DELETE", ignoreCase = true)) {
                    _uiState.value = current.copy(
                        posts = current.posts.filterNot { it.id == event.postId },
                        followingPosts = current.followingPosts.filterNot { it.id == event.postId },
                        reels = current.reels.filterNot { it.id == event.postId }
                    )
                    persistCurrentFeed()
                    return@launch
                }

                val freshRequest = async { postRepository.fetchFeed() }
                val followingRequest = async { postRepository.fetchFollowingFeed(limit = 30) }
                val fresh = freshRequest.await()
                val following = followingRequest.await()
                val latest = _uiState.value
                val muted = latest.mutedUsers

                if (fresh.isNotEmpty() || following.isNotEmpty()) {
                    val freshPosts = fresh.filter {
                        it.videoUrl.isNullOrBlank() &&
                            !it.isReel &&
                            it.author.lowercase() !in muted
                    }
                    val freshReels = fresh.filter {
                        !it.videoUrl.isNullOrBlank() &&
                            it.author.lowercase() !in muted
                    }
                    _uiState.value = latest.copy(
                        posts = if (freshPosts.isNotEmpty()) reconcileRefreshedFeed(latest.posts, freshPosts) else latest.posts,
                        followingPosts = if (following.isNotEmpty()) reconcileRefreshedFeed(latest.followingPosts, following) else latest.followingPosts,
                        reels = if (freshReels.isNotEmpty()) reconcileRefreshedFeed(latest.reels, freshReels) else latest.reels
                    )
                    persistCurrentFeed()
                }
            }
        }
    }

    private fun handleIncomingRealtimeActivity(event: RealtimeEvent.ActivityEvent) {
        if (!event.eventType.equals("INSERT", ignoreCase = true) || event.isRead || event.id.isBlank()) return

        val state = _uiState.value
        val myId = state.myProfile.id
        if (myId.isNotBlank() && event.recipientId.isNotBlank() && event.recipientId != myId) return

        val normalizedType = event.activityType.trim().uppercase()
        if (normalizedType in setOf(
                "LIKE", "LIKES", "COMMENT", "COMMENTS", "REPLY", "MENTION",
                "FOLLOW", "REPOST"
            )
        ) {
            // These are mirrored by authoritative public.notifications rows in production.
            // Ignore the legacy realtime copy so one event can create only one banner/timeline row.
            return
        }
        val category = when (normalizedType) {
            "LIKE", "LIKES", "BOOKMARK", "SAVE" -> NotificationFilter.LIKES
            "COMMENT", "COMMENTS", "REPLY", "MENTION" -> NotificationFilter.COMMENTS
            "MARKET", "ORDER", "MARKET_ORDER" -> NotificationFilter.MARKET
            else -> NotificationFilter.ALL
        }
        val entityType = event.entityType.trim()
        val postId = event.entityId.takeIf {
            it.isNotBlank() && entityType.equals("post", ignoreCase = true)
        }
        val marketId = event.entityId.takeIf {
            it.isNotBlank() && entityType.equals("market", ignoreCase = true)
        }
        val action = event.message.ifBlank {
            event.activityType.replace('_', ' ').lowercase().ifBlank { "New activity" }
        }
        val activity = ActivityItem(
            id = event.id,
            user = event.actorId,
            avatar = "",
            action = action,
            time = "Just now",
            rawTimestamp = event.createdAt,
            isUnread = true,
            category = category,
            targetPostId = postId,
            targetMarketId = marketId,
            targetType = entityType.takeIf { it.isNotBlank() }
        )

        val semanticKey = notificationSemanticKey(activity)
        if (state.activities.none {
                it.id == activity.id || notificationSemanticKey(it) == semanticKey
            }) {
            _uiState.value = state.copy(activities = listOf(activity) + state.activities)
            persistExtendedCache()
        }

        val wireType = when (normalizedType) {
            "LIKES" -> "like"
            "COMMENTS" -> "comment"
            "ALL" -> "social"
            else -> normalizedType.lowercase().ifBlank { "social" }
        }
        val notificationType = BlinkNotificationType.fromWire(wireType)
        if (!NotificationPreferenceStore.isAllowed(appContext, notificationType)) return

        val actorProfile = state.profiles.firstOrNull { it.id == event.actorId }
        val actorName = actorProfile?.fullName?.takeIf { it.isNotBlank() }
            ?: actorProfile?.username?.takeIf { it.isNotBlank() }
            ?: ""
        val actorUsername = actorProfile?.username.orEmpty()
        val title = if (actorName.isNotBlank()) {
            "$actorName $action"
        } else {
            action.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        val destination = when {
            postId != null -> BlinkInAppNotificationDestination.POST
            marketId != null -> BlinkInAppNotificationDestination.MARKET
            event.actorId.isNotBlank() -> BlinkInAppNotificationDestination.PROFILE
            else -> BlinkInAppNotificationDestination.NOTIFICATIONS
        }
        val targetKey = event.entityId.ifBlank { event.id }

        BlinkInAppNotificationCenter.publish(
            BlinkInAppNotification(
                key = "social:" + wireType + ":" + event.actorId + ":" + targetKey,
                title = title,
                body = actorUsername.takeIf { it.isNotBlank() }?.let { "@$it" }.orEmpty(),
                destination = destination,
                senderId = event.actorId,
                senderUsername = actorUsername,
                senderName = actorName,
                senderAvatar = actorProfile?.avatarUrl.orEmpty(),
                postId = postId,
                marketId = marketId,
                targetType = entityType.takeIf { it.isNotBlank() },
                targetId = event.entityId.takeIf { it.isNotBlank() },
                activity = activity
            )
        )
    }

    private fun notificationSemanticKey(item: ActivityItem): String = listOf(
        item.user.trim().lowercase(),
        item.action.trim().lowercase(),
        item.targetPostId.orEmpty(),
        item.targetMarketId.orEmpty(),
        item.targetType.orEmpty().uppercase(),
        item.rawTimestamp.trim().take(19)
    ).joinToString("|")

    private fun handleIncomingRealtimeNotification(event: RealtimeEvent.NotificationEvent) {
        if (!event.eventType.equals("INSERT", ignoreCase = true) || event.isRead || event.id.isBlank()) return

        val state = _uiState.value
        val myId = state.myProfile.id
        if (myId.isNotBlank() && event.userId.isNotBlank() && event.userId != myId) return

        val normalizedType = event.type.trim().lowercase()
        val action = event.text.ifBlank {
            normalizedType.replace('_', ' ').ifBlank { "New notification" }
        }
        val isMessage = normalizedType == "system" &&
            action.contains("sent you a message", ignoreCase = true)
        val targetPostId = event.postId.takeIf { it.isNotBlank() }
            ?: event.targetId.takeIf {
                it.isNotBlank() && event.targetType.equals("post", ignoreCase = true)
            }
        val targetMarketId = event.targetId.takeIf {
            it.isNotBlank() && event.targetType.equals("market", ignoreCase = true)
        }
        val targetType = when {
            isMessage -> "CHAT"
            normalizedType == "follow" -> "PROFILE"
            event.targetType.isNotBlank() -> event.targetType
            else -> "NOTIFICATION"
        }
        val activity = ActivityItem(
            id = "notification:" + event.id,
            user = event.actorId,
            avatar = "",
            action = action,
            time = "Just now",
            rawTimestamp = event.createdAt,
            isUnread = true,
            category = when (normalizedType) {
                "like", "repost", "save" -> NotificationFilter.LIKES
                "comment", "reply", "mention" -> NotificationFilter.COMMENTS
                "market", "market_order", "order" -> NotificationFilter.MARKET
                else -> NotificationFilter.ALL
            },
            targetPostId = targetPostId,
            targetMarketId = targetMarketId,
            targetType = targetType,
            previewText = event.subText.takeIf { it.isNotBlank() }
        )

        val semanticKey = notificationSemanticKey(activity)
        if (state.activities.none {
                it.id == activity.id || notificationSemanticKey(it) == semanticKey
            }) {
            _uiState.value = state.copy(activities = listOf(activity) + state.activities)
            persistExtendedCache()
        }

        // Direct messages have their own realtime + FCM pipeline. Keep the row in the
        // Notifications page, but do not render a second foreground banner here.
        if (isMessage) return

        val notificationType = BlinkNotificationType.fromWire(normalizedType)
        if (!NotificationPreferenceStore.isAllowed(appContext, notificationType)) return

        val actorProfile = state.profiles.firstOrNull { it.id == event.actorId }
        val actorName = actorProfile?.fullName?.takeIf { it.isNotBlank() }
            ?: actorProfile?.username?.takeIf { it.isNotBlank() }
            ?: ""
        val actorUsername = actorProfile?.username.orEmpty()
        val title = when {
            action.startsWith("@") -> action
            actorName.isNotBlank() && !action.contains(actorName, ignoreCase = true) -> "$actorName $action"
            else -> action.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        val destination = when {
            targetPostId != null -> BlinkInAppNotificationDestination.POST
            targetMarketId != null -> BlinkInAppNotificationDestination.MARKET
            normalizedType == "follow" || targetType.equals("profile", ignoreCase = true) ->
                BlinkInAppNotificationDestination.PROFILE
            else -> BlinkInAppNotificationDestination.NOTIFICATIONS
        }
        val targetKey = targetPostId
            ?: targetMarketId
            ?: event.targetId.takeIf { it.isNotBlank() }
            ?: event.actorId.takeIf { it.isNotBlank() }
            ?: event.id

        BlinkInAppNotificationCenter.publish(
            BlinkInAppNotification(
                key = "social:" + normalizedType.ifBlank { "social" } + ":" +
                    event.actorId + ":" + targetKey,
                notificationId = event.id,
                title = title,
                body = event.subText.ifBlank {
                    actorUsername.takeIf { it.isNotBlank() }?.let { "@$it" }.orEmpty()
                },
                destination = destination,
                senderId = event.actorId,
                senderUsername = actorUsername,
                senderName = actorName,
                senderAvatar = actorProfile?.avatarUrl.orEmpty(),
                postId = targetPostId,
                marketId = targetMarketId,
                targetType = targetType,
                targetId = event.targetId.takeIf { it.isNotBlank() },
                activity = activity
            )
        )
    }

    private fun handleIncomingRealtimeMessage(msg: ChatMessage) {
        val myId = supabaseService.getCurrentUserId().orEmpty()
        val isMine = msg.isFromMe || (myId.isNotBlank() && msg.senderId == myId)
        if (isMine) {
            val state = _uiState.value
            var changed = false
            val updated = state.conversations.map { conversation ->
                var messageChanged = false
                val messages = conversation.messages.map { existing ->
                    if (existing.id == msg.id && msg.id.isNotBlank()) {
                        messageChanged = true
                        existing.copy(status = msg.status, isRead = msg.isRead)
                    } else existing
                }.toMutableList()
                if (messageChanged) {
                    changed = true
                    conversation.copy(messages = messages)
                } else conversation
            }
            if (changed) {
                _uiState.value = state.copy(conversations = updated)
                persistConversations()
            }
            return
        }

        viewModelScope.launch {
            if (msg.id.isNotBlank()) {
                runCatching { chatRepository.markMessageDelivered(msg.id) }
            }
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
            } else if (NotificationPreferenceStore.isAllowed(appContext, BlinkNotificationType.MESSAGE)) {
                val handledInApp = BlinkInAppNotificationCenter.publish(
                    BlinkInAppNotification(
                        key = "message:" + enriched.id,
                        title = displayName,
                        body = enriched.text.take(180),
                        destination = BlinkInAppNotificationDestination.CHAT,
                        senderId = msg.senderId,
                        senderUsername = partner,
                        senderName = displayName,
                        senderAvatar = avatar
                    )
                )
                if (!handledInApp) {
                    withContext(Dispatchers.IO) {
                        BlinkNotificationHelper.showChatMessageNotification(
                            appContext,
                            partner,
                            displayName,
                            enriched.text,
                            avatar,
                            enriched.id
                        )
                    }
                }
            }
        }
    }

    private fun appendMessageToState(partnerUsername: String, message: ChatMessage) {
        val conversations = _uiState.value.conversations.toMutableList(); val index = conversations.indexOfFirst { it.partnerUsername.equals(partnerUsername, true) }
        if (index >= 0) { val old = conversations[index]; conversations[index] = old.copy(lastMessage = message.text, lastMessageTime = message.timestamp, lastMessageRawTime = message.rawTimestamp, messages = (old.messages + message).toMutableList()) }
        else conversations.add(0, ChatConversation("local_${UUID.randomUUID()}", partnerUsername, partnerName = partnerUsername.replace(".", " ").replace("_", " ").capitalizeWords(), partnerAvatar = "", lastMessage = message.text, lastMessageTime = message.timestamp, messages = mutableListOf(message)))
        _uiState.value = _uiState.value.copy(conversations = conversations)
        persistConversations()
    }
    private fun replaceMessageInState(partnerUsername: String, oldId: String, newMsg: ChatMessage) {
        val state = _uiState.value
        val conversations = state.conversations.toMutableList()
        var index = conversations.indexOfFirst { it.partnerUsername.equals(partnerUsername, true) }
        val serverConversationId = newMsg.conversationId
            ?.takeIf { it.isNotBlank() && !it.startsWith("local_") }

        if (index < 0) {
            val id = serverConversationId ?: "local_${UUID.randomUUID()}"
            conversations.add(
                0,
                ChatConversation(
                    id = id,
                    partnerUsername = partnerUsername,
                    partnerName = partnerUsername.replace(".", " ").replace("_", " ").capitalizeWords(),
                    partnerAvatar = "",
                    lastMessage = newMsg.text,
                    lastMessageTime = newMsg.timestamp,
                    lastMessageRawTime = newMsg.rawTimestamp,
                    messages = mutableListOf(newMsg.copy(conversationId = serverConversationId))
                )
            )
            _uiState.value = state.copy(conversations = conversations)
            persistConversations()
            return
        }

        val old = conversations[index]
        val resolvedConversationId = if (old.id.startsWith("local_") && serverConversationId != null) {
            serverConversationId
        } else old.id
        val normalized = newMsg.copy(conversationId = serverConversationId ?: newMsg.conversationId)
        val messages = old.messages.toMutableList()
        val messageIndex = messages.indexOfFirst { it.id == oldId || (normalized.id.isNotBlank() && it.id == normalized.id) }
        if (messageIndex >= 0) messages[messageIndex] = normalized
        else if (messages.none { it.id == normalized.id }) messages.add(normalized)

        conversations[index] = old.copy(
            id = resolvedConversationId,
            lastMessage = normalized.text,
            lastMessageTime = normalized.timestamp,
            lastMessageRawTime = normalized.rawTimestamp,
            messages = messages.distinctBy { it.id.ifBlank { "${it.rawTimestamp}:${it.text}" } }
                .sortedBy { it.rawTimestamp.ifBlank { "9999" } }
                .toMutableList()
        )

        // If a server summary arrived while this local conversation was sending, merge it.
        val duplicateIndex = conversations.indexOfFirst { candidate ->
            candidate.id == resolvedConversationId && candidate.partnerUsername.equals(partnerUsername, true)
        }
        if (duplicateIndex >= 0 && duplicateIndex != index) {
            val primary = conversations[index]
            val duplicate = conversations[duplicateIndex]
            val mergedMessages = (duplicate.messages + primary.messages)
                .distinctBy { it.id.ifBlank { "${it.rawTimestamp}:${it.text}" } }
                .sortedBy { it.rawTimestamp.ifBlank { "9999" } }
                .toMutableList()
            conversations[index] = primary.copy(messages = mergedMessages)
            conversations.removeAt(duplicateIndex)
            if (duplicateIndex < index) index -= 1
        }

        var history = state.messageHistoryHasMore
        if (resolvedConversationId != old.id) {
            history = history - old.id + (resolvedConversationId to (history[old.id] ?: history[resolvedConversationId] ?: true))
        }
        _uiState.value = state.copy(
            conversations = conversations,
            messageHistoryHasMore = history,
            loadingOlderConversationId = if (state.loadingOlderConversationId == old.id) resolvedConversationId else state.loadingOlderConversationId,
            loadingInitialConversationId = if (state.loadingInitialConversationId == old.id) resolvedConversationId else state.loadingInitialConversationId
        )
        persistConversations()
    }
    private fun updateMessageStatusInState(
        partnerUsername: String,
        messageId: String,
        status: MessageStatus,
        pendingLabel: String = "Sending..."
    ) {
        val conversations = _uiState.value.conversations.toMutableList()
        val index = conversations.indexOfFirst { it.partnerUsername.equals(partnerUsername, true) }
        if (index >= 0) {
            val old = conversations[index]
            conversations[index] = old.copy(
                messages = old.messages.map {
                    if (it.id == messageId) {
                        it.copy(
                            status = status,
                            timestamp = if (status == MessageStatus.SENDING) pendingLabel else it.timestamp
                        )
                    } else it
                }.toMutableList()
            )
            _uiState.value = _uiState.value.copy(conversations = conversations)
            persistConversations()
        }
    }

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
        amount: Int = if (tier == VerificationBadge.GOLD) 2000 else 800
    ) {
        if (tier == VerificationBadge.NONE) return
        val reference = paymentReference.trim()
        if (reference.isBlank()) {
            showToast("Secure cash checkout is not connected yet. Use Blink Coins for BLINK Verified.")
            return
        }
        viewModelScope.launch {
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
            val live = runCatching { supabaseService.fetchGameLeaderboard() }
                .getOrDefault(_uiState.value.gameLeaderboardUsers)
            _uiState.value = _uiState.value.copy(gameLeaderboardUsers = live)
        }
        return result
    }

    fun recordPostView(postId: String) {
        viewModelScope.launch {
            val views = supabaseService.recordPostView(postId, _uiState.value.myProfile.username)
            if (views <= 0) return@launch
            _uiState.value = _uiState.value.copy(
                posts = _uiState.value.posts.map { if (it.id == postId) it.copy(viewsCount = maxOf(it.viewsCount, views)) else it },
                followingPosts = _uiState.value.followingPosts.map { if (it.id == postId) it.copy(viewsCount = maxOf(it.viewsCount, views)) else it },
                reels = _uiState.value.reels.map { if (it.id == postId) it.copy(viewsCount = maxOf(it.viewsCount, views)) else it }
            )
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
            val synced = runCatching { supabaseService.markActivityRead(activity.id) }.getOrDefault(false)
            if (!synced) {
                _uiState.value = _uiState.value.copy(
                    activities = _uiState.value.activities.map {
                        if (it.id == activity.id) it.copy(isUnread = true) else it
                    }
                )
            }
        }

        if (activity.targetType.equals("CHAT", ignoreCase = true)) {
            val actorId = activity.user.trim()
            val cached = cachedProfile(actorId)
            if (cached != null && cached.username.isNotBlank()) {
                setTab(MainTab.MESSAGES)
                openChatWithUser(cached.username, cached.fullName, cached.avatarUrl)
                return
            }
            if (actorId.isBlank() || !_uiState.value.isOnline) {
                if (actorId.isBlank()) openActivity(true)
                else showToast("This conversation isn't available offline yet.")
                return
            }
            viewModelScope.launch {
                val profile = runCatching { supabaseService.fetchProfileById(actorId) }.getOrNull()
                if (profile != null && profile.username.isNotBlank()) {
                    val latest = _uiState.value
                    _uiState.value = latest.copy(
                        profiles = listOf(profile) + latest.profiles.filterNot {
                            it.id == profile.id || it.username.equals(profile.username, true)
                        }
                    )
                    persistProfile(profile)
                    setTab(MainTab.MESSAGES)
                    openChatWithUser(profile.username, profile.fullName, profile.avatarUrl)
                } else {
                    openActivity(true)
                    showToast("This conversation is no longer available.")
                }
            }
            return
        }

        activity.targetPostId?.let { postId ->
            val target = (_uiState.value.posts + _uiState.value.followingPosts + _uiState.value.reels).find { it.id == postId }
            val isReel = activity.targetType.equals("reel", ignoreCase = true) || target?.isReel == true
            _uiState.value = _uiState.value.copy(
                selectedTab = MainTab.HOME,
                feedSubTab = if (isReel) 1 else 0,
                routedReelId = postId.takeIf { isReel }
            )
            handleDeepLink(
                AppDeepLink(
                    type = if (isReel) ShareContentType.REEL else ShareContentType.POST,
                    id = postId
                )
            )
            if (activity.category == NotificationFilter.COMMENTS && target != null) {
                openCommentsForPost(postId)
            }
            return
        }

        activity.targetMarketId?.let { marketId ->
            _uiState.value.marketItems.find { it.id == marketId }?.let { openProductDetail(it) }
            return
        }

        if (activity.user.isNotBlank()) {
            openProfile(activity.user)
        } else {
            openActivity(true)
        }
    }

    fun markAllActivitiesRead() {
        val unreadIds = _uiState.value.activities.filter { it.isUnread }.mapTo(hashSetOf()) { it.id }
        if (unreadIds.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            activities = _uiState.value.activities.map { it.copy(isUnread = false) }
        )
        viewModelScope.launch {
            val success = runCatching { supabaseService.markAllActivitiesRead() }.getOrDefault(false)
            if (!success) {
                _uiState.value = _uiState.value.copy(
                    activities = _uiState.value.activities.map {
                        if (it.id in unreadIds) it.copy(isUnread = true) else it
                    }
                )
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
            _uiState.value = BlinkUiState(destination = AppDestination.ONBOARDING, isDarkMode = _uiState.value.isDarkMode)
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
