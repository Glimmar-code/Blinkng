package com.blinkng.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blinkng.desktop.data.DesktopAdminCapability
import com.blinkng.desktop.data.DesktopProfile
import com.blinkng.desktop.data.DesktopSession
import com.blinkng.desktop.data.DesktopSupabaseClient
import com.blinkng.desktop.data.DesktopUserSettings
import com.blinkng.shared.BlinkOnboardingPolicy
import com.blinkng.shared.discovery.BlinkUniversitySearch

class DesktopAppState(
    val client: DesktopSupabaseClient = DesktopSupabaseClient(),
) {
    var initialized by mutableStateOf(false)
        private set
    var session by mutableStateOf<DesktopSession?>(client.session)
        private set
    var profile by mutableStateOf<DesktopProfile?>(null)
        private set
    var adminCapability by mutableStateOf(DesktopAdminCapability(false, null, false))
        private set
    var settings by mutableStateOf<DesktopUserSettings?>(null)
        private set
    var selectedRoute by mutableStateOf("home")
    var globalSearch by mutableStateOf("")
    var showNewStudentSuggestions by mutableStateOf(false)
    private var pendingNewMemberDiscovery = false
    var busy by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    suspend fun initialize() {
        if (initialized) return
        busy = true
        try {
            session = client.restoreSession()
            if (session != null) loadAuthenticatedState()
        } catch (t: Throwable) {
            errorMessage = t.message
        } finally {
            busy = false
            initialized = true
        }
    }

    suspend fun signIn(email: String, password: String) = authAction {
        session = client.signIn(email, password)
        loadAuthenticatedState()
    }

    suspend fun signUp(email: String, password: String, fullName: String) = authAction {
        session = client.signUp(email, password, fullName)
        loadAuthenticatedState()
    }

    suspend fun signUpWithReferral(
        email: String, password: String, fullName: String,
        referrerId: String?, birthDate: String,
    ) = authAction {
        require(com.blinkng.shared.discovery.BlinkBirthdayPolicy.isValid(birthDate)) {
            "Enter a valid birthday (YYYY-MM-DD)."
        }
        session = client.signUpWithReferral(email, password, fullName, referrerId)
        if (session != null) {
            runCatching { client.savePrivateBirthDate(birthDate) }
                .onFailure { errorMessage = "Birthday wasn't saved. Update your private birthday in profile settings." }
        }
        pendingNewMemberDiscovery = session != null
        loadAuthenticatedState()
    }

    suspend fun signUp(email: String, password: String, username: String, fullName: String) = authAction {
        session = client.signUp(email, password, username, fullName)
        loadAuthenticatedState()
    }

    suspend fun signInWithGoogle() = authAction {
        session = client.signInWithGoogle()
        loadAuthenticatedState()
    }

    suspend fun sendPasswordReset(email: String) {
        busy = true
        errorMessage = null
        try {
            client.sendPasswordReset(email)
        } catch (t: Throwable) {
            errorMessage = t.message ?: "Could not send the password reset email."
            throw t
        } finally {
            busy = false
        }
    }

    fun signOut() {
        client.signOut()
        session = null
        profile = null
        settings = null
        adminCapability = DesktopAdminCapability(false, null, false)
        selectedRoute = "home"
        globalSearch = ""
        pendingNewMemberDiscovery = false
        showNewStudentSuggestions = false
    }

    // Retry after a restored session's profile request fails, without trapping
    // the user in the loading state or discarding their saved sign-in.
    suspend fun retryProfileLoad() {
        if (session == null || busy) return
        busy = true
        errorMessage = null
        try {
            loadAuthenticatedState()
        } catch (t: Throwable) {
            profile = null
            errorMessage = t.message ?: "Could not load your profile. Please retry."
        } finally {
            busy = false
        }
    }

    suspend fun refreshProfile() {
        profile = client.refreshProfile()
    }

    suspend fun checkOnboardingUsername(username: String): Pair<Boolean, String> {
        val validation = BlinkOnboardingPolicy.usernameValidationMessage(username)
        if (validation != null) return false to validation
        return runCatching {
            val available = client.isUsernameAvailable(username)
            available to if (available) "Username available" else "Username already taken."
        }.getOrElse {
            false to (it.message ?: "Unable to check username.")
        }
    }

    suspend fun saveOnboardingUsername(username: String) = profileAction {
        profile = client.saveOnboardingProfile(
            username = username,
            onboardingStep = 1,
            onboardingCompleted = false,
        )
    }

    suspend fun saveOnboardingBasics(
        university: String,
        department: String,
        level: String,
        gender: String,
        birthDate: String,
        notStudent: Boolean = false,
    ) = profileAction {
        val canonicalUniversity = if (notStudent) "" else BlinkUniversitySearch.canonical(university)
            ?: throw IllegalArgumentException("Select a university from the BLINK directory.")
        require(notStudent || department.trim().isNotBlank()) { "Choose your department." }
        require(gender in BlinkOnboardingPolicy.genders) { "Choose a valid gender option." }
        client.savePrivateBirthDate(birthDate)
        profile = client.saveOnboardingProfile(
            university = canonicalUniversity,
            accountType = if (notStudent) BlinkOnboardingPolicy.NON_STUDENT else "STUDENT",
            department = if (notStudent) "" else department,
            academicLevel = if (notStudent) "" else level,
            gender = gender,
            onboardingStep = 2,
            onboardingCompleted = false,
        )
    }

    suspend fun saveOnboardingInterests(interests: List<String>) = profileAction {
        profile = client.saveOnboardingProfile(
            interests = interests,
            onboardingStep = 3,
            onboardingCompleted = false,
        )
    }

    suspend fun onboardingSuggestions(): List<DesktopProfile> =
        client.fetchOnboardingSuggestions()

    suspend fun onboardingFollowingIds(): Set<String> =
        client.fetchFollowingIds()

    suspend fun setOnboardingFollowing(profileId: String, shouldFollow: Boolean): Set<String> =
        client.setFollowing(profileId, shouldFollow)

    suspend fun finishOnboarding() = profileAction {
        val following = client.fetchFollowingIds()
        require(following.size >= BlinkOnboardingPolicy.REQUIRED_FOLLOWS) {
            "Follow at least ${BlinkOnboardingPolicy.REQUIRED_FOLLOWS} people to continue."
        }
        val current = profile ?: client.refreshProfile()
        require(current.username.isNotBlank()) { "Choose your username." }
        if (BlinkOnboardingPolicy.campusRequired(current.accountType)) {
            require(!current.university.isNullOrBlank()) { "Choose your university." }
            require(!current.department.isNullOrBlank()) { "Choose your department." }
        }
        require(!current.gender.isNullOrBlank()) { "Choose your gender." }
        require(current.interests.isNotEmpty()) { "Choose at least one interest." }

        profile = client.saveOnboardingProfile(
            onboardingStep = BlinkOnboardingPolicy.TOTAL_STEPS,
            onboardingCompleted = true,
        )
        if (pendingNewMemberDiscovery) {
            showNewStudentSuggestions = true
            pendingNewMemberDiscovery = false
        }
    }

    suspend fun updateSettings(value: DesktopUserSettings) {
        client.saveSettings(value)
        settings = value
    }

    fun clearError() {
        errorMessage = null
    }

    private suspend fun loadAuthenticatedState() {
        profile = client.fetchProfile()
        settings = runCatching { client.fetchSettings() }.getOrNull()
        adminCapability = client.fetchAdminCapability()
    }

    private suspend fun profileAction(block: suspend () -> Unit) {
        busy = true
        errorMessage = null
        try {
            block()
        } catch (t: Throwable) {
            errorMessage = t.message ?: "Unable to save your profile."
            throw t
        } finally {
            busy = false
        }
    }

    private suspend fun authAction(block: suspend () -> Unit) {
        busy = true
        errorMessage = null
        try {
            block()
        } catch (t: Throwable) {
            errorMessage = t.message ?: "Authentication failed."
            throw t
        } finally {
            busy = false
        }
    }
}
