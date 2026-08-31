package com.example.data.supabase

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.data.models.AchievementBadge
import com.example.data.models.ChatConversation
import com.example.data.models.ChatMessage
import com.example.data.models.MessageStatus
import com.example.data.models.ContactField
import com.example.data.models.FeedPost
import com.example.data.models.LeaderboardUser
import com.example.data.models.CampusPeer
import com.example.data.models.RoommateApplicant
import com.example.data.models.StudyCircle
import com.example.data.models.MarketItem
import com.example.data.models.PollOption
import com.example.data.models.PostPoll
import com.example.data.models.SocialLinks
import com.example.data.models.Story
import com.example.data.models.UserProfile
import com.example.data.models.SkillEndorsement
import com.example.data.models.AvailabilityStatus
import com.example.data.models.VerificationBadge
import com.example.data.models.Comment
import com.example.data.models.CommentReply
import com.example.data.models.ActivityItem
import com.example.data.models.NotificationFilter
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

        fun getCurrentUsername(): String? {
        val context = SupabaseService.appContext ?: return null
        return context.getSharedPreferences("blink_auth_prefs", Context.MODE_PRIVATE)
            .getString("username", null)
    }

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
                        put("full_name", profile.fullName.trim())
                        put("username", profile.username.trim().lowercase(Locale.US))
                        put("avatar_url", profile.avatarUrl)
                        put("cover_photo", profile.coverPhotoUrl)
                        put("professional_headline", profile.professionalHeadline)
                        put("current_job_title", profile.currentJobTitle)
                        put("bio", profile.bio)
                        put("university", profile.university)
                        put("faculty", profile.faculty)
                        put("department", profile.department)
                        put("course_of_study", profile.courseOfStudy)
                        put("academic_level", profile.academicLevel)
                        put("graduation_year", profile.graduationYear)
                        put("country_of_origin", profile.countryOfOrigin)
                        put("current_city_state", profile.currentCityState)
                        put("email", profile.email.value)
                        put("phone", profile.phone.value)
                        put("whatsapp", profile.whatsapp.value)
                        put("website", profile.links.website)
                        put("linkedin", profile.links.linkedin)
                        put("twitter", profile.links.twitter)
                        put("instagram", profile.links.instagram)
                        put("featured_link", profile.links.featuredLink)
                        put("featured_link_label", profile.links.featuredLinkLabel)
                        put("favorite_quote", profile.favoriteQuote)
                        put("custom_status", profile.availability.name)
                        put("profile_views_this_week", profile.profileViewsThisWeek)
                        put("verification_badge", profile.verificationBadge.name)
                        put("is_verified", profile.verificationBadge != VerificationBadge.NONE)
                        put("updated_at", nowIso())
                        
                        val skillsArray = JSONArray()
                        profile.coreSkills.forEach { skillsArray.put(it) }
                        put("core_skills", skillsArray)
                        
                        val hobbiesArray = JSONArray()
                        profile.hobbies.forEach { hobbiesArray.put(it) }
                        put("hobbies", hobbiesArray)
                        
                        val languagesArray = JSONArray()
                        profile.languages.forEach { languagesArray.put(it) }
                        put("languages", languagesArray)
                        
                        val skillEndorsementsArray = JSONArray()
                        profile.skillEndorsements.forEach {
                            val seObj = JSONObject()
                            seObj.put("skill", it.skill)
                            seObj.put("endorsements", it.endorsements)
                            seObj.put("endorsed_by_me", it.endorsedByMe)
                            skillEndorsementsArray.put(seObj)
                        }
                        put("skill_endorsements", skillEndorsementsArray)
                        
                        val badgesArray = JSONArray()
                        profile.badges.forEach {
                            val bObj = JSONObject()
                            bObj.put("id", it.id)
                            bObj.put("title", it.title)
                            bObj.put("description", it.description)
                            bObj.put("iconName", it.iconName)
                            badgesArray.put(bObj)
                        }
                        put("badges", badgesArray)
                        
                        put("daily_streak", profile.dailyStreak)
                        put("world_rank", profile.worldRank)
                        put("campus_rank", profile.campusRank)
                        put("verified_at_millis", profile.verifiedAtMillis)
                        put("is_seller_active", profile.isSellerActive)
                        put("seller_store_name", profile.sellerStoreName)
                        put("joined_label", profile.joinedLabel)
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
                return@withContext upsertSuccess

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "PROFILE_UPDATE exception",
                    e
                )
                return@withContext false
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
                val currentUserId = getCurrentUserId() ?: ""
                val currentUsername = appContext
                    ?.getSharedPreferences("blink_auth_prefs", android.content.Context.MODE_PRIVATE)
                    ?.getString("username", "") ?: ""

                // 1. Fetch posts
                val request = newRequestBuilder(
                    "/rest/v1/feed_posts?select=*&order=created_at.desc&limit=100"
                ).get().build()

                val postsStr = executeRequest(request).use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        Log.e(TAG, "FEED_FETCH failed status=${response.code} body=$body")
                        return@withContext emptyList()
                    }
                    if (body.isBlank() || body == "[]") return@withContext emptyList()
                    body
                }

                // 2. Fetch my likes
                val myLikes = mutableSetOf<String>()
                if (currentUserId.isNotBlank() || currentUsername.isNotBlank()) {
                    val filter = when {
                        currentUserId.isNotBlank() -> "user_id=eq.$currentUserId"
                        currentUsername.isNotBlank() -> "username=eq.$currentUsername"
                        else -> ""
                    }
                    val likesReq = newRequestBuilder("/rest/v1/post_likes?select=post_id&$filter").get().build()
                    executeRequest(likesReq).use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string().orEmpty()
                            if (body.isNotBlank() && body != "[]") {
                                val arr = JSONArray(body)
                                for (i in 0 until arr.length()) {
                                    myLikes.add(arr.getJSONObject(i).optString("post_id"))
                                }
                            }
                        }
                    }
                }

                // 3. Fetch my bookmarks
                val myBookmarks = mutableSetOf<String>()
                if (currentUserId.isNotBlank() || currentUsername.isNotBlank()) {
                    val filter = when {
                        currentUserId.isNotBlank() -> "user_id=eq.$currentUserId"
                        currentUsername.isNotBlank() -> "username=eq.$currentUsername"
                        else -> ""
                    }
                    val bmkReq = newRequestBuilder("/rest/v1/post_bookmarks?select=post_id&$filter").get().build()
                    executeRequest(bmkReq).use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string().orEmpty()
                            if (body.isNotBlank() && body != "[]") {
                                val arr = JSONArray(body)
                                for (i in 0 until arr.length()) {
                                    myBookmarks.add(arr.getJSONObject(i).optString("post_id"))
                                }
                            }
                        }
                    }
                }

                // 4. Parse and map
                val json = JSONArray(postsStr)
                buildList {
                    for (i in 0 until json.length()) {
                        try {
                            val obj = json.getJSONObject(i)
                            var post = parseFeedPost(obj)
                            if (myLikes.contains(post.id)) {
                                post = post.copy(isLiked = true)
                            }
                            if (myBookmarks.contains(post.id)) {
                                post = post.copy(isBookmarked = true)
                            }
                            add(post)
                        } catch (e: Exception) {
                            Log.e(TAG, "FEED_FETCH item parse error", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "FEED_FETCH exception", e)
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
    ): FeedPost? =
        withContext(Dispatchers.IO) {
            try {
                val userId =
                    getCurrentUserId()
                        ?: author.trim().lowercase(Locale.US).ifBlank { "user_student" }

                val json =
                    JSONObject().apply {
                        put("user_id", userId)
                        put("type", when {
                            isReel || !videoUrl.isNullOrBlank() -> "reel"
                            !imageUrl.isNullOrBlank() -> "photo"
                            else -> "text"
                        })
                        put("faculty", facultyTag.trim().ifBlank { "SIMME" })
                        put("text", text.trim())
                        put("content", text.trim())
                        
                        if (!imageUrl.isNullOrBlank()) {
                            put("image_url", imageUrl)
                            put("media_url", imageUrl)
                        }
                        if (!videoUrl.isNullOrBlank()) {
                            put("video_url", videoUrl)
                        }
                        
                        put("is_reel", isReel)
                        put("like_count", 0)
                        put("comment_count", 0)
                        put("share_count", 0)
                        put("view_count", 0)
                        put("username", author)
                        put("author", author)
                        put("avatar_url", authorAvatar)
                        put("author_avatar", authorAvatar)
                        
                        if (tags.isNotEmpty()) {
                            val tagsArray = JSONArray()
                            tags.forEach { tagsArray.put(it) }
                            put("tags", tagsArray)
                        }
                        if (mentions.isNotEmpty()) {
                            val mentionsArray = JSONArray()
                            mentions.forEach { mentionsArray.put(it) }
                            put("mentions", mentionsArray)
                        }
                        
                        if (poll != null) {
                            val pollObj = JSONObject()
                            pollObj.put("question", poll.question)
                            pollObj.put("total_votes", poll.totalVotes)
                            pollObj.put("has_voted", poll.hasVoted)
                            val optionsArray = JSONArray()
                            poll.options.forEach { opt ->
                                val optObj = JSONObject()
                                optObj.put("id", opt.id)
                                optObj.put("text", opt.text)
                                optObj.put("votes", opt.votes)
                                optObj.put("is_voted_by_me", opt.isVotedByMe)
                                optionsArray.put(optObj)
                            }
                            pollObj.put("options", optionsArray)
                            put("poll_data", pollObj)
                        }
                        
                        put("audience", audience)
                        put("category", category)
                        location?.let { put("location", it) }
                        linkUrl?.let { put("link_url", it) }
                        put("allow_comments", allowComments)
                        put("hide_likes", hideLikes)
                        put("is_pinned", isPinned)
                        put("is_disappearing", isDisappearing)
                        audioTitle?.let { put("audio_title", it) }
                        altText?.let { put("alt_text", it) }
                    }

                val request =
                    newRequestBuilder(
                        "/rest/v1/feed_posts",
                        authenticated = true
                    )
                        .addHeader("Prefer", "return=representation")
                        .post(
                            json.toString().toRequestBody(jsonMediaType)
                        )
                        .build()

                executeRequest(request).use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful && body.isNotBlank()) {
                        try {
                            val arr = JSONArray(body)
                            if (arr.length() > 0) {
                                return@withContext parseFeedPost(arr.getJSONObject(0))
                            }
                        } catch (e: Exception) {
                            try {
                                return@withContext parseFeedPost(JSONObject(body))
                            } catch (e2: Exception) {
                                Log.e(TAG, "Failed to parse created post", e2)
                            }
                        }
                    }
                    Log.e(TAG, "POST_CREATE failed: ${response.code} $body")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "POST_CREATE exception", e)
                null
            }
        }
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
    
    suspend fun togglePostBookmark(
        postId: String,
        bookmarked: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentUserId = getCurrentUserId() ?: ""
            val currentUsername = appContext
                ?.getSharedPreferences("blink_auth_prefs", android.content.Context.MODE_PRIVATE)
                ?.getString("username", "") ?: ""

            if (bookmarked) {
                val obj = JSONObject().apply {
                    put("post_id", postId)
                    if (currentUserId.isNotBlank()) put("user_id", currentUserId)
                    if (currentUsername.isNotBlank()) put("username", currentUsername)
                }
                val req = newRequestBuilder("/rest/v1/post_bookmarks", authenticated = true)
                    .addHeader("Prefer", "resolution=merge-duplicates")
                    .post(obj.toString().toRequestBody(jsonMediaType))
                    .build()
                executeRequest(req).use { it.isSuccessful }
            } else {
                val filter = when {
                    currentUserId.isNotBlank() -> "user_id=eq.$currentUserId"
                    currentUsername.isNotBlank() -> "username=eq.$currentUsername"
                    else -> ""
                }
                val url = if (filter.isNotBlank()) "/rest/v1/post_bookmarks?post_id=eq.$postId&$filter" else "/rest/v1/post_bookmarks?post_id=eq.$postId"
                val req = newRequestBuilder(url, authenticated = true).delete().build()
                executeRequest(req).use { it.isSuccessful }
            }
        } catch (e: Exception) {
            Log.e(TAG, "togglePostBookmark failed", e)
            false
        }
    }
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

    // ============================================================
    // POLL & STORY HELPERS
    // ============================================================

    private suspend fun fetchUserStoryViews(username: String): Set<String> = withContext(Dispatchers.IO) {
        try {
            val req = newRequestBuilder(
                "/rest/v1/story_views?viewer_username=eq.${URLEncoder.encode(username, "UTF-8")}&select=story_id",
                authenticated = true
            ).get().build()
            executeRequest(req).use { resp ->
                if (!resp.isSuccessful) return@withContext emptySet()
                val body = resp.body?.string().orEmpty()
                if (body.isBlank() || body == "[]") return@withContext emptySet()
                val arr = JSONArray(body)
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    val sid = arr.optJSONObject(i)?.optString("story_id", "")
                    if (!sid.isNullOrBlank()) set.add(sid)
                }
                set
            }
        } catch (e: Exception) {
            emptySet()
        }
    }

    private suspend fun fetchUserStoryLikes(username: String): Set<String> = withContext(Dispatchers.IO) {
        try {
            val req = newRequestBuilder(
                "/rest/v1/story_likes?username=eq.${URLEncoder.encode(username, "UTF-8")}&select=story_id",
                authenticated = true
            ).get().build()
            executeRequest(req).use { resp ->
                if (!resp.isSuccessful) return@withContext emptySet()
                val body = resp.body?.string().orEmpty()
                if (body.isBlank() || body == "[]") return@withContext emptySet()
                val arr = JSONArray(body)
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    val sid = arr.optJSONObject(i)?.optString("story_id", "")
                    if (!sid.isNullOrBlank()) set.add(sid)
                }
                set
            }
        } catch (e: Exception) {
            emptySet()
        }
    }

    private suspend fun fetchUserPollVotes(username: String): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            val req = newRequestBuilder(
                "/rest/v1/poll_votes?username=eq.${URLEncoder.encode(username, "UTF-8")}&select=post_id,option_id",
                authenticated = true
            ).get().build()
            executeRequest(req).use { resp ->
                if (!resp.isSuccessful) return@withContext emptyMap()
                val body = resp.body?.string().orEmpty()
                if (body.isBlank() || body == "[]") return@withContext emptyMap()
                val arr = JSONArray(body)
                val map = mutableMapOf<String, String>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val pid = obj.optString("post_id", "")
                    val optId = obj.optString("option_id", "")
                    if (pid.isNotBlank() && optId.isNotBlank()) {
                        map[pid] = optId
                    }
                }
                map
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }


    // ============================================================
    // STORIES & POLLS
    // ============================================================

    suspend fun fetchStories(): List<Story> = withContext(Dispatchers.IO) {
        try {
            val currentUsername = getCurrentUsername() ?: ""
            val viewedIds = fetchUserStoryViews(currentUsername)
            val likedIds = fetchUserStoryLikes(currentUsername)

            val request = newRequestBuilder(
                "/rest/v1/stories?select=*&order=created_at.desc&limit=50",
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
                    val isLiked = likedIds.contains(id) || obj.optBoolean("is_liked", false)
                    val hasUnseen = !viewedIds.contains(id)

                    list.add(
                        Story(
                            id = id,
                            username = username,
                            avatar = avatar.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300&h=300&fit=crop" },
                            hasUnseen = hasUnseen,
                            isUser = username.equals(currentUsername, ignoreCase = true),
                            storyImage = storyImage,
                            caption = caption,
                            faculty = faculty,
                            university = university,
                            likesCount = likesCount,
                            isLiked = isLiked
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
                put("created_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date()))
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

    suspend fun markStoryViewed(storyId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername() ?: "user"
            val userId = getCurrentUserId() ?: "user"

            val json = JSONObject().apply {
                put("story_id", storyId)
                put("viewer_id", userId)
                put("viewer_username", username)
                put("created_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date()))
            }
            val request = newRequestBuilder(
                "/rest/v1/story_views",
                authenticated = true
            )
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            executeRequest(request).use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "markStoryViewed exception", e)
            false
        }
    }

    suspend fun toggleStoryLike(storyId: String, nextLiked: Boolean, newCount: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername() ?: "user"
            val userId = getCurrentUserId() ?: "user"

            if (nextLiked) {
                val json = JSONObject().apply {
                    put("story_id", storyId)
                    put("user_id", userId)
                    put("username", username)
                    put("created_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.format(Date()))
                }
                val req = newRequestBuilder("/rest/v1/story_likes", authenticated = true)
                    .post(json.toString().toRequestBody(jsonMediaType))
                    .build()
                executeRequest(req).close()
            } else {
                val req = newRequestBuilder(
                    "/rest/v1/story_likes?story_id=eq.${URLEncoder.encode(storyId, "UTF-8")}&username=eq.${URLEncoder.encode(username, "UTF-8")}",
                    authenticated = true
                )
                    .delete()
                    .build()
                executeRequest(req).close()
            }

            val patchJson = JSONObject().apply { put("likes_count", newCount) }
            val patchReq = newRequestBuilder(
                "/rest/v1/stories?id=eq.${URLEncoder.encode(storyId, "UTF-8")}",
                authenticated = true
            )
                .patch(patchJson.toString().toRequestBody(jsonMediaType))
                .build()
            executeRequest(patchReq).use { resp -> resp.isSuccessful }
        } catch (e: Exception) {
            Log.e(TAG, "toggleStoryLike exception", e)
            false
        }
    }

    suspend fun reactToStory(storyId: String, emoji: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername() ?: "user"
            val userId = getCurrentUserId() ?: "user"

            val json = JSONObject().apply {
                put("story_id", storyId)
                put("user_id", userId)
                put("username", username)
                put("emoji", emoji)
                put("created_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date()))
            }
            val request = newRequestBuilder(
                "/rest/v1/story_reactions",
                authenticated = true
            )
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            executeRequest(request).use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "reactToStory exception", e)
            false
        }
    }

    suspend fun replyToStory(storyId: String, recipientUsername: String, replyText: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername() ?: "user"
            val userId = getCurrentUserId() ?: "user"

            val json = JSONObject().apply {
                put("story_id", storyId)
                put("sender_id", userId)
                put("sender_username", username)
                put("recipient_username", recipientUsername)
                put("reply_text", replyText)
                put("created_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date()))
            }
            val request = newRequestBuilder(
                "/rest/v1/story_replies",
                authenticated = true
            )
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            executeRequest(request).use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "replyToStory exception", e)
            false
        }
    }

    suspend fun votePoll(postId: String, optionId: String, updatedPoll: PostPoll): Boolean = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername() ?: "user"
            val userId = getCurrentUserId() ?: "user"

            val voteJson = JSONObject().apply {
                put("post_id", postId)
                put("option_id", optionId)
                put("user_id", userId)
                put("username", username)
                put("created_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date()))
            }
            val voteReq = newRequestBuilder(
                "/rest/v1/poll_votes",
                authenticated = true
            )
                .post(voteJson.toString().toRequestBody(jsonMediaType))
                .build()
            try { executeRequest(voteReq).close() } catch (e: Exception) { Log.w(TAG, "poll_votes error", e) }

            val pollObj = JSONObject().apply {
                put("question", updatedPoll.question)
                put("total_votes", updatedPoll.totalVotes)
                put("has_voted", true)
                val optionsArray = JSONArray()
                updatedPoll.options.forEach { opt ->
                    val optObj = JSONObject().apply {
                        put("id", opt.id)
                        put("text", opt.text)
                        put("votes", opt.votes)
                        put("is_voted_by_me", opt.isVotedByMe)
                    }
                    optionsArray.put(optObj)
                }
                put("options", optionsArray)
            }

            val patchJson = JSONObject().apply {
                put("poll_data", pollObj)
            }

            val patchReq = newRequestBuilder(
                "/rest/v1/feed_posts?id=eq.${URLEncoder.encode(postId, "UTF-8")}",
                authenticated = true
            )
                .patch(patchJson.toString().toRequestBody(jsonMediaType))
                .build()

            executeRequest(patchReq).use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "votePoll exception", e)
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

    suspend fun fetchMessages(): List<ChatConversation> = withContext(Dispatchers.IO) {
        try {
            val currentUserId = getCurrentUserId() ?: ""
            val currentUsername = getCurrentUsername() ?: ""

            val request = newRequestBuilder(
                "/rest/v1/messages" +
                        "?select=*" +
                        "&order=created_at.asc" +
                        "&limit=300"
            )
                .get()
                .build()

            executeRequest(request).use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank() || body == "[]") {
                    return@withContext emptyList()
                }

                val array = JSONArray(body)
                val conversationMap = mutableMapOf<String, MutableList<ChatMessage>>()

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val senderId = obj.optString("sender_id", "")
                    val receiverId = obj.optString("receiver_id", "")
                    val senderUsername = obj.optString("sender_username", "")
                    val receiverUsername = obj.optString("receiver_username", "")

                    val isMine = if (currentUserId.isNotBlank()) {
                        senderId == currentUserId || (senderUsername.isNotBlank() && senderUsername.equals(currentUsername, ignoreCase = true))
                    } else if (currentUsername.isNotBlank()) {
                        senderUsername.equals(currentUsername, ignoreCase = true)
                    } else {
                        senderUsername.equals("you", ignoreCase = true)
                    }

                    val partner = if (isMine) {
                        receiverUsername.ifBlank { receiverId }
                    } else {
                        senderUsername.ifBlank { senderId }
                    }

                    if (partner.isBlank()) continue

                    val text = obj.optString(
                        "text",
                        obj.optString("content", obj.optString("message", ""))
                    )
                    val isoCreatedAt = obj.optString("created_at", "")
                    val isRead = obj.optBoolean("is_read", false)

                    val message = ChatMessage(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        senderId = senderId.ifBlank { senderUsername },
                        senderUsername = senderUsername,
                        receiverId = receiverId.ifBlank { receiverUsername },
                        receiverUsername = receiverUsername,
                        text = text,
                        rawTimestamp = isoCreatedAt,
                        timestamp = formatTimeAgo(isoCreatedAt),
                        isFromMe = isMine,
                        isRead = isRead,
                        status = MessageStatus.SENT
                    )

                    conversationMap.getOrPut(partner) { mutableListOf() }.add(message)
                }

                conversationMap.map { entry ->
                    val partner = entry.key
                    val messages = entry.value.sortedBy { it.rawTimestamp.ifBlank { it.timestamp } }
                    val unread = messages.count { !it.isFromMe && !it.isRead }
                    val lastMsg = messages.lastOrNull()

                    ChatConversation(
                        id = "conv_$partner",
                        partnerUsername = partner,
                        partnerName = partner.replace(".", " ").replace("_", " ").capitalizeWords(),
                        partnerAvatar = "",
                        isOnline = false,
                        lastMessage = lastMsg?.text.orEmpty(),
                        lastMessageTime = lastMsg?.timestamp ?: "Recent",
                        lastMessageRawTime = lastMsg?.rawTimestamp.orEmpty(),
                        unreadCount = unread,
                        isVerified = false,
                        verificationBadge = VerificationBadge.NONE,
                        messages = messages.toMutableList()
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MESSAGES_FETCH exception", e)
            emptyList()
        }
    }

    suspend fun sendMessage(
        receiverUsername: String,
        text: String
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        try {
            val currentUserId = getCurrentUserId() ?: ""
            val currentUsername = getCurrentUsername() ?: ""

            if (receiverUsername.isBlank() || text.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Receiver username or text is blank"))
            }

            val json = JSONObject().apply {
                if (currentUserId.isNotBlank()) put("sender_id", currentUserId)
                if (currentUsername.isNotBlank()) put("sender_username", currentUsername)
                put("receiver_username", receiverUsername.trim().lowercase(Locale.US))
                put("text", text.trim())
                put("content", text.trim())
                put("is_read", false)
                put("created_at", nowIso())
            }

            val request = newRequestBuilder("/rest/v1/messages", authenticated = true)
                .addHeader("Prefer", "return=representation")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            executeRequest(request).use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.e(TAG, "MESSAGE_SEND failed status=${response.code} body=$body")
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))
                }

                var createdMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    senderId = currentUserId,
                    senderUsername = currentUsername,
                    receiverUsername = receiverUsername,
                    text = text.trim(),
                    rawTimestamp = nowIso(),
                    timestamp = "Just now",
                    isFromMe = true,
                    isRead = false,
                    status = MessageStatus.SENT
                )

                if (body.isNotBlank() && body != "[]") {
                    try {
                        val arr = JSONArray(body)
                        if (arr.length() > 0) {
                            val resObj = arr.getJSONObject(0)
                            val serverId = resObj.optString("id", createdMsg.id)
                            val createdAt = resObj.optString("created_at", createdMsg.rawTimestamp)
                            createdMsg = createdMsg.copy(
                                id = serverId,
                                rawTimestamp = createdAt,
                                timestamp = formatTimeAgo(createdAt)
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed parsing created message response", e)
                    }
                }

                // Update conversations table as well
                updateConversationInSupabase(currentUsername, receiverUsername, text.trim(), createdMsg.rawTimestamp)

                Result.success(createdMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "MESSAGE_SEND exception", e)
            Result.failure(e)
        }
    }

    private suspend fun updateConversationInSupabase(
        senderUsername: String,
        receiverUsername: String,
        lastMessageText: String,
        timestamp: String
    ) {
        try {
            val convoJson = JSONObject().apply {
                put("user1_username", senderUsername)
                put("user2_username", receiverUsername)
                put("last_message", lastMessageText)
                put("last_message_at", timestamp)
                put("updated_at", timestamp)
            }
            val request = newRequestBuilder("/rest/v1/conversations", authenticated = true)
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(convoJson.toString().toRequestBody(jsonMediaType))
                .build()
            executeRequest(request).use { }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update conversation record", e)
        }
    }

    suspend fun markMessagesRead(partnerUsername: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentUsername = getCurrentUsername() ?: ""
            if (partnerUsername.isBlank() || currentUsername.isBlank()) return@withContext false

            val patchObj = JSONObject().apply {
                put("is_read", true)
            }
            val url = "/rest/v1/messages?sender_username=eq.$partnerUsername&receiver_username=eq.$currentUsername&is_read=eq.false"
            val request = newRequestBuilder(url, authenticated = true)
                .patch(patchObj.toString().toRequestBody(jsonMediaType))
                .build()

            executeRequest(request).use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "markMessagesRead failed", e)
            false
        }
    }

    // ============================================================
    // PARSERS
    // ============================================================


    private fun parseFeedPost(obj: JSONObject): FeedPost {
        val author = obj.optString("author", obj.optString("username", ""))
        val imagesArray = obj.optJSONArray("images")
        val imagesList = mutableListOf<String>()
        if (imagesArray != null) {
            for (i in 0 until imagesArray.length()) {
                val url = imagesArray.optString(i, "")
                if (url.isNotBlank()) imagesList.add(url)
            }
        }
        val imageUrl = obj.optString("image_url", obj.optString("media_url", ""))
        if (imageUrl.isNotBlank() && !imagesList.contains(imageUrl)) {
            imagesList.add(imageUrl)
        }

        val tagsArray = obj.optJSONArray("tags")
        val tagsList = mutableListOf<String>()
        if (tagsArray != null) {
            for (i in 0 until tagsArray.length()) {
                val t = tagsArray.optString(i, "")
                if (t.isNotBlank()) tagsList.add(t)
            }
        }

        val mentionsArray = obj.optJSONArray("mentions")
        val mentionsList = mutableListOf<String>()
        if (mentionsArray != null) {
            for (i in 0 until mentionsArray.length()) {
                val m = mentionsArray.optString(i, "")
                if (m.isNotBlank()) mentionsList.add(m)
            }
        }

        var poll: PostPoll? = null
        val pollObj = obj.optJSONObject("poll_data")
        if (pollObj != null) {
            val q = pollObj.optString("question", "")
            val optsArr = pollObj.optJSONArray("options")
            val opts = mutableListOf<PollOption>()
            if (optsArr != null) {
                for (i in 0 until optsArr.length()) {
                    val opt = optsArr.optJSONObject(i)
                    if (opt != null) {
                        opts.add(PollOption(
                            id = opt.optString("id", ""),
                            text = opt.optString("text", ""),
                            votes = opt.optInt("votes", 0),
                            isVotedByMe = opt.optBoolean("is_voted_by_me", false)
                        ))
                    }
                }
            }
            poll = PostPoll(
                question = q,
                options = opts,
                totalVotes = pollObj.optInt("total_votes", 0),
                hasVoted = pollObj.optBoolean("has_voted", false)
            )
        }

        val isVerified = obj.optBoolean("is_verified", false) || obj.optString("verification_badge", "").equals("BLUE", ignoreCase = true) || obj.optString("verification_badge", "").equals("GOLD", ignoreCase = true)
        val badgeStr = obj.optString("verification_badge", "").uppercase(Locale.US)
        val badge = when (badgeStr) {
            "GOLD" -> VerificationBadge.GOLD
            "BLUE" -> VerificationBadge.BLUE
            else -> if (isVerified) VerificationBadge.BLUE else VerificationBadge.NONE
        }

        return FeedPost(
            id = obj.optString("id", ""),
            author = author,
            authorAvatar = obj.optString("author_avatar", obj.optString("avatar_url", "")),
            facultyTag = obj.optString("faculty_tag", obj.optString("faculty", "")),
            isVerified = isVerified,
            verificationBadge = badge,
            timeAgo = obj.optString("time_ago", "Recently"),
            text = obj.optString("text", obj.optString("content", "")),
            images = imagesList,
            tags = tagsList,
            mentions = mentionsList,
            poll = poll,
            likes = obj.optInt("likes_count", obj.optInt("like_count", obj.optInt("likes", 0))),
            isLiked = obj.optBoolean("is_liked", false),
            commentsCount = obj.optInt("comments_count", obj.optInt("comment_count", 0)),
            sharesCount = obj.optInt("shares_count", obj.optInt("share_count", 0)),
            viewsCount = obj.optInt("views_count", obj.optInt("view_count", 0)),
            isBookmarked = obj.optBoolean("is_bookmarked", false),
            isReel = obj.optBoolean("is_reel", false),
            videoDuration = obj.optString("video_duration", "0:00"),
            videoUrl = obj.optString("video_url", null),
            audience = obj.optString("audience", "Everyone"),
            category = obj.optString("category", "Campus Life"),
            location = obj.optString("location", null).takeIf { it?.isNotBlank() == true },
            linkUrl = obj.optString("link_url", null).takeIf { it?.isNotBlank() == true },
            allowComments = obj.optBoolean("allow_comments", true),
            hideLikes = obj.optBoolean("hide_likes", false),
            isPinned = obj.optBoolean("is_pinned", false),
            isDisappearing = obj.optBoolean("is_disappearing", false),
            audioTitle = obj.optString("audio_title", null).takeIf { it?.isNotBlank() == true },
            altText = obj.optString("alt_text", null).takeIf { it?.isNotBlank() == true }
        )
    }

    private fun parseLeaderboardUser(obj: JSONObject, rank: Int = obj.optInt("rank", 0)): LeaderboardUser {
        val badgeStr = obj.optString("verification_badge", "").uppercase(Locale.US)
        val badge = when (badgeStr) {
            "GOLD" -> VerificationBadge.GOLD
            "BLUE" -> VerificationBadge.BLUE
            else -> if (obj.optBoolean("is_verified", false)) VerificationBadge.BLUE else VerificationBadge.NONE
        }
        return LeaderboardUser(
            rank = rank,
            username = obj.optString("username", ""),
            fullName = obj.optString("full_name", obj.optString("name", "")),
            avatar = obj.optString("avatar_url", ""),
            points = obj.optInt("points", obj.optInt("follower_count", 0)),
            faculty = obj.optString("faculty", ""),
            university = obj.optString("university", ""),
            level = obj.optString("academic_level", ""),
            streakDays = obj.optInt("daily_streak", 0),
            verificationBadge = badge
        )
    }

    private fun parseMarketItem(obj: JSONObject): MarketItem {
        val imagesArray = obj.optJSONArray("images")
        val imagesList = mutableListOf<String>()
        if (imagesArray != null) {
            for (i in 0 until imagesArray.length()) {
                val url = imagesArray.optString(i, "")
                if (url.isNotBlank()) imagesList.add(url)
            }
        } else {
            val img = obj.optString("image_url", "")
            if (img.isNotBlank()) imagesList.add(img)
        }

        val sellerIsVerified = obj.optBoolean("seller_is_verified", false) || obj.optString("verification_badge", "").equals("BLUE", ignoreCase = true) || obj.optString("verification_badge", "").equals("GOLD", ignoreCase = true)
        val badgeStr = obj.optString("verification_badge", "").uppercase(Locale.US)
        val badge = when (badgeStr) {
            "GOLD" -> VerificationBadge.GOLD
            "BLUE" -> VerificationBadge.BLUE
            else -> if (sellerIsVerified) VerificationBadge.BLUE else VerificationBadge.NONE
        }

        return MarketItem(
            id = obj.optString("id", ""),
            title = obj.optString("title", ""),
            price = obj.optLong("price", 0L),
            images = imagesList,
            sellerUsername = obj.optString("seller_username", ""),
            sellerAvatar = obj.optString("seller_avatar", ""),
            sellerName = obj.optString("seller_name", ""),
            sellerPhone = obj.optString("seller_phone", ""),
            sellerWhatsapp = obj.optString("seller_whatsapp", ""),
            sellerIsVerified = sellerIsVerified,
            verificationBadge = badge,
            sellerRating = obj.optDouble("seller_rating", 0.0),
            sellerReviewCount = obj.optInt("seller_review_count", 0),
            university = obj.optString("university", ""),
            location = obj.optString("location", ""),
            category = obj.optString("category", ""),
            condition = obj.optString("condition", ""),
            description = obj.optString("description", ""),
            postedTime = obj.optString("posted_time", obj.optString("time_ago", "Recently")),
            isFeatured = obj.optBoolean("is_featured", false),
            isSold = obj.optBoolean("is_sold", false)
        )
    }

    private fun parseUserProfile(obj: JSONObject): UserProfile {
        val badge = when (obj.optString("verification_badge", "").uppercase(Locale.US)) {
            "GOLD" -> VerificationBadge.GOLD
            "BLUE" -> VerificationBadge.BLUE
            else -> VerificationBadge.NONE
        }

        val links = SocialLinks(
            website = obj.optString("website", ""),
            linkedin = obj.optString("linkedin", ""),
            twitter = obj.optString("twitter", ""),
            instagram = obj.optString("instagram", ""),
            featuredLink = obj.optString("featured_link", ""),
            featuredLinkLabel = obj.optString("featured_link_label", "")
        )

        val skills = mutableListOf<String>()
        obj.optJSONArray("core_skills")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optString(i, "")
                if (s.isNotBlank()) skills.add(s)
            }
        }
        
        val hobbiesList = mutableListOf<String>()
        obj.optJSONArray("hobbies")?.let { arr ->
            for (i in 0 until arr.length()) {
                val h = arr.optString(i, "")
                if (h.isNotBlank()) hobbiesList.add(h)
            }
        }
        
        val languagesList = mutableListOf<String>()
        obj.optJSONArray("languages")?.let { arr ->
            for (i in 0 until arr.length()) {
                val l = arr.optString(i, "")
                if (l.isNotBlank()) languagesList.add(l)
            }
        }
        
        val endorsementsList = mutableListOf<SkillEndorsement>()
        obj.optJSONArray("skill_endorsements")?.let { arr ->
            for (i in 0 until arr.length()) {
                val seObj = arr.optJSONObject(i)
                if (seObj != null) {
                    endorsementsList.add(
                        SkillEndorsement(
                            skill = seObj.optString("skill", ""),
                            endorsements = seObj.optInt("endorsements", 0),
                            endorsedByMe = seObj.optBoolean("endorsed_by_me", false)
                        )
                    )
                }
            }
        }
        
        val badgesList = mutableListOf<AchievementBadge>()
        obj.optJSONArray("badges")?.let { arr ->
            for (i in 0 until arr.length()) {
                val bObj = arr.optJSONObject(i)
                if (bObj != null) {
                    badgesList.add(
                        AchievementBadge(
                            id = bObj.optString("id", ""),
                            title = bObj.optString("title", ""),
                            description = bObj.optString("description", ""),
                            iconName = bObj.optString("iconName", "trophy")
                        )
                    )
                }
            }
        }
        
        val availabilityStr = obj.optString("custom_status", "")
        val availabilityStatus = try {
            if (availabilityStr.isNotBlank()) {
                AvailabilityStatus.valueOf(availabilityStr)
            } else {
                AvailabilityStatus.NONE
            }
        } catch (e: Exception) {
            AvailabilityStatus.NONE
        }

        return UserProfile(
            id = obj.optString("id", ""),
            fullName = obj.optString("full_name", obj.optString("name", "")),
            username = obj.optString("username", ""),
            avatarUrl = obj.optString("avatar_url", ""),
            coverPhotoUrl = obj.optString("cover_photo", obj.optString("cover_photo_url", obj.optString("cover_url", ""))),
            verificationBadge = badge,
            professionalHeadline = obj.optString("professional_headline", ""),
            currentJobTitle = obj.optString("current_job_title", ""),
            university = obj.optString("university", ""),
            faculty = obj.optString("faculty", ""),
            department = obj.optString("department", ""),
            courseOfStudy = obj.optString("course_of_study", ""),
            academicLevel = obj.optString("academic_level", ""),
            graduationYear = obj.optString("graduation_year", ""),
            bio = obj.optString("bio", ""),
            availability = availabilityStatus,
            countryOfOrigin = obj.optString("country_of_origin", ""),
            currentCityState = obj.optString("current_city_state", ""),
            email = ContactField(obj.optString("email", ""), true),
            phone = ContactField(obj.optString("phone", ""), true),
            whatsapp = ContactField(obj.optString("whatsapp", ""), true),
            links = links,
            coreSkills = skills,
            skillEndorsements = endorsementsList,
            hobbies = hobbiesList,
            languages = languagesList,
            favoriteQuote = obj.optString("favorite_quote", ""),
            followerCount = obj.optInt("follower_count", 0),
            followingCount = obj.optInt("following_count", 0),
            profileViewsThisWeek = obj.optInt("profile_views_this_week", obj.optInt("profile_views", 0)),
            dailyStreak = obj.optInt("daily_streak", 0),
            worldRank = obj.optInt("world_rank", 0),
            campusRank = obj.optInt("campus_rank", 0),
            onlineNow = obj.optBoolean("online_now", false),
            verifiedAtMillis = obj.optLong("verified_at_millis", 0L),
            joinedLabel = obj.optString("joined_label", ""),
            isSellerActive = obj.optBoolean("is_seller_active", false),
            sellerStoreName = obj.optString("seller_store_name", ""),
            badges = badgesList
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

    // COMMENTS
    // ============================================================

    suspend fun fetchComments(postId: String): List<Comment> = withContext(Dispatchers.IO) {
        try {
            val req = newRequestBuilder("/rest/v1/feed_comments?post_id=eq.$postId&order=created_at.asc")
                .get()
                .build()
            val currentUserId = getCurrentUserId() ?: ""
            val currentUsername = SupabaseService.appContext?.getSharedPreferences("blink_auth_prefs", android.content.Context.MODE_PRIVATE)?.getString("username", "") ?: ""

            var myLikedComments = setOf<Long>()
            val likeReq = newRequestBuilder("/rest/v1/comment_likes?select=comment_id" + 
                if (currentUserId.isNotBlank()) "&user_id=eq.$currentUserId" else if (currentUsername.isNotBlank()) "&username=eq.$currentUsername" else "")
                .get().build()
            executeRequest(likeReq).use { r ->
                if (r.isSuccessful) {
                    val body = r.body?.string().orEmpty()
                    if (body.isNotBlank() && body != "[]") {
                        val arr = JSONArray(body)
                        val set = mutableSetOf<Long>()
                        for (i in 0 until arr.length()) set.add(arr.getJSONObject(i).optLong("comment_id"))
                        myLikedComments = set
                    }
                }
            }

            executeRequest(req).use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@withContext emptyList()
                if (body.isBlank() || body == "[]") return@withContext emptyList()

                val arr = JSONArray(body)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optLong("id")
                        add(Comment(
                            id = id,
                            user = obj.optString("username", ""),
                            avatar = obj.optString("avatar_url", ""),
                            text = obj.optString("text", ""),
                            time = obj.optString("time_ago", "Recently"),
                            likes = obj.optInt("like_count", 0),
                            isLiked = myLikedComments.contains(id),
                            replies = emptyList() // If replies are supported, fetch them
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchComments failed", e)
            emptyList()
        }
    }

    suspend fun addComment(postId: String, text: String, replyToUser: String?): Comment? = withContext(Dispatchers.IO) {
        try {
            val currentUserId = getCurrentUserId() ?: ""
            val currentUsername = SupabaseService.appContext?.getSharedPreferences("blink_auth_prefs", android.content.Context.MODE_PRIVATE)?.getString("username", "") ?: ""
            val currentUserAvatar = SupabaseService.appContext?.getSharedPreferences("blink_auth_prefs", android.content.Context.MODE_PRIVATE)?.getString("avatar_url", "") ?: ""

            val obj = JSONObject().apply {
                put("post_id", postId)
                put("user_id", currentUserId)
                put("username", currentUsername)
                put("avatar_url", currentUserAvatar)
                put("text", text)
                if (replyToUser != null) put("reply_to_user", replyToUser)
            }
            val req = newRequestBuilder("/rest/v1/feed_comments", authenticated = true)
                .addHeader("Prefer", "return=representation")
                .post(obj.toString().toRequestBody(jsonMediaType))
                .build()

            executeRequest(req).use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful && body.isNotBlank()) {
                    val arr = JSONArray(body)
                    if (arr.length() > 0) {
                        val resObj = arr.getJSONObject(0)
                        return@withContext Comment(
                            id = resObj.optLong("id"),
                            user = resObj.optString("username", currentUsername),
                            avatar = resObj.optString("avatar_url", currentUserAvatar),
                            text = resObj.optString("text", text),
                            time = "Just now",
                            likes = 0,
                            isLiked = false
                        )
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "addComment failed", e)
            null
        }
    }

    suspend fun toggleCommentLike(commentId: Long, liked: Boolean, newLikeCount: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentUserId = getCurrentUserId() ?: ""
            val currentUsername = SupabaseService.appContext?.getSharedPreferences("blink_auth_prefs", android.content.Context.MODE_PRIVATE)?.getString("username", "") ?: ""

            if (liked) {
                val obj = JSONObject().apply {
                    put("comment_id", commentId)
                    if (currentUserId.isNotBlank()) put("user_id", currentUserId)
                    if (currentUsername.isNotBlank()) put("username", currentUsername)
                }
                val req = newRequestBuilder("/rest/v1/comment_likes", authenticated = true)
                    .addHeader("Prefer", "resolution=merge-duplicates")
                    .post(obj.toString().toRequestBody(jsonMediaType)).build()
                executeRequest(req).use { it.isSuccessful }
            } else {
                val filter = when {
                    currentUserId.isNotBlank() -> "user_id=eq.$currentUserId"
                    currentUsername.isNotBlank() -> "username=eq.$currentUsername"
                    else -> ""
                }
                val url = if (filter.isNotBlank()) "/rest/v1/comment_likes?comment_id=eq.$commentId&$filter" else "/rest/v1/comment_likes?comment_id=eq.$commentId"
                val req = newRequestBuilder(url, authenticated = true).delete().build()
                executeRequest(req).use { it.isSuccessful }
            }

            val patchObj = JSONObject().apply { put("like_count", newLikeCount) }
            val patchReq = newRequestBuilder("/rest/v1/feed_comments?id=eq.$commentId", authenticated = true)
                .patch(patchObj.toString().toRequestBody(jsonMediaType)).build()
            executeRequest(patchReq).use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e(TAG, "toggleCommentLike failed", e)
            false
        }
    }

    suspend fun fetchActivities(): Result<List<ActivityItem>> = withContext(Dispatchers.IO) {
        try {
            val currentUserId = getCurrentUserId() ?: ""
            val currentUsername = getCurrentUsername() ?: ""

            if (currentUserId.isBlank() && currentUsername.isBlank()) {
                return@withContext Result.failure(IllegalStateException("User not logged in"))
            }

            val encodedUsername = encodeValue(currentUsername)
            val encodedUserId = encodeValue(currentUserId)

            val url = if (currentUserId.isNotBlank() && currentUsername.isNotBlank()) {
                "/rest/v1/activities?select=*&or=(recipient_username.eq.$encodedUsername,user_id.eq.$encodedUserId)&order=created_at.desc&limit=100"
            } else if (currentUsername.isNotBlank()) {
                "/rest/v1/activities?select=*&recipient_username=eq.$encodedUsername&order=created_at.desc&limit=100"
            } else {
                "/rest/v1/activities?select=*&user_id=eq.$encodedUserId&order=created_at.desc&limit=100"
            }

            val request = newRequestBuilder(url, authenticated = true).get().build()

            executeRequest(request).use { response ->
                var body = response.body?.string().orEmpty()

                if (response.code == 404 || response.code == 400) {
                    val fallbackUrl = if (currentUsername.isNotBlank()) {
                        "/rest/v1/notifications?select=*&username=eq.$encodedUsername&order=created_at.desc&limit=100"
                    } else {
                        "/rest/v1/notifications?select=*&user_id=eq.$encodedUserId&order=created_at.desc&limit=100"
                    }
                    val fallbackReq = newRequestBuilder(fallbackUrl, authenticated = true).get().build()
                    executeRequest(fallbackReq).use { fallbackResp ->
                        if (fallbackResp.isSuccessful) {
                            body = fallbackResp.body?.string().orEmpty()
                        } else if (!response.isSuccessful) {
                            return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))
                        }
                    }
                } else if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))
                }

                if (body.isBlank() || body == "[]") {
                    return@withContext Result.success(emptyList())
                }

                val array = JSONArray(body)
                val items = mutableListOf<ActivityItem>()

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id", UUID.randomUUID().toString())
                    val actor = obj.optString("actor_username", obj.optString("username", "campus_user"))
                    val avatar = obj.optString("actor_avatar", obj.optString("avatar_url", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&auto=format&fit=crop&q=80"))
                    val action = obj.optString("action", obj.optString("content", obj.optString("title", "interacted with you")))
                    val createdAt = obj.optString("created_at", "")
                    val isRead = obj.optBoolean("is_read", false)
                    val catStr = obj.optString("category", obj.optString("type", "ALL")).uppercase(Locale.US)
                    val category = try {
                        NotificationFilter.valueOf(catStr)
                    } catch (_: Exception) {
                        when {
                            catStr.contains("LIKE") || catStr.contains("SAVE") -> NotificationFilter.LIKES
                            catStr.contains("COMMENT") || catStr.contains("MENTION") -> NotificationFilter.COMMENTS
                            catStr.contains("MARKET") || catStr.contains("ITEM") -> NotificationFilter.MARKET
                            else -> NotificationFilter.ALL
                        }
                    }

                    val targetPostId = obj.optString("target_post_id", obj.optString("post_id", "")).ifBlank { null }
                    val targetMarketId = obj.optString("target_market_id", obj.optString("market_id", "")).ifBlank { null }
                    val targetUsername = obj.optString("target_username", obj.optString("actor_username", "")).ifBlank { null }
                    val targetType = obj.optString("target_type", "").ifBlank { null }
                    val previewText = obj.optString("preview_text", "").ifBlank { null }

                    items.add(
                        ActivityItem(
                            id = id,
                            user = actor,
                            avatar = avatar,
                            action = action,
                            time = formatTimeAgo(createdAt),
                            rawTimestamp = createdAt,
                            isUnread = !isRead,
                            category = category,
                            targetPostId = targetPostId,
                            targetMarketId = targetMarketId,
                            targetUsername = targetUsername,
                            targetType = targetType,
                            previewText = previewText,
                            verificationBadge = VerificationBadge.NONE
                        )
                    )
                }

                Result.success(items)
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchActivities exception", e)
            Result.failure(e)
        }
    }

    suspend fun recordActivity(
        recipientUsername: String,
        action: String,
        category: NotificationFilter = NotificationFilter.ALL,
        targetPostId: String? = null,
        targetMarketId: String? = null,
        targetUsername: String? = null,
        targetType: String? = null,
        previewText: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentUsername = getCurrentUsername() ?: ""
            val currentUserId = getCurrentUserId() ?: ""
            if (recipientUsername.isBlank() || recipientUsername.equals(currentUsername, ignoreCase = true)) {
                return@withContext false
            }

            val currentUserAvatar = SupabaseService.appContext?.getSharedPreferences("blink_auth_prefs", android.content.Context.MODE_PRIVATE)?.getString("avatar_url", "") ?: ""

            val json = JSONObject().apply {
                put("recipient_username", recipientUsername.trim().lowercase(Locale.US))
                if (currentUserId.isNotBlank()) put("user_id", currentUserId)
                put("actor_username", currentUsername)
                if (currentUserAvatar.isNotBlank()) put("actor_avatar", currentUserAvatar)
                put("action", action)
                put("category", category.name)
                targetPostId?.let { put("target_post_id", it) }
                targetMarketId?.let { put("target_market_id", it) }
                targetUsername?.let { put("target_username", it) }
                targetType?.let { put("target_type", it) }
                previewText?.let { put("preview_text", it) }
                put("is_read", false)
                put("created_at", nowIso())
            }

            val request = newRequestBuilder("/rest/v1/activities", authenticated = true)
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            executeRequest(request).use { response ->
                if (!response.isSuccessful) {
                    val fallbackJson = JSONObject().apply {
                        put("username", recipientUsername.trim().lowercase(Locale.US))
                        put("type", category.name)
                        put("title", "@$currentUsername")
                        put("content", action)
                        put("created_at", nowIso())
                    }
                    val fallbackReq = newRequestBuilder("/rest/v1/notifications", authenticated = true)
                        .post(fallbackJson.toString().toRequestBody(jsonMediaType))
                        .build()
                    executeRequest(fallbackReq).use { it.isSuccessful }
                } else {
                    true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "recordActivity failed", e)
            false
        }
    }

    suspend fun markActivityRead(activityId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val patchObj = JSONObject().apply { put("is_read", true) }
            val req = newRequestBuilder("/rest/v1/activities?id=eq.$activityId", authenticated = true)
                .patch(patchObj.toString().toRequestBody(jsonMediaType))
                .build()
            executeRequest(req).use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e(TAG, "markActivityRead failed", e)
            false
        }
    }

    suspend fun markAllActivitiesRead(): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentUsername = getCurrentUsername() ?: ""
            val patchObj = JSONObject().apply { put("is_read", true) }
            val req = newRequestBuilder("/rest/v1/activities?recipient_username=eq.$currentUsername&is_read=eq.false", authenticated = true)
                .patch(patchObj.toString().toRequestBody(jsonMediaType))
                .build()
            executeRequest(req).use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e(TAG, "markAllActivitiesRead failed", e)
            false
        }
    }

    suspend fun recordSkillEndorsement(targetUsername: String, skillName: String, endorserUsername: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("target_username", targetUsername.trim().lowercase(Locale.US))
                put("skill", skillName.trim())
                put("endorser_username", endorserUsername.trim().lowercase(Locale.US))
                put("created_at", nowIso())
            }
            val req = newRequestBuilder("/rest/v1/skill_endorsements", authenticated = true)
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()
            val success = executeRequest(req).use { it.isSuccessful }
            if (success) {
                recordActivity(
                    recipientUsername = targetUsername,
                    action = "endorsed your skill: $skillName",
                    category = NotificationFilter.ALL,
                    targetUsername = endorserUsername
                )
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "recordSkillEndorsement failed", e)
            false
        }
    }

    suspend fun submitVerificationRequest(tier: String, paymentReference: String, amount: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentUsername = getCurrentUsername() ?: ""
            val json = JSONObject().apply {
                put("username", currentUsername)
                put("tier", tier)
                put("payment_reference", paymentReference)
                put("amount", amount)
                put("status", "approved") // Instant verified upon successful gateway payment simulation
                put("created_at", nowIso())
            }
            val req = newRequestBuilder("/rest/v1/verification_requests", authenticated = true)
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()
            executeRequest(req).use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e(TAG, "submitVerificationRequest failed", e)
            false
        }
    }

    suspend fun updateGameStats(score: Int, coins: Int, streak: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentUsername = getCurrentUsername() ?: ""
            val json = JSONObject().apply {
                put("username", currentUsername)
                put("points", score)
                put("coins", coins)
                put("streak", streak)
                put("updated_at", nowIso())
            }
            val req = newRequestBuilder("/rest/v1/leaderboard", authenticated = true)
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()
            executeRequest(req).use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e(TAG, "updateGameStats failed", e)
            false
        }
    }
}

enum class ProfileMediaType {
    AVATAR,
    COVER
}

private fun String.capitalizeWords(): String {
    return split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { char -> char.uppercase() }
        }
}
