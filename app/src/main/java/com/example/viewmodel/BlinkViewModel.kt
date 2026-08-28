package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.models.*
import com.example.data.repository.*
import com.example.data.supabase.SupabaseService
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
    val isEditProfileOpen: Boolean = false,
    val isActivityOpen: Boolean = false,
    val isMenuOpen: Boolean = false,
    val isGetVerifiedOpen: Boolean = false,
    val isCreatePostOpen: Boolean = false,

    val activeCommentsPostId: String? = null,
    val activePostOptionsPost: FeedPost? = null,
    val activeConversationPartner: String? = null,

    val isConversationFullScreen: Boolean = false,

    val stories: List<Story> = emptyList(),
    val posts: List<FeedPost> = emptyList(),
    val reels: List<FeedPost> = emptyList(),
    val savedDrafts: List<PostDraft> = emptyList(),
    val scheduledPosts: List<ScheduledPost> = emptyList(),

    val marketItems: List<MarketItem> = emptyList(),
    val leaderboardUsers: List<LeaderboardUser> = emptyList(),
    val conversations: List<ChatConversation> = emptyList(),
    val activities: List<ActivityItem> = emptyList(),
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

            val restored =
                supabaseService.restoreSession()

            if (restored) {

                Log.d(
                    TAG,
                    "Supabase session restored."
                )

                refreshMyProfileFromSupabase(
                    showErrorToast = false
                )

                fetchSupabaseData()

                return
            }

            /*
             * Only fall back to local profile data when there is no
             * valid Supabase session.
             */
            restoreLocalSession()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "restoreSupabaseSession failed",
                e
            )

            restoreLocalSession()
        }
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

                val localPosts =
                    _uiState.value.posts.filter {
                        it.id.startsWith("post_") || it.id.startsWith("local_")
                    }

                val localReels =
                    _uiState.value.reels.filter {
                        it.id.startsWith("post_") || it.id.startsWith("local_")
                    }

                val mergedPosts =
                    (localPosts + normalPosts).distinctBy { it.id }

                val mergedReels =
                    (localReels + fetchedReels).distinctBy { it.id }

                val market =
                    supabaseService
                        .fetchMarketItems()

                val conversations =
                    supabaseService
                        .fetchMessages()

                val leaderboard =
                    supabaseService
                        .fetchLeaderboard()

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
                        isLiveSupabaseConnected =
                            true
                    )

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
        faculty: String
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
            "Welcome to Blink! Complete your campus profile."
        )
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
                clean.equals("user_me", ignoreCase = true) ||
                clean.equals("Your Story", ignoreCase = true) ||
                clean.equals(myUser, ignoreCase = true) ||
                clean.equals(myName, ignoreCase = true) ||
                clean.equals(myId, ignoreCase = true) ||
                clean.equals("efe.design", ignoreCase = true) ||
                clean.equals("Efe Chukwu", ignoreCase = true) ||
                clean.equals("golowosile", ignoreCase = true) ||
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
        _uiState.value =
            _uiState.value.copy(
                myProfile =
                    updated,
                isEditProfileOpen =
                    false,
                viewingProfile =
                    if (
                        _uiState.value
                            .viewingProfile
                            ?.username
                            ?.equals(
                                updated.username,
                                ignoreCase = true
                            ) == true
                    ) {
                        updated
                    } else {
                        _uiState.value
                            .viewingProfile
                    }
            )

        saveLocalProfile(
            updated
        )

        updateLocalAuthorData(
            updated
        )

        showToast(
            "✅ Profile saved successfully."
        )

        viewModelScope.launch {
            try {
                profileRepository.updateProfile(updated)
            } catch (e: Exception) {
                Log.e(TAG, "updateProfile background sync error", e)
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
            ?: profile.id.takeIf { it.isNotBlank() && it != "user_me" }
            ?: "user_${profile.username}"

        val postId = "post_${System.currentTimeMillis()}"
        val newPost = FeedPost(
            id = postId,
            author = profile.username,
            authorAvatar = profile.avatarUrl,
            facultyTag = faculty.ifBlank { profile.faculty.ifBlank { "SIMME" } },
            isVerified = profile.verificationBadge != VerificationBadge.NONE,
            verificationBadge = profile.verificationBadge,
            timeAgo = "Just now",
            text = text,
            images = if (!imageUri.isNullOrBlank()) listOf(imageUri) else emptyList(),
            videoUrl = videoUri,
            tags = tags,
            mentions = mentions,
            poll = poll,
            isReel = isReel,
            likes = 0,
            isLiked = false,
            commentsCount = 0,
            sharesCount = 0,
            viewsCount = 1,
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

        // INSTANT OPTIMISTIC PUBLISHING: Feed and profile update instantly!
        val newPosts = listOf(newPost) + _uiState.value.posts
        val newReels = if (isReel || !videoUri.isNullOrBlank()) {
            listOf(newPost) + _uiState.value.reels
        } else {
            _uiState.value.reels
        }

        _uiState.value = _uiState.value.copy(
            posts = newPosts,
            reels = newReels,
            isCreatePostOpen = false
        )

        showToast(
            if (isReel) "✨ Reel published to Campus!"
            else "✨ Post published to Feed & Profile!"
        )

        // Background asynchronous media upload and cloud persistence
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var uploadedImageUrl: String? = null
                var uploadedVideoUrl: String? = null

                if (!imageUri.isNullOrBlank()) {
                    uploadedImageUrl = uploadPostUri(
                        userId = userId,
                        uriString = imageUri,
                        isVideo = false
                    )
                }

                if (!videoUri.isNullOrBlank()) {
                    uploadedVideoUrl = uploadPostUri(
                        userId = userId,
                        uriString = videoUri,
                        isVideo = true
                    )
                }

                supabaseService.createFeedPost(
                    author = profile.username,
                    authorAvatar = profile.avatarUrl,
                    facultyTag = faculty,
                    text = text,
                    imageUrl = uploadedImageUrl ?: imageUri,
                    videoUrl = uploadedVideoUrl ?: videoUri,
                    tags = tags,
                    mentions = mentions,
                    poll = poll,
                    isReel = isReel
                )
            } catch (e: Exception) {
                Log.e(TAG, "Background sync for post creation notice (local post remains live)", e)
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

        val updatedPosts =
            _uiState.value
                .posts
                .map { post ->

                    if (
                        post.id == postId
                    ) {

                        val liked =
                            !post.isLiked

                        post.copy(
                            isLiked =
                                liked,
                            likes =
                                (
                                    post.likes +
                                            if (liked) 1 else -1
                                    ).coerceAtLeast(
                                    0
                                )
                        )

                    } else {
                        post
                    }
                }

        val updatedReels =
            _uiState.value
                .reels
                .map { reel ->

                    if (
                        reel.id == postId
                    ) {

                        val liked =
                            !reel.isLiked

                        reel.copy(
                            isLiked =
                                liked,
                            likes =
                                (
                                    reel.likes +
                                            if (liked) 1 else -1
                                    ).coerceAtLeast(
                                    0
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

        /*
         * Your Supabase service currently doesn't expose the required
         * RPC/table operation for persistent likes. The UI state is updated
         * immediately; persistence should be added through a likes RPC next.
         */
    }

    fun toggleBookmark(
        postId: String
    ) {

        val updatedPosts =
            _uiState.value
                .posts
                .map { post ->

                    if (
                        post.id == postId
                    ) {

                        post.copy(
                            isBookmarked =
                                !post.isBookmarked
                        )

                    } else {
                        post
                    }
                }

        val updatedReels =
            _uiState.value
                .reels
                .map { reel ->

                    if (
                        reel.id == postId
                    ) {

                        reel.copy(
                            isBookmarked =
                                !reel.isBookmarked
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

    fun deletePost(
        postId: String
    ) {

        _uiState.value =
            _uiState.value.copy(
                posts =
                    _uiState.value
                        .posts
                        .filterNot {
                            it.id == postId
                        },
                reels =
                    _uiState.value
                        .reels
                        .filterNot {
                            it.id == postId
                        },
                activePostOptionsPost =
                    null
            )

        /*
         * Add a Supabase DELETE/RPC operation here once your RLS policy
         * for deleting user-owned posts is confirmed.
         */
        showToast(
            "Post removed from your feed."
        )
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

        if (
            text.isBlank()
        ) {
            return
        }

        val profile =
            _uiState.value
                .myProfile

        val newComment =
            Comment(
                id =
                    System.currentTimeMillis(),
                user =
                    profile.username,
                avatar =
                    profile.avatarUrl,
                text =
                    text.trim(),
                time =
                    "Just now",
                likes =
                    0,
                isLiked =
                    false
            )

        val updatedComments =
            listOf(
                newComment
            ) +
                    _uiState.value
                        .comments

        val updatedPosts =
            _uiState.value
                .posts
                .map { post ->

                    if (
                        post.id == postId
                    ) {

                        post.copy(
                            commentsCount =
                                post.commentsCount + 1
                        )

                    } else {
                        post
                    }
                }

        _uiState.value =
            _uiState.value.copy(
                comments =
                    updatedComments,
                posts =
                    updatedPosts
            )

        showToast(
            if (
                replyToUser.isNullOrBlank()
            )
                "💬 Comment posted."
            else
                "↩️ Reply posted."
        )
    }

    fun toggleCommentLike(
        commentId: Long
    ) {

        val updated =
            _uiState.value
                .comments
                .map { comment ->

                    if (
                        comment.id == commentId
                    ) {

                        val liked =
                            !comment.isLiked

                        comment.copy(
                            isLiked =
                                liked,
                            likes =
                                (
                                    comment.likes +
                                            if (liked) 1 else -1
                                    ).coerceAtLeast(
                                    0
                                )
                        )

                    } else {
                        comment
                    }
                }

        _uiState.value =
            _uiState.value.copy(
                comments =
                    updated
            )
    }

    // ============================================================
    // CHAT
    // ============================================================

    fun openChatWithUser(
        username: String,
        sellerName: String? = null,
        sellerAvatar: String? = null
    ) {

        val state =
            _uiState.value

        val existing =
            state.conversations
                .find {
                    it.partnerUsername
                        .equals(
                            username,
                            ignoreCase = true
                        )
                }

        if (
            existing != null
        ) {

            _uiState.value =
                state.copy(
                    activeConversationPartner =
                        username,
                    isConversationFullScreen =
                        true
                )

            return
        }

        val newConversation =
            ChatConversation(
                id =
                    "c_${System.currentTimeMillis()}",
                partnerUsername =
                    username,
                partnerName =
                    sellerName
                        ?: username
                            .replace(
                                ".",
                                " "
                            )
                            .capitalizeWords(),
                partnerAvatar =
                    sellerAvatar.orEmpty(),
                isOnline =
                    true,
                lastMessage =
                    "",
                lastMessageTime =
                    "New",
                unreadCount =
                    0,
                isVerified =
                    false,
                faculty =
                    "",
                messages =
                    mutableListOf()
            )

        _uiState.value =
            state.copy(
                conversations =
                    listOf(
                        newConversation
                    ) +
                            state.conversations,
                activeConversationPartner =
                    username,
                isConversationFullScreen =
                    true
            )
    }

    fun closeConversation() {

        _uiState.value =
            _uiState.value.copy(
                activeConversationPartner =
                    null,
                isConversationFullScreen =
                    false
            )
    }

    fun sendMessage(
        partnerUsername: String,
        text: String,
        isFromMe: Boolean = true
    ) {

        if (
            text.isBlank()
        ) {
            return
        }

        val currentUserId =
            supabaseService
                .getCurrentUserId()

        val conversations =
            _uiState.value
                .conversations
                .map { conversation ->

                    if (
                        conversation.partnerUsername
                            .equals(
                                partnerUsername,
                                ignoreCase = true
                            )
                    ) {

                        val newMessage =
                            ChatMessage(
                                id =
                                    "msg_${System.currentTimeMillis()}",
                                senderId =
                                    currentUserId
                                        ?: "local_user",
                                text =
                                    text.trim(),
                                timestamp =
                                    "Just now",
                                isFromMe =
                                    isFromMe
                            )

                        conversation.copy(
                            lastMessage =
                                text.trim(),
                            lastMessageTime =
                                "Just now",
                            unreadCount =
                                if (
                                    isFromMe
                                ) {
                                    conversation.unreadCount
                                } else {
                                    conversation.unreadCount + 1
                                },
                            messages =
                                (
                                    conversation
                                        .messages +
                                            newMessage
                                    ).toMutableList()
                        )

                    } else {
                        conversation
                    }
                }

        _uiState.value =
            _uiState.value.copy(
                conversations =
                    conversations
            )

        if (
            isFromMe
        ) {

            viewModelScope.launch {

                val sent =
                    supabaseService
                        .sendMessage(
                            partnerUsername,
                            text.trim()
                        )

                if (
                    !sent
                ) {

                    showToast(
                        "Message saved locally but could not sync."
                    )
                }
            }
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

        val current =
            _uiState.value
                .myProfile

        val updated =
            current.copy(
                isSellerActive =
                    true,
                sellerStoreName =
                    storeName.trim(),
                phone =
                    ContactField(
                        phone.trim(),
                        true
                    ),
                whatsapp =
                    ContactField(
                        whatsapp.trim(),
                        true
                    ),
                currentCityState =
                    "$city, $state"
            )

        viewModelScope.launch {

            val success =
                profileRepository
                    .updateProfile(
                        updated
                    )

            if (
                success
            ) {

                _uiState.value =
                    _uiState.value.copy(
                        myProfile =
                            updated,
                        isBecomeSellerOpen =
                            false
                    )

                saveLocalProfile(
                    updated
                )

                showToast(
                    "🏪 Seller account activated and synced."
                )

            } else {

                showToast(
                    "Seller activation could not sync."
                )
            }
        }
    }

    // ============================================================
    // VERIFICATION
    // ============================================================

    fun endorseSkill(
        skill: String
    ) {

        val current =
            _uiState.value
                .myProfile

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
    }

    fun applyVerification(
        tier: VerificationBadge
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

            val synced =
                profileRepository
                    .updateProfile(
                        updatedProfile
                    )

            if (
                synced
            ) {

                showToast(
                    "🎉 Verification updated and synced."
                )

            } else {

                showToast(
                    "Verification updated locally, but sync failed."
                )
            }
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