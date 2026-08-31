package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.models.*
import com.example.data.repository.*
import com.example.data.supabase.RealtimeEvent
import com.example.data.supabase.SupabaseRealtimeManager
import com.example.data.supabase.SupabaseService
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppDestination {
    SPLASH,
    ONBOARDING,
    SIGN_IN,
    SIGN_UP,
    PROFILE_SETUP,
    MAIN
}

enum class MainTab(
    val index: Int,
    val title: String
) {
    HOME(0, "Home"),
    SEARCH(1, "Search"),
    LEADERBOARD(2, "Leaderboard"),
    MARKET(3, "Market"),
    MESSAGES(4, "Messages")
}

data class BlinkUiState(
    val destination: AppDestination = AppDestination.SPLASH,
    val selectedTab: MainTab = MainTab.HOME,
    val isDarkMode: Boolean = true,

    val myProfile: UserProfile = UserProfile(),

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

    val activeCommentsPostId: String? = null,
    val activePostOptionsPost: FeedPost? = null,
    val activeConversationPartner: String? = null,
    val activeViewingStory: Story? = null,

    val isConversationFullScreen: Boolean = false,

        val stories: List<Story> = listOf(
        Story(
            id = "story_me",
            username = "Your Story",
            avatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&auto=format&fit=crop&q=80",
            hasUnseen = false,
            isUser = true
        )
    ),
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
    val comments: List<Comment> = emptyList(),

    val mutedUsers: Set<String> = emptySet(),

    val feedSubTab: Int = 0,

    val isLiveSupabaseConnected: Boolean = false
)

