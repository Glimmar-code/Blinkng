package com.example.data.supabase

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.data.models.AchievementBadge
import com.example.data.models.ChatConversation
import com.example.data.models.ChatMessage
import com.example.data.models.ContactField
import com.example.data.models.FeedPost
import com.example.data.models.LeaderboardUser
import com.example.data.models.MarketItem
import com.example.data.models.PollOption
import com.example.data.models.PostPoll
import com.example.data.models.SocialLinks
import com.example.data.models.Story
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

class SupabaseService {

    companion object {
        private const val TAG = "SupabaseService"

        private const val PREFS =
            "blink_supabase_session"

        private const val ACCESS_TOKEN =
            "access_token"

        private const val REFRESH_TOKEN =
            "refresh_token"

        private var appContext: Context? = null

        /**
         * Call once from BlinkApplication.onCreate()
         */
        fun initialize(context: Context) {
            appContext = context.applicationContext
        }

        /**
         * Save the real authenticated Supabase session.
         *
         * IMPORTANT:
         * These are the tokens returned by Supabase Auth.
         * Never put the anon key in here.
         */
        fun saveSession(
            accessToken: String?,
            refreshToken: String? = null
        ) {
            val context = appContext ?: return

            context
                .getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )
                .edit()
                .apply {

                    if (!accessToken.isNullOrBlank()) {
                        putString(
                            ACCESS_TOKEN,
                            accessToken
                        )
                    }

                    if (!refreshToken.isNullOrBlank()) {
                        putString(
                            REFRESH_TOKEN,
                            refreshToken
                        )
                    }

                    apply()
                }
        }

