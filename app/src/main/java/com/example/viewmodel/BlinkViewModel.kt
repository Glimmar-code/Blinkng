package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.AccountSessionStore
import com.example.data.models.*
import com.example.data.repository.*
import com.example.data.supabase.RealtimeEvent
import com.example.data.supabase.SupabaseRealtimeManager
import com.example.data.supabase.SupabaseService
import com.example.data.supabase.MessageMediaService
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
    val comments: List<Comment> = emptyList(),
    val mutedUsers: Set<String> = emptySet(),
    val followingUserIds: Set<String> = emptySet(),
    val feedSubTab: Int = 0,
    val isLiveSupabaseConnected: Boolean = false,
    val isFeedLoading: Boolean = true,
    val feedErrorMessage: String? = null,
    val isCreatingPost: Boolean = false
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
        private const val KEY_DEPARTMENT = "department"
        private const val KEY_ACADEMIC_LEVEL = "academic_level"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
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
    val realtimeManager = SupabaseRealtimeManager.getInstance()
    private val _uiState = MutableStateFlow(BlinkUiState())
    val uiState: StateFlow<BlinkUiState> = _uiState.asStateFlow()
    private val _snackBarMessages = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val snackBarMessages: SharedFlow<String> = _snackBarMessages.asSharedFlow()

    init {
        SupabaseService.initialize(appContext)
        observeAuthState()
        viewModelScope.launch { restoreSupabaseSession() }
        startServerStatusMonitoring()
        loadDraftsFromPrefs()
        viewModelScope.launch { realtimeManager.events.collect { handleRealtimeEvent(it) } }
    }

    private fun destinationForProfile(profile: UserProfile): AppDestination =
        if (profile.onboardingCompleted) AppDestination.MAIN else AppDestination.PROFILE_SETUP

    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.authState.collect { authState ->
                when (authState) {
                    is AuthState.Authenticated -> {
                        val profile = authState.userProfile
                        _uiState.value = _uiState.value.copy(
                            myProfile = profile,
                            destination = destinationForProfile(profile)
                        )
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
            var restored = supabaseService.restoreSession()

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
                        _uiState.value = _uiState.value.copy(
                            myProfile = profile,
                            destination = destinationForProfile(profile)
                        )
                        saveLocalProfile(profile)
                        authRepository.markAuthenticated(profile)
                        if (profile.onboardingCompleted) fetchSupabaseData()
                        return
                    }
                }

                if (hasLocalAuthenticatedProfile()) {
                    restoreLocalSession()
                    authRepository.markAuthenticated(_uiState.value.myProfile)
                    if (_uiState.value.myProfile.onboardingCompleted) fetchSupabaseData()
                    return
                }
            }

            if (
                hasLocalAuthenticatedProfile() &&
                (SupabaseService.accessToken() != null || AccountSessionStore.list(appContext).isNotEmpty())
            ) {
                restoreLocalSession()
                if (_uiState.value.myProfile.onboardingCompleted) fetchSupabaseData()
            } else {
                _uiState.value = _uiState.value.copy(destination = AppDestination.SIGN_IN)
            }
        } catch (e: Exception) {
            Log.w(TAG, "restoreSupabaseSession notice: ${e.message}")
            if (
                hasLocalAuthenticatedProfile() &&
                (SupabaseService.accessToken() != null || AccountSessionStore.list(appContext).isNotEmpty())
            ) {
                restoreLocalSession()
                if (_uiState.value.myProfile.onboardingCompleted) fetchSupabaseData()
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
        val savedDepartment = prefs.getString(KEY_DEPARTMENT, "").orEmpty()
        val savedAcademicLevel = prefs.getString(KEY_ACADEMIC_LEVEL, "").orEmpty()
        val savedAvatar = prefs.getString(KEY_AVATAR, authPrefs.getString(KEY_AVATAR, "")).orEmpty()
        val savedCover = prefs.getString(KEY_COVER, authPrefs.getString(KEY_COVER, "")).orEmpty()
        val onboardingCompleted = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        val badge = when (prefs.getString(KEY_VERIFICATION, "")?.uppercase()) {
            "GOLD" -> VerificationBadge.GOLD
            "BLUE" -> VerificationBadge.BLUE
            else -> VerificationBadge.NONE
        }
        val restoredProfile = UserProfile(
            fullName = savedName,
            username = savedUsername,
            email = ContactField(savedEmail, true),
            faculty = savedFaculty,
            university = savedUniversity,
            department = savedDepartment,
            academicLevel = savedAcademicLevel,
            avatarUrl = savedAvatar,
            coverPhotoUrl = savedCover,
            verificationBadge = badge,
            onboardingCompleted = onboardingCompleted,
            isSellerActive = prefs.getBoolean(KEY_SELLER_ACTIVE, false)
        )
        _uiState.value = _uiState.value.copy(
            myProfile = restoredProfile,
            destination = destinationForProfile(restoredProfile)
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
            .putString(KEY_DEPARTMENT, profile.department)
            .putString(KEY_ACADEMIC_LEVEL, profile.academicLevel)
            .putBoolean(KEY_ONBOARDING_COMPLETED, profile.onboardingCompleted)
            .putString(KEY_AVATAR, profile.avatarUrl)
            .putString(KEY_COVER, profile.coverPhotoUrl)
            .putString(KEY_VERIFICATION, profile.verificationBadge.name)
            .putBoolean(KEY_SELLER_ACTIVE, profile.isSellerActive)
            .apply()
    }

    private fun saveSession(profile: UserProfile) = saveLocalProfile(profile)

    private fun startServerStatusMonitoring() {
        viewModelScope.launch {
            while (true) {
                _uiState.value = _uiState.value.copy(isLiveSupabaseConnected = runCatching { supabaseService.checkServerStatus() }.getOrDefault(false))
                delay(15_000L)
            }
        }
    }

    fun fetchSupabaseData() {
        viewModelScope.launch {
            val before = _uiState.value
            val hadFeed = before.posts.isNotEmpty() || before.reels.isNotEmpty()

            _uiState.value = before.copy(
                isFeedLoading = !hadFeed,
                feedErrorMessage = null
            )

            val feedResult = runCatching { supabaseService.fetchFeedPosts() }
                .onFailure { Log.e(TAG, "Feed fetch failed", it) }

            val fetched = feedResult.getOrNull()
            val normalPosts = fetched
                ?.filter { !it.isReel && it.videoUrl.isNullOrBlank() }
                ?.distinctBy { it.id }
                ?: before.posts
            val fetchedReels = fetched
                ?.filter { it.isReel || !it.videoUrl.isNullOrBlank() }
                ?.distinctBy { it.id }
                ?: before.reels

            _uiState.value = _uiState.value.copy(
                posts = normalPosts,
                reels = fetchedReels,
                isLiveSupabaseConnected = feedResult.isSuccess,
                isFeedLoading = false,
                feedErrorMessage = feedResult.exceptionOrNull()?.let {
                    "Couldn't refresh live Supabase data. Check your connection and try again."
                }
            )

            val liveProfiles = runCatching { supabaseService.fetchProfiles() }
                .onFailure { Log.e(TAG, "Profiles fetch failed", it) }
                .getOrDefault(before.profiles)
                .filter { it.username.isNotBlank() }
                .distinctBy { it.id.ifBlank { it.username.lowercase() } }

            val followingIds = runCatching { supabaseService.fetchFollowingIds() }
                .onFailure { Log.e(TAG, "Following fetch failed", it) }
                .getOrDefault(before.followingUserIds)

            val market = runCatching { supabaseService.fetchMarketItems() }
                .onFailure { Log.e(TAG, "Market fetch failed", it) }
                .getOrDefault(before.marketItems)

            val conversations = runCatching {
                MessageMediaService.hydrateVideos(supabaseService.fetchMessages())
            }
                .onFailure { Log.e(TAG, "Message fetch failed", it) }
                .getOrDefault(before.conversations)

            val leaderboard = runCatching { supabaseService.fetchLeaderboard() }
                .onFailure { Log.e(TAG, "Leaderboard fetch failed", it) }
                .getOrDefault(before.leaderboardUsers)

            val cloudStories = runCatching { supabaseService.fetchStories() }
                .onFailure { Log.e(TAG, "Stories fetch failed", it) }
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
                followingUserIds = followingIds,
                posts = normalPosts,
                reels = fetchedReels,
                marketItems = market,
                conversations = conversations,
                leaderboardUsers = leaderboard,
                stories = mergedStories,
                activitiesLoading = true,
                activitiesError = null
            )

            runCatching { supabaseService.fetchActivities() }
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
        }
    }

    fun refreshLeaderboard() {
        viewModelScope.launch {
            runCatching { supabaseService.fetchLeaderboard() }
                .onSuccess { live ->
                    _uiState.value = _uiState.value.copy(leaderboardUsers = live)
                    showToast("Leaderboard refreshed from Supabase.")
                }
                .onFailure {
                    Log.e(TAG, "Leaderboard refresh failed", it)
                    showToast("Couldn't refresh the leaderboard.")
                }
        }
    }


    suspend fun refreshMyProfileFromSupabase(showErrorToast: Boolean = true) {
        try {
            val userId = supabaseService.getCurrentUserId()
            if (userId.isNullOrBlank()) return
            profileRepository.fetchById(userId)?.let { profile ->
                _uiState.value = _uiState.value.copy(myProfile = profile)
                saveLocalProfile(profile)
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
                _uiState.value = _uiState.value.copy(myProfile = profile, destination = destinationForProfile(profile))
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
                    _uiState.value = _uiState.value.copy(myProfile = profile, destination = destinationForProfile(profile))
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

    fun completeProfileOnboarding(
        university: String,
        department: String,
        academicLevel: String,
        bio: String,
        skills: List<String>,
        phone: String = "",
        whatsapp: String = ""
    ) {
        val current = _uiState.value.myProfile
        val cleanSkills = skills.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val completed = current.copy(
            university = university.trim(),
            department = department.trim(),
            academicLevel = academicLevel.trim(),
            bio = bio.trim(),
            coreSkills = cleanSkills.toMutableList(),
            skillEndorsements = cleanSkills
                .map { skill -> SkillEndorsement(skill, 1, true) }
                .toMutableList(),
            phone = ContactField(if (phone.isNotBlank()) phone.trim() else current.phone.value, true),
            whatsapp = ContactField(if (whatsapp.isNotBlank()) whatsapp.trim() else current.whatsapp.value, true),
            onboardingCompleted = true
        )

        _uiState.value = _uiState.value.copy(myProfile = completed, destination = AppDestination.PROFILE_SETUP)

        viewModelScope.launch {
            val userId = supabaseService.getCurrentUserId()
            if (userId.isNullOrBlank()) {
                showToast("Your session expired. Sign in again to finish onboarding.")
                return@launch
            }

            if (!supabaseService.updateProfile(completed)) {
                showToast("Couldn't finish onboarding yet. Please retry.")
                return@launch
            }

            val authoritative = profileRepository.fetchById(userId) ?: completed
            _uiState.value = _uiState.value.copy(
                myProfile = authoritative,
                destination = AppDestination.MAIN
            )
            saveLocalProfile(authoritative)
            fetchSupabaseData()
            showToast("🎉 Your Blink profile is ready.")
        }
    }

    fun setDestination(destination: AppDestination) { _uiState.value = _uiState.value.copy(destination = destination) }
    fun navigateTo(destination: AppDestination) = setDestination(destination)
    fun selectTab(tab: MainTab) { _uiState.value = _uiState.value.copy(selectedTab = tab, viewingProfile = null, viewingProduct = null, isConversationFullScreen = false) }
    fun setTab(tab: MainTab) = selectTab(tab)
    fun setFeedSubTab(tab: Int) { _uiState.value = _uiState.value.copy(feedSubTab = tab.coerceIn(0, 3)) }
    fun toggleDarkMode() { _uiState.value = _uiState.value.copy(isDarkMode = !_uiState.value.isDarkMode) }
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

    fun openProfile(username: String) {
        if (isMe(username)) { _uiState.value = _uiState.value.copy(viewingProfile = _uiState.value.myProfile); return }
        viewModelScope.launch {
            val remoteProfile = profileRepository.fetchByUsername(username)
            if (remoteProfile != null) { _uiState.value = _uiState.value.copy(viewingProfile = remoteProfile); return@launch }
            showToast("User @${username.removePrefix("@")} was not found.")
        }
    }

    fun openProfileFromChat(username: String) {
        if (isMe(username)) {
            _uiState.value = _uiState.value.copy(viewingProfile = _uiState.value.myProfile, isConversationFullScreen = false, activeConversationPartner = null)
            return
        }
        viewModelScope.launch {
            val remote = profileRepository.fetchByUsername(username)
            if (remote != null) {
                _uiState.value = _uiState.value.copy(viewingProfile = remote, isConversationFullScreen = false, activeConversationPartner = null)
            } else {
                showToast("User @${username.removePrefix("@")} was not found.")
            }
        }
    }
    fun closeProfile() { _uiState.value = _uiState.value.copy(viewingProfile = null) }

    fun setFollowing(profile: UserProfile, shouldFollow: Boolean) {
        if (profile.id.isBlank() || isMe(profile.id) || isMe(profile.username)) return

        viewModelScope.launch {
            val previous = _uiState.value.followingUserIds
            val optimistic = if (shouldFollow) previous + profile.id else previous - profile.id
            _uiState.value = _uiState.value.copy(followingUserIds = optimistic)

            val success = supabaseService.setFollowing(profile.id, shouldFollow)
            if (!success) {
                _uiState.value = _uiState.value.copy(followingUserIds = previous)
                showToast(if (shouldFollow) "Couldn't follow @${profile.username}." else "Couldn't unfollow @${profile.username}.")
                return@launch
            }

            val followingIds = supabaseService.fetchFollowingIds()
            val refreshedTarget = profileRepository.fetchById(profile.id) ?: profile
            val myId = supabaseService.getCurrentUserId().orEmpty()
            val refreshedMe = myId.takeIf { it.isNotBlank() }?.let { profileRepository.fetchById(it) }

            val currentState = _uiState.value
            val refreshedProfiles = currentState.profiles.map { existing ->
                when {
                    existing.id == refreshedTarget.id -> refreshedTarget
                    refreshedMe != null && existing.id == refreshedMe.id -> refreshedMe
                    else -> existing
                }
            }

            _uiState.value = currentState.copy(
                followingUserIds = followingIds,
                viewingProfile = if (currentState.viewingProfile?.id == refreshedTarget.id) refreshedTarget else currentState.viewingProfile,
                myProfile = refreshedMe ?: currentState.myProfile,
                profiles = refreshedProfiles
            )
            refreshedMe?.let { saveLocalProfile(it) }
        }
    }

    fun updateProfile(updated: UserProfile) {
        viewModelScope.launch {
            try {
                if (profileRepository.updateProfile(updated)) {
                    val authoritative = profileRepository.fetchCurrent(updated.username) ?: updated
                    _uiState.value = _uiState.value.copy(myProfile = authoritative, isEditProfileOpen = false, viewingProfile = if (_uiState.value.viewingProfile?.username?.equals(authoritative.username, true) == true) authoritative else _uiState.value.viewingProfile)
                    saveLocalProfile(authoritative); updateLocalAuthorData(authoritative); showToast("✅ Profile saved successfully.")
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
        viewModelScope.launch {
            val success = runCatching { postRepository.togglePostLike(postId, nextLiked, nextCount) }.getOrDefault(false)
            if (success && nextLiked) {
                val target = (_uiState.value.posts + _uiState.value.reels).find { it.id == postId }
                if (target != null && target.author.isNotBlank()) supabaseService.recordActivity(target.author, "liked your post", NotificationFilter.LIKES, postId, targetType = "POST")
            } else if (!success) {
                _uiState.value = _uiState.value.copy(posts = _uiState.value.posts.map { if (it.id == postId) it.copy(isLiked = !nextLiked, likes = (it.likes + if (!nextLiked) 1 else -1).coerceAtLeast(0)) else it }, reels = _uiState.value.reels.map { if (it.id == postId) it.copy(isLiked = !nextLiked, likes = (it.likes + if (!nextLiked) 1 else -1).coerceAtLeast(0)) else it })
                showToast("Failed to update like.")
            }
        }
    }

    fun toggleBookmark(postId: String) {
        var next = false
        _uiState.value = _uiState.value.copy(posts = _uiState.value.posts.map { if (it.id == postId) { next = !it.isBookmarked; it.copy(isBookmarked = next) } else it }, reels = _uiState.value.reels.map { if (it.id == postId) { next = !it.isBookmarked; it.copy(isBookmarked = next) } else it })
        viewModelScope.launch { if (!runCatching { postRepository.togglePostBookmark(postId, next) }.getOrDefault(false)) { _uiState.value = _uiState.value.copy(posts = _uiState.value.posts.map { if (it.id == postId) it.copy(isBookmarked = !next) else it }, reels = _uiState.value.reels.map { if (it.id == postId) it.copy(isBookmarked = !next) else it }); showToast("Failed to update bookmark.") } }
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

        viewModelScope.launch {
            val deleted = runCatching { supabaseService.deleteFeedPost(postId) }.getOrDefault(false)
            if (deleted) {
                showToast(if (target.videoUrl.isNullOrBlank()) "Post deleted." else "Reel deleted.")
            } else {
                _uiState.value = _uiState.value.copy(posts = postsBefore, reels = reelsBefore)
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
            viewModelScope.launch { chatRepository.markConversationRead(clean) }
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
    fun closeConversation() { _uiState.value = _uiState.value.copy(activeConversationPartner = null, isConversationFullScreen = false) }

    fun sendMessage(partnerUsername: String, text: String, isFromMe: Boolean = true) {
        val cleanText = text.trim(); val cleanPartner = partnerUsername.trim(); if (cleanText.isBlank() || cleanPartner.isBlank()) return
        val uid = supabaseService.getCurrentUserId() ?: "local_user"; val currentUsername = supabaseService.getCurrentUsername() ?: "you"; val tempId = "temp_${UUID.randomUUID()}"
        appendMessageToState(cleanPartner, ChatMessage(id = tempId, senderId = uid, senderUsername = currentUsername, receiverUsername = cleanPartner, text = cleanText, timestamp = "Sending...", isFromMe = true, isRead = false, status = MessageStatus.SENDING))
        viewModelScope.launch {
            chatRepository.sendMessage(cleanPartner, cleanText).fold({ serverMsg -> replaceMessageInState(cleanPartner, tempId, serverMsg.copy(status = MessageStatus.SENT)); supabaseService.recordActivity(cleanPartner, "sent you a direct message", NotificationFilter.ALL, targetUsername = currentUsername, previewText = cleanText, targetType = "CHAT") }, { updateMessageStatusInState(cleanPartner, tempId, MessageStatus.FAILED); showToast("Failed to send message. Tap message to retry.") })
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
        if (failedMessage.status != MessageStatus.FAILED) return
        updateMessageStatusInState(partnerUsername, failedMessage.id, MessageStatus.SENDING)
        viewModelScope.launch {
            chatRepository.sendMessage(partnerUsername.trim(), failedMessage.text.trim()).fold({ serverMsg -> replaceMessageInState(partnerUsername, failedMessage.id, serverMsg.copy(status = MessageStatus.SENT)) }, { updateMessageStatusInState(partnerUsername, failedMessage.id, MessageStatus.FAILED); showToast("Retry failed. Check network connection.") })
        }
    }

    private fun handleRealtimeEvent(event: RealtimeEvent) {
        when (event) {
            is RealtimeEvent.MessageEvent -> handleIncomingRealtimeMessage(event.message)
            is RealtimeEvent.ConversationEvent -> viewModelScope.launch { _uiState.value = _uiState.value.copy(conversations = chatRepository.fetchConversations()) }
            is RealtimeEvent.NotificationEvent -> fetchSupabaseData()
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
                }
            }
        }
    }

    private fun handleIncomingRealtimeMessage(msg: ChatMessage) {
        val currentUsername = supabaseService.getCurrentUsername() ?: ""
        val partner = if (msg.isFromMe || (currentUsername.isNotBlank() && msg.senderUsername.equals(currentUsername, true))) msg.receiverUsername else msg.senderUsername
        if (partner.isBlank()) return
        val conversations = _uiState.value.conversations.toMutableList(); val index = conversations.indexOfFirst { it.partnerUsername.equals(partner, true) }; val active = _uiState.value.activeConversationPartner?.equals(partner, true) == true
        if (index >= 0) {
            val old = conversations[index]; val msgs = old.messages.toMutableList(); val existing = msgs.indexOfFirst { it.id == msg.id || (it.status == MessageStatus.SENDING && it.text == msg.text) }; if (existing >= 0) msgs[existing] = msg else msgs.add(msg)
            conversations[index] = old.copy(lastMessage = msg.text, lastMessageTime = msg.timestamp, lastMessageRawTime = msg.rawTimestamp, unreadCount = if (active) 0 else old.unreadCount + if (!msg.isFromMe) 1 else 0, messages = msgs.sortedBy { it.rawTimestamp.ifBlank { it.timestamp } }.toMutableList())
        } else conversations.add(0, ChatConversation("conv_$partner", partner, partnerName = partner.replace(".", " ").replace("_", " ").capitalizeWords(), partnerAvatar = "", lastMessage = msg.text, lastMessageTime = msg.timestamp, lastMessageRawTime = msg.rawTimestamp, unreadCount = if (!msg.isFromMe && !active) 1 else 0, messages = mutableListOf(msg)))
        _uiState.value = _uiState.value.copy(conversations = conversations)
        if (!msg.isFromMe && active) viewModelScope.launch { chatRepository.markConversationRead(partner) }
    }

    private fun appendMessageToState(partnerUsername: String, message: ChatMessage) {
        val conversations = _uiState.value.conversations.toMutableList(); val index = conversations.indexOfFirst { it.partnerUsername.equals(partnerUsername, true) }
        if (index >= 0) { val old = conversations[index]; conversations[index] = old.copy(lastMessage = message.text, lastMessageTime = message.timestamp, lastMessageRawTime = message.rawTimestamp, messages = (old.messages + message).toMutableList()) }
        else conversations.add(0, ChatConversation("conv_$partnerUsername", partnerUsername, partnerName = partnerUsername.replace(".", " ").replace("_", " ").capitalizeWords(), partnerAvatar = "", lastMessage = message.text, lastMessageTime = message.timestamp, messages = mutableListOf(message)))
        _uiState.value = _uiState.value.copy(conversations = conversations)
    }
    private fun replaceMessageInState(partnerUsername: String, oldId: String, newMsg: ChatMessage) { val conversations = _uiState.value.conversations.toMutableList(); val index = conversations.indexOfFirst { it.partnerUsername.equals(partnerUsername, true) }; if (index >= 0) { val old = conversations[index]; conversations[index] = old.copy(lastMessage = newMsg.text, lastMessageTime = newMsg.timestamp, lastMessageRawTime = newMsg.rawTimestamp, messages = old.messages.map { if (it.id == oldId) newMsg else it }.toMutableList()); _uiState.value = _uiState.value.copy(conversations = conversations) } }
    private fun updateMessageStatusInState(partnerUsername: String, messageId: String, status: MessageStatus) { val conversations = _uiState.value.conversations.toMutableList(); val index = conversations.indexOfFirst { it.partnerUsername.equals(partnerUsername, true) }; if (index >= 0) { val old = conversations[index]; conversations[index] = old.copy(messages = old.messages.map { if (it.id == messageId) it.copy(status = status) else it }.toMutableList()); _uiState.value = _uiState.value.copy(conversations = conversations) } }

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

    suspend fun claimDailySpin(): GameSpinResult? {
        val result = supabaseService.claimDailySpin()
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
            runCatching { authRepository.signOut() }
            SupabaseService.clearSession(); prefs.edit().clear().apply()
            _uiState.value = BlinkUiState(destination = AppDestination.SIGN_IN, isDarkMode = _uiState.value.isDarkMode)
            showToast("Logged out successfully.")
        }
    }

    fun openStory(story: Story) { val updated = _uiState.value.stories.map { if (it.id == story.id) it.copy(hasUnseen = false) else it }; _uiState.value = _uiState.value.copy(stories = updated, activeViewingStory = updated.find { it.id == story.id } ?: story.copy(hasUnseen = false)); markStoryViewed(story.id) }
    fun closeStory() { _uiState.value = _uiState.value.copy(activeViewingStory = null) }
    fun createStory(storyImage: String, caption: String, faculty: String = "") {
        val p=_uiState.value.myProfile
        val s=Story(UUID.randomUUID().toString(),p.username,p.avatarUrl,false,true,storyImage,caption,"Just now",faculty.ifBlank{p.faculty},p.university,0,false,p.verificationBadge)
        viewModelScope.launch(Dispatchers.IO){
            if(postRepository.createStory(s,false)){
                _uiState.value=_uiState.value.copy(stories=listOf(s)+_uiState.value.stories.filter{!it.isUser&&it.id!="story_me"})
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
                _uiState.value=_uiState.value.copy(stories=listOf(story)+_uiState.value.stories.filter{!it.isUser&&it.id!="story_me"},isCreateStoryOpen=false)
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