class BlinkViewModel(
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG =
            "BlinkViewModel"

        private const val PREFS =
            "blink_user_session"

        private const val KEY_IS_LOGGED_IN =
            "is_logged_in"

        private const val KEY_EMAIL =
            "email"

        private const val KEY_FULL_NAME =
            "full_name"

        private const val KEY_USERNAME =
            "username"

        private const val KEY_FACULTY =
            "faculty"

        private const val KEY_UNIVERSITY =
            "university"

        private const val KEY_AVATAR =
            "avatar_url"

        private const val KEY_COVER =
            "cover_url"

        private const val KEY_VERIFICATION =
            "verification_badge"

        private const val KEY_SELLER_ACTIVE =
            "is_seller_active"
    }

    private val application =
        application

    private val appContext: Context =
        application.applicationContext

    private val prefs =
        application.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

    private val supabaseService =
        SupabaseService()

    val authRepository =
        AuthRepository(
            application,
            supabaseService
        )

    val profileRepository =
        ProfileRepository(
            supabaseService
        )

    val postRepository =
        PostRepository(
            supabaseService
        )

    val marketRepository =
        MarketRepository(
            supabaseService
        )

    val chatRepository =
        ChatRepository(
            supabaseService
        )

    val realtimeManager = SupabaseRealtimeManager.getInstance()

    private val _uiState =
        MutableStateFlow(
            BlinkUiState()
        )

    val uiState:
            StateFlow<BlinkUiState> =
        _uiState.asStateFlow()

    private val _snackBarMessages =
        MutableSharedFlow<String>(
            extraBufferCapacity = 10
        )

    val snackBarMessages:
            SharedFlow<String> =
        _snackBarMessages.asSharedFlow()

    init {

        /*
         * Make sure the REST service can access the persisted Supabase
         * JWT before any authenticated request is attempted.
         */
        SupabaseService.initialize(
            appContext
        )

        observeAuthState()

        viewModelScope.launch {
            restoreSupabaseSession()
        }

        startServerStatusMonitoring()
        loadDraftsFromPrefs()

        viewModelScope.launch {
            realtimeManager.events.collect { event ->
                handleRealtimeEvent(event)
            }
        }

        viewModelScope.launch {
            delay(700)
            fetchSupabaseData()
        }
    }

    // ============================================================
    // AUTH STATE
    // ============================================================

    private fun observeAuthState() {

        viewModelScope.launch {

            authRepository.authState.collect { authState ->

                when (authState) {

                    is AuthState.Authenticated -> {

                        val profile =
                            authState.userProfile

                        _uiState.value =
                            _uiState.value.copy(
                                myProfile =
                                    profile,
                                destination =
                                    when (
                                        _uiState.value.destination
                                    ) {
                                        AppDestination.SIGN_IN,
                                        AppDestination.SIGN_UP,
                                        AppDestination.ONBOARDING,
                                        AppDestination.SPLASH ->
                                            AppDestination.MAIN

                                        else ->
                                            _uiState.value
                                                .destination
                                    }
                            )

                        saveLocalProfile(
                            profile
                        )

                        /*
                         * Make a live profile request after authentication.
                         */
                        refreshMyProfileFromSupabase(
                            showErrorToast = false
                        )

                        fetchSupabaseData()
                    }

                    is AuthState.Unauthenticated -> {

                        if (
                            _uiState.value.destination ==
                            AppDestination.MAIN
                        ) {

                            _uiState.value =
                                _uiState.value.copy(
                                    destination =
                                        AppDestination.SIGN_IN
                                )
                        }
                    }

                    else -> Unit
                }
            }
        }
    }
    private suspend fun restoreSupabaseSession() {
        try {
            if (!supabaseService.restoreSession()) throw IllegalStateException("No valid Supabase session.")
            val uid=supabaseService.getCurrentUserId() ?: throw IllegalStateException("Session has no authenticated UUID.")
            val profile=profileRepository.fetchById(uid) ?: throw IllegalStateException("Authenticated user has no profile row.")
            _uiState.value=_uiState.value.copy(myProfile=profile,destination=AppDestination.MAIN)
            saveLocalProfile(profile);authRepository.markAuthenticated(profile);fetchSupabaseData()
        }catch(e:Exception){Log.e(TAG,"restoreSupabaseSession failed",e);prefs.edit().clear().apply();SupabaseService.clearSession();_uiState.value=_uiState.value.copy(destination=AppDestination.SIGN_IN);viewModelScope.launch{authRepository.signOut()}}
    }

    private fun restoreLocalSession() {

        val isLoggedIn =
            prefs.getBoolean(
                KEY_IS_LOGGED_IN,
                false
            )

        if (!isLoggedIn) {
            return
        }

        val savedEmail =
            prefs.getString(
                KEY_EMAIL,
                ""
            ).orEmpty()

        val savedName =
            prefs.getString(
                KEY_FULL_NAME,
                "Campus Student"
            ).orEmpty()

        val savedUsername =
            prefs.getString(
                KEY_USERNAME,
                "student"
            ).orEmpty()

        val savedFaculty =
            prefs.getString(
                KEY_FACULTY,
                ""
            ).orEmpty()

        val savedUniversity =
            prefs.getString(
                KEY_UNIVERSITY,
                ""
            ).orEmpty()

        val savedAvatar =
            prefs.getString(
                KEY_AVATAR,
                ""
            ).orEmpty()

        val savedCover =
            prefs.getString(
                KEY_COVER,
                ""
            ).orEmpty()

        val badge =
            when (
                prefs.getString(
                    KEY_VERIFICATION,
                    ""
                )?.uppercase()
            ) {
                "GOLD" ->
                    VerificationBadge.GOLD

                "BLUE" ->
                    VerificationBadge.BLUE

                else ->
                    VerificationBadge.NONE
            }

        val restoredProfile =
            UserProfile(
                fullName =
                    savedName,
                username =
                    savedUsername,
                email =
                    ContactField(
                        savedEmail,
                        true
                    ),
                faculty =
                    savedFaculty,
                university =
                    savedUniversity,
                avatarUrl =
                    savedAvatar,
                coverPhotoUrl =
                    savedCover,
                verificationBadge =
                    badge,
                isSellerActive =
                    prefs.getBoolean(
                        KEY_SELLER_ACTIVE,
                        false
                    )
            )

        _uiState.value =
            _uiState.value.copy(
                myProfile =
                    restoredProfile,
                destination =
                    AppDestination.MAIN
            )
    }

    private fun saveLocalProfile(
        profile: UserProfile
    ) {

        prefs.edit()
            .putBoolean(
                KEY_IS_LOGGED_IN,
                true
            )
            .putString(
                KEY_EMAIL,
                profile.email.value
            )
            .putString(
                KEY_FULL_NAME,
                profile.fullName
            )
            .putString(
                KEY_USERNAME,
                profile.username
            )
            .putString(
                KEY_FACULTY,
                profile.faculty
            )
            .putString(
                KEY_UNIVERSITY,
                profile.university
            )
            .putString(
                KEY_AVATAR,
                profile.avatarUrl
            )
            .putString(
                KEY_COVER,
                profile.coverPhotoUrl
            )
            .putString(
                KEY_VERIFICATION,
                profile.verificationBadge.name
            )
            .putBoolean(
                KEY_SELLER_ACTIVE,
                profile.isSellerActive
            )
            .apply()
    }

    // Keep compatibility with your existing calls.
    private fun saveSession(
        profile: UserProfile
    ) {
        saveLocalProfile(profile)
    }

    // ============================================================
    // CONNECTIVITY
    // ============================================================

    private fun startServerStatusMonitoring() {

        viewModelScope.launch {

            while (true) {

                try {

                    val connected =
                        supabaseService
                            .checkServerStatus()

                    _uiState.value =
                        _uiState.value.copy(
                            isLiveSupabaseConnected =
                                connected
                        )

                } catch (e: Exception) {

                    _uiState.value =
                        _uiState.value.copy(
                            isLiveSupabaseConnected =
                                false
                        )
                }

                delay(
                    15_000L
                )
            }
        }
    }

    // ============================================================
    // SUPABASE DATA
    // ============================================================

    fun fetchSupabaseData() {

        viewModelScope.launch {

            try {

                val posts =
                    supabaseService
                        .fetchFeedPosts()

                /*
                 * Don't leave reels in a separate stale cache.
                 * Build both lists from the same Supabase response.
                 */
                val normalPosts =
                    posts.filter {
                        !it.isReel
                    }

                val fetchedReels =
                    posts.filter {
                        it.isReel ||
                                !it.videoUrl.isNullOrBlank()
                    }

                val mergedPosts = normalPosts.distinctBy { it.id }
                val mergedReels = fetchedReels.distinctBy { it.id }

                val market =
                    supabaseService
                        .fetchMarketItems()

                val conversations =
                    supabaseService
                        .fetchMessages()

                val leaderboard =
                    supabaseService
                        .fetchLeaderboard()

                val cloudStories =
                    supabaseService
                        .fetchStories()

                val myProfile = _uiState.value.myProfile
                val userStoryHeader = Story(
                    id = "story_me",
                    username = "Your Story",
                    avatar = myProfile.avatarUrl.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&auto=format&fit=crop&q=80" },
                    hasUnseen = false,
                    isUser = true
                )
                val mergedStories = if (cloudStories.isNotEmpty()) {
                    val userStories = cloudStories.filter { it.isUser || it.username.equals(myProfile.username, ignoreCase = true) }
                    val otherStories = cloudStories.filter { !it.isUser && !it.username.equals(myProfile.username, ignoreCase = true) }
                    if (userStories.isNotEmpty()) userStories + otherStories else listOf(userStoryHeader) + otherStories
                } else {
                    listOf(userStoryHeader)
                }

                _uiState.value = _uiState.value.copy(activitiesLoading = true, activitiesError = null)

                val activitiesResult = supabaseService.fetchActivities()
                var fetchedActivities = _uiState.value.activities
                var activitiesErr: String? = null
                activitiesResult.fold(
                    onSuccess = { list ->
                        fetchedActivities = list
                        activitiesErr = null
                    },
                    onFailure = { err ->
                        Log.w(TAG, "Failed to fetch activities: ${err.message}")
                        activitiesErr = err.message
                    }
                )

                _uiState.value =
                    _uiState.value.copy(
                        posts =
                            mergedPosts,
                        reels =
                            mergedReels,
                        marketItems =
                            market,
                        conversations =
                            conversations,
                        leaderboardUsers =
                            leaderboard,
                        stories =
                            mergedStories,
                        activities =
                            fetchedActivities,
                        activitiesLoading =
                            false,
                        activitiesError =
                            activitiesErr,
                        isLiveSupabaseConnected =
                            true
                    )

                val curUser = supabaseService.getCurrentUsername() ?: myProfile.username
                val curUid = supabaseService.getCurrentUserId() ?: ""
                if (curUser.isNotBlank() || curUid.isNotBlank()) {
                    realtimeManager.connect(curUser, curUid)
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "fetchSupabaseData failed",
                    e
                )

                _uiState.value =
                    _uiState.value.copy(
                        isLiveSupabaseConnected =
                            false
                    )
            }
        }
    }

    suspend fun refreshMyProfileFromSupabase(
        showErrorToast: Boolean = true
    ) {

        try {

            val userId =
                supabaseService
                    .getCurrentUserId()

            if (
                userId.isNullOrBlank()
            ) {
                return
            }

            val profile =
                profileRepository
                    .fetchById(
                        userId
                    )

            if (
                profile != null
            ) {

                _uiState.value =
                    _uiState.value.copy(
                        myProfile =
                            profile
                    )

                saveLocalProfile(
                    profile
                )
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "refreshMyProfileFromSupabase failed",
                e
            )

            if (showErrorToast) {
                showToast(
                    "Unable to refresh your profile."
                )
            }
        }
    }

    // ============================================================
    // SIGN IN
    // ============================================================

    fun signInWithCredentials(
        emailOrUsername: String,
        password: String,
        onResult: (
            success: Boolean,
            errorMessage: String?
        ) -> Unit
    ) {

        if (
            emailOrUsername.isBlank() ||
            password.isBlank()
        ) {

            onResult(
                false,
                "Please enter both email/username and password."
            )

            return
        }

        viewModelScope.launch {

            val result =
                authRepository
                    .signInWithEmail(
                        emailOrUsername,
                        password
                    )

            if (
                result.isSuccess &&
                result.userProfile != null
            ) {

                val profile =
                    result.userProfile

                _uiState.value =
                    _uiState.value.copy(
                        myProfile =
                            profile,
                        destination =
                            AppDestination.MAIN
                    )

                saveLocalProfile(
                    profile
                )

                fetchSupabaseData()

                showToast(
                    "✨ Signed in as @${profile.username}"
                )

                onResult(
                    true,
                    null
                )

            } else {

                val message =
                    result.errorMessage
                        ?: "Unable to sign in."

                showToast(
                    message
                )

                onResult(
                    false,
                    message
                )
            }
        }
    }

    // ============================================================
    // GOOGLE LOGIN
    // ============================================================

    fun loginWithGoogle(
        email: String = ""
    ) {

        viewModelScope.launch {

            try {

                showToast(
                    "🔐 Connecting to Google..."
                )

                val result =
                    authRepository
                        .signInWithGoogle(
                            email
                        )

                if (
                    result.isSuccess &&
                    result.userProfile != null
                ) {

                    val profile =
                        result.userProfile

                    /*
                     * AuthRepository must save the real Supabase JWT
                     * returned by Google/Supabase Auth.
                     *
                     * Check below that a valid session exists.
                     */
                    val hasSession =
                        !supabaseService
                            .getCurrentUserId()
                            .isNullOrBlank()

                    if (!hasSession) {

                        Log.w(
                            TAG,
                            "Google authentication returned a profile but no Supabase JWT is stored."
                        )

                        showToast(
                            "Google signed in, but the Supabase session was not restored."
                        )
                    }

                    _uiState.value =
                        _uiState.value.copy(
                            myProfile =
                                profile,
                            destination =
                                AppDestination.MAIN
                        )

                    saveLocalProfile(
                        profile
                    )

                    /*
                     * Pull the authoritative profile from Supabase.
                     */
                    refreshMyProfileFromSupabase(
                        showErrorToast = false
                    )

                    fetchSupabaseData()

                    showToast(
                        "✨ Welcome back, @${_uiState.value.myProfile.username}"
                    )

                } else {

                    showToast(
                        result.errorMessage
                            ?: "Google authentication failed."
                    )
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "loginWithGoogle failed",
                    e
                )

                showToast(
                    e.message
                        ?: "Google authentication failed."
                )
            }
        }
    }

    // ============================================================
    // PASSWORD RESET
    // ============================================================

    fun sendPasswordReset(
        email: String,
        onResult: (
            success: Boolean,
            message: String
        ) -> Unit
    ) {

        if (
            email.isBlank() ||
            !email.contains("@")
        ) {

            onResult(
                false,
                "Please enter a valid university or Gmail address."
            )

            return
        }

        viewModelScope.launch {

            val success =
                authRepository
                    .recoverPassword(
                        email
                    )

            if (success) {

                val message =
                    "Password reset instructions sent to $email."

                showToast(
                    message
                )

                onResult(
                    true,
                    message
                )

            } else {

                val message =
                    "Could not send password reset email."

                showToast(
                    message
                )

                onResult(
                    false,
                    message
                )
            }
        }
    }

    // ============================================================
    // SIGN UP
    // ============================================================

    /*
     * IMPORTANT:
     *
     * Your previous version NEVER called Supabase Auth here.
     *
     * This method keeps the existing onboarding flow, but because the
     * exact AuthRepository sign-up method wasn't provided, the actual
     * Supabase account creation still belongs in AuthRepository.
     *
     * Once AuthRepository creates the account and returns/stores the JWT,
     * completeProfileOnboarding() below will sync the profile.
     */
    fun signUp(
        fullName: String,
        username: String,
        email: String,
        password: String = "",
        faculty: String = "SIMME"
    ) {
        val cleanName =
            fullName
                .trim()
                .ifBlank {
                    "Campus Student"
                }

        val cleanUsername =
            username
                .trim()
                .lowercase()
                .replace(
                    "@",
                    ""
                )
                .ifBlank {
                    "student_${
                        System.currentTimeMillis() % 10000
                    }"
                }

        val cleanEmail =
            email
                .trim()
                .lowercase()
                .ifBlank {
                    "$cleanUsername@unilag.edu.ng"
                }

        val cleanPassword =
            password
                .trim()
                .ifBlank {
                    "CampusPass123!"
                }

        val initialProfile =
            _uiState.value
                .myProfile
                .copy(
                    fullName =
                        cleanName,
                    username =
                        cleanUsername,
                    email =
                        ContactField(
                            cleanEmail,
                            true
                        ),
                    faculty =
                        faculty
                            .trim()
                )

        _uiState.value =
            _uiState.value.copy(
                myProfile =
                    initialProfile,
                destination =
                    AppDestination.PROFILE_SETUP
            )

        saveLocalProfile(
            initialProfile
        )

        showToast(
            "Creating your campus account..."
        )

        viewModelScope.launch {
            try {
                val result = authRepository.signUpWithEmail(
                    email = cleanEmail,
                    password = cleanPassword,
                    username = cleanUsername,
                    fullName = cleanName,
                    faculty = faculty.trim()
                )
                if (result.isSuccess && result.userProfile != null) {
                    val synced = result.userProfile
                    _uiState.value = _uiState.value.copy(
                        myProfile = synced
                    )
                    saveLocalProfile(synced)
                    showToast("Account created! Set up your campus profile.")
                } else if (!result.errorMessage.isNullOrBlank()) {
                    showToast(result.errorMessage)
                }
            } catch (e: Exception) {
                Log.e(TAG, "signUp Supabase Auth failed", e)
            }
        }
    }

    // ============================================================
    // PROFILE ONBOARDING
    // ============================================================

    fun completeProfileOnboarding(
        university: String,
        academicLevel: String,
        bio: String,
        skills: List<String>,
        phone: String = "",
        whatsapp: String = ""
    ) {

        val current =
            _uiState.value.myProfile

        val updatedSkills =
            skills
                .filter {
                    it.isNotBlank()
                }
                .map {
                    SkillEndorsement(
                        skill =
                            it,
                        endorsements =
                            1,
                        endorsedByMe =
                            true
                    )
                }

        val completed =
            current.copy(

                university =
                    university.trim(),

                academicLevel =
                    academicLevel.trim(),

                bio =
                    bio.trim(),

                skillEndorsements =
                    if (
                        updatedSkills.isNotEmpty()
                    ) {
                        updatedSkills.toMutableList()
                    } else {
                        current.skillEndorsements
                    },

                phone =
                    ContactField(
                        if (
                            phone.isNotBlank()
                        ) {
                            phone.trim()
                        } else {
                            current.phone.value
                        },
                        true
                    ),

                whatsapp =
                    ContactField(
                        if (
                            whatsapp.isNotBlank()
                        ) {
                            whatsapp.trim()
                        } else {
                            current.whatsapp.value
                        },
                        true
                    )
            )

        _uiState.value =
            _uiState.value.copy(
                myProfile =
                    completed
            )

        saveLocalProfile(
            completed
        )

        viewModelScope.launch {

            val userId =
                supabaseService
                    .getCurrentUserId()

            if (
                userId.isNullOrBlank()
            ) {

                Log.w(
                    TAG,
                    "completeProfileOnboarding: no authenticated Supabase user."
                )

                /*
                 * Keep onboarding data locally until real auth exists.
                 */
                _uiState.value =
                    _uiState.value.copy(
                        destination =
                            AppDestination.MAIN
                    )

                showToast(
                    "Profile saved locally. Sign in to sync it with Supabase."
                )

                return@launch
            }

            val synced =
                supabaseService
                    .updateProfile(
                        completed
                    )

            if (synced) {

                _uiState.value =
                    _uiState.value.copy(
                        myProfile =
                            completed,
                        destination =
                            AppDestination.MAIN
                    )

                showToast(
                    "🎉 Profile created and synced with Supabase."
                )

            } else {

                _uiState.value =
                    _uiState.value.copy(
                        destination =
                            AppDestination.MAIN
                    )

                showToast(
                    "Profile saved locally, but Supabase sync failed."
                )
            }
        }
    }

    // ============================================================
    // NAVIGATION
    // ============================================================

    fun navigateTo(
        destination: AppDestination
    ) {

        _uiState.value =
            _uiState.value.copy(
                destination =
                    destination
            )
    }

    fun setDestination(
        destination: AppDestination
    ) {
        navigateTo(
            destination
        )
    }

    fun selectTab(
        tab: MainTab
    ) {

        _uiState.value =
            _uiState.value.copy(
                selectedTab =
                    tab,
                viewingProfile =
                    null,
                viewingProduct =
                    null,
                isConversationFullScreen =
                    false
            )
    }

    fun setTab(
        tab: MainTab
    ) {
        selectTab(
            tab
        )
    }

    fun setFeedSubTab(
        tab: Int
    ) {

        _uiState.value =
            _uiState.value.copy(
                feedSubTab =
                    tab.coerceIn(
                        0,
                        3
                    )
            )
    }

    fun toggleDarkMode() {

        _uiState.value =
            _uiState.value.copy(
                isDarkMode =
                    !_uiState.value
                        .isDarkMode
            )
    }

    fun openMenu(
        open: Boolean
    ) {

        _uiState.value =
            _uiState.value.copy(
                isMenuOpen =
                    open
            )
    }

    fun openActivity(
        open: Boolean
    ) {

        _uiState.value =
            _uiState.value.copy(
                isActivityOpen =
                    open
            )
    }

    fun openCommentsForPost(
        postId: String?
    ) {
        _uiState.value =
            _uiState.value.copy(
                activeCommentsPostId =
                    postId
            )
        if (postId != null) {
            viewModelScope.launch {
                val fetched = postRepository.fetchComments(postId)
                _uiState.value = _uiState.value.copy(comments = fetched)
            }
        }
    }

    fun openPostOptions(
        post: FeedPost?
    ) {

        _uiState.value =
            _uiState.value.copy(
                activePostOptionsPost =
                    post
            )
    }

    fun openCreatePost(
        open: Boolean
    ) {

        _uiState.value =
            _uiState.value.copy(
                isCreatePostOpen =
                    open
            )
    }

    fun openPostItem(
        open: Boolean
    ) {

        _uiState.value =
            _uiState.value.copy(
                isPostItemOpen =
                    open
            )
    }

    fun openBecomeSeller(
        open: Boolean
    ) {

        _uiState.value =
            _uiState.value.copy(
                isBecomeSellerOpen =
                    open
            )
    }

    fun openEditProfile(
        open: Boolean
    ) {

        _uiState.value =
            _uiState.value.copy(
                isEditProfileOpen =
                    open
            )
    }

    fun openGetVerified(
        open: Boolean
    ) {

        _uiState.value =
            _uiState.value.copy(
                isGetVerifiedOpen =
                    open
            )
    }

    // ============================================================
    // PROFILE
    // ============================================================

    fun isMe(
        identifier: String?
    ): Boolean {
        if (identifier.isNullOrBlank()) return false
        val clean = identifier.trim().removePrefix("@").trim()
        val myUser = _uiState.value.myProfile.username.trim().removePrefix("@").trim()
        val myName = _uiState.value.myProfile.fullName.trim()
        val myId = _uiState.value.myProfile.id.trim()

        return clean.equals("you", ignoreCase = true) ||
                clean.equals("me", ignoreCase = true) ||
                clean.equals("self", ignoreCase = true) ||
                clean.equals("Your Story", ignoreCase = true) ||
                clean.equals(myUser, ignoreCase = true) ||
                clean.equals(myName, ignoreCase = true) ||
                clean.equals(myId, ignoreCase = true) ||
                clean.replace(" ", ".").equals(myUser, ignoreCase = true) ||
                myUser.replace(".", " ").equals(clean, ignoreCase = true)
    }

    fun openProfile(
        username: String
    ) {
        val state = _uiState.value

        if (isMe(username)) {
            _uiState.value = state.copy(
                viewingProfile = state.myProfile
            )
            return
        }

        viewModelScope.launch {
            val remoteProfile = profileRepository.fetchByUsername(username)

            if (remoteProfile != null) {
                _uiState.value = _uiState.value.copy(
                    viewingProfile = remoteProfile
                )
                return@launch
            }

            /*
             * Fallback to leaderboard information so the UI doesn't look empty.
             */
            val leader = _uiState.value.leaderboardUsers.find {
                it.username.equals(username, ignoreCase = true) ||
                        it.fullName.equals(username, ignoreCase = true)
            }

            val fallbackBadge = leader?.verificationBadge ?: when {
                username.contains("zara", ignoreCase = true) -> VerificationBadge.GOLD
                username.contains("aluta", ignoreCase = true) -> VerificationBadge.GOLD
                username.contains("kemi", ignoreCase = true) -> VerificationBadge.BLUE
                username.contains("tech", ignoreCase = true) -> VerificationBadge.BLUE
                username.contains("luna", ignoreCase = true) -> VerificationBadge.BLUE
                username.contains("amara", ignoreCase = true) -> VerificationBadge.BLUE
                else -> VerificationBadge.NONE
            }

            val guest = UserProfile(
                id = "guest_$username",
                fullName = leader?.fullName
                    ?: username.replace(".", " ").capitalizeWords(),
                username = username.removePrefix("@"),
                avatarUrl = leader?.avatar.orEmpty(),
                faculty = leader?.faculty.orEmpty(),
                university = leader?.university.orEmpty(),
                professionalHeadline = "Student • Blink Campus",
                currentJobTitle = "Community Creator",
                bio = "Exploring campus life and connecting with fellow students.",
                verificationBadge = fallbackBadge,
                followerCount = leader?.points ?: 120,
                followingCount = 45
            )

            _uiState.value = _uiState.value.copy(
                viewingProfile = guest
            )
        }
    }

    fun closeProfile() {

        _uiState.value =
            _uiState.value.copy(
                viewingProfile =
                    null
            )
    }

    fun updateProfile(
        updated: UserProfile
    ) {
        viewModelScope.launch {
            try {
                val success = profileRepository.updateProfile(updated)
                if (success) {
                    // Fetch authoritative profile after update
                    val authoritativeProfile = profileRepository.fetchCurrent(updated.username) ?: updated
                    
                    _uiState.value =
                        _uiState.value.copy(
                            myProfile =
                                authoritativeProfile,
                            isEditProfileOpen =
                                false,
                            viewingProfile =
                                if (
                                    _uiState.value
                                        .viewingProfile
                                        ?.username
                                        ?.equals(
                                            authoritativeProfile.username,
                                            ignoreCase = true
                                        ) == true
                                ) {
                                    authoritativeProfile
                                } else {
                                    _uiState.value
                                        .viewingProfile
                                }
                        )

                    saveLocalProfile(
                        authoritativeProfile
                    )

                    updateLocalAuthorData(
                        authoritativeProfile
                    )

                    showToast(
                        "✅ Profile saved successfully."
                    )
                } else {
                    showToast("❌ Failed to update profile.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "updateProfile background sync error", e)
                showToast("❌ Failed to update profile: ${e.message}")
            }
        }
    }

    fun updateMyProfile(
        updated: UserProfile
    ) {
        updateProfile(
            updated
        )
    }

    private fun updateLocalAuthorData(
        profile: UserProfile
    ) {

        val oldProfile =
            _uiState.value.myProfile

        val oldNames =
            setOf(
                oldProfile.username.lowercase(),
                oldProfile.fullName.lowercase()
            )

        val updatedPosts =
            _uiState.value.posts.map { post ->

                if (
                    post.author.lowercase()
                        in oldNames
                ) {
                    post.copy(
                        author =
                            profile.username,
                        authorAvatar =
                            profile.avatarUrl
                    )
                } else {
                    post
                }
            }

        val updatedReels =
            _uiState.value.reels.map { reel ->

                if (
                    reel.author.lowercase()
                        in oldNames
                ) {
                    reel.copy(
                        author =
                            profile.username,
                        authorAvatar =
                            profile.avatarUrl
                    )
                } else {
                    reel
                }
            }

        val updatedMarket =
            _uiState.value.marketItems.map {
                item ->

                if (
                    item.sellerUsername
                        .lowercase()
                        in oldNames
                ) {

                    item.copy(
                        sellerUsername =
                            profile.username,
                        sellerAvatar =
                            profile.avatarUrl,
                        sellerName =
                            profile.fullName,
                        sellerPhone =
                            profile.phone.value,
                        sellerWhatsapp =
                            profile.whatsapp.value
                    )

                } else {
                    item
                }
            }

        _uiState.value =
            _uiState.value.copy(
                posts =
                    updatedPosts,
                reels =
                    updatedReels,
                marketItems =
                    updatedMarket
            )
    }

    // ============================================================
    // CREATE POST / REEL & DRAFTS & SCHEDULING
    // ============================================================

    private fun loadDraftsFromPrefs() {
        try {
            val json = prefs.getString("blink_saved_drafts_data", null)
            if (!json.isNullOrBlank()) {
                // Simple delimited parser for offline drafts
                val drafts = mutableListOf<PostDraft>()
                val items = json.split(";;;DRAFT_DELIM;;;")
                for (item in items) {
                    if (item.isBlank()) continue
                    val parts = item.split(":::FIELD:::")
                    if (parts.size >= 8) {
                        drafts.add(
                            PostDraft(
                                id = parts.getOrNull(0) ?: "draft_${System.currentTimeMillis()}",
                                text = parts.getOrNull(1) ?: "",
                                faculty = parts.getOrNull(2) ?: "SIMME",
                                imageUri = parts.getOrNull(3)?.takeIf { it.isNotBlank() },
                                videoUri = parts.getOrNull(4)?.takeIf { it.isNotBlank() },
                                isReel = parts.getOrNull(5)?.toBoolean() ?: false,
                                category = parts.getOrNull(6) ?: "Campus Life",
                                audience = parts.getOrNull(7) ?: "Everyone",
                                tags = parts.getOrNull(8)?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                                mentions = parts.getOrNull(9)?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                                savedAtTimestamp = parts.getOrNull(10)?.toLongOrNull() ?: System.currentTimeMillis()
                            )
                        )
                    }
                }
                _uiState.value = _uiState.value.copy(savedDrafts = drafts)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load drafts from prefs", e)
        }
    }

    private fun saveDraftsToPrefs(drafts: List<PostDraft>) {
        try {
            val serialized = drafts.joinToString(";;;DRAFT_DELIM;;;") { d ->
                listOf(
                    d.id,
                    d.text,
                    d.faculty,
                    d.imageUri ?: "",
                    d.videoUri ?: "",
                    d.isReel.toString(),
                    d.category,
                    d.audience,
                    d.tags.joinToString(","),
                    d.mentions.joinToString(","),
                    d.savedAtTimestamp.toString()
                ).joinToString(":::FIELD:::")
            }
            prefs.edit().putString("blink_saved_drafts_data", serialized).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save drafts to prefs", e)
        }
    }

    fun saveDraft(draft: PostDraft) {
        val updated = listOf(draft) + _uiState.value.savedDrafts.filter { it.id != draft.id }
        _uiState.value = _uiState.value.copy(savedDrafts = updated)
        saveDraftsToPrefs(updated)
        showToast("💾 Draft saved to phone storage")
    }

    fun deleteDraft(draftId: String) {
        val updated = _uiState.value.savedDrafts.filter { it.id != draftId }
        _uiState.value = _uiState.value.copy(savedDrafts = updated)
        saveDraftsToPrefs(updated)
        showToast("🗑️ Draft deleted")
    }

    fun schedulePost(
        post: FeedPost,
        timeMillis: Long,
        timeFormatted: String
    ) {
        val sched = ScheduledPost(
            id = "sched_${System.currentTimeMillis()}",
            post = post,
            scheduledTimeMillis = timeMillis,
            scheduledTimeFormatted = timeFormatted
        )
        val updated = listOf(sched) + _uiState.value.scheduledPosts
        _uiState.value = _uiState.value.copy(
            scheduledPosts = updated,
            isCreatePostOpen = false
        )
        showToast("⏰ Post scheduled for $timeFormatted")
    }

    fun deleteScheduledPost(id: String) {
        val updated = _uiState.value.scheduledPosts.filter { it.id != id }
        _uiState.value = _uiState.value.copy(scheduledPosts = updated)
        showToast("🗑️ Scheduled post removed")
    }

    fun publishScheduledPostNow(id: String) {
        val sched = _uiState.value.scheduledPosts.find { it.id == id } ?: return
        val updated = _uiState.value.scheduledPosts.filter { it.id != id }
        val updatedPosts = listOf(sched.post) + _uiState.value.posts
        val updatedReels = if (sched.post.isReel || sched.post.videoUrl != null) {
            listOf(sched.post) + _uiState.value.reels
        } else {
            _uiState.value.reels
        }
        _uiState.value = _uiState.value.copy(
            scheduledPosts = updated,
            posts = updatedPosts,
            reels = updatedReels
        )
        showToast("✨ Post published to campus feed")
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
        val profile = _uiState.value.myProfile
        val userId = supabaseService.getCurrentUserId()
            ?: profile.id.takeIf { it.isNotBlank() }
            ?: "user_${profile.username}"
        
        val originalPosts = _uiState.value.posts
        val originalReels = _uiState.value.reels

        // Background asynchronous media upload and cloud persistence
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var uploadedImageUrl: String? = null
                var uploadedVideoUrl: String? = null

                if (!imageUri.isNullOrBlank()) {
                    if (imageUri.startsWith("content://")) {
                        uploadedImageUrl = uploadPostUri(
                            userId = userId,
                            uriString = imageUri,
                            isVideo = false
                        )
                        if (uploadedImageUrl == null) {
                            showToast("Failed to upload image. Post not saved.")
                            return@launch
                        }
                    } else {
                        uploadedImageUrl = imageUri
                    }
                }

                if (!videoUri.isNullOrBlank()) {
                    if (videoUri.startsWith("content://")) {
                        uploadedVideoUrl = uploadPostUri(
                            userId = userId,
                            uriString = videoUri,
                            isVideo = true
                        )
                        if (uploadedVideoUrl == null) {
                            showToast("Failed to upload video. Post not saved.")
                            return@launch
                        }
                    } else {
                        uploadedVideoUrl = videoUri
                    }
                }

                val resultPost = supabaseService.createFeedPost(
                    author = profile.username,
                    authorAvatar = profile.avatarUrl,
                    facultyTag = faculty,
                    text = text,
                    imageUrl = uploadedImageUrl,
                    videoUrl = uploadedVideoUrl,
                    tags = tags,
                    mentions = mentions,
                    poll = poll,
                    isReel = isReel,
                    audience = audience,
                    category = category,
                    location = location,
                    linkUrl = linkUrl,
                    allowComments = allowComments,
                    hideLikes = hideLikes,
                    isPinned = isPinned,
                    isDisappearing = isDisappearing,
                    audioTitle = audioTitle,
                    altText = altText
                )

                if (category.contains("Story", ignoreCase = true)) {
                    val storyObj = Story(
                        id = "story_${java.util.UUID.randomUUID()}",
                        username = profile.username,
                        avatar = profile.avatarUrl.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&fit=crop" },
                        hasUnseen = false,
                        isUser = true,
                        storyImage = uploadedImageUrl.orEmpty(),
                        caption = text,
                        timeAgo = "Just now",
                        faculty = faculty.ifBlank { profile.faculty },
                        university = profile.university,
                        likesCount = 0,
                        isLiked = false,
                        verificationBadge = profile.verificationBadge
                    )
                    postRepository.createStory(storyObj)
                    withContext(Dispatchers.Main) {
                        val existingOtherStories = _uiState.value.stories.filter { !it.isUser && it.id != "story_me" }
                        _uiState.value = _uiState.value.copy(
                            stories = listOf(storyObj) + existingOtherStories
                        )
                    }
                }

                if (resultPost != null) {
                    // Prepend the new post and refresh UI
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            posts = listOf(resultPost) + originalPosts,
                            reels = if (isReel || !uploadedVideoUrl.isNullOrBlank()) listOf(resultPost) + originalReels else originalReels,
                            isCreatePostOpen = false
                        )
                        showToast(
                            if (isReel) "✨ Reel published to Campus!"
                            else "✨ Post published to Feed & Profile!"
                        )
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showToast("Failed to create post on server.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Background sync for post creation notice (local post remains live)", e)
                withContext(Dispatchers.Main) {
                    showToast("Failed to create post: ${e.message}")
                }
            }
        }
    }

    private suspend fun uploadPostUri(
        userId: String,
        uriString: String,
        isVideo: Boolean
    ): String? {

        return withContext(
            Dispatchers.IO
        ) {

            try {

                val uri =
                    Uri.parse(
                        uriString
                    )

                val mimeType =
                    appContext
                        .contentResolver
                        .getType(
                            uri
                        )
                        ?: if (isVideo)
                            "video/mp4"
                        else
                            "image/jpeg"

                val bytes =
                    appContext
                        .contentResolver
                        .openInputStream(
                            uri
                        )
                        ?.use {
                            it.readBytes()
                        }

                if (
                    bytes == null ||
                    bytes.isEmpty()
                ) {

                    Log.e(
                        TAG,
                        "uploadPostUri: unable to read $uri"
                    )

                    return@withContext null
                }

                supabaseService
                    .uploadPostMedia(
                        userId =
                            userId,
                        bytes =
                            bytes,
                        mimeType =
                            mimeType,
                        isVideo =
                            isVideo
                    )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "uploadPostUri failed",
                    e
                )

                null
            }
        }
    }

    // ============================================================
    // SOCIAL ACTIONS
    // ============================================================

    fun togglePostLike(
        postId: String
    ) {
        var nextLiked = false
        var nextCount = 0

        val updatedPosts = _uiState.value.posts.map { post ->
            if (post.id == postId) {
                val liked = !post.isLiked
                nextLiked = liked
                val likes = (post.likes + if (liked) 1 else -1).coerceAtLeast(0)
                nextCount = likes
                post.copy(isLiked = liked, likes = likes)
            } else {
                post
            }
        }

        val updatedReels = _uiState.value.reels.map { reel ->
            if (reel.id == postId) {
                val liked = !reel.isLiked
                nextLiked = liked
                val likes = (reel.likes + if (liked) 1 else -1).coerceAtLeast(0)
                nextCount = likes
                reel.copy(isLiked = liked, likes = likes)
            } else {
                reel
            }
        }

        _uiState.value = _uiState.value.copy(
            posts = updatedPosts,
            reels = updatedReels
        )

        viewModelScope.launch {
            val success = try {
                postRepository.togglePostLike(
                    postId = postId,
                    liked = nextLiked,
                    newLikeCount = nextCount
                )
            } catch (e: Exception) {
                false
            }
            if (success) {
                if (nextLiked) {
                    val targetPost = (_uiState.value.posts + _uiState.value.reels).find { it.id == postId }
                    if (targetPost != null && targetPost.author.isNotBlank()) {
                        supabaseService.recordActivity(
                            recipientUsername = targetPost.author,
                            action = "liked your post",
                            category = NotificationFilter.LIKES,
                            targetPostId = postId,
                            targetType = "POST"
                        )
                    }
                }
            } else {
                // Rollback
                val revertedPosts = _uiState.value.posts.map { post ->
                    if (post.id == postId) {
                        post.copy(isLiked = !nextLiked, likes = (post.likes + if (!nextLiked) 1 else -1).coerceAtLeast(0))
                    } else post
                }
                val revertedReels = _uiState.value.reels.map { reel ->
                    if (reel.id == postId) {
                        reel.copy(isLiked = !nextLiked, likes = (reel.likes + if (!nextLiked) 1 else -1).coerceAtLeast(0))
                    } else reel
                }
                _uiState.value = _uiState.value.copy(posts = revertedPosts, reels = revertedReels)
                showToast("Failed to update like.")
            }
        }
    }

    fun toggleBookmark(postId: String) {
        var nextBookmarked = false

        val updatedPosts = _uiState.value.posts.map { post ->
            if (post.id == postId) {
                nextBookmarked = !post.isBookmarked
                post.copy(isBookmarked = nextBookmarked)
            } else {
                post
            }
        }

        val updatedReels = _uiState.value.reels.map { reel ->
            if (reel.id == postId) {
                nextBookmarked = !reel.isBookmarked
                reel.copy(isBookmarked = nextBookmarked)
            } else {
                reel
            }
        }

        _uiState.value = _uiState.value.copy(
            posts = updatedPosts,
            reels = updatedReels
        )

        viewModelScope.launch {
            val success = try {
                postRepository.togglePostBookmark(postId, nextBookmarked)
            } catch (e: Exception) {
                false
            }
            if (!success) {
                // Rollback
                val revertedPosts = _uiState.value.posts.map { post ->
                    if (post.id == postId) {
                        post.copy(isBookmarked = !nextBookmarked)
                    } else post
                }
                val revertedReels = _uiState.value.reels.map { reel ->
                    if (reel.id == postId) {
                        reel.copy(isBookmarked = !nextBookmarked)
                    } else reel
                }
                _uiState.value = _uiState.value.copy(posts = revertedPosts, reels = revertedReels)
                showToast("Failed to update bookmark.")
            }
        }
    }

