package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.models.ContactField
import com.example.data.models.UserProfile
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class AuthState {
    object Initial : AuthState()
    object Loading : AuthState()
    data class Authenticated(val userProfile: UserProfile, val token: String? = null) : AuthState()
    data class Unauthenticated(val message: String? = null) : AuthState()
}

data class AuthResult(
    val isSuccess: Boolean,
    val userProfile: UserProfile? = null,
    val errorMessage: String? = null
) {
    companion object {
        fun success(profile: UserProfile): AuthResult = AuthResult(true, userProfile = profile)
        fun failure(error: String): AuthResult = AuthResult(false, errorMessage = error)
    }
}

class AuthRepository(
    private val context: Context,
    private val supabaseService: SupabaseService = SupabaseService()
) {
    private val prefs = context.getSharedPreferences("blink_auth_prefs", Context.MODE_PRIVATE)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = SupabaseConfig.url.trimEnd('/')
    private val anonKey = SupabaseConfig.anonKey

    init {
        checkCachedSession()
    }

    private fun checkCachedSession() {
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        if (isLoggedIn) {
            val email = prefs.getString("email", "golowosile@gmail.com") ?: "golowosile@gmail.com"
            val fullName = prefs.getString("full_name", "Gbolahan Olowosile") ?: "Gbolahan Olowosile"
            val username = prefs.getString("username", "golowosile") ?: "golowosile"
            val faculty = prefs.getString("faculty", "SIMME") ?: "SIMME"
            val university = prefs.getString("university", "University of Lagos") ?: "University of Lagos"
            val avatarUrl = prefs.getString("avatar_url", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300&h=300&fit=crop") ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300&h=300&fit=crop"

            val cachedProfile = UserProfile(
                email = ContactField(email, true),
                fullName = fullName,
                username = username,
                faculty = faculty,
                university = university,
                avatarUrl = avatarUrl
            )
            _authState.value = AuthState.Authenticated(cachedProfile)
        } else {
            _authState.value = AuthState.Unauthenticated()
        }
    }

    suspend fun signUpWithEmail(
        email: String,
        password: String,
        username: String,
        fullName: String? = null,
        faculty: String = "SIMME"
    ): AuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val cleanUsername = username.trim().lowercase().replace("@", "")
        val cleanName = fullName?.trim()?.ifBlank { null } ?: cleanUsername.replace(".", " ")

        if (cleanEmail.isBlank() || password.isBlank() || cleanUsername.isBlank()) {
            return@withContext AuthResult.failure("Please complete all required fields.")
        }

        try {
            val result = supabaseService.signUpUser(
                email = cleanEmail,
                password = password,
                username = cleanUsername,
                fullName = cleanName,
                faculty = faculty
            )

            if (result.isSuccess) {
                val profile = result.getOrThrow()
                persistSession(profile)
                _authState.value = AuthState.Authenticated(profile)
                return@withContext AuthResult.success(profile)
            } else {
                val error = result.exceptionOrNull()?.message ?: "Sign up failed."
                Log.e("AuthRepository", "signUpWithEmail failed: $error")
                return@withContext AuthResult.failure(error)
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "signUpWithEmail error: ${e.message}")
            return@withContext AuthResult.failure(e.message ?: "Sign up failed.")
        }
    }

    suspend fun signInWithEmail(
        emailOrUsername: String,
        password: String
    ): AuthResult = withContext(Dispatchers.IO) {
        val cleanInput = emailOrUsername.trim().lowercase()
        if (cleanInput.isBlank() || password.isBlank()) {
            return@withContext AuthResult.failure("Please enter both email/username and password.")
        }

        try {
            val result = supabaseService.authenticateUser(cleanInput, password)
            if (result.isSuccess) {
                val profile = result.getOrThrow()
                persistSession(profile)
                _authState.value = AuthState.Authenticated(profile)
                return@withContext AuthResult.success(profile)
            } else {
                return@withContext AuthResult.failure(result.exceptionOrNull()?.message ?: "Invalid email or password.")
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "signInWithEmail error: ${e.message}")
            return@withContext AuthResult.failure("Connection error. Please try again.")
        }
    }

    /**
     * Handles Google OAuth / Google sign in asynchronously and updates authState.
     * Note: Navigation is handled reactively by observing authState in ViewModel/UI.
     */
    suspend fun signInWithGoogle(email: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            _authState.value = AuthState.Loading
            val userId = supabaseService.getCurrentUserId() ?: "user_g_${System.currentTimeMillis()}"
            val profile = supabaseService.getOrCreateGoogleProfile(userId = userId, email = email)
            persistSession(profile)
            _authState.value = AuthState.Authenticated(profile)
            return@withContext AuthResult.success(profile)
        } catch (e: Exception) {
            Log.e("AuthRepository", "signInWithGoogle error: ${e.message}")
            _authState.value = AuthState.Unauthenticated(e.message)
            return@withContext AuthResult.failure(e.message ?: "Google sign in failed.")
        }
    }

    suspend fun recoverPassword(email: String): Boolean = withContext(Dispatchers.IO) {
        supabaseService.recoverPassword(email)
    }

    fun signOut() {
        prefs.edit().clear().apply()
        _authState.value = AuthState.Unauthenticated()
    }

    private fun persistSession(profile: UserProfile) {
        prefs.edit().apply {
            putBoolean("is_logged_in", true)
            putString("email", profile.email.value)
            putString("full_name", profile.fullName)
            putString("username", profile.username)
            putString("faculty", profile.faculty)
            putString("university", profile.university)
            putString("avatar_url", profile.avatarUrl)
            putString("cover_url", profile.coverPhotoUrl)
            apply()
        }
    }
}
