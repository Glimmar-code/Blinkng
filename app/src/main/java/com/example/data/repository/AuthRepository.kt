package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.auth.AccountSessionStore
import com.example.auth.GoogleAuthCallbackActivity
import com.example.data.models.ContactField
import com.example.data.models.UserProfile
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import com.example.notification.BlinkFirebaseMessagingService
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

sealed class AuthState { object Initial : AuthState(); object Loading : AuthState(); data class Authenticated(val userProfile: UserProfile, val token: String? = null) : AuthState(); data class Unauthenticated(val message: String? = null) : AuthState() }

data class AuthResult(val isSuccess: Boolean, val userProfile: UserProfile? = null, val errorMessage: String? = null) {
    companion object { fun success(profile: UserProfile): AuthResult = AuthResult(true, userProfile = profile); fun failure(error: String): AuthResult = AuthResult(false, errorMessage = error) }
}

class AuthRepository(private val context: Context, private val supabaseService: SupabaseService = SupabaseService()) {
    private val prefs = context.getSharedPreferences("blink_auth_prefs", Context.MODE_PRIVATE)
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS).build()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = SupabaseConfig.url.trimEnd('/')
    private val anonKey = SupabaseConfig.anonKey

    suspend fun signUpWithEmail(email: String, password: String, username: String, fullName: String? = null, faculty: String = "SIMME"): AuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase(); val cleanUsername = username.trim().lowercase().removePrefix("@"); val cleanName = fullName?.trim()?.ifBlank { null } ?: cleanUsername.replace(".", " ")
        if (cleanEmail.isBlank() || password.isBlank() || cleanUsername.isBlank()) return@withContext AuthResult.failure("Please complete all required fields.")
        if (!cleanUsername.matches(Regex("^[a-z0-9][a-z0-9._-]{1,29}$"))) {
            return@withContext AuthResult.failure("Use 2–30 lowercase letters, numbers, dots, dashes or underscores for your username.")
        }
        if (cleanName.length !in 2..60 || cleanName.equals("Blink User", ignoreCase = true)) {
            return@withContext AuthResult.failure("Please choose a display name between 2 and 60 characters.")
        }
        val availability = supabaseService.checkProfileIdentity(cleanUsername, cleanName)
        if (availability?.usernameAvailable == false) {
            return@withContext AuthResult.failure("That username is already taken. Please choose another one.")
        }
        if (availability?.fullNameAvailable == false) {
            return@withContext AuthResult.failure("That display name is already in use. Add a middle name or another identifier.")
        }
        try { val result = supabaseService.signUpUser(cleanEmail, password, cleanUsername, cleanName, faculty); if (result.isSuccess) { val profile = result.getOrThrow(); persistSession(profile); _authState.value = AuthState.Authenticated(profile, SupabaseService.accessToken()); AuthResult.success(profile) } else AuthResult.failure(result.exceptionOrNull()?.message ?: "Sign up failed.") } catch (e: Exception) { Log.e("AuthRepository", "signUpWithEmail error", e); AuthResult.failure(e.message ?: "Sign up failed.") }
    }

    suspend fun signInWithEmail(emailOrUsername: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val cleanInput = emailOrUsername.trim().lowercase(); if (cleanInput.isBlank() || password.isBlank()) return@withContext AuthResult.failure("Please enter both email/username and password.")
        try {
            val isEmail = cleanInput.contains("@") && !cleanInput.startsWith("@")
            val result = if (isEmail) {
                supabaseService.authenticateUser(cleanInput, password)
            } else {
                authenticateWithUsername(cleanInput, password)
            }
            if (result.isSuccess) { val profile = result.getOrThrow(); persistSession(profile); _authState.value = AuthState.Authenticated(profile, SupabaseService.accessToken()); AuthResult.success(profile) } else AuthResult.failure(result.exceptionOrNull()?.message ?: "Invalid email/username or password.")
        } catch (e: Exception) { Log.e("AuthRepository", "signInWithEmail error", e); AuthResult.failure(e.message ?: "Connection error. Please try again.") }
    }

    private suspend fun authenticateWithUsername(username: String, password: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val cleanUsername = username.trim().removePrefix("@").lowercase()
            if (cleanUsername.isBlank()) return@withContext Result.failure(Exception("Username is required."))

            val body = JSONObject().apply {
                put("username", cleanUsername)
                put("password", password)
            }
            val request = Request.Builder()
                .url("$baseUrl/functions/v1/username-login")
                .addHeader("apikey", anonKey)
                .addHeader("Accept", "application/json")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val serverMessage = runCatching { JSONObject(raw).optString("error") }.getOrNull()?.takeIf { it.isNotBlank() }
                    return@withContext Result.failure(Exception(serverMessage ?: "Invalid email/username or password."))
                }

                val auth = JSONObject(raw)
                val accessToken = auth.optString("access_token", "")
                val refreshToken = auth.optString("refresh_token", "")
                val user = auth.optJSONObject("user")
                if (accessToken.isBlank() || user == null) {
                    return@withContext Result.failure(Exception("Supabase did not return a valid authenticated session."))
                }

                SupabaseService.saveSession(accessToken = accessToken, refreshToken = refreshToken)

                val userId = user.optString("id", "")
                val userEmail = user.optString("email", "")
                if (userId.isBlank() || userEmail.isBlank()) {
                    SupabaseService.clearSession()
                    return@withContext Result.failure(Exception("Supabase did not return a complete user session."))
                }

                val metadata = user.optJSONObject("user_metadata")
                val resolvedUsername = metadata?.optString("username", "")?.trim()?.takeIf { it.isNotBlank() } ?: cleanUsername
                val fullName = metadata?.optString("full_name", "")?.trim()?.takeIf { it.isNotBlank() }
                    ?: metadata?.optString("name", "")?.trim()?.takeIf { it.isNotBlank() }
                    ?: resolvedUsername

                val profile = supabaseService.ensureAuthenticatedProfile(
                    userId = userId,
                    email = userEmail,
                    username = resolvedUsername,
                    fullName = fullName,
                    faculty = metadata?.optString("faculty", "")?.trim()?.takeIf { it.isNotBlank() },
                    university = metadata?.optString("university", "")?.trim()?.takeIf { it.isNotBlank() }
                )
                Result.success(profile)
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "authenticateWithUsername error", e)
            Result.failure(Exception(e.message ?: "Authentication failed."))
        }
    }

    /**
     * Opens Blink's native Android Google account chooser. The actual Google ID token
     * is handled by GoogleAuthCallbackActivity and exchanged server-side by Supabase Auth.
     * This deliberately replaces the old browser/redirect based OAuth flow.
     */
    suspend fun signInWithGoogle(email: String): AuthResult = withContext(Dispatchers.Main) {
        try {
            context.startActivity(
                Intent(context, GoogleAuthCallbackActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            AuthResult.failure("GOOGLE_OAUTH_STARTED")
        } catch (e: Exception) {
            Log.e("AuthRepository", "Unable to launch native Google sign-in", e)
            AuthResult.failure("Unable to open Google sign-in on this device.")
        }
    }

    /**
     * Verifies the Google ID token with Supabase Auth and converts it into the same
     * Supabase access/refresh session used by email/username login and all existing RLS.
     */
    suspend fun signInWithGoogleIdToken(idToken: String, nonce: String? = null): AuthResult = withContext(Dispatchers.IO) {
        if (idToken.isBlank()) return@withContext AuthResult.failure("Google did not return a valid ID token.")

        try {
            val body = JSONObject().apply {
                put("provider", "google")
                put("id_token", idToken)
                nonce?.takeIf { it.isNotBlank() }?.let { put("nonce", it) }
            }

            val request = Request.Builder()
                .url("$baseUrl/auth/v1/token?grant_type=id_token")
                .addHeader("apikey", anonKey)
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching {
                        JSONObject(raw).let { error ->
                            error.optString("error_description")
                                .ifBlank { error.optString("msg") }
                                .ifBlank { error.optString("message") }
                                .ifBlank { error.optString("error") }
                        }
                    }.getOrNull().orEmpty()
                    Log.e("AuthRepository", "Google ID-token exchange failed status=${response.code} body=${raw.take(400)}")
                    return@withContext AuthResult.failure(
                        message.ifBlank { "Google authentication could not be verified by Supabase." }
                    )
                }

                val auth = JSONObject(raw)
                val accessToken = auth.optString("access_token", "")
                val refreshToken = auth.optString("refresh_token", "")
                val user = auth.optJSONObject("user")
                if (accessToken.isBlank() || user == null) {
                    return@withContext AuthResult.failure("Supabase did not return a valid Google session.")
                }

                val userId = user.optString("id", "")
                val userEmail = user.optString("email", "").trim().lowercase()
                if (userId.isBlank() || userEmail.isBlank()) {
                    return@withContext AuthResult.failure("Google account information was incomplete.")
                }

                SupabaseService.saveSession(
                    accessToken = accessToken,
                    refreshToken = refreshToken.ifBlank { null }
                )

                val metadata = user.optJSONObject("user_metadata")
                val displayName = metadata?.optString("full_name", "")?.trim()?.takeIf { it.isNotBlank() }
                    ?: metadata?.optString("name", "")?.trim()?.takeIf { it.isNotBlank() }
                    ?: userEmail.substringBefore('@')
                val avatarUrl = metadata?.optString("avatar_url", "")?.trim()?.takeIf { it.isNotBlank() }
                    ?: metadata?.optString("picture", "")?.trim()?.takeIf { it.isNotBlank() }

                val profile = try {
                    supabaseService.getOrCreateGoogleProfile(
                        userId = userId,
                        email = userEmail,
                        displayName = displayName,
                        avatarUrl = avatarUrl
                    )
                } catch (profileError: Exception) {
                    SupabaseService.clearSession()
                    throw profileError
                }

                persistSession(profile)
                _authState.value = AuthState.Authenticated(profile, accessToken)
                AuthResult.success(profile)
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Native Google sign-in error", e)
            AuthResult.failure(e.message ?: "Google authentication failed.")
        }
    }

    suspend fun recoverPassword(email: String): Boolean = withContext(Dispatchers.IO) { supabaseService.recoverPassword(email) }

    fun markAuthenticated(profile: UserProfile) {
        val token = SupabaseService.accessToken()
        if (token.isNullOrBlank()) { _authState.value = AuthState.Unauthenticated("No Supabase session is available."); return }
        persistSession(profile)
        _authState.value = AuthState.Authenticated(profile, token)
    }

    suspend fun signOut() {
        val rememberedIdentifier = prefs.getString("email", "").orEmpty()
            .ifBlank { prefs.getString("username", "").orEmpty() }
        AccountSessionStore.rememberIdentifier(context.applicationContext, rememberedIdentifier)
        AccountSessionStore.setSignInRequired(context.applicationContext, true)

        try {
            supabaseService.revokeCurrentSupabaseSession()
        } catch (e: Exception) {
            Log.w("AuthRepository", "Supabase logout failed", e)
        } finally {
            SupabaseService.clearSession()
        }

        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }.onFailure { Log.w("AuthRepository", "Unable to clear Google credential state", it) }

        prefs.edit().clear().apply()
        context.getSharedPreferences("blink_user_session", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_logged_in", false)
            .apply()
        _authState.value = AuthState.Unauthenticated()
    }

    private fun persistSession(profile: UserProfile) {
        prefs.edit().apply { putBoolean("is_logged_in", true); putString("email", profile.email.value); putString("full_name", profile.fullName); putString("username", profile.username); putString("faculty", profile.faculty); putString("university", profile.university); putString("avatar_url", profile.avatarUrl); putString("cover_url", profile.coverPhotoUrl); apply() }
        AccountSessionStore.rememberIdentifier(
            context.applicationContext,
            profile.email.value.ifBlank { profile.username }
        )
        AccountSessionStore.setSignInRequired(context.applicationContext, false)
        AccountSessionStore.recordCurrentSession(context.applicationContext, profile.id, profile.username, profile.fullName, profile.email.value, profile.avatarUrl)
        BlinkFirebaseMessagingService.syncCurrentToken(context.applicationContext)
    }
}