fun sharePost(
        postId: String
    ) {

        val updatedPosts =
            _uiState.value.posts.map { post ->

                if (
                    post.id == postId
                ) {

                    post.copy(
                        sharesCount =
                            post.sharesCount + 1
                    )

                } else {
                    post
                }
            }

        val updatedReels =
            _uiState.value.reels.map { reel ->

                if (
                    reel.id == postId
                ) {

                    reel.copy(
                        sharesCount =
                            reel.sharesCount + 1
                    )

                } else {
                    reel
                }
            }

        _uiState.value =
            _uiState.value.copy(
                posts =
                    updatedPosts,
                reels =
                    updatedReels
            )

        showToast(
            "🔗 Post link ready to share."
        )
    }

    fun deletePost(postId: String) {
        val originalPosts = _uiState.value.posts
        val originalReels = _uiState.value.reels

        _uiState.value = _uiState.value.copy(
            posts = originalPosts.filterNot { it.id == postId },
            reels = originalReels.filterNot { it.id == postId },
            activePostOptionsPost = null
        )

        viewModelScope.launch {
            val success = try {
                supabaseService.deleteFeedPost(postId)
            } catch (e: Exception) {
                false
            }
            if (success) {
                showToast("Post removed from your feed.")
            } else {
                showToast("Failed to delete post.")
                // Rollback
                _uiState.value = _uiState.value.copy(
                    posts = originalPosts,
                    reels = originalReels
                )
            }
        }
    }

    fun reportPost(
        postId: String,
        reason: String
    ) {

        _uiState.value =
            _uiState.value.copy(
                activePostOptionsPost =
                    null
            )

        showToast(
            "🚨 Report submitted."
        )
    }

    fun muteUser(
        username: String
    ) {

        val normalized =
            username.lowercase()

        _uiState.value =
            _uiState.value.copy(
                mutedUsers =
                    _uiState.value
                        .mutedUsers
                        .plus(
                            normalized
                        ),
                posts =
                    _uiState.value
                        .posts
                        .filterNot {
                            it.author
                                .equals(
                                    username,
                                    ignoreCase = true
                                )
                        },
                reels =
                    _uiState.value
                        .reels
                        .filterNot {
                            it.author
                                .equals(
                                    username,
                                    ignoreCase = true
                                )
                        },
                activePostOptionsPost =
                    null
            )

        showToast(
            "🔇 @$username muted."
        )
    }

    // ============================================================
    // POLLS
    // ============================================================

    fun votePoll(
        postId: String,
        optionId: String
    ) {

        val updated =
            _uiState.value
                .posts
                .map { post ->

                    if (
                        post.id != postId ||
                        post.poll == null
                    ) {
                        return@map post
                    }

                    val poll =
                        post.poll

                    val already =
                        poll.options
                            .find {
                                it.isVotedByMe
                            }

                    if (
                        already?.id ==
                        optionId
                    ) {
                        return@map post
                    }

                    val options =
                        poll.options.map {
                            option ->

                            when {

                                option.id ==
                                        optionId ->

                                    option.copy(
                                        votes =
                                            option.votes + 1,
                                        isVotedByMe =
                                            true
                                    )

                                option.isVotedByMe ->

                                    option.copy(
                                        votes =
                                            (
                                                option.votes -
                                                        1
                                                ).coerceAtLeast(
                                                0
                                            ),
                                        isVotedByMe =
                                            false
                                    )

                                else ->
                                    option
                            }
                        }

                    val total =
                        if (
                            already == null
                        ) {
                            poll.totalVotes +
                                    1
                        } else {
                            poll.totalVotes
                        }

                    post.copy(
                        poll =
                            poll.copy(
                                options =
                                    options,
                                totalVotes =
                                    total,
                                hasVoted =
                                    true
                            )
                    )
                }

        _uiState.value =
            _uiState.value.copy(
                posts =
                    updated
            )

        val updatedPost = updated.find { it.id == postId }
        updatedPost?.poll?.let { pollState ->
            viewModelScope.launch(Dispatchers.IO) {
                postRepository.votePoll(postId, optionId, pollState)
            }
        }

        showToast(
            "🗳️ Vote recorded."
        )
    }

    // ============================================================
    // COMMENTS
    // ============================================================

    fun addComment(
        postId: String,
        text: String,
        replyToUser: String? = null
    ) {
        if (text.isBlank()) return

        viewModelScope.launch {
            val newComment = postRepository.addComment(postId, text, replyToUser)
            if (newComment != null) {
                _uiState.value = _uiState.value.copy(
                    comments = listOf(newComment) + _uiState.value.comments
                )
                val updatedPosts = _uiState.value.posts.map { post ->
                    if (post.id == postId) post.copy(commentsCount = post.commentsCount + 1) else post
                }
                val updatedReels = _uiState.value.reels.map { reel ->
                    if (reel.id == postId) reel.copy(commentsCount = reel.commentsCount + 1) else reel
                }
                _uiState.value = _uiState.value.copy(posts = updatedPosts, reels = updatedReels)
                showToast(if (replyToUser.isNullOrBlank()) "💬 Comment posted." else "↩️ Reply posted.")
                val targetPost = (_uiState.value.posts + _uiState.value.reels).find { it.id == postId }
                if (targetPost != null && targetPost.author.isNotBlank()) {
                    supabaseService.recordActivity(
                        recipientUsername = targetPost.author,
                        action = if (replyToUser.isNullOrBlank()) "commented on your post" else "replied to comment",
                        category = NotificationFilter.COMMENTS,
                        targetPostId = postId,
                        previewText = text,
                        targetType = "POST"
                    )
                }
            } else {
                showToast("Failed to post comment.")
            }
        }
    }

    fun toggleCommentLike(
        commentId: String
    ) {
        var nextLiked = false
        var nextCount = 0

        val updated = _uiState.value.comments.map { comment ->
            if (comment.id == commentId) {
                nextLiked = !comment.isLiked
                nextCount = (comment.likes + if (nextLiked) 1 else -1).coerceAtLeast(0)
                comment.copy(isLiked = nextLiked, likes = nextCount)
            } else comment
        }
        _uiState.value = _uiState.value.copy(comments = updated)

        viewModelScope.launch {
            val success = try {
                postRepository.toggleCommentLike(commentId, nextLiked, nextCount)
            } catch (e: Exception) { false }
            
            if (success) {
                if (nextLiked) {
                    val comment = _uiState.value.comments.find { it.id == commentId }
                    if (comment != null && comment.user.isNotBlank()) {
                        supabaseService.recordActivity(
                            recipientUsername = comment.user,
                            action = "liked your comment",
                            category = NotificationFilter.LIKES,
                            previewText = comment.text,
                            targetType = "POST"
                        )
                    }
                }
            } else {
                val reverted = _uiState.value.comments.map { comment ->
                    if (comment.id == commentId) {
                        comment.copy(isLiked = !nextLiked, likes = (comment.likes + if (!nextLiked) 1 else -1).coerceAtLeast(0))
                    } else comment
                }
                _uiState.value = _uiState.value.copy(comments = reverted)
                showToast("Failed to update comment like.")
            }
        }
    }

    // ============================================================
    // CHAT & REALTIME MESSAGING
    // ============================================================

    fun openChatWithUser(
        username: String,
        sellerName: String? = null,
        sellerAvatar: String? = null
    ) {
        val cleanUsername = username.trim()
        val state = _uiState.value

        val existing = state.conversations.find {
            it.partnerUsername.equals(cleanUsername, ignoreCase = true)
        }

        if (existing != null) {
            val clearedConversations = state.conversations.map {
                if (it.partnerUsername.equals(cleanUsername, ignoreCase = true)) {
                    it.copy(unreadCount = 0)
                } else it
            }
            _uiState.value = state.copy(
                conversations = clearedConversations,
                activeConversationPartner = cleanUsername,
                isConversationFullScreen = true
            )
            viewModelScope.launch {
                chatRepository.markConversationRead(cleanUsername)
            }
            return
        }

        val newConversation = ChatConversation(
            id = "c_${System.currentTimeMillis()}",
            partnerUsername = cleanUsername,
            partnerName = sellerName ?: cleanUsername.replace(".", " ").replace("_", " ").capitalizeWords(),
            partnerAvatar = sellerAvatar.orEmpty(),
            isOnline = true,
            lastMessage = "",
            lastMessageTime = "New",
            unreadCount = 0,
            isVerified = false,
            faculty = "",
            messages = mutableListOf()
        )

        _uiState.value = state.copy(
            conversations = listOf(newConversation) + state.conversations,
            activeConversationPartner = cleanUsername,
            isConversationFullScreen = true
        )

        viewModelScope.launch {
            chatRepository.markConversationRead(cleanUsername)
        }
    }

    fun closeConversation() {
        _uiState.value = _uiState.value.copy(
            activeConversationPartner = null,
            isConversationFullScreen = false
        )
    }

    fun sendMessage(
        partnerUsername: String,
        text: String,
        isFromMe: Boolean = true
    ) {
        val cleanText = text.trim()
        val cleanPartner = partnerUsername.trim()

        if (cleanText.isBlank() || cleanPartner.isBlank()) return

        val currentUserId = supabaseService.getCurrentUserId() ?: "local_user"
        val currentUsername = supabaseService.getCurrentUsername() ?: "you"

        val tempId = "temp_${UUID.randomUUID()}"
        val tempMessage = ChatMessage(
            id = tempId,
            senderId = currentUserId,
            senderUsername = currentUsername,
            receiverUsername = cleanPartner,
            text = cleanText,
            rawTimestamp = "",
            timestamp = "Sending...",
            isFromMe = true,
            isRead = false,
            status = MessageStatus.SENDING
        )

        appendMessageToState(cleanPartner, tempMessage)

        viewModelScope.launch {
            val result = chatRepository.sendMessage(cleanPartner, cleanText)
            result.fold(
                onSuccess = { serverMsg ->
                    replaceMessageInState(cleanPartner, tempId, serverMsg.copy(status = MessageStatus.SENT))
                    supabaseService.recordActivity(
                        recipientUsername = cleanPartner,
                        action = "sent you a direct message",
                        category = NotificationFilter.ALL,
                        targetUsername = currentUsername,
                        previewText = cleanText,
                        targetType = "CHAT"
                    )
                },
                onFailure = { err ->
                    updateMessageStatusInState(cleanPartner, tempId, MessageStatus.FAILED)
                    showToast("Failed to send message. Tap message to retry.")
                }
            )
        }
    }

    fun retrySendMessage(partnerUsername: String, failedMessage: ChatMessage) {
        if (failedMessage.status != MessageStatus.FAILED) return
        val cleanText = failedMessage.text.trim()
        val tempId = failedMessage.id
        val cleanPartner = partnerUsername.trim()

        updateMessageStatusInState(cleanPartner, tempId, MessageStatus.SENDING)

        viewModelScope.launch {
            val result = chatRepository.sendMessage(cleanPartner, cleanText)
            result.fold(
                onSuccess = { serverMsg ->
                    replaceMessageInState(cleanPartner, tempId, serverMsg.copy(status = MessageStatus.SENT))
                    val currentUsername = supabaseService.getCurrentUsername() ?: "you"
                    supabaseService.recordActivity(
                        recipientUsername = cleanPartner,
                        action = "sent you a direct message",
                        category = NotificationFilter.ALL,
                        targetUsername = currentUsername,
                        previewText = cleanText,
                        targetType = "CHAT"
                    )
                },
                onFailure = { err ->
                    updateMessageStatusInState(cleanPartner, tempId, MessageStatus.FAILED)
                    showToast("Retry failed. Check network connection.")
                }
            )
        }
    }

    private fun handleRealtimeEvent(event: RealtimeEvent) {
        when (event) {
            is RealtimeEvent.MessageEvent -> {
                handleIncomingRealtimeMessage(event.message)
            }
            is RealtimeEvent.ConversationEvent -> {
                viewModelScope.launch {
                    val conversations = chatRepository.fetchConversations()
                    if (conversations.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(conversations = conversations)
                    }
                }
            }
            is RealtimeEvent.NotificationEvent -> {
                viewModelScope.launch {
                    fetchSupabaseData()
                }
            }
            is RealtimeEvent.FeedPostEvent -> {
                viewModelScope.launch {
                    val freshPosts = postRepository.fetchFeed()
                    if (freshPosts.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(posts = freshPosts)
                    }
                }
            }
        }
    }

    private fun handleIncomingRealtimeMessage(msg: ChatMessage) {
        val currentUsername = supabaseService.getCurrentUsername() ?: ""
        val partnerUsername = if (msg.isFromMe || (currentUsername.isNotBlank() && msg.senderUsername.equals(currentUsername, ignoreCase = true))) {
            msg.receiverUsername
        } else {
            msg.senderUsername
        }

        if (partnerUsername.isBlank()) return

        val conversations = _uiState.value.conversations.toMutableList()
        val existingIndex = conversations.indexOfFirst {
            it.partnerUsername.equals(partnerUsername, ignoreCase = true)
        }
        val isActivePartner = _uiState.value.activeConversationPartner?.equals(partnerUsername, ignoreCase = true) == true

        if (existingIndex >= 0) {
            val oldConvo = conversations[existingIndex]
            val msgs = oldConvo.messages.toMutableList()
            val existingMsgIdx = msgs.indexOfFirst { it.id == msg.id || (it.status == MessageStatus.SENDING && it.text == msg.text) }

            if (existingMsgIdx >= 0) {
                msgs[existingMsgIdx] = msg
            } else {
                msgs.add(msg)
            }

            val sortedMsgs = msgs.sortedBy { it.rawTimestamp.ifBlank { it.timestamp } }.toMutableList()
            val unreadIncrement = if (!msg.isFromMe && !isActivePartner) 1 else 0

            conversations[existingIndex] = oldConvo.copy(
                lastMessage = msg.text,
                lastMessageTime = msg.timestamp,
                lastMessageRawTime = msg.rawTimestamp,
                unreadCount = if (isActivePartner) 0 else oldConvo.unreadCount + unreadIncrement,
                messages = sortedMsgs
            )
        } else {
            val newConvo = ChatConversation(
                id = "conv_$partnerUsername",
                partnerUsername = partnerUsername,
                partnerName = partnerUsername.replace(".", " ").replace("_", " ").capitalizeWords(),
                partnerAvatar = "",
                lastMessage = msg.text,
                lastMessageTime = msg.timestamp,
                lastMessageRawTime = msg.rawTimestamp,
                unreadCount = if (!msg.isFromMe && !isActivePartner) 1 else 0,
                messages = mutableListOf(msg)
            )
            conversations.add(0, newConvo)
        }

        _uiState.value = _uiState.value.copy(conversations = conversations)

        if (!msg.isFromMe && isActivePartner) {
            viewModelScope.launch {
                chatRepository.markConversationRead(partnerUsername)
            }
        }
    }

    private fun appendMessageToState(partnerUsername: String, message: ChatMessage) {
        val conversations = _uiState.value.conversations.toMutableList()
        val index = conversations.indexOfFirst { it.partnerUsername.equals(partnerUsername, ignoreCase = true) }
        if (index >= 0) {
            val old = conversations[index]
            val newMsgs = (old.messages + message).toMutableList()
            conversations[index] = old.copy(
                lastMessage = message.text,
                lastMessageTime = message.timestamp,
                lastMessageRawTime = message.rawTimestamp,
                messages = newMsgs
            )
        } else {
            val newConvo = ChatConversation(
                id = "conv_$partnerUsername",
                partnerUsername = partnerUsername,
                partnerName = partnerUsername.replace(".", " ").replace("_", " ").capitalizeWords(),
                partnerAvatar = "",
                lastMessage = message.text,
                lastMessageTime = message.timestamp,
                lastMessageRawTime = message.rawTimestamp,
                messages = mutableListOf(message)
            )
            conversations.add(0, newConvo)
        }
        _uiState.value = _uiState.value.copy(conversations = conversations)
    }

    private fun replaceMessageInState(partnerUsername: String, oldId: String, newMsg: ChatMessage) {
        val conversations = _uiState.value.conversations.toMutableList()
        val index = conversations.indexOfFirst { it.partnerUsername.equals(partnerUsername, ignoreCase = true) }
        if (index >= 0) {
            val old = conversations[index]
            val newMsgs = old.messages.map { if (it.id == oldId) newMsg else it }.toMutableList()
            conversations[index] = old.copy(
                lastMessage = newMsg.text,
                lastMessageTime = newMsg.timestamp,
                lastMessageRawTime = newMsg.rawTimestamp,
                messages = newMsgs
            )
            _uiState.value = _uiState.value.copy(conversations = conversations)
        }
    }

    private fun updateMessageStatusInState(partnerUsername: String, messageId: String, status: MessageStatus) {
        val conversations = _uiState.value.conversations.toMutableList()
        val index = conversations.indexOfFirst { it.partnerUsername.equals(partnerUsername, ignoreCase = true) }
        if (index >= 0) {
            val old = conversations[index]
            val newMsgs = old.messages.map { if (it.id == messageId) it.copy(status = status) else it }.toMutableList()
            conversations[index] = old.copy(messages = newMsgs)
            _uiState.value = _uiState.value.copy(conversations = conversations)
        }
    }

    // ============================================================
    // MARKET
    // ============================================================

    fun addMarketListing(
        item: MarketItem
    ) {

        _uiState.value =
            _uiState.value.copy(
                marketItems =
                    listOf(
                        item
                    ) +
                            _uiState.value
                                .marketItems,
                isPostItemOpen =
                    false
            )

        viewModelScope.launch {

            val success =
                supabaseService
                    .createMarketItem(
                        item
                    )

            if (
                success
            ) {

                showToast(
                    "🛍️ Product synced to Aluta Market."
                )

            } else {

                showToast(
                    "Product was added locally but failed to sync."
                )
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

        val profile =
            _uiState.value
                .myProfile

        val item =
            MarketItem(
                id =
                    "m_${System.currentTimeMillis()}",
                title =
                    title,
                price =
                    price,
                images =
                    if (
                        imageUrl
                            .isNullOrBlank()
                    ) {
                        emptyList()
                    } else {
                        listOf(
                            imageUrl
                        )
                    },
                sellerUsername =
                    profile.username,
                sellerAvatar =
                    profile.avatarUrl,
                sellerName =
                    profile.fullName,
                sellerPhone =
                    profile.phone.value,
                sellerWhatsapp =
                    profile.whatsapp.value,
                sellerIsVerified =
                    profile.verificationBadge !=
                            VerificationBadge.NONE,
                verificationBadge =
                    profile.verificationBadge,
                university =
                    profile.university,
                location =
                    profile.currentCityState,
                category =
                    category,
                condition =
                    condition,
                description =
                    description,
                postedTime =
                    "Just now"
            )

        addMarketListing(
            item
        )
    }

    fun openProductDetail(
        item: MarketItem
    ) {

        _uiState.value =
            _uiState.value.copy(
                viewingProduct =
                    item
            )
    }

    fun closeProductDetail() {

        _uiState.value =
            _uiState.value.copy(
                viewingProduct =
                    null
            )
    }

    fun activateSellerAccount(
        storeName: String,
        phone: String,
        whatsapp: String,
        state: String,
        city: String
    ) {
        val current = _uiState.value.myProfile
        val updated = current.copy(
            isSellerActive = true,
            sellerStoreName = storeName.trim().ifBlank { "Verified Campus Store" },
            phone = ContactField(phone.trim(), true),
            whatsapp = ContactField(whatsapp.trim(), true),
            currentCityState = "$city, $state"
        )

        // Optimistically activate immediately, close become seller screen, and show congratulations
        _uiState.value = _uiState.value.copy(
            myProfile = updated,
            isBecomeSellerOpen = false,
            showSellerCongratulationsDialog = true
        )
        saveLocalProfile(updated)
        showToast("🎉 Congratulations! You are now a verified seller.")

        viewModelScope.launch {
            try {
                val success = profileRepository.updateProfile(updated)
                if (success) {
                    Log.d(TAG, "Seller account updated and synced to Supabase.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Background sync for seller activation", e)
            }
        }
    }

    fun dismissSellerCongratulations() {
        _uiState.value = _uiState.value.copy(showSellerCongratulationsDialog = false)
    }

    // ============================================================
    // VERIFICATION
    // ============================================================

    fun endorseSkill(
        skill: String
    ) {
        val current = _uiState.value.myProfile
        val targetUsername = _uiState.value.viewingProfile?.username ?: current.username
        val endorserUsername = current.username

        val updated =
            current.skillEndorsements
                .map { endorsement ->

                    if (
                        endorsement.skill
                            .equals(
                                skill,
                                ignoreCase = true
                            )
                    ) {

                        val next =
                            !endorsement
                                .endorsedByMe

                        endorsement.copy(
                            endorsedByMe =
                                next,
                            endorsements =
                                (
                                    endorsement.endorsements +
                                            if (next) 1 else -1
                                    ).coerceAtLeast(
                                    0
                                )
                        )

                    } else {
                        endorsement
                    }
                }
                .toMutableList()

        val updatedProfile =
            current.copy(
                skillEndorsements =
                    updated
            )

        _uiState.value =
            _uiState.value.copy(
                myProfile =
                    updatedProfile
            )

        showToast(
            "Endorsement updated for $skill."
        )

        viewModelScope.launch {
            supabaseService.recordSkillEndorsement(targetUsername, skill, endorserUsername)
        }
    }

    fun applyVerification(
        tier: VerificationBadge,
        paymentReference: String = "pay_aluta_${System.currentTimeMillis()}",
        amount: Int = if (tier == VerificationBadge.GOLD) 2500 else 800
    ) {
        val current = _uiState.value.myProfile
        val updatedProfile = current.copy(
            verificationBadge = tier,
            isSellerActive = true
        )

        val updatedViewingProfile = if (_uiState.value.viewingProfile != null && isMe(_uiState.value.viewingProfile?.username)) {
            updatedProfile
        } else {
            _uiState.value.viewingProfile
        }

        _uiState.value = _uiState.value.copy(
            myProfile = updatedProfile,
            viewingProfile = updatedViewingProfile,
            posts = _uiState.value.posts.map { post ->
                if (isMe(post.author)) {
                    post.copy(
                        verificationBadge = tier,
                        isVerified = tier != VerificationBadge.NONE
                    )
                } else {
                    post
                }
            },
            reels = _uiState.value.reels.map { reel ->
                if (isMe(reel.author)) {
                    reel.copy(
                        verificationBadge = tier,
                        isVerified = tier != VerificationBadge.NONE
                    )
                } else {
                    reel
                }
            },
            marketItems = _uiState.value.marketItems.map { item ->
                if (isMe(item.sellerUsername) || isMe(item.sellerName)) {
                    item.copy(
                        verificationBadge = tier,
                        sellerIsVerified = tier != VerificationBadge.NONE
                    )
                } else {
                    item
                }
            },
            stories = _uiState.value.stories.map { story ->
                if (story.isUser || isMe(story.username)) {
                    story.copy(
                        verificationBadge = tier
                    )
                } else {
                    story
                }
            },
            leaderboardUsers = _uiState.value.leaderboardUsers.map { user ->
                if (isMe(user.username) || isMe(user.fullName)) {
                    user.copy(
                        verificationBadge = tier
                    )
                } else {
                    user
                }
            },
            isGetVerifiedOpen = false
        )

        saveLocalProfile(updatedProfile)

        viewModelScope.launch {
            val tierName = if (tier == VerificationBadge.GOLD) "GOLD" else "BLUE"
            val success = supabaseService.submitVerificationRequest(tierName, paymentReference, amount)
            val synced = profileRepository.updateProfile(updatedProfile)

            if (success && synced) {
                showToast("🎉 Verified successfully via payment gateway & Supabase backend.")
            } else {
                showToast("Verification applied locally, sync pending network.")
            }
        }
    }

    fun updateGameStats(score: Int, coins: Int, streak: Int) {
        viewModelScope.launch {
            supabaseService.updateGameStats(score, coins, streak)
        }
    }

    // ============================================================
    // POST VIEWS
    // ============================================================

    fun recordPostView(
        postId: String
    ) {

        viewModelScope.launch {

            val username =
                _uiState.value
                    .myProfile
                    .username

            val views =
                supabaseService
                    .recordPostView(
                        postId,
                        username
                    )

            if (
                views <= 0
            ) {
                return@launch
            }

            val updatedPosts =
                _uiState.value
                    .posts
                    .map {
                        post ->

                        if (
                            post.id == postId
                        ) {

                            post.copy(
                                viewsCount =
                                    maxOf(
                                        post.viewsCount,
                                        views
                                    )
                            )

                        } else {
                            post
                        }
                    }

            val updatedReels =
                _uiState.value
                    .reels
                    .map {
                        reel ->

                        if (
                            reel.id == postId
                        ) {

                            reel.copy(
                                viewsCount =
                                    maxOf(
                                        reel.viewsCount,
                                        views
                                    )
                            )

                        } else {
                            reel
                        }
                    }

            _uiState.value =
                _uiState.value.copy(
                    posts =
                        updatedPosts,
                    reels =
                        updatedReels
                )
        }
    }

    // ============================================================
    // NOTIFICATIONS
    // ============================================================

    fun handleNotificationClick(
        activity: ActivityItem
    ) {

        _uiState.value =
            _uiState.value.copy(
                isActivityOpen =
                    false
            )

        if (
            activity.targetPostId != null
        ) {

            val target =
                (
                    _uiState.value.posts +
                            _uiState.value.reels
                    ).find {
                        it.id ==
                                activity.targetPostId
                    }

            if (
                target != null
            ) {

                _uiState.value =
                    _uiState.value.copy(
                        selectedTab =
                            MainTab.HOME,
                        feedSubTab =
                            if (
                                target.isReel
                            ) 1 else 0
                    )

                if (
                    activity.category ==
                    NotificationFilter.COMMENTS
                ) {

                    openCommentsForPost(
                        target.id
                    )

                } else {

                    showToast(
                        "Viewing @${target.author}'s post."
                    )
                }
            }

        } else if (
            activity.targetMarketId != null
        ) {

            val market =
                _uiState.value
                    .marketItems
                    .find {
                        it.id ==
                                activity.targetMarketId
                    }

            if (
                market != null
            ) {
                openProductDetail(
                    market
                )
            }

        } else {

            openProfile(
                activity.user
            )
        }
    }

    // ============================================================
    // LOGOUT
    // ============================================================

    fun logout() {
        realtimeManager.disconnect()

        viewModelScope.launch {

            try {
                authRepository.signOut()
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "AuthRepository signOut failed",
                    e
                )
            }

            /*
             * Always clear the REST service JWT as well.
             */
            SupabaseService.clearSession()

            prefs.edit()
                .clear()
                .apply()

            _uiState.value =
                BlinkUiState(
                    destination =
                        AppDestination.SIGN_IN,
                    isDarkMode =
                        _uiState.value.isDarkMode
                )

            showToast(
                "Logged out successfully."
            )
        }
    }

    // ============================================================
    // TEST NOTIFICATION
    // ============================================================

    fun simulateBackgroundNotification(
        context: android.content.Context
    ) {
        viewModelScope.launch {
            showToast(
                "Simulating notifications... Close the app to see them offline!"
            )

            // 1. Message
            delay(3000L)
            val senderUsername = "kemi_eng"
            val senderName = "Kemi Adeleke"
            val message = "Hey! Let's review those files when you're back online."
            sendMessage(senderUsername, message, isFromMe = false)
            com.example.notification.BlinkNotificationHelper.showChatMessageNotification(
                context = context,
                senderUsername = senderUsername,
                senderName = senderName,
                messageText = message
            )

            // 2. Follow
            delay(3000L)
            com.example.notification.BlinkNotificationHelper.showFollowNotification(
                context = context,
                username = "jide_tech"
            )

            // 3. Like Post
            delay(3000L)
            com.example.notification.BlinkNotificationHelper.showLikeNotification(
                context = context,
                username = "chika_med",
                postId = "post_123"
            )

            // 4. Comment / Reply
            delay(3000L)
            com.example.notification.BlinkNotificationHelper.showCommentNotification(
                context = context,
                username = "zainab_law",
                comment = "This is exactly what I was looking for, thanks!",
                postId = "post_123"
            )

            // 5. Market / Buy Product Inquiry
            delay(3000L)
            com.example.notification.BlinkNotificationHelper.showBuyerInquiryNotification(
                context = context,
                buyerName = "Ebuka",
                productName = "MacBook Pro M1",
                marketId = "mkt_890"
            )

            // 6. Save Post
            delay(3000L)
            com.example.notification.BlinkNotificationHelper.showGenericNotification(
                context = context,
                channelId = com.example.notification.BlinkNotificationHelper.CHANNEL_SOCIAL,
                title = "Post Saved",
                body = "tola_art saved your post to their collection.",
                notificationId = 2050
            )

            // 7. Connect Request
            delay(3000L)
            com.example.notification.BlinkNotificationHelper.showGenericNotification(
                context = context,
                channelId = com.example.notification.BlinkNotificationHelper.CHANNEL_SOCIAL,
                title = "Connection Request",
                body = "segun_eng wants to connect with you.",
                notificationId = 2051
            )

            // 8. Profile View
            delay(3000L)
            com.example.notification.BlinkNotificationHelper.showGenericNotification(
                context = context,
                channelId = com.example.notification.BlinkNotificationHelper.CHANNEL_SOCIAL,
                title = "Profile Views",
                body = "12 people viewed your profile today.",
                notificationId = 2052
            )

            // 9. Call
            delay(3000L)
            com.example.notification.BlinkNotificationHelper.showGenericNotification(
                context = context,
                channelId = com.example.notification.BlinkNotificationHelper.CHANNEL_MESSAGES,
                title = "Missed Call",
                body = "Missed audio call from Kemi Adeleke",
                notificationId = 1050
            )
        }
    }

    // ============================================================
    // STORY INTERACTIONS & SUPABASE PERSISTENCE
    // ============================================================

    fun openStory(story: Story) {
        val updatedStories = _uiState.value.stories.map {
            if (it.id == story.id) it.copy(hasUnseen = false) else it
        }
        val targetStory = updatedStories.find { it.id == story.id } ?: story.copy(hasUnseen = false)

        _uiState.value = _uiState.value.copy(
            stories = updatedStories,
            activeViewingStory = targetStory
        )
        markStoryViewed(story.id)
    }

    fun closeStory() {
        _uiState.value = _uiState.value.copy(
            activeViewingStory = null
        )
    }

    fun createStory(
        storyImage: String,
        caption: String,
        faculty: String = ""
    ) {
        val profile = _uiState.value.myProfile
        val storyId = "story_${java.util.UUID.randomUUID()}"
        val newStory = Story(
            id = storyId,
            username = profile.username,
            avatar = profile.avatarUrl.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&fit=crop" },
            hasUnseen = false,
            isUser = true,
            storyImage = storyImage,
            caption = caption,
            timeAgo = "Just now",
            faculty = faculty.ifBlank { profile.faculty },
            university = profile.university,
            likesCount = 0,
            isLiked = false,
            verificationBadge = profile.verificationBadge
        )

        val existingStories = _uiState.value.stories.filter { !it.isUser && it.id != "story_me" }
        val updatedStories = listOf(newStory) + existingStories
        _uiState.value = _uiState.value.copy(stories = updatedStories)

        viewModelScope.launch(Dispatchers.IO) {
            val success = postRepository.createStory(newStory)
            if (!success) {
                showToast("Failed to persist story to Supabase.")
            } else {
                showToast("✨ Story published!")
            }
        }
    }

    fun markStoryViewed(storyId: String) {
        val updated = _uiState.value.stories.map {
            if (it.id == storyId) it.copy(hasUnseen = false) else it
        }
        _uiState.value = _uiState.value.copy(stories = updated)
        if (storyId != "story_me") {
            viewModelScope.launch(Dispatchers.IO) {
                postRepository.markStoryViewed(storyId)
            }
        }
    }

    fun toggleStoryLike(storyId: String) {
        var nextLiked = false
        var nextCount = 0
        val updated = _uiState.value.stories.map { story ->
            if (story.id == storyId) {
                nextLiked = !story.isLiked
                nextCount = (story.likesCount + if (nextLiked) 1 else -1).coerceAtLeast(0)
                story.copy(
                    isLiked = nextLiked,
                    likesCount = nextCount
                )
            } else {
                story
            }
        }
        val active = updated.find { it.id == storyId }
        _uiState.value = _uiState.value.copy(
            stories = updated,
            activeViewingStory = active ?: _uiState.value.activeViewingStory
        )
        viewModelScope.launch(Dispatchers.IO) {
            val ok = postRepository.toggleStoryLike(storyId, nextLiked, nextCount)
            if (ok && nextLiked && active != null && !active.username.equals(_uiState.value.myProfile.username, ignoreCase = true)) {
                supabaseService.recordActivity(
                    recipientUsername = active.username,
                    action = "liked your story",
                    category = NotificationFilter.LIKES,
                    targetPostId = storyId,
                    targetType = "STORY"
                )
            }
        }
        showToast(if (nextLiked) "❤️ Story liked" else "Unliked story")
    }

    fun reactToStory(storyId: String, emoji: String) {
        val story = _uiState.value.stories.find { it.id == storyId }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = postRepository.reactToStory(storyId, emoji)
            if (ok && story != null && !story.username.equals(_uiState.value.myProfile.username, ignoreCase = true)) {
                supabaseService.recordActivity(
                    recipientUsername = story.username,
                    action = "reacted $emoji to your story",
                    category = NotificationFilter.COMMENTS,
                    targetPostId = storyId,
                    targetType = "STORY"
                )
            }
        }
        showToast("Reacted $emoji")
    }

    fun replyToStory(storyUsername: String, replyText: String) {
        if (replyText.isBlank()) return
        val story = _uiState.value.stories.find { it.username.equals(storyUsername, ignoreCase = true) }
        val storyId = story?.id ?: "story_${System.currentTimeMillis()}"
        viewModelScope.launch(Dispatchers.IO) {
            val ok = postRepository.replyToStory(storyId, storyUsername, replyText)
            if (ok && !storyUsername.equals(_uiState.value.myProfile.username, ignoreCase = true)) {
                supabaseService.recordActivity(
                    recipientUsername = storyUsername,
                    action = "replied to your story",
                    category = NotificationFilter.COMMENTS,
                    targetPostId = storyId,
                    previewText = replyText,
                    targetType = "STORY"
                )
            }
        }
        showToast("💬 Reply sent to @$storyUsername")
    }

    // ============================================================
    // UI HELPERS
    // ============================================================

    fun showToast(
        message: String
    ) {

        _snackBarMessages
            .tryEmit(
                message
            )
    }
}

private fun String.capitalizeWords(): String {

    return split(" ")
        .filter {
            it.isNotBlank()
        }
        .joinToString(" ") { word ->

            word.replaceFirstChar {
                char ->
                char.uppercase()
            }
        }
}