        fun clearSession() {
            appContext
                ?.getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )
                ?.edit()
                ?.clear()
                ?.apply()
        }

        fun accessToken(): String? {
            return appContext
                ?.getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )
                ?.getString(
                    ACCESS_TOKEN,
                    null
                )
        }

        fun refreshToken(): String? {
            return appContext
                ?.getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )
                ?.getString(
                    REFRESH_TOKEN,
                    null
                )
        }
    }

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(
                30,
                TimeUnit.SECONDS
            )
            .readTimeout(
                60,
                TimeUnit.SECONDS
            )
            .writeTimeout(
                60,
                TimeUnit.SECONDS
            )
            .build()

    private val jsonMediaType =
        "application/json; charset=utf-8".toMediaType()

    private val baseUrl =
        SupabaseConfig.url.trimEnd('/')

    private val anonKey =
        SupabaseConfig.anonKey

    // ============================================================
    // HTTP
    // ============================================================

    /**
     * Builds a Supabase REST/Storage/Auth request.
     *
     * authenticated = true:
     *   Uses the current user's JWT when available.
     *
     * authenticated = false:
     *   Uses only the anon key.
     */
    private fun newRequestBuilder(
        endpoint: String,
        authenticated: Boolean = true
    ): Request.Builder {

        val fullUrl =
            if (endpoint.startsWith("http")) {
                endpoint
            } else {
                "$baseUrl$endpoint"
            }

        val builder =
            Request.Builder()
                .url(fullUrl)
                .addHeader(
                    "apikey",
                    anonKey
                )
                .addHeader(
                    "Accept",
                    "application/json"
                )
                
        if (authenticated) {
            builder.addHeader("X-Authenticated", "true")
        }

        val token =
            if (authenticated) {
                accessToken()
                    ?.takeIf { it.isNotBlank() }
                    ?: anonKey
            } else {
                anonKey
            }

        builder.addHeader(
            "Authorization",
            "Bearer $token"
        )

        return builder
    }

    
    private val refreshMutex = Mutex()

    private suspend fun executeRequest(request: Request): okhttp3.Response {
        val isAuthenticated = request.header("X-Authenticated") == "true"
        var activeRequest = request
        if (isAuthenticated) {
            activeRequest = request.newBuilder().removeHeader("X-Authenticated").build()
        }

        var response = withContext(Dispatchers.IO) { client.newCall(activeRequest).execute() }
        
        if (isAuthenticated && response.code == 401) {
            response.close()
            refreshMutex.withLock {
                val currentToken = activeRequest.header("Authorization")?.removePrefix("Bearer ") ?: ""
                val storedToken = accessToken() ?: anonKey
                
                if (storedToken != anonKey && storedToken != currentToken) {
                    // Token was refreshed by another thread
                    activeRequest = activeRequest.newBuilder()
                        .header("Authorization", "Bearer $storedToken")
                        .build()
                    response = withContext(Dispatchers.IO) { client.newCall(activeRequest).execute() }
                } else {
                    val refreshed = refreshSession()
                    if (refreshed) {
                        val refreshedToken = accessToken() ?: anonKey
                        activeRequest = activeRequest.newBuilder()
                            .header("Authorization", "Bearer $refreshedToken")
                            .build()
                        response = withContext(Dispatchers.IO) { client.newCall(activeRequest).execute() }
                    } else {
                        clearSession()
                        throw java.io.IOException("Unauthorized - Refresh failed")
                    }
                }
            }
        }
        return response
    }

    private fun requestJson(
        builder: Request.Builder
    ): Request {
        return builder.build()
    }

    // ============================================================
    // AUTHENTICATION
    // ============================================================

    suspend fun authenticateUser(
        emailOrUsername: String,
        password: String
    ): Result<UserProfile> =
        withContext(Dispatchers.IO) {

            try {

                val cleanInput =
                    emailOrUsername
                        .trim()
                        .lowercase(Locale.US)

                if (cleanInput.isBlank()) {
                    return@withContext Result.failure(
                        Exception("Email or username is required.")
                    )
                }

                val loginEmail =
                    if (cleanInput.contains("@")) {
                        cleanInput
                    } else {
                        /*
                         * IMPORTANT:
                         * If Blink supports username login through a custom
                         * username -> email lookup, that should happen here.
                         *
                         * This fallback is retained for compatibility with
                         * your existing project.
                         */
                        "$cleanInput@student.university.edu.ng"
                    }

                val body =
                    JSONObject().apply {
                        put(
                            "email",
                            loginEmail
                        )
                        put(
                            "password",
                            password
                        )
                    }

                val request =
                    newRequestBuilder(
                        "/auth/v1/token?grant_type=password",
                        authenticated = false
                    )
                        .post(
                            body.toString()
                                .toRequestBody(
                                    jsonMediaType
                                )
                        )
                        .build()

                executeRequest(request).use { response ->

                    val responseBody =
                        response.body
                            ?.string()
                            .orEmpty()

                    if (!response.isSuccessful) {

                        Log.e(
                            TAG,
                            "AUTH_SIGN_IN failed " +
                                    "status=${response.code} " +
                                    "body=$responseBody"
                        )

                        return@withContext Result.failure(
                            Exception(
                                parseSupabaseError(
                                    responseBody,
                                    "Invalid email or password."
                                )
                            )
                        )
                    }

                    val auth =
                        JSONObject(
                            responseBody
                        )

                    val accessToken =
                        auth.optString(
                            "access_token",
                            ""
                        )

                    val refreshToken =
                        auth.optString(
                            "refresh_token",
                            ""
                        )

                    val user =
                        auth.optJSONObject(
                            "user"
                        )

                    if (
                        accessToken.isBlank() ||
                        user == null
                    ) {

                        return@withContext Result.failure(
                            Exception(
                                "Supabase did not return a valid authenticated session."
                            )
                        )
                    }

                    saveSession(
                        accessToken =
                            accessToken,
                        refreshToken =
                            refreshToken
                    )

                    val userId =
                        user.optString(
                            "id",
                            ""
                        )

                    val userEmail =
                        user.optString(
                            "email",
                            loginEmail
                        )

                    val metadata =
                        user.optJSONObject(
                            "user_metadata"
                        )

                    val username =
                        metadata
                            ?.optString(
                                "username",
                                ""
                            )
                            ?.trim()
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?: userEmail
                                .substringBefore("@")
                                .lowercase(
                                    Locale.US
                                )
                                .replace(
                                    ".",
                                    "_"
                                )

                    val fullName =
                        metadata
                            ?.optString(
                                "full_name",
                                metadata.optString(
                                    "name",
                                    ""
                                )
                            )
                            ?.trim()
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?: username

                    val profile =
                        ensureAuthenticatedProfile(
                            userId =
                                userId,
                            email =
                                userEmail,
                            username =
                                username,
                            fullName =
                                fullName,
                            faculty =
                                metadata
                                    ?.optString(
                                        "faculty",
                                        ""
                                    ),
                            university =
                                metadata
                                    ?.optString(
                                        "university",
                                        ""
                                    )
                        )

                    Result.success(
                        profile
                    )
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "AUTH_SIGN_IN exception",
                    e
                )

                Result.failure(
                    Exception(
                        e.message
                            ?: "Authentication failed."
                    )
                )
            }
        }

    /**
     * Creates a new user in Supabase Auth via /auth/v1/signup, saves the JWT session,
     * and initializes the user profile in the profiles table.
     */
    suspend fun signUpUser(
        email: String,
        password: String,
        username: String,
        fullName: String,
        faculty: String = "SIMME",
        university: String = "University of Lagos"
    ): Result<UserProfile> =
        withContext(Dispatchers.IO) {
            try {
                val cleanEmail = email.trim().lowercase(Locale.US)
                if (cleanEmail.isBlank()) {
                    return@withContext Result.failure(Exception("Email address is required."))
                }
                val cleanUsername = username.trim().lowercase(Locale.US).replace("@", "").replace(" ", "_")
                val cleanFullName = fullName.trim().ifBlank { cleanUsername }

                val body = JSONObject().apply {
                    put("email", cleanEmail)
                    put("password", password)
                    put("data", JSONObject().apply {
                        put("username", cleanUsername)
                        put("full_name", cleanFullName)
                        put("name", cleanFullName)
                        put("faculty", faculty)
                        put("university", university)
                    })
                }

                val request = newRequestBuilder(
                    "/auth/v1/signup",
                    authenticated = false
                )
                    .post(body.toString().toRequestBody(jsonMediaType))
                    .build()

                executeRequest(request).use { response ->
                    val responseBody = response.body?.string().orEmpty()

                    if (!response.isSuccessful) {
                        Log.e(TAG, "AUTH_SIGN_UP failed status=${response.code} body=$responseBody")
                        if (responseBody.contains("already registered", ignoreCase = true) ||
                            responseBody.contains("already exists", ignoreCase = true)
                        ) {
                            return@withContext authenticateUser(cleanEmail, password)
                        }

                        return@withContext Result.failure(
                            Exception(parseSupabaseError(responseBody, "Sign up failed."))
                        )
                    }

                    val json = JSONObject(responseBody)
                    val accessToken = json.optString("access_token", "")
                    val refreshToken = json.optString("refresh_token", "")
                    val userObj = json.optJSONObject("user") ?: json
                    val userId = userObj.optString("id", "")

                    if (accessToken.isNotBlank()) {
                        saveSession(accessToken = accessToken, refreshToken = refreshToken)
                    }

                    val profile = ensureAuthenticatedProfile(
                        userId = userId.ifBlank { "user_${System.currentTimeMillis()}" },
                        email = cleanEmail,
                        username = cleanUsername,
                        fullName = cleanFullName,
                        faculty = faculty,
                        university = university
                    )

                    Result.success(profile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "AUTH_SIGN_UP exception", e)
                Result.failure(Exception(e.message ?: "Sign up failed."))
            }
        }

    /**
     * Refreshes the current access token using Supabase's refresh token.
     *
     * Call this when a request returns HTTP 401, or at application/session
     * startup when you want to restore an expired access token.
     */
    suspend fun refreshSession(): Boolean =
        withContext(Dispatchers.IO) {

            try {

                val storedRefreshToken =
                    refreshToken()
                        ?: return@withContext false

                if (
                    storedRefreshToken.isBlank()
                ) {
                    return@withContext false
                }

                val body =
                    JSONObject().apply {
                        put(
                            "refresh_token",
                            storedRefreshToken
                        )
                    }

                val request =
                    newRequestBuilder(
                        "/auth/v1/token?grant_type=refresh_token",
                        authenticated = false
                    )
                        .post(
                            body.toString()
                                .toRequestBody(
                                    jsonMediaType
                                )
                        )
                        .build()

                executeRequest(request).use { response ->

                    val responseBody =
                        response.body
                            ?.string()
                            .orEmpty()

                    if (!response.isSuccessful) {

                        Log.e(
                            TAG,
                            "AUTH_REFRESH failed " +
                                    "status=${response.code} " +
                                    "body=$responseBody"
                        )

                        return@withContext false
                    }

                    val json =
                        JSONObject(
                            responseBody
                        )

                    val newAccessToken =
                        json.optString(
                            "access_token",
                            ""
                        )

                    val newRefreshToken =
                        json.optString(
                            "refresh_token",
                            ""
                        )

                    if (
                        newAccessToken.isBlank()
                    ) {
                        return@withContext false
                    }

                    saveSession(
                        accessToken =
                            newAccessToken,
                        refreshToken =
                            newRefreshToken
                                .ifBlank {
                                    storedRefreshToken
                                }
                    )

                    true
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "AUTH_REFRESH exception",
                    e
                )

                false
            }
        }

    /**
     * Restores the stored session.
     *
     * If no access token exists, tries the refresh token.
     */
    suspend fun restoreSession(): Boolean =
        withContext(Dispatchers.IO) {

            val access =
                accessToken()

            if (
                !access.isNullOrBlank()
            ) {

                // Check the JWT expiry when possible.
                val expiry =
                    jwtExpirationMillis(
                        access
                    )

                if (
                    expiry == null ||
                    expiry >
                    System.currentTimeMillis() +
                    30_000L
                ) {
                    return@withContext true
                }
            }

            refreshSession()
        }

    suspend fun recoverPassword(
        email: String
    ): Boolean =
        withContext(Dispatchers.IO) {

            try {

                val body =
                    JSONObject().apply {
                        put(
                            "email",
                            email
                                .trim()
                                .lowercase(
                                    Locale.US
                                )
                        )
                    }

                val request =
                    newRequestBuilder(
                        "/auth/v1/recover",
                        authenticated = false
                    )
                        .post(
                            body.toString()
                                .toRequestBody(
                                    jsonMediaType
                                )
                        )
                        .build()

                executeRequest(request).use { response ->

                    val responseBody =
                        response.body
                            ?.string()
                            .orEmpty()

                    if (!response.isSuccessful) {

                        Log.e(
                            TAG,
                            "AUTH_RECOVER failed " +
                                    "status=${response.code} " +
                                    "body=$responseBody"
                        )
                    }

                    response.isSuccessful ||
                            response.code == 429
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "AUTH_RECOVER exception",
                    e
                )

                false
            }
        }

    fun signOut() {
        clearSession()
    }

    // ============================================================
    // SESSION / CURRENT USER
    // ============================================================

    fun getCurrentUserId(): String? {

        val token =
            accessToken()
                ?: return null

        return decodeJwtSubject(
            token
        )
    }

    fun isAuthenticated(): Boolean {
        return !getCurrentUserId().isNullOrBlank()
    }

    suspend fun checkServerStatus(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/")
                .addHeader("apikey", SupabaseConfig.anonKey)
                .get()
                .build()
            executeRequest(request).use { response ->
                response.isSuccessful || response.code == 401 || response.code == 400 || response.code == 404
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkServerStatus failed", e)
            false
        }
    }

    // ============================================================
    // PROFILE
    // ============================================================

    fun isValidUuid(str: String?): Boolean {
        if (str.isNullOrBlank() || str.length != 36) return false
        return try {
            UUID.fromString(str)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun ensureAuthenticatedProfile(
        userId: String,
        email: String,
        username: String,
        fullName: String,
        faculty: String? = null,
        university: String? = null
    ): UserProfile =
        withContext(Dispatchers.IO) {

            val cleanUsername =
                username
                    .trim()
                    .lowercase(
                        Locale.US
                    )
                    .replace(
                        " ",
                        "_"
                    )

            val cleanFullName =
                fullName
                    .trim()
                    .ifBlank {
                        cleanUsername
                    }

            val validUserId = when {
                isValidUuid(userId) -> userId
                isValidUuid(getCurrentUserId()) -> getCurrentUserId()!!
                else -> UUID.randomUUID().toString()
            }

            val existing =
                if (isValidUuid(userId)) {
                    fetchProfileById(userId)
                } else {
                    fetchProfileByUsername(cleanUsername) ?: fetchProfileByEmail(email)
                }

            if (existing != null) {
                return@withContext existing
            }

            val json =
                JSONObject().apply {
                    put(
                        "id",
                        validUserId
                    )

                    put(
                        "email",
                        email.trim()
                            .lowercase(
                                Locale.US
                            )
                    )

                    put(
                        "username",
                        cleanUsername
                    )

                    put(
                        "full_name",
                        cleanFullName
                    )

                    if (
                        !faculty.isNullOrBlank()
                    ) {
                        put(
                            "faculty",
                            faculty.trim()
                        )
                    }

                    if (
                        !university.isNullOrBlank()
                    ) {
                        put(
                            "university",
                            university.trim()
                        )
                    }

                    put(
                        "updated_at",
                        nowIso()
                    )
                }

            val request =
                newRequestBuilder(
                    "/rest/v1/profiles",
                    authenticated = true
                )
                    .addHeader(
                        "Prefer",
                        "resolution=merge-duplicates,return=representation"
                    )
                    .post(
                        json.toString()
                            .toRequestBody(
                                jsonMediaType
                            )
                    )
                    .build()

            executeRequest(request).use { response ->

                val body =
                    response.body
                        ?.string()
                        .orEmpty()

                if (!response.isSuccessful) {
                    if (response.code != 401 && response.code != 409) {
                        Log.w(
                            TAG,
                            "PROFILE_CREATE status=${response.code} body=$body"
                        )
                    }
                } else {
                    Log.d(
                        TAG,
                        "PROFILE_CREATE success userId=$validUserId"
                    )
                }
            }

            fetchProfileById(
                validUserId
            ) ?: UserProfile(
                id =
                    validUserId,
                fullName =
                    cleanFullName,
                username =
                    cleanUsername,
                email =
                    ContactField(
                        email,
                        true
                    ),
                faculty =
                    faculty
                        .orEmpty(),
                university =
                    university
                        .orEmpty()
            )
        }

    suspend fun getOrCreateGoogleProfile(
        userId: String,
        email: String,
        displayName: String? = null,
        avatarUrl: String? = null
    ): UserProfile =
        withContext(Dispatchers.IO) {

            val cleanEmail =
                email
                    .trim()
                    .lowercase(
                        Locale.US
                    )

            val existing =
                if (isValidUuid(userId)) {
                    fetchProfileById(userId)
                } else {
                    fetchProfileByEmail(cleanEmail)
                }

            if (existing != null) {

                return@withContext existing.copy(
                    email =
                        ContactField(
                            cleanEmail,
                            true
                        )
                )
            }

            val derivedUsername =
                cleanEmail
                    .substringBefore("@")
                    .replace(
                        ".",
                        "_"
                    )
                    .replace(
                        " ",
                        "_"
                    )
                    .lowercase(
                        Locale.US
                    )

            val name =
                displayName
                    ?.trim()
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: cleanEmail
                        .substringBefore("@")
                        .replace(
                            ".",
                            " "
                        )
                        .capitalizeWords()

            val profile =
                ensureAuthenticatedProfile(
                    userId =
                        userId,
                    email =
                        cleanEmail,
                    username =
                        derivedUsername,
                    fullName =
                        name
                )

            /*
             * If Google returned a profile photo, persist it into the
             * user's profile if one was not already present.
             */
            if (
                !avatarUrl.isNullOrBlank() &&
                profile.avatarUrl.isBlank()
            ) {

                val updated =
                    profile.copy(
                        avatarUrl =
                            avatarUrl
                    )

                updateProfile(
                    updated
                )

                return@withContext updated
            }

            profile
        }

    suspend fun fetchProfileById(
        userId: String
    ): UserProfile? =
        withContext(Dispatchers.IO) {

            try {

                if (
                    userId.isBlank()
                ) {
                    return@withContext null
                }

                // If not a valid UUID string (e.g. "user_me", "user_g_123", or username),
                // query by username/email instead of sending id=eq to Postgres.
                if (!isValidUuid(userId)) {
                    val cleanUser = userId.removePrefix("user_").trim()
                    if (cleanUser.isNotBlank() && cleanUser != "me") {
                        return@withContext fetchProfileByUsername(cleanUser) ?: fetchProfileByEmail(cleanUser)
                    }
                    return@withContext null
                }

                val encoded =
                    encodeValue(
                        userId
                    )

                val request =
                    newRequestBuilder(
                        "/rest/v1/profiles" +
                                "?id=eq.$encoded" +
                                "&select=*" +
                                "&limit=1"
                    )
                        .get()
                        .build()

                executeRequest(request).use { response ->

                    val body =
                        response.body
                            ?.string()
                            .orEmpty()

                    if (!response.isSuccessful) {
                        if (response.code != 401 && response.code != 404) {
                            Log.w(
                                TAG,
                                "PROFILE_FETCH_BY_ID status=${response.code} body=$body"
                            )
                        }
                        return@withContext null
                    }

                    if (
                        body.isBlank() ||
                        body == "[]"
                    ) {
                        return@withContext null
                    }

                    val array =
                        JSONArray(body)

                    if (
                        array.length() == 0
                    ) {
                        null
                    } else {
                        parseUserProfile(
                            array.getJSONObject(0)
                        )
                    }
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "PROFILE_FETCH_BY_ID exception",
                    e
                )

                null
            }
        }

    suspend fun fetchProfileByEmail(
        email: String
    ): UserProfile? =
        withContext(Dispatchers.IO) {

            try {

                val cleanEmail =
                    email
                        .trim()
                        .lowercase(
                            Locale.US
                        )

                if (
                    cleanEmail.isBlank()
                ) {
                    return@withContext null
                }

                val encoded =
                    encodeValue(
                        cleanEmail
                    )

                val request =
                    newRequestBuilder(
                        "/rest/v1/profiles" +
                                "?email=eq.$encoded" +
                                "&select=*" +
                                "&limit=1"
                    )
                        .get()
                        .build()

                executeRequest(request).use { response ->

                    val body =
                        response.body
                            ?.string()
                            .orEmpty()

                    if (!response.isSuccessful) {
                        return@withContext null
                    }

                    if (
                        body.isBlank() ||
                        body == "[]"
                    ) {
                        return@withContext null
                    }

                    val array =
                        JSONArray(body)

                    if (
                        array.length() == 0
                    ) {
                        null
                    } else {
                        parseUserProfile(
                            array.getJSONObject(0)
                        )
                    }
                }

            } catch (e: Exception) {
                null
            }
        }

    suspend fun fetchProfileByUsername(
        username: String
    ): UserProfile? =
        withContext(Dispatchers.IO) {

            try {

                val cleanUsername =
                    username
                        .trim()
                        .removePrefix("@")
                        .lowercase(
                            Locale.US
                        )

                if (
                    cleanUsername.isBlank()
                ) {
                    return@withContext null
                }

                val encoded =
                    encodeValue(
                        cleanUsername
                    )

                val request =
                    newRequestBuilder(
                        "/rest/v1/profiles" +
                                "?username=eq.$encoded" +
                                "&select=*" +
                                "&limit=1"
                    )
                        .get()
                        .build()

                executeRequest(request).use { response ->

                    val body =
                        response.body
                            ?.string()
                            .orEmpty()

                    if (!response.isSuccessful) {

                        Log.e(
                            TAG,
                            "PROFILE_FETCH_BY_USERNAME failed " +
                                    "status=${response.code} " +
                                    "body=$body"
                        )

                        return@withContext null
                    }

                    if (
                        body.isBlank() ||
                        body == "[]"
                    ) {
                        return@withContext null
                    }

                    val array =
                        JSONArray(body)

                    if (
                        array.length() == 0
                    ) {
                        null
                    } else {
                        parseUserProfile(
                            array.getJSONObject(0)
                        )
                    }
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "PROFILE_FETCH_BY_USERNAME exception",
                    e
                )

                null
            }
        }

    suspend fun searchProfiles(
        query: String
    ): List<UserProfile> =
        withContext(Dispatchers.IO) {

            try {

                val cleanQuery =
                    query.trim()

                if (
                    cleanQuery.isBlank()
                ) {
                    return@withContext emptyList()
                }

                val encoded =
                    encodeValue(
                        cleanQuery
                    )

                val request =
                    newRequestBuilder(
                        "/rest/v1/profiles" +
                                "?or=(" +
                                "username.ilike.*$encoded*," +
                                "full_name.ilike.*$encoded*" +
                                ")" +
                                "&select=*" +
                                "&limit=30"
                    )
                        .get()
                        .build()

                executeRequest(request).use { response ->

                    val body =
                        response.body
                            ?.string()
                            .orEmpty()

                    if (!response.isSuccessful) {

                        Log.e(
                            TAG,
                            "PROFILE_SEARCH failed " +
                                    "status=${response.code} " +
                                    "body=$body"
                        )

                        return@withContext emptyList()
                    }

                    if (
                        body.isBlank() ||
                        body == "[]"
                    ) {
                        return@withContext emptyList()
                    }

                    val array =
                        JSONArray(body)

                    buildList {

                        for (
                            i in 0 until array.length()
                        ) {

                            add(
                                parseUserProfile(
                                    array.getJSONObject(i)
                                )
                            )
                        }
                    }
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "PROFILE_SEARCH exception",
                    e
                )

                emptyList()
            }
        }

    /**
     * Updates the existing profile by auth user ID.
     *
     * This is intentionally NOT:
     *
     * profiles?username=eq.username
     *
     * because username can change.
     */
    suspend fun updateProfile(
        profile: UserProfile
    ): Boolean =
        withContext(Dispatchers.IO) {

            try {
                val currentUserId =
                    getCurrentUserId()
                        ?: profile.id.takeIf { it.isNotBlank() }

                val json =
                    JSONObject().apply {

                        put(
                            "full_name",
                            profile.fullName.trim()
                        )

                        put(
                            "username",
                            profile.username
                                .trim()
                                .lowercase(
                                    Locale.US
                                )
                        )

                        put(
                            "avatar_url",
                            profile.avatarUrl
                        )

                        put(
                            "cover_photo",
                            profile.coverPhotoUrl
                        )

                        put(
                            "professional_headline",
                            profile.professionalHeadline
                        )

                        put(
                            "current_job_title",
                            profile.currentJobTitle
                        )

                        put(
                            "bio",
                            profile.bio
                        )

                        put(
                            "university",
                            profile.university
                        )

                        put(
                            "faculty",
                            profile.faculty
                        )

                        put(
                            "department",
                            profile.department
                        )

                        put(
                            "course_of_study",
                            profile.courseOfStudy
                        )

                        put(
                            "academic_level",
                            profile.academicLevel
                        )

                        put(
                            "graduation_year",
                            profile.graduationYear
                        )

                        put(
                            "country_of_origin",
                            profile.countryOfOrigin
                        )

                        put(
                            "current_city_state",
                            profile.currentCityState
                        )

                        put(
                            "email",
                            profile.email.value
                        )

                        put(
                            "phone",
                            profile.phone.value
                        )

                        put(
                            "whatsapp",
                            profile.whatsapp.value
                        )

                        put(
                            "website",
                            profile.links.website
                        )

                        put(
                            "linkedin",
                            profile.links.linkedin
                        )

                        put(
                            "twitter",
                            profile.links.twitter
                        )

                        put(
                            "instagram",
                            profile.links.instagram
                        )

                        put(
                            "featured_link",
                            profile.links.featuredLink
                        )

                        put(
                            "featured_link_label",
                            profile.links.featuredLinkLabel
                        )

                        put(
                            "favorite_quote",
                            profile.favoriteQuote
                        )

                        put(
                            "custom_status",
                            profile.availability.label
                        )

                        put(
                            "profile_views_this_week",
                            profile.profileViewsThisWeek
                        )

                        put(
                            "verification_badge",
                            profile.verificationBadge.name
                        )

                        put(
                            "is_verified",
                            profile.verificationBadge != VerificationBadge.NONE
                        )

                        put(
                            "updated_at",
                            nowIso()
                        )
                    }

                // 1. Try update by user_id if present and valid UUID
                if (!currentUserId.isNullOrBlank() && isValidUuid(currentUserId)) {
                    val encodedId = encodeValue(currentUserId)
                    val request = newRequestBuilder("/rest/v1/profiles?id=eq.$encodedId", authenticated = true)
                        .addHeader("Prefer", "return=representation")
                        .patch(json.toString().toRequestBody(jsonMediaType))
                        .build()

                    val (success, body) = executeRequest(request).use { resp ->
                        Pair(resp.isSuccessful, resp.body?.string().orEmpty())
                    }
                    if (success && body.isNotBlank() && body != "[]") {
                        Log.d(TAG, "PROFILE_UPDATE success by ID: $currentUserId")
                        return@withContext true
                    }
                }

                // 2. Try update by username
                val cleanUser = profile.username.trim().lowercase(Locale.US)
                if (cleanUser.isNotBlank()) {
                    val reqUser = newRequestBuilder("/rest/v1/profiles?username=eq.${encodeValue(cleanUser)}", authenticated = true)
                        .addHeader("Prefer", "return=representation")
                        .patch(json.toString().toRequestBody(jsonMediaType))
                        .build()

                    val (success, body) = executeRequest(reqUser).use { resp ->
                        Pair(resp.isSuccessful, resp.body?.string().orEmpty())
                    }
                    if (success && body.isNotBlank() && body != "[]") {
                        Log.d(TAG, "PROFILE_UPDATE success by username: $cleanUser")
                        return@withContext true
                    }
                }

                // 3. Upsert via POST
                val validProfileId = if (isValidUuid(currentUserId)) {
                    currentUserId!!
                } else if (isValidUuid(getCurrentUserId())) {
                    getCurrentUserId()!!
                } else {
                    UUID.randomUUID().toString()
                }

                val upsertJson = JSONObject(json.toString()).apply {
                    put("id", validProfileId)
                }
                val upsertReq = newRequestBuilder("/rest/v1/profiles", authenticated = true)
                    .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                    .post(upsertJson.toString().toRequestBody(jsonMediaType))
                    .build()

                val (upsertSuccess, _) = executeRequest(upsertReq).use { resp ->
                    Pair(resp.isSuccessful, resp.body?.string().orEmpty())
                }

                Log.d(TAG, "PROFILE_UPDATE upsert result: $upsertSuccess")
                true

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "PROFILE_UPDATE exception",
                    e
                )
                true
            }
        }

    // ============================================================
    // PROFILE MEDIA / STORAGE
    // ============================================================

    suspend fun uploadProfileMedia(
        userId: String,
        bytes: ByteArray,
        mimeType: String,
        type: ProfileMediaType
    ): String? =
        withContext(Dispatchers.IO) {

            try {

                if (
                    userId.isBlank()
                ) {
                    Log.e(
                        TAG,
                        "STORAGE_UPLOAD rejected: empty user ID"
                    )

                    return@withContext null
                }

                if (
                    bytes.isEmpty()
                ) {
                    Log.e(
                        TAG,
                        "STORAGE_UPLOAD rejected: empty byte array"
                    )

                    return@withContext null
                }

                val extension =
                    when {

                        mimeType.contains(
                            "png",
                            ignoreCase = true
                        ) ->
                            "png"

                        mimeType.contains(
                            "webp",
                            ignoreCase = true
                        ) ->
                            "webp"

                        mimeType.contains(
                            "heic",
                            ignoreCase = true
                        ) ->
                            "heic"

                        mimeType.contains(
                            "jpeg",
                            ignoreCase = true
                        ) ->
                            "jpg"

                        else ->
                            "jpg"
                    }

                val folder =
                    when (type) {

                        ProfileMediaType.AVATAR ->
                            "users/$userId/avatar"

                        ProfileMediaType.COVER ->
                            "users/$userId/cover"
                    }

                val path =
                    "$folder/${UUID.randomUUID()}.$extension"

                val request =
                    newRequestBuilder(
                        "/storage/v1/object/profile-media/$path",
                        authenticated = true
                    )
                        .addHeader(
                            "Content-Type",
                            mimeType
                        )
                        .addHeader(
                            "x-upsert",
                            "true"
                        )
                        .post(
                            bytes.toRequestBody(
                                mimeType.toMediaType()
                            )
                        )
                        .build()

                executeRequest(request).use { response ->

                    val body =
                        response.body
                            ?.string()
                            .orEmpty()

                    if (!response.isSuccessful) {

                        Log.e(
                            TAG,
                            "STORAGE_UPLOAD failed " +
                                    "status=${response.code} " +
                                    "path=$path " +
                                    "body=$body"
                        )

                        return@withContext null
                    }

                    /*
                     * This URL works if the profile-media bucket is PUBLIC.
                     *
                     * If your bucket is private, don't expose this URL.
                     * In that case we should generate a signed URL instead.
                     */
                    val publicUrl =
                        "$baseUrl/storage/v1/object/public/profile-media/$path"

                    Log.d(
                        TAG,
                        "STORAGE_UPLOAD success path=$path"
                    )

                    publicUrl
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "STORAGE_UPLOAD exception",
                    e
                )

                null
            }
        }

    // ============================================================
    // FEED
    // ============================================================

    suspend fun fetchFeedPosts(): List<FeedPost> =
        withContext(Dispatchers.IO) {

            try {

                val request =
                    newRequestBuilder(
                        "/rest/v1/feed_posts" +
                                "?select=*" +
                                "&order=created_at.desc" +
                                "&limit=100"
                    )
                        .get()
                        .build()

                executeRequest(request).use { response ->

                    val body =
                        response.body
                            ?.string()
                            .orEmpty()

                    if (!response.isSuccessful) {
                        if (response.code != 401) {
                            Log.e(
                                TAG,
                                "FEED_FETCH failed " +
                                        "status=${response.code} " +
                                        "body=$body"
                            )
                        } else {
                            Log.d(
                                TAG,
                                "FEED_FETCH: table protected by RLS; requires authenticated session."
                            )
                        }

                        return@withContext emptyList()
                    }

                    if (
                        body.isBlank() ||
                        body == "[]"
                    ) {
                        return@withContext emptyList()
                    }

                    val json =
                        JSONArray(
                            body
                        )

                    buildList {

                        for (
                            i in 0 until json.length()
                        ) {

                            parseFeedPost(
                                json.getJSONObject(i)
                            )?.let {
                                add(it)
                            }
                        }
                    }
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "FEED_FETCH exception",
                    e
                )

                emptyList()
            }
        }

    /**
     * Creates a feed post using the authenticated Supabase user's ID.
     */
    suspend fun createFeedPost(
        author: String,
        authorAvatar: String,
        facultyTag: String,
        text: String,
        imageUrl: String?,
        videoUrl: String? = null,
        tags: List<String> = emptyList(),
        mentions: List<String> = emptyList(),
        poll: PostPoll? = null,
        isReel: Boolean = false
    ): Boolean =
        withContext(Dispatchers.IO) {

            try {
                val userId =
                    getCurrentUserId()
                        ?: author.trim().lowercase(Locale.US).ifBlank { "user_student" }

                val json =
                    JSONObject().apply {

                        put(
                            "user_id",
                            userId
                        )

                        put(
                            "type",
                            when {
                                isReel || !videoUrl.isNullOrBlank() -> "reel"
                                !imageUrl.isNullOrBlank() -> "photo"
                                else -> "text"
                            }
                        )

                        put(
                            "faculty",
                            facultyTag
                                .trim()
                                .ifBlank {
                                    "SIMME"
                                }
                        )

                        put(
                            "text",
                            text.trim()
                        )

                        put(
                            "content",
                            text.trim()
                        )

                        if (!imageUrl.isNullOrBlank()) {
                            put(
                                "image_url",
                                imageUrl
                            )
                            put(
                                "media_url",
                                imageUrl
                            )
                        }

                        if (!videoUrl.isNullOrBlank()) {
                            put(
                                "video_url",
                                videoUrl
                            )
                        }

                        put(
                            "is_reel",
                            isReel
                        )

                        put(
                            "like_count",
                            0
                        )

                        put(
                            "comment_count",
                            0
                        )

                        put(
                            "share_count",
                            0
                        )

                        put(
                            "view_count",
                            0
                        )

                        put(
                            "username",
                            author
                        )

                        put(
                            "author",
                            author
                        )

                        put(
                            "avatar_url",
                            authorAvatar
                        )

                        put(
                            "author_avatar",
                            authorAvatar
                        )

                        if (tags.isNotEmpty()) {
                            put(
                                "tags",
                                JSONArray(tags)
                            )
                        }

                        if (mentions.isNotEmpty()) {
                            put(
                                "mentions",
                                JSONArray(mentions)
                            )
                        }

                        if (poll != null) {
                            val pollJson = JSONObject()
                            pollJson.put("question", poll.question)
                            val options = JSONArray()
                            poll.options.forEach { option ->
                                options.put(
                                    JSONObject().apply {
                                        put("id", option.id)
                                        put("text", option.text)
                                        put("votes", option.votes)
                                    }
                                )
                            }
                            pollJson.put("options", options)
                            put("poll_data", pollJson.toString())
                        }
                    }

                val request =
                    newRequestBuilder(
                        "/rest/v1/feed_posts",
                        authenticated = true
                    )
                        .addHeader(
                            "Prefer",
                            "return=representation"
                        )
                        .post(
                            json.toString()
                                .toRequestBody(
                                    jsonMediaType
                                )
                        )
                        .build()

                executeRequest(request).use { response ->

                    val body =
                        response.body
                            ?.string()
                            .orEmpty()

                    if (!response.isSuccessful) {
                        Log.e(
                            TAG,
                            "POST_CREATE failed " +
                                    "status=${response.code} " +
                                    "body=$body"
                        )
                    } else {
                        Log.d(
                            TAG,
                            "POST_CREATE success userId=$userId"
                        )
                    }

                    response.isSuccessful
                }

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "POST_CREATE exception",
                    e
                )
                true
            }
        }

    // ============================================================
    // POST MEDIA UPLOAD
    // ============================================================

    suspend fun uploadPostMedia(
        userId: String,
        bytes: ByteArray,
        mimeType: String,
        isVideo: Boolean
    ): String? =
        withContext(Dispatchers.IO) {

            try {

                if (
                    userId.isBlank() ||
                    bytes.isEmpty()
                ) {
                    return@withContext null
                }

                val extension =
                    when {

                        mimeType.contains(
                            "mp4",
                            true
                        ) ->
                            "mp4"

                        mimeType.contains(
                            "webm",
                            true
                        ) ->
                            "webm"

                        mimeType.contains(
                            "png",
                            true
                        ) ->
                            "png"

                        mimeType.contains(
                            "webp",
                            true
                        ) ->
                            "webp"

                        else ->
                            if (isVideo)
                                "mp4"
                            else
                                "jpg"
                    }

                val folder =
                    if (isVideo) {
                        "users/$userId/posts/videos"
                    } else {
                        "users/$userId/posts/images"
                    }

                val path =
                    "$folder/${UUID.randomUUID()}.$extension"

                val request =
                    newRequestBuilder(
                        "/storage/v1/object/feed-media/$path",
                        authenticated = true
                    )
                        .addHeader(
                            "Content-Type",
                            mimeType
                        )
                        .addHeader(
                            "x-upsert",
                            "true"
                        )
                        .post(
                            bytes.toRequestBody(
                                mimeType.toMediaType()
                            )
                        )
                        .build()

                executeRequest(request).use { response ->

                    val body =
                        response.body
                            ?.string()
                            .orEmpty()

                    if (!response.isSuccessful) {

                        Log.e(
                            TAG,
                            "POST_MEDIA_UPLOAD failed " +
                                    "status=${response.code} " +
                                    "body=$body"
                        )

                        return@withContext null
                    }

                    "$baseUrl/storage/v1/object/public/feed-media/$path"
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "POST_MEDIA_UPLOAD exception",
                    e
                )

                null
            }
        }

    // ============================================================
    // POST VIEW
    // ============================================================

    suspend fun recordPostView(
        postId: String,
        viewerUsername: String
    ): Int =
        withContext(Dispatchers.IO) {

            try {

                val json =
                    JSONObject().apply {

                        put(
                            "p_post_id",
                            postId
                        )

                        put(
                            "p_viewer_username",
                            viewerUsername
                        )
                    }

                val request =
                    newRequestBuilder(
                        "/rest/v1/rpc/record_post_view",
                        authenticated = true
                    )
                        .post(
                            json.toString()
                                .toRequestBody(
                                    jsonMediaType
                                )
                        )
                        .build()

                executeRequest(request).use { response ->

                    val body =
                        response.body
                            ?.string()
                            .orEmpty()

                    if (!response.isSuccessful) {

                        Log.e(
                            TAG,
                            "POST_VIEW failed " +
                                    "status=${response.code} " +
                                    "body=$body"
                        )

                        return@withContext 0
                    }

                    body.toIntOrNull()
                        ?: 1
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "POST_VIEW exception",
                    e
                )

                0
            }
        }

    // ============================================================
    // POST LIKES & DELETION
    // ============================================================

    /**
     * Persists a post like/unlike action to both post_likes table (for user-specific state)
     * and updates like_count in feed_posts table using authenticated JWT headers.
     */
    suspend fun togglePostLike(
        postId: String,
        liked: Boolean,
        newLikeCount: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentUserId = getCurrentUserId() ?: ""
            val currentUsername = appContext
                ?.getSharedPreferences("blink_auth_prefs", Context.MODE_PRIVATE)
                ?.getString("username", "") ?: ""

            // 1. Manage user record in post_likes table
            if (liked) {
                val likeObj = JSONObject().apply {
                    put("post_id", postId)
                    if (currentUserId.isNotBlank()) {
                        put("user_id", currentUserId)
                    }
                    if (currentUsername.isNotBlank()) {
                        put("username", currentUsername)
                    }
                }
                val likeRequest = newRequestBuilder(
                    "/rest/v1/post_likes",
                    authenticated = true
                )
                    .addHeader("Prefer", "resolution=merge-duplicates")
                    .post(likeObj.toString().toRequestBody(jsonMediaType))
                    .build()
                executeRequest(likeRequest).close()
            } else {
                val filter = when {
                    currentUserId.isNotBlank() -> "user_id=eq.$currentUserId"
                    currentUsername.isNotBlank() -> "username=eq.$currentUsername"
                    else -> ""
                }
                val deleteUrl = if (filter.isNotBlank()) {
                    "/rest/v1/post_likes?post_id=eq.$postId&$filter"
                } else {
                    "/rest/v1/post_likes?post_id=eq.$postId"
                }
                val unlikeRequest = newRequestBuilder(
                    deleteUrl,
                    authenticated = true
                )
                    .delete()
                    .build()
                executeRequest(unlikeRequest).close()
            }

            // 2. Persist updated like count to feed_posts table
            val updateJson = JSONObject().apply {
                put("like_count", newLikeCount.coerceAtLeast(0))
                put("likes", newLikeCount.coerceAtLeast(0))
            }
            val updateRequest = newRequestBuilder(
                "/rest/v1/feed_posts?id=eq.$postId",
                authenticated = true
            )
                .patch(updateJson.toString().toRequestBody(jsonMediaType))
                .build()

            executeRequest(updateRequest).use { resp ->
                resp.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "togglePostLike failed for postId=$postId", e)
            false
        }
    }

    suspend fun deleteFeedPost(
        postId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = newRequestBuilder(
                "/rest/v1/feed_posts?id=eq.$postId",
                authenticated = true
            )
                .delete()
                .build()

            executeRequest(request).use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteFeedPost failed for postId=$postId", e)
            false
        }
    }

    // ============================================================
    // STORIES
    // ============================================================

    suspend fun fetchStories(): List<Story> = withContext(Dispatchers.IO) {
        try {
            val request = newRequestBuilder(
                "/rest/v1/stories?select=*&order=created_at.desc&limit=30",
                authenticated = true
            )
                .get()
                .build()

            executeRequest(request).use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank() || body == "[]") {
                    return@withContext emptyList()
                }

                val array = JSONArray(body)
                val list = mutableListOf<Story>()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id", UUID.randomUUID().toString())
                    val username = obj.optString("username", "Student")
                    val avatar = obj.optString("avatar", obj.optString("avatar_url", ""))
                    val storyImage = obj.optString("story_image", obj.optString("image_url", ""))
                    val caption = obj.optString("caption", "")
                    val faculty = obj.optString("faculty", "SIMME")
                    val university = obj.optString("university", "University of Lagos")
                    val likesCount = obj.optInt("likes_count", obj.optInt("likes", 0))

                    list.add(
                        Story(
                            id = id,
                            username = username,
                            avatar = avatar.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300&h=300&fit=crop" },
                            hasUnseen = true,
                            storyImage = storyImage,
                            caption = caption,
                            faculty = faculty,
                            university = university,
                            likesCount = likesCount
                        )
                    )
                }
                list
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchStories exception", e)
            emptyList()
        }
    }

    suspend fun createStory(story: Story): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("id", story.id)
                put("username", story.username)
                put("avatar", story.avatar)
                put("story_image", story.storyImage)
                put("caption", story.caption)
                put("faculty", story.faculty)
                put("university", story.university)
                put("likes_count", story.likesCount)
            }
            val request = newRequestBuilder(
                "/rest/v1/stories",
                authenticated = true
            )
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            executeRequest(request).use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "createStory exception", e)
            false
        }
    }

    // ============================================================
    // PROFILES LIST
    // ============================================================

    suspend fun fetchProfiles():
        List<UserProfile> =
        withContext(Dispatchers.IO) {

            try {

                val request =
                    newRequestBuilder(
                        "/rest/v1/profiles" +
                                "?select=*" +
                                "&limit=100"
                    )
                        .get()
                        .build()

                executeRequest(request).use { response ->

                    val body =
                        response.body
                            ?.string()
                            .orEmpty()

                    if (!response.isSuccessful) {
                        return@withContext emptyList()
                    }

                    if (
                        body.isBlank() ||
                        body == "[]"
                    ) {
                        return@withContext emptyList()
                    }

                    val array =
                        JSONArray(body)

                    buildList {

                        for (
                            i in 0 until array.length()
                        ) {

                            add(
                                parseUserProfile(
                                    array.getJSONObject(i)
                                )
                            )
                        }
                    }
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "PROFILES_FETCH exception",
                    e
                )

                emptyList()
            }
        }

    // ============================================================
    // LEADERBOARD
    // ============================================================

    suspend fun fetchLeaderboard():
        List<LeaderboardUser> =
        withContext(Dispatchers.IO) {

            try {

                val request =
                    newRequestBuilder(
                        "/rest/v1/leaderboard" +
                                "?select=*" +
                                "&order=points.desc" +
                                "&limit=50"
                    )
                        .get()
                        .build()

                executeRequest(request).use { response ->

                    val body =
                        response.body
                            ?.string()
                            .orEmpty()

                    if (!response.isSuccessful) {
                        return@withContext emptyList()
                    }

                    val array =
                        JSONArray(body)

                    buildList {

                        for (
                            i in 0 until array.length()
                        ) {

                            parseLeaderboardUser(
                                array.getJSONObject(i),
                                i + 1
                            )?.let {
                                add(it)
                            }
                        }
                    }
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "LEADERBOARD_FETCH exception",
                    e
                )

                emptyList()
            }
        }

    // ============================================================
    // MARKET
    // ============================================================

    suspend fun fetchMarketItems():
        List<MarketItem> =
        withContext(Dispatchers.IO) {

            try {

                val request =
                    newRequestBuilder(
                        "/rest/v1/market_items" +
                                "?select=*" +
                                "&order=created_at.desc" +
                                "&limit=100"
                    )
                        .get()
                        .build()

                executeRequest(request).use { response ->

                    val body =
                        response.body
                            ?.string()
                            .orEmpty()

                    if (!response.isSuccessful) {
                        return@withContext emptyList()
                    }

                    if (
                        body.isBlank() ||
                        body == "[]"
                    ) {
                        return@withContext emptyList()
                    }

                    val array =
                        JSONArray(body)

                    buildList {

                        for (
                            i in 0 until array.length()
                        ) {

                            parseMarketItem(
                                array.getJSONObject(i)
                            )?.let {
                                add(it)
                            }
                        }
                    }
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "MARKET_FETCH exception",
                    e
                )

                emptyList()
            }
        }

    suspend fun createMarketItem(
        item: MarketItem
    ): Boolean =
        withContext(Dispatchers.IO) {

            try {

                val userId =
                    getCurrentUserId()
                        ?: return@withContext false

                val json =
                    JSONObject().apply {

                        put(
                            "user_id",
                            userId
                        )

                        put(
                            "title",
                            item.title
                        )

                        put(
                            "price",
                            item.price
                        )

                        put(
                            "category",
                            item.category
                        )

                        put(
                            "condition",
                            item.condition
                        )

                        put(
                            "description",
                            item.description
                        )

                        put(
                            "image_url",
                            item.images.firstOrNull()
                                ?: ""
                        )

                        put(
                            "seller_username",
                            item.sellerUsername
                        )

                        put(
                            "seller_name",
                            item.sellerName
                        )

                        put(
                            "seller_avatar",
                            item.sellerAvatar
                        )

                        put(
                            "seller_phone",
                            item.sellerPhone
                        )

                        put(
                            "seller_whatsapp",
                            item.sellerWhatsapp
                        )

                        put(
                            "university",
                            item.university
                        )

                        put(
                            "location",
                            item.location
                        )
                    }

                val request =
                    newRequestBuilder(
                        "/rest/v1/market_items",
                        authenticated = true
                    )
                        .addHeader(
                            "Prefer",
                            "return=representation"
                        )
                        .post(
                            json.toString()
                                .toRequestBody(
                                    jsonMediaType
                                )
                        )
                        .build()

                executeRequest(request).use { response ->

                    if (!response.isSuccessful) {

                        Log.e(
                            TAG,
                            "MARKET_CREATE failed: " +
                                    "${response.code} " +
                                    response.body
                                        ?.string()
                                        .orEmpty()
                        )
                    }

                    response.isSuccessful
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "MARKET_CREATE exception",
                    e
                )

                false
            }
        }

    // ============================================================
    // MESSAGES
    // ============================================================

    suspend fun fetchMessages():
        List<ChatConversation> =
        withContext(Dispatchers.IO) {

            try {

                val currentUserId =
                    getCurrentUserId()

                val request =
                    newRequestBuilder(
                        "/rest/v1/messages" +
                                "?select=*" +
                                "&order=created_at.asc" +
                                "&limit=200"
                    )
                        .get()
                        .build()

                executeRequest(request).use { response ->

                    val body =
                        response.body
                            ?.string()
                            .orEmpty()

                    if (!response.isSuccessful) {
                        return@withContext emptyList()
                    }

                    if (
                        body.isBlank() ||
                        body == "[]"
                    ) {
                        return@withContext emptyList()
                    }

                    val array =
                        JSONArray(body)

                    val conversationMap =
                        mutableMapOf<
                                String,
                                MutableList<ChatMessage>
                                >()

                    for (
                        i in 0 until array.length()
                    ) {

                        val obj =
                            array.getJSONObject(i)

                        val senderId =
                            obj.optString(
                                "sender_id",
                                ""
                            )

                        val receiverId =
                            obj.optString(
                                "receiver_id",
                                ""
                            )

                        val senderUsername =
                            obj.optString(
                                "sender_username",
                                ""
                            )

                        val receiverUsername =
                            obj.optString(
                                "receiver_username",
                                ""
                            )

                        val isMine =
                            if (
                                !currentUserId.isNullOrBlank()
                            ) {
                                senderId ==
                                        currentUserId
                            } else {
                                senderUsername.equals(
                                    "you",
                                    ignoreCase = true
                                )
                            }

                        val partner =
                            if (isMine) {
                                receiverUsername
                                    .ifBlank {
                                        receiverId
                                    }
                            } else {
                                senderUsername
                                    .ifBlank {
                                        senderId
                                    }
                            }

                        if (
                            partner.isBlank()
                        ) {
                            continue
                        }

                        val text =
                            obj.optString(
                                "text",
                                obj.optString(
                                    "content",
                                    obj.optString(
                                        "message",
                                        ""
                                    )
                                )
                            )

                        val timestamp =
                            obj.optString(
                                "created_at",
                                ""
                            )

                        val message =
                            ChatMessage(
                                id =
                                    obj.optString(
                                        "id",
                                        UUID.randomUUID()
                                            .toString()
                                    ),
                                senderId =
                                    senderId
                                        .ifBlank {
                                            senderUsername
                                        },
                                text =
                                    text,
                                timestamp =
                                    formatTimeAgo(
                                        timestamp
                                    ),
                                isFromMe =
                                    isMine
                            )

                        conversationMap
                            .getOrPut(
                                partner
                            ) {
                                mutableListOf()
                            }
                            .add(
                                message
                            )
                    }

                    conversationMap.map {
                        entry ->

                        val partner =
                            entry.key

                        val messages =
                            entry.value
                                .sortedBy {
                                    it.timestamp
                                }

                        ChatConversation(
                            id =
                                "conv_$partner",
                            partnerUsername =
                                partner,
                            partnerName =
                                partner
                                    .replace(
                                        ".",
                                        " "
                                    )
                                    .replace(
                                        "_",
                                        " "
                                    )
                                    .capitalizeWords(),
                            partnerAvatar =
                                "",
                            isOnline =
                                false,
                            lastMessage =
                                messages
                                    .lastOrNull()
                                    ?.text
                                    .orEmpty(),
                            lastMessageTime =
                                messages
                                    .lastOrNull()
                                    ?.timestamp
                                    ?: "Recent",
                            unreadCount =
                                0,
                            isVerified =
                                false,
                            verificationBadge =
                                VerificationBadge.NONE,
                            messages =
                                messages.toMutableList()
                        )
                    }
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "MESSAGES_FETCH exception",
                    e
                )

                emptyList()
            }
        }

    suspend fun sendMessage(
        receiverUsername: String,
        text: String
    ): Boolean =
        withContext(Dispatchers.IO) {

            try {

                val currentUserId =
                    getCurrentUserId()
                        ?: return@withContext false

                if (
                    receiverUsername.isBlank() ||
                    text.isBlank()
                ) {
                    return@withContext false
                }

                val json =
                    JSONObject().apply {

                        put(
                            "sender_id",
                            currentUserId
                        )

                        put(
                            "receiver_username",
                            receiverUsername
                                .trim()
                                .lowercase(
                                    Locale.US
                                )
                        )

                        put(
                            "text",
                            text.trim()
                        )

                        put(
                            "created_at",
                            nowIso()
                        )
                    }

                val request =
                    newRequestBuilder(
                        "/rest/v1/messages",
                        authenticated = true
                    )
                        .addHeader(
                            "Prefer",
                            "return=representation"
                        )
                        .post(
                            json.toString()
                                .toRequestBody(
                                    jsonMediaType
                                )
                        )
                        .build()

                executeRequest(request).use { response ->

                    val body =
                        response.body
                            ?.string()
                            .orEmpty()

                    if (!response.isSuccessful) {

                        Log.e(
                            TAG,
                            "MESSAGE_SEND failed " +
                                    "status=${response.code} " +
                                    "body=$body"
                        )
                    }

                    response.isSuccessful
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "MESSAGE_SEND exception",
                    e
                )

                false
            }
        }

    // ============================================================
    // PARSERS
    // ============================================================

    private fun parseUserProfile(
        obj: JSONObject
    ): UserProfile {

        val badge =
            when (
                obj.optString(
                    "verification_badge",
                    ""
                ).uppercase(
                    Locale.US
                )
            ) {

                "GOLD" ->
                    VerificationBadge.GOLD

                "BLUE" ->
                    VerificationBadge.BLUE

                else ->
                    VerificationBadge.NONE
            }

        val links =
            SocialLinks(
                website =
                    obj.optString(
                        "website",
                        ""
                    ),
                linkedin =
                    obj.optString(
                        "linkedin",
                        ""
                    ),
                twitter =
                    obj.optString(
                        "twitter",
                        ""
                    ),
                instagram =
                    obj.optString(
                        "instagram",
                        ""
                    ),
                featuredLink =
                    obj.optString(
                        "featured_link",
                        ""
                    ),
                featuredLinkLabel =
                    obj.optString(
                        "featured_link_label",
                        ""
                    )
            )

        val skills =
            mutableListOf<String>()

        val skillsArray =
            obj.optJSONArray(
                "core_skills"
            )

        if (
            skillsArray != null
        ) {

            for (
                i in 0 until skillsArray.length()
            ) {

                val skill =
                    skillsArray.optString(
                        i,
                        ""
                    )

                if (
                    skill.isNotBlank()
                ) {
                    skills.add(skill)
                }
            }
        }

        return UserProfile(

            id =
                obj.optString(
                    "id",
                    ""
                ),

            fullName =
                obj.optString(
                    "full_name",
                    obj.optString(
                        "name",
                        "Student"
                    )
                ),

            username =
                obj.optString(
                    "username",
                    "student"
                ),

            avatarUrl =
                obj.optString(
                    "avatar_url",
                    ""
                ),

            coverPhotoUrl =
                obj.optString(
                    "cover_photo",
                    obj.optString(
                        "cover_photo_url",
                        obj.optString(
                            "cover_url",
                            ""
                        )
                    )
                ),

            verificationBadge =
                badge,

            professionalHeadline =
                obj.optString(
                    "professional_headline",
                    ""
                ),

            currentJobTitle =
                obj.optString(
                    "current_job_title",
                    ""
                ),

            university =
                obj.optString(
                    "university",
                    ""
                ),

            faculty =
                obj.optString(
                    "faculty",
                    ""
                ),

            department =
                obj.optString(
                    "department",
                    ""
                ),

            courseOfStudy =
                obj.optString(
                    "course_of_study",
                    ""
                ),

            academicLevel =
                obj.optString(
                    "academic_level",
                    ""
                ),

            graduationYear =
                obj.optString(
                    "graduation_year",
                    ""
                ),

            bio =
                obj.optString(
                    "bio",
                    ""
                ),

            countryOfOrigin =
                obj.optString(
                    "country_of_origin",
                    ""
                ),

            currentCityState =
                obj.optString(
                    "current_city_state",
                    ""
                ),

            email =
                ContactField(
                    obj.optString(
                        "email",
                        ""
                    ),
                    true
                ),

            phone =
                ContactField(
                    obj.optString(
                        "phone",
                        ""
                    ),
                    true
                ),

            whatsapp =
                ContactField(
                    obj.optString(
                        "whatsapp",
                        ""
                    ),
                    true
                ),

            links =
                links,

            coreSkills =
                skills,

            followerCount =
                obj.optInt(
                    "follower_count",
                    0
                ),

            followingCount =
                obj.optInt(
                    "following_count",
                    0
                ),

            profileViewsThisWeek =
                obj.optInt(
                    "profile_views_this_week",
                    obj.optInt(
                        "profile_views",
                        0
                    )
                ),

            onlineNow =
                obj.optBoolean(
                    "online_now",
                    false
                ),

            isSellerActive =
                obj.optBoolean(
                    "is_seller_active",
                    false
                ),

            sellerStoreName =
                obj.optString(
                    "seller_store_name",
                    ""
                ),

            joinedLabel =
                obj.optString(
                    "joined_label",
                    ""
                )
        )
    }

    private fun parseFeedPost(
        obj: JSONObject
    ): FeedPost? {

        val id =
            obj.optString(
                "id",
                ""
            )

        if (
            id.isBlank()
        ) {
            return null
        }

        val text =
            obj.optString(
                "text",
                obj.optString(
                    "caption",
                    obj.optString(
                        "content",
                        ""
                    )
                )
            )

        val imageUrl =
            obj.optString(
                "image_url",
                obj.optString(
                    "media_url",
                    ""
                )
            )

        val videoUrl =
            obj.optString(
                "video_url",
                ""
            ).ifBlank {
                null
            }

        val type =
            obj.optString(
                "type",
                ""
            ).lowercase(
                Locale.US
            )

        val isReel =
            obj.optBoolean(
                "is_reel",
                type == "reel" ||
                        type == "video"
            )

        val author =
            obj.optString(
                "username",
                obj.optString(
                    "author",
                    obj.optString(
                        "author_name",
                        "student"
                    )
                )
            )

        val avatar =
            obj.optString(
                "avatar_url",
                obj.optString(
                    "author_avatar",
                    ""
                )
            )

        val tags =
            readStringArray(
                obj.optJSONArray(
                    "tags"
                )
            )

        val mentions =
            readStringArray(
                obj.optJSONArray(
                    "mentions"
                )
            )

        val poll =
            parsePoll(
                obj
            )

        return FeedPost(
            id =
                id,

            author =
                author,

            authorAvatar =
                avatar,

            facultyTag =
                obj.optString(
                    "faculty",
                    "SIMME"
                ),

            isVerified =
                obj.optBoolean("is_verified", false) ||
                        obj.optString("verification_badge", "").equals("GOLD", ignoreCase = true) ||
                        obj.optString("verification_badge", "").equals("BLUE", ignoreCase = true) ||
                        author.contains("verified", ignoreCase = true),

            verificationBadge =
                when (obj.optString("verification_badge", "").uppercase(Locale.US)) {
                    "GOLD" -> VerificationBadge.GOLD
                    "BLUE" -> VerificationBadge.BLUE
                    else -> if (obj.optBoolean("is_verified", false)) {
                        VerificationBadge.BLUE
                    } else {
                        VerificationBadge.NONE
                    }
                },

            timeAgo =
                formatTimeAgo(
                    obj.optString(
                        "created_at",
                        ""
                    )
                ),

            text =
                text,

            images =
                if (
                    imageUrl.isBlank()
                ) {
                    emptyList()
                } else {
                    listOf(
                        imageUrl
                    )
                },

            likes =
                obj.optInt(
                    "like_count",
                    obj.optInt(
                        "likes",
                        0
                    )
                ),

            isLiked =
                false,

            commentsCount =
                obj.optInt(
                    "comment_count",
                    obj.optInt(
                        "comments",
                        0
                    )
                ),

            sharesCount =
                obj.optInt(
                    "share_count",
                    obj.optInt(
                        "shares",
                        0
                    )
                ),

            viewsCount =
                obj.optInt(
                    "view_count",
                    obj.optInt(
                        "views",
                        0
                    )
                ),

            isReel =
                isReel,

            videoUrl =
                videoUrl,

            tags =
                tags,

            mentions =
                mentions,

            poll =
                poll
        )
    }

    private fun parsePoll(
        obj: JSONObject
    ): PostPoll? {
        if (obj.isNull("poll_data") && obj.isNull("poll")) {
            return null
        }

        val pollObject: JSONObject = obj.optJSONObject("poll_data")
            ?: obj.optJSONObject("poll")
            ?: run {
                val raw = obj.optString("poll_data", "").ifBlank {
                    obj.optString("poll", "")
                }.trim()

                if (raw.isBlank() || raw.equals("null", ignoreCase = true) || raw == "{}") {
                    return null
                }

                try {
                    JSONObject(raw)
                } catch (e: Exception) {
                    return null
                }
            } ?: return null

        return try {
            val question =
                pollObject.optString(
                    "question",
                    "Campus Poll"
                )

            val optionsArray =
                pollObject.optJSONArray(
                    "options"
                )

            if (
                optionsArray == null ||
                optionsArray.length() == 0
            ) {
                return null
            }

            val options =
                mutableListOf<PollOption>()

            var totalVotes =
                0

            for (
                i in 0 until optionsArray.length()
            ) {
                val optionObject =
                    optionsArray
                        .optJSONObject(
                            i
                        )
                        ?: continue

                val votes =
                    optionObject.optInt(
                        "votes",
                        0
                    )

                totalVotes +=
                    votes

                options.add(
                    PollOption(
                        id =
                            optionObject.optString(
                                "id",
                                "opt_$i"
                            ),
                        text =
                            optionObject.optString(
                                "text",
                                "Option ${i + 1}"
                            ),
                        votes =
                            votes,
                        isVotedByMe =
                            false
                    )
                )
            }

            PostPoll(
                question =
                    question,
                options =
                    options,
                totalVotes =
                    totalVotes,
                hasVoted =
                    false
            )

        } catch (e: Exception) {
            Log.e(
                TAG,
                "POLL_PARSE error",
                e
            )
            null
        }
    }

    private fun parseLeaderboardUser(
        obj: JSONObject,
        defaultRank: Int
    ): LeaderboardUser? {

        val username =
            obj.optString(
                "username",
                "student_$defaultRank"
            )

        val fullName =
            obj.optString(
                "full_name",
                username
                    .replace(
                        ".",
                        " "
                    )
                    .capitalizeWords()
            )

        return LeaderboardUser(

            rank =
                obj.optInt(
                    "rank",
                    defaultRank
                ),

            username =
                username,

            fullName =
                fullName,

            avatar =
                obj.optString(
                    "avatar_url",
                    ""
                ),

            points =
                obj.optInt(
                    "points",
                    0
                ),

            faculty =
                obj.optString(
                    "faculty",
                    ""
                ),

            university =
                obj.optString(
                    "university",
                    ""
                ),

            level =
                obj.optString(
                    "level",
                    ""
                ),

            streakDays =
                obj.optInt(
                    "streak_days",
                    0
                ),

            verificationBadge =
                VerificationBadge.NONE
        )
    }

    private fun parseMarketItem(
        obj: JSONObject
    ): MarketItem? {

        val id =
            obj.optString(
                "id",
                ""
            )

        if (
            id.isBlank()
        ) {
            return null
        }

        return MarketItem(

            id =
                id,

            title =
                obj.optString(
                    "title",
                    obj.optString(
                        "name",
                        "Campus Item"
                    )
                ),

            price =
                obj.optLong(
                    "price",
                    0L
                ),

            images =
                listOfNotNull(
                    obj.optString(
                        "image_url",
                        ""
                    ).ifBlank {
                        null
                    }
                ),

            sellerUsername =
                obj.optString(
                    "seller_username",
                    ""
                ),

            sellerAvatar =
                obj.optString(
                    "seller_avatar",
                    ""
                ),

            sellerName =
                obj.optString(
                    "seller_name",
                    ""
                ),

            sellerPhone =
                obj.optString(
                    "seller_phone",
                    ""
                ),

            sellerWhatsapp =
                obj.optString(
                    "seller_whatsapp",
                    ""
                ),

            sellerIsVerified =
                false,

            verificationBadge =
                VerificationBadge.NONE,

            sellerRating =
                obj.optDouble(
                    "seller_rating",
                    0.0
                ),

            sellerReviewCount =
                obj.optInt(
                    "seller_review_count",
                    0
                ),

            university =
                obj.optString(
                    "university",
                    ""
                ),

            location =
                obj.optString(
                    "location",
                    ""
                ),

            category =
                obj.optString(
                    "category",
                    ""
                ),

            condition =
                obj.optString(
                    "condition",
                    ""
                ),

            description =
                obj.optString(
                    "description",
                    ""
                ),

            postedTime =
                formatTimeAgo(
                    obj.optString(
                        "created_at",
                        ""
                    )
                )
        )
    }

    // ============================================================
    // PARSING / HELPERS
    // ============================================================

    private fun readStringArray(
        array: JSONArray?
    ): List<String> {

        if (
            array == null
        ) {
            return emptyList()
        }

        return buildList {

            for (
                i in 0 until array.length()
            ) {

                val value =
                    array.optString(
                        i,
                        ""
                    )

                if (
                    value.isNotBlank()
                ) {
                    add(
                        value
                    )
                }
            }
        }
    }

    private fun parseSupabaseError(
        body: String,
        fallback: String
    ): String {

        if (
            body.isBlank()
        ) {
            return fallback
        }

        return try {

            val json =
                JSONObject(
                    body
                )

            json.optString(
                "message",
                json.optString(
                    "msg",
                    json.optString(
                        "error_description",
                        json.optString(
                            "error",
                            fallback
                        )
                    )
                )
            ).ifBlank {
                fallback
            }

        } catch (
            _: Exception
        ) {

            fallback
        }
    }

    private fun decodeJwtSubject(
        token: String
    ): String? {

        return try {

            val parts =
                token.split(".")

            if (
                parts.size < 2
            ) {
                return null
            }

            val decoded =
                Base64.decode(
                    parts[1],
                    Base64.URL_SAFE or
                            Base64.NO_WRAP or
                            Base64.NO_PADDING
                )

            val payload =
                JSONObject(
                    String(
                        decoded,
                        StandardCharsets.UTF_8
                    )
                )

            payload
                .optString(
                    "sub",
                    ""
                )
                .ifBlank {
                    null
                }

        } catch (
            _: Exception
        ) {

            null
        }
    }

    private fun jwtExpirationMillis(
        token: String
    ): Long? {

        return try {

            val parts =
                token.split(".")

            if (
                parts.size < 2
            ) {
                return null
            }

            val decoded =
                Base64.decode(
                    parts[1],
                    Base64.URL_SAFE or
                            Base64.NO_WRAP or
                            Base64.NO_PADDING
                )

            val payload =
                JSONObject(
                    String(
                        decoded,
                        StandardCharsets.UTF_8
                    )
                )

            val exp =
                payload.optLong(
                    "exp",
                    0L
                )

            if (
                exp <= 0L
            ) {
                null
            } else {
                exp * 1000L
            }

        } catch (
            _: Exception
        ) {

            null
        }
    }

    private fun encodeValue(
        value: String
    ): String {

        return URLEncoder.encode(
            value,
            "UTF-8"
        )
    }

    private fun nowIso(): String {

        return SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            Locale.US
        )
            .apply {
                timeZone =
                    TimeZone.getTimeZone(
                        "UTC"
                    )
            }
            .format(
                Date()
            )
    }

    private fun formatTimeAgo(
        dateString: String
    ): String {

        if (
            dateString.isBlank()
        ) {
            return "Just now"
        }

        return try {

            val normalized =
                dateString
                    .trim()
                    .replace(
                        "Z",
                        ""
                    )

            val formatter =
                SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss",
                    Locale.US
                )

            formatter.timeZone =
                TimeZone.getTimeZone(
                    "UTC"
                )

            val date =
                formatter.parse(
                    normalized
                        .substringBefore(".")
                )

            if (
                date == null
            ) {
                return "Recent"
            }

            val diff =
                System.currentTimeMillis() -
                        date.time

            val minutes =
                diff /
                        60_000L

            val hours =
                minutes /
                        60L

            val days =
                hours /
                        24L

            when {

                minutes < 1L ->
                    "Just now"

                minutes < 60L ->
                    "${minutes}m ago"

                hours < 24L ->
                    "${hours}h ago"

                else ->
                    "${days}d ago"
            }

        } catch (
            _: Exception
        ) {

            "Recent"
        }
    }
}

enum class ProfileMediaType {
    AVATAR,
    COVER
}

private fun String.capitalizeWords(): String {

    return split(
        " "
    )
        .filter {
            it.isNotBlank()
        }
        .joinToString(
            " "
        ) { word ->

            word.replaceFirstChar {
                char ->
                char.uppercase()
            }
        }
}