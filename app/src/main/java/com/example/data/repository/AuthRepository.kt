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
        _authState.value = AuthState.Initial
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
            val result = supabaseService.signUpUser(cleanEmail, password, cleanUsername, cleanName, faculty)
            if (result.isSuccess) {
                val profile = result.getOrThrow()
                persistSession(profile)
                _authState.value = AuthState.Authenticated(profile, SupabaseService.accessToken())
                AuthResult.success(profile)
            } else {
                AuthResult.failure(result.exceptionOrNull()?.message ?: "Sign up failed.")
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "signUpWithEmail error", e)
            AuthResult.failure(e.message ?: "Sign up failed.")
        }
    }

    suspend fun signInWithEmail(emailOrUsername: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val cleanInput = emailOrUsername.trim().lowercase()
        if (cleanInput.isBlank() || password.isBlank()) {
            return@withContext AuthResult.failure("Please enter both email/username and password.")
        }
        try {
            val result = supabaseService.authenticateUser(cleanInput, password)
            if (result.isSuccess) {
                val profile = result.getOrThrow()
                persistSession(profile)
                _authState.value = AuthState.Authenticated(profile, SupabaseService.accessToken())
                AuthResult.success(profile)
            } else {
                AuthResult.failure(result.exceptionOrNull()?.message ?: "Invalid email or password.")
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "signInWithEmail error", e)
            AuthResult.failure(e.message ?: "Connection error. Please try again.")
        }
    }

    suspend fun signInWithGoogle(email: String): AuthResult = withContext(Dispatchers.IO) {
        _authState.value = AuthState.Unauthenticated()
        AuthResult.failure("Google sign-in is not connected to Supabase Auth yet. Configure the Google OAuth flow before enabling this button.")
    }

    suspend fun recoverPassword(email: String): Boolean = withContext(Dispatchers.IO) {
        supabaseService.recoverPassword(email)
    }

    fun markAuthenticated(profile: UserProfile) {
        val token = SupabaseService.accessToken()
        if (token.isNullOrBlank()) {
            _authState.value = AuthState.Unauthenticated("No Supabase session is available.")
            return
        }
        persistSession(profile)
        _authState.value = AuthState.Authenticated(profile, token)
    }

    fun signOut() {
        prefs.edit().clear().apply()
        SupabaseService.clearSession()
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