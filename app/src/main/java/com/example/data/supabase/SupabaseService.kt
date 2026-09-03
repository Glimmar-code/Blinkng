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
import kotlinx.coroutines.runBlocking
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
                    ?: throw IllegalStateException("Authenticated Supabase request requires a real user JWT.")
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
                val storedToken = accessToken() ?: ""
                
                if (storedToken != anonKey && storedToken != currentToken) {
                    // Token was refreshed by another thread
                    activeRequest = activeRequest.newBuilder()
                        .header("Authorization", "Bearer $storedToken")
                        .build()
                    response = withContext(Dispatchers.IO) { client.newCall(activeRequest).execute() }
                } else {
                    val refreshed = refreshSession()
                    if (refreshed) {
                        val refreshedToken = accessToken() ?: ""
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
                        fetchProfileByUsername(cleanInput)?.email?.value?.trim()?.lowercase(Locale.US).orEmpty()
                    }

                if (loginEmail.isBlank()) {
                    return@withContext Result.failure(Exception("Username was not found in Supabase."))
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

                    if (userId.isBlank() || !isValidUuid(userId) || accessToken.isBlank()) {
                        return@withContext Result.failure(Exception("Supabase did not return a usable authenticated session."))
                    }
                    val profile = ensureAuthenticatedProfile(
                        userId = userId,
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
    suspend fun revokeCurrentSupabaseSession() = withContext(Dispatchers.IO) {
        try {
            if (!accessToken().isNullOrBlank()) {
                val req = newRequestBuilder("/auth/v1/logout", authenticated = true).post("".toRequestBody(jsonMediaType)).build()
                executeRequest(req).use { response ->
                    if (!response.isSuccessful && response.code !in setOf(401, 403)) {
                        Log.w(TAG,"AUTH_LOGOUT failed status=${response.code} body=${response.body?.string().orEmpty()}")
                    }
                }
            }
        } finally { clearSession() }
    }


    // ============================================================
    // SESSION / CURRENT USER
    // ============================================================
    fun getCurrentUsername(): String? {
        val c=SupabaseService.appContext ?: return null
        return c.getSharedPreferences("blink_auth_prefs", Context.MODE_PRIVATE).getString("username",null)
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
        val token=accessToken() ?: return false
        val uid=decodeJwtSubject(token) ?: return false
        val exp=jwtExpirationMillis(token) ?: return false
        return isValidUuid(uid) && exp > System.currentTimeMillis()
    }

    suspend fun checkServerStatus(): Boolean = withContext(Dispatchers.IO) {
        try {
            val healthToken = accessToken()?.takeIf { it.isNotBlank() } ?: anonKey
            val request = Request.Builder()
                .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $healthToken")
                .addHeader("Accept", "application/json")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
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
    ): UserProfile = withContext(Dispatchers.IO) {
        val uid = userId.takeIf { isValidUuid(it) } ?: getCurrentUserId()
            ?: throw IllegalStateException("No valid authenticated Supabase user ID.")
        fetchProfileById(uid)?.let { return@withContext it }
        val cleanUsername = username.trim().lowercase(Locale.US).replace(Regex("[^a-z0-9._-]"), "_")
        if (cleanUsername.isBlank()) throw IllegalStateException("Profile username is required.")
        val body = JSONObject().apply {
            put("id", uid); put("email", email.trim().lowercase(Locale.US));
            put("username", cleanUsername); put("full_name", fullName.trim().ifBlank { cleanUsername })
            faculty?.trim()?.takeIf { it.isNotBlank() }?.let { put("faculty", it) }
            university?.trim()?.takeIf { it.isNotBlank() }?.let { put("university", it) }
            put("updated_at", nowIso())
        }
        executeRequest(newRequestBuilder("/rest/v1/profiles", true)
            .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
            .post(body.toString().toRequestBody(jsonMediaType)).build()).use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful || raw == "[]" || raw.isBlank()) {
                throw IllegalStateException(parseSupabaseError(raw, "Profile creation failed."))
            }
        }
        fetchProfileById(uid) ?: throw IllegalStateException("Profile was written but could not be read back.")
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
    suspend fun updateProfile(profile: UserProfile): Boolean = withContext(Dispatchers.IO) {
        try {
            val uid = getCurrentUserId() ?: throw IllegalStateException("Not authenticated.")
            val year = profile.graduationYear.trim().toIntOrNull()
            val body = JSONObject().apply {
                put("full_name", profile.fullName.trim())
                put("username", profile.username.trim().lowercase(Locale.US))
                put("avatar_url", profile.avatarUrl)
                put("cover_photo_url", profile.coverPhotoUrl)
                put("professional_headline", profile.professionalHeadline)
                put("current_job_title", profile.currentJobTitle)
                put("bio", profile.bio)
                put("university", profile.university)
                put("faculty", profile.faculty)
                put("department", profile.department)
                put("course_of_study", profile.courseOfStudy)
                put("academic_level", profile.academicLevel)
                if (year != null) put("graduation_year", year) else put("graduation_year", JSONObject.NULL)
                put("country_of_origin", profile.countryOfOrigin)
                put("current_city_state", profile.currentCityState)
                put("phone", profile.phone.value); put("whatsapp", profile.whatsapp.value)
                put("website", profile.links.website); put("linkedin", profile.links.linkedin)
                put("twitter", profile.links.twitter); put("instagram", profile.links.instagram)
                put("featured_link", profile.links.featuredLink); put("featured_link_label", profile.links.featuredLinkLabel)
                put("favorite_quote", profile.favoriteQuote)
                put("availability", profile.availability.label)
                put("core_skills", JSONArray(profile.coreSkills)); put("hobbies", JSONArray(profile.hobbies)); put("languages", JSONArray(profile.languages))
                put("updated_at", nowIso())
            }
            executeRequest(newRequestBuilder("/rest/v1/profiles?id=eq.${encodeValue(uid)}", true)
                .addHeader("Prefer", "return=representation")
                .patch(body.toString().toRequestBody(jsonMediaType)).build()).use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful || raw == "[]" || raw.isBlank()) {
                    Log.e(TAG, "PROFILE_UPDATE failed status=${resp.code} body=$raw")
                    return@withContext false
                }
                true
            }
        } catch (e: Exception) { Log.e(TAG, "PROFILE_UPDATE exception", e); false }
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

    suspend fun fetchFeedPosts(): List<FeedPost> = withContext(Dispatchers.IO) {
        var uid = getCurrentUserId()

        // feed_posts is protected by RLS for authenticated users. If the access
        // token was lost/expired but we still have a refresh token, recover the
        // session before reading. Falling back to anon here returns HTTP 200
        // with an empty array, which makes the Home feed look blank.
        if (!isAuthenticated() && !refreshToken().isNullOrBlank()) {
            if (refreshSession()) {
                uid = getCurrentUserId()
            }
        }

        if (uid.isNullOrBlank()) {
            throw IllegalStateException("No authenticated Supabase session is available for the feed.")
        }

        val postsRaw = executeRequest(newRequestBuilder("/rest/v1/feed_posts?select=*&order=created_at.desc&limit=100", authenticated = true).get().build()).use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException(parseSupabaseError(raw, "Feed fetch failed."))
            JSONArray(if (raw.isBlank()) "[]" else raw)
        }
        val userIds = buildSet { for (i in 0 until postsRaw.length()) postsRaw.getJSONObject(i).optString("user_id").takeIf { isValidUuid(it) }?.let { add(it) } }
        val profiles = mutableMapOf<String, JSONObject>()
        if (userIds.isNotEmpty()) {
            executeRequest(newRequestBuilder("/rest/v1/profiles?id=in.(${userIds.joinToString(",")})&select=id,username,avatar_url,is_verified,verification_badge,full_name", authenticated = true).get().build()).use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (resp.isSuccessful && raw.isNotBlank() && raw != "[]") {
                    val a = JSONArray(raw)
                    for (i in 0 until a.length()) { val p = a.getJSONObject(i); profiles[p.optString("id")] = p }
                }
            }
        }
        val liked = if (uid != null) {
            executeRequest(newRequestBuilder("/rest/v1/post_likes?select=post_id&user_id=eq.${encodeValue(uid)}", true).get().build()).use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (resp.isSuccessful && raw.isNotBlank() && raw != "[]") {
                    val a = JSONArray(raw)
                    buildSet { for (i in 0 until a.length()) add(a.getJSONObject(i).optString("post_id")) }
                } else emptySet()
            }
        } else emptySet()
        val bookmarked = if (uid != null) {
            executeRequest(newRequestBuilder("/rest/v1/post_bookmarks?select=post_id&user_id=eq.${encodeValue(uid)}", true).get().build()).use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (resp.isSuccessful && raw.isNotBlank() && raw != "[]") {
                    val a = JSONArray(raw)
                    buildSet { for (i in 0 until a.length()) add(a.getJSONObject(i).optString("post_id")) }
                } else emptySet()
            }
        } else emptySet()
        buildList {
            for (i in 0 until postsRaw.length()) {
                val source = postsRaw.getJSONObject(i)
                val profile = profiles[source.optString("user_id")] ?: continue
                val username = profile.optString("username").trim()
                if (username.isBlank()) continue

                val mapped = JSONObject(source.toString()).apply {
                    put("author", username)
                    put("author_avatar", profile.optString("avatar_url"))
                    put("username", username)
                    put("is_verified", profile.optBoolean("is_verified"))
                    put("verification_badge", profile.optString("verification_badge"))
                }

                add(
                    parseFeedPost(mapped).copy(
                        isLiked = liked.contains(source.optString("id")),
                        isBookmarked = bookmarked.contains(source.optString("id"))
                    )
                )
            }
        }
    }

    /**
     * Creates a feed post using the authenticated Supabase user's ID.
     */
    suspend fun createFeedPost(
        author: String, authorAvatar: String, facultyTag: String, text: String, imageUrl: String?, videoUrl: String? = null,
        tags: List<String> = emptyList(), mentions: List<String> = emptyList(), poll: PostPoll? = null, isReel: Boolean = false,
        audience: String = "Everyone", category: String = "Campus Life", location: String? = null, linkUrl: String? = null,
        allowComments: Boolean = true, hideLikes: Boolean = false, isPinned: Boolean = false, isDisappearing: Boolean = false,
        audioTitle: String? = null, altText: String? = null
    ): FeedPost? = withContext(Dispatchers.IO) {
        try {
            val uid = getCurrentUserId() ?: throw IllegalStateException("Not authenticated.")
            val mentionIds = JSONArray()
            for (mention in mentions) {
                val mid = if (isValidUuid(mention)) mention else fetchProfileByUsername(mention.removePrefix("@"))?.id
                if (!mid.isNullOrBlank() && isValidUuid(mid)) mentionIds.put(mid)
            }
            val imageUrls = buildList {
                val raw = imageUrl?.trim().orEmpty()
                if (raw.isNotBlank()) {
                    if (raw.startsWith("[")) {
                        runCatching {
                            val array = JSONArray(raw)
                            for (index in 0 until array.length()) {
                                array.optString(index)
                                    .trim()
                                    .takeIf { it.isNotBlank() }
                                    ?.let { add(it) }
                            }
                        }.onFailure {
                            add(raw)
                        }
                    } else {
                        add(raw)
                    }
                }
            }

            val body = JSONObject().apply {
                put("user_id", uid)
                put(
                    "type",
                    when {
                        isReel || !videoUrl.isNullOrBlank() -> "reel"
                        imageUrls.isNotEmpty() -> "photo"
                        else -> "text"
                    }
                )
                put("faculty", facultyTag.trim())
                put("text", text.trim())
                if (imageUrls.isNotEmpty()) {
                    put("image_url", imageUrls.first())
                    put("images", JSONArray(imageUrls))
                }
                videoUrl?.takeIf { it.isNotBlank() }?.let { put("video_url", it) }
                put("tags", JSONArray(tags.filter { it.isNotBlank() }))
                put("mentions", mentionIds)
                put("is_reel", isReel)
                put("audience", audience)
                put("category", category)
                location?.takeIf { it.isNotBlank() }?.let { put("location", it) }
                linkUrl?.takeIf { it.isNotBlank() }?.let { put("link_url", it) }
                put("allow_comments", allowComments)
                put("hide_likes", hideLikes)
                put("is_pinned", isPinned)
                put("is_disappearing", isDisappearing)
                audioTitle?.takeIf { it.isNotBlank() }?.let { put("audio_title", it) }
                altText?.takeIf { it.isNotBlank() }?.let { put("alt_text", it) }
            }
            val created = executeRequest(newRequestBuilder("/rest/v1/feed_posts", true).addHeader("Prefer", "return=representation")
                .post(body.toString().toRequestBody(jsonMediaType)).build()).use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful || raw.isBlank() || raw == "[]") throw IllegalStateException(parseSupabaseError(raw, "Could not create post."))
                JSONArray(raw).getJSONObject(0)
            }
            val postId = created.optString("id").takeIf { isValidUuid(it) } ?: throw IllegalStateException("Invalid post ID returned by Supabase.")
            if (poll != null) {
                val p = JSONObject().apply { put("post_id", postId); put("question", poll.question); put("allows_multiple", false) }
                val pollRow = executeRequest(newRequestBuilder("/rest/v1/polls", true).addHeader("Prefer", "return=representation")
                    .post(p.toString().toRequestBody(jsonMediaType)).build()).use { resp ->
                    val raw = resp.body?.string().orEmpty(); if (!resp.isSuccessful || raw.isBlank() || raw == "[]") throw IllegalStateException(parseSupabaseError(raw, "Could not create poll.")); JSONArray(raw).getJSONObject(0)
                }
                val pollId = pollRow.optString("id")
                for ((index, option) in poll.options.withIndex()) {
                    val o = JSONObject().apply { put("poll_id", pollId); put("option_text", option.text); put("position", index) }
                    executeRequest(newRequestBuilder("/rest/v1/poll_options", true).post(o.toString().toRequestBody(jsonMediaType)).build()).use { resp ->
                        if (!resp.isSuccessful) throw IllegalStateException(parseSupabaseError(resp.body?.string().orEmpty(), "Could not create poll option."))
                    }
                }
                executeRequest(newRequestBuilder("/rest/v1/feed_posts?id=eq.$postId", true).patch(JSONObject().put("poll_id", pollId).toString().toRequestBody(jsonMediaType)).build()).use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException(parseSupabaseError(resp.body?.string().orEmpty(), "Could not attach poll."))
                }
            }
            fetchFeedPosts().firstOrNull { it.id == postId } ?: parseFeedPost(created)
        } catch (e: Exception) { Log.e(TAG, "POST_CREATE exception", e); null }
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
                        "/storage/v1/object/post-media/$path",
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

                    "$baseUrl/storage/v1/object/public/post-media/$path"
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
    
    suspend fun togglePostBookmark(postId: String, bookmarked: Boolean): Boolean = withContext(Dispatchers.IO) {
        val uid=getCurrentUserId() ?: throw IllegalStateException("Not authenticated.")
        val request=if(bookmarked) newRequestBuilder("/rest/v1/post_bookmarks",true).addHeader("Prefer","resolution=merge-duplicates").post(JSONObject().apply{put("post_id",postId);put("user_id",uid)}.toString().toRequestBody(jsonMediaType)).build()
        else newRequestBuilder("/rest/v1/post_bookmarks?post_id=eq.${encodeValue(postId)}&user_id=eq.${encodeValue(uid)}",true).delete().build()
        executeRequest(request).use{resp->val raw=resp.body?.string().orEmpty();if(!resp.isSuccessful)throw IllegalStateException(parseSupabaseError(raw,"Bookmark update failed."));true}
    }
    suspend fun togglePostLike(postId: String, liked: Boolean, newLikeCount: Int): Boolean = withContext(Dispatchers.IO) {
        val uid=getCurrentUserId() ?: throw IllegalStateException("Not authenticated.")
        val request=if(liked) newRequestBuilder("/rest/v1/post_likes",true).addHeader("Prefer","resolution=merge-duplicates").post(JSONObject().apply{put("post_id",postId);put("user_id",uid)}.toString().toRequestBody(jsonMediaType)).build()
        else newRequestBuilder("/rest/v1/post_likes?post_id=eq.${encodeValue(postId)}&user_id=eq.${encodeValue(uid)}",true).delete().build()
        executeRequest(request).use{resp->val raw=resp.body?.string().orEmpty();if(!resp.isSuccessful)throw IllegalStateException(parseSupabaseError(raw,"Like update failed."))}
        executeRequest(newRequestBuilder("/rest/v1/feed_posts?id=eq.${encodeValue(postId)}&select=like_count",true).get().build()).use{resp->val raw=resp.body?.string().orEmpty();if(!resp.isSuccessful)throw IllegalStateException(parseSupabaseError(raw,"Like count refresh failed."))}
        true
    }

    suspend fun deleteFeedPost(postId: String): Boolean = withContext(Dispatchers.IO) {
        val uid=getCurrentUserId() ?: throw IllegalStateException("Not authenticated.")
        executeRequest(newRequestBuilder("/rest/v1/feed_posts?id=eq.${encodeValue(postId)}&user_id=eq.${encodeValue(uid)}",true).delete().build()).use{resp->val raw=resp.body?.string().orEmpty();if(!resp.isSuccessful)throw IllegalStateException(parseSupabaseError(raw,"Post deletion failed."));true}
    }

    // ============================================================

    // ============================================================
    // POLL & STORY HELPERS
    // ============================================================

    private suspend fun fetchUserStoryViews(username: String): Set<String> = withContext(Dispatchers.IO) {
        try {
            val req = newRequestBuilder(
                "/rest/v1/story_views?user_id=eq.${encodeValue(getCurrentUserId().orEmpty())}&select=story_id",
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
                "/rest/v1/story_likes?user_id=eq.${getCurrentUserId()}&select=story_id",
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
        val uid = getCurrentUserId()
        val viewed = if (uid != null) {
            executeRequest(newRequestBuilder("/rest/v1/story_views?user_id=eq.${encodeValue(uid)}&select=story_id", true).get().build()).use { r ->
                val b = r.body?.string().orEmpty()
                if (r.isSuccessful && b.isNotBlank() && b != "[]") {
                    val a = JSONArray(b)
                    buildSet { for (i in 0 until a.length()) add(a.getJSONObject(i).optString("story_id")) }
                } else emptySet()
            }
        } else emptySet()
        val liked = if (uid != null) {
            executeRequest(newRequestBuilder("/rest/v1/story_likes?user_id=eq.${encodeValue(uid)}&select=story_id", true).get().build()).use { r ->
                val b = r.body?.string().orEmpty()
                if (r.isSuccessful && b.isNotBlank() && b != "[]") {
                    val a = JSONArray(b)
                    buildSet { for (i in 0 until a.length()) add(a.getJSONObject(i).optString("story_id")) }
                } else emptySet()
            }
        } else emptySet()
        val raw = executeRequest(newRequestBuilder("/rest/v1/stories?active=eq.true&order=created_at.desc&limit=50", uid != null).get().build()).use { r ->
            val b = r.body?.string().orEmpty()
            if (!r.isSuccessful) return@withContext emptyList()
            b
        }
        val arr = JSONArray(if (raw.isBlank()) "[]" else raw)
        val cache = mutableMapOf<String, UserProfile>()
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val sid = o.optString("id")
                val u = o.optString("user_id")
                val prof = if (u.isNotBlank()) fetchProfileById(u)?.also { cache[u] = it } else null
                add(
                    Story(
                        id = sid,
                        username = prof?.username ?: u,
                        avatar = prof?.avatarUrl.orEmpty(),
                        hasUnseen = !viewed.contains(sid),
                        isUser = (uid != null && u == uid),
                        storyImage = o.optString("image_url", o.optString("media_url", o.optString("video_url", ""))),
                        caption = o.optString("caption", o.optString("text", "")),
                        timeAgo = formatTimeAgo(o.optString("created_at")),
                        faculty = prof?.faculty.orEmpty(),
                        university = prof?.university.orEmpty(),
                        likesCount = o.optInt("likes_count", 0),
                        isLiked = liked.contains(sid),
                        verificationBadge = prof?.verificationBadge ?: VerificationBadge.NONE
                    )
                )
            }
        }
    }

    suspend fun uploadStoryMedia(userId: String, bytes: ByteArray, mimeType: String, isVideo: Boolean): String? = withContext(Dispatchers.IO) {
        if (userId.isBlank() || bytes.isEmpty()) return@withContext null
        try {
            val ext = when { isVideo && mimeType.contains("webm", true) -> "webm"; isVideo -> "mp4"; mimeType.contains("png", true) -> "png"; mimeType.contains("webp", true) -> "webp"; else -> "jpg" }
            val path = "$userId/${UUID.randomUUID()}.$ext"
            executeRequest(newRequestBuilder("/storage/v1/object/story-media/$path", true).addHeader("Content-Type", mimeType).post(bytes.toRequestBody(mimeType.toMediaType())).build()).use { response ->
                val raw=response.body?.string().orEmpty()
                if(!response.isSuccessful) throw IllegalStateException(parseSupabaseError(raw,"Story upload failed."))
            }
            "$baseUrl/storage/v1/object/public/story-media/$path"
        } catch (e: Exception) { Log.e(TAG,"uploadStoryMedia failed",e); null }
    }

    suspend fun createStory(story: Story, isVideo: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            val uid=getCurrentUserId() ?: throw IllegalStateException("Not authenticated.")
            val body=JSONObject().apply {
                put("id",story.id); put("user_id",uid); put("active",true); put("media_url",story.storyImage)
                put("media_type",if(isVideo)"video" else "image"); put("caption",story.caption)
                if(isVideo) put("video_url",story.storyImage) else put("image_url",story.storyImage)
                put("likes_count",0)
            }
            executeRequest(newRequestBuilder("/rest/v1/stories",true).addHeader("Prefer","return=minimal").post(body.toString().toRequestBody(jsonMediaType)).build()).use { response ->
                val raw=response.body?.string().orEmpty()
                if(!response.isSuccessful) throw IllegalStateException(parseSupabaseError(raw,"Story creation failed."))
                true
            }
        } catch(e:Exception){Log.e(TAG,"createStory exception",e);false}
    }

    suspend fun markStoryViewed(storyId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uid=getCurrentUserId() ?: throw IllegalStateException("Not authenticated.")
            val body=JSONObject().apply{put("story_id",storyId);put("user_id",uid)}
            executeRequest(newRequestBuilder("/rest/v1/story_views",true).addHeader("Prefer","resolution=merge-duplicates").post(body.toString().toRequestBody(jsonMediaType)).build()).use{r->val b=r.body?.string().orEmpty();if(!r.isSuccessful)throw IllegalStateException(parseSupabaseError(b,"Story view failed."));true}
        }catch(e:Exception){Log.e(TAG,"markStoryViewed exception",e);false}
    }

    suspend fun toggleStoryLike(storyId: String, nextLiked: Boolean, newCount: Int): Boolean = withContext(Dispatchers.IO) {
        val uid=getCurrentUserId() ?: throw IllegalStateException("Not authenticated.")
        val req=if(nextLiked)newRequestBuilder("/rest/v1/story_likes",true).addHeader("Prefer","resolution=merge-duplicates").post(JSONObject().apply{put("story_id",storyId);put("user_id",uid)}.toString().toRequestBody(jsonMediaType)).build()
        else newRequestBuilder("/rest/v1/story_likes?story_id=eq.${encodeValue(storyId)}&user_id=eq.${encodeValue(uid)}",true).delete().build()
        executeRequest(req).use{resp->val raw=resp.body?.string().orEmpty();if(!resp.isSuccessful)throw IllegalStateException(parseSupabaseError(raw,"Story like update failed."));true}
    }

    suspend fun reactToStory(storyId: String, emoji: String): Boolean = withContext(Dispatchers.IO) {
        val uid=getCurrentUserId() ?: throw IllegalStateException("Not authenticated.")
        val body=JSONObject().apply{put("story_id",storyId);put("user_id",uid);put("reaction_type",emoji)}
        executeRequest(newRequestBuilder("/rest/v1/story_reactions",true).addHeader("Prefer","resolution=merge-duplicates").post(body.toString().toRequestBody(jsonMediaType)).build()).use{resp->val raw=resp.body?.string().orEmpty();if(!resp.isSuccessful)throw IllegalStateException(parseSupabaseError(raw,"Story reaction failed."));true}
    }

    suspend fun replyToStory(storyId: String, recipientUsername: String, replyText: String): Boolean = withContext(Dispatchers.IO) {
        val uid=getCurrentUserId() ?: throw IllegalStateException("Not authenticated.")
        val body=JSONObject().apply{put("story_id",storyId);put("user_id",uid);put("content",replyText)}
        executeRequest(newRequestBuilder("/rest/v1/story_replies",true).post(body.toString().toRequestBody(jsonMediaType)).build()).use{resp->val raw=resp.body?.string().orEmpty();if(!resp.isSuccessful)throw IllegalStateException(parseSupabaseError(raw,"Story reply failed."));true}
    }

    suspend fun votePoll(postId: String, optionId: String, updatedPoll: PostPoll): Boolean = withContext(Dispatchers.IO) {
        val uid=getCurrentUserId() ?: throw IllegalStateException("Not authenticated.")
        val pollRaw=executeRequest(newRequestBuilder("/rest/v1/polls?post_id=eq.${encodeValue(postId)}&select=id&limit=1",true).get().build()).use{resp->val raw=resp.body?.string().orEmpty();if(!resp.isSuccessful)throw IllegalStateException(parseSupabaseError(raw,"Poll lookup failed."));raw}
        val pollId=JSONArray(pollRaw).optJSONObject(0)?.optString("id") ?: throw IllegalStateException("Post has no poll.")
        val exists=executeRequest(newRequestBuilder("/rest/v1/poll_votes?poll_id=eq.${encodeValue(pollId)}&user_id=eq.${encodeValue(uid)}&select=id",true).get().build()).use{resp->val raw=resp.body?.string().orEmpty();if(!resp.isSuccessful)throw IllegalStateException(parseSupabaseError(raw,"Vote lookup failed."));raw!="[]"}
        if(exists) throw IllegalStateException("You have already voted in this poll.")
        val body=JSONObject().apply{put("poll_id",pollId);put("option_id",optionId);put("user_id",uid)}
        executeRequest(newRequestBuilder("/rest/v1/poll_votes",true).addHeader("Prefer","return=minimal").post(body.toString().toRequestBody(jsonMediaType)).build()).use{resp->val raw=resp.body?.string().orEmpty();if(!resp.isSuccessful)throw IllegalStateException(parseSupabaseError(raw,"Poll vote failed."));true}
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
                                "&order=created_at.desc" +
                                "&limit=100",
                        authenticated = true
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

    suspend fun fetchLeaderboard(): List<LeaderboardUser> = withContext(Dispatchers.IO) {
        try {
            val raw = executeRequest(newRequestBuilder("/rest/v1/game_leaderboard?select=*&order=score.desc,world_rank.asc&limit=50", true).get().build()).use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext emptyList()
                body
            }
            val arr = JSONArray(if (raw.isBlank()) "[]" else raw)
            buildList { for (i in 0 until arr.length()) parseLeaderboardUser(arr.getJSONObject(i), i + 1)?.let { add(it) } }
        } catch (e: Exception) {
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
                            "seller_id",
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
        val uid = getCurrentUserId() ?: return@withContext emptyList()
        val raw = executeRequest(newRequestBuilder("/rest/v1/messages?select=*&order=created_at.asc&limit=300", true).get().build()).use { r ->
            val b = r.body?.string().orEmpty()
            if (!r.isSuccessful) return@withContext emptyList()
            b
        }
        val arr = JSONArray(if (raw.isBlank()) "[]" else raw)
        val groups = mutableMapOf<String, MutableList<ChatMessage>>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val cid = o.optString("conversation_id")
            val sender = o.optString("sender_id")
            if (cid.isBlank() || sender.isBlank()) continue
            val isMine = sender == uid
            val msg = ChatMessage(
                id = o.optString("id"),
                senderId = sender,
                text = o.optString("content"),
                timestamp = formatTimeAgo(o.optString("created_at")),
                isFromMe = isMine,
                conversationId = cid,
                rawTimestamp = o.optString("created_at"),
                isRead = o.optBoolean("is_read", false),
                status = MessageStatus.SENT
            )
            groups.getOrPut(cid) { mutableListOf() }.add(msg)
        }
        groups.map { (cid, msgs) ->
            val sorted = msgs.sortedBy { it.rawTimestamp }
            val other = executeRequest(newRequestBuilder("/rest/v1/conversation_participants?conversation_id=eq.${encodeValue(cid)}&user_id=neq.${encodeValue(uid)}&select=user_id&limit=1", true).get().build()).use { r ->
                val b = r.body?.string().orEmpty()
                if (!r.isSuccessful || b == "[]" || b.isBlank()) "" else JSONArray(b).optJSONObject(0)?.optString("user_id").orEmpty()
            }
            val prof = other.takeIf { isValidUuid(it) }?.let { fetchProfileById(it) }
            ChatConversation(
                id = cid,
                partnerUsername = prof?.username ?: other,
                partnerId = other,
                partnerName = prof?.fullName ?: other,
                partnerAvatar = prof?.avatarUrl.orEmpty(),
                isOnline = prof?.onlineNow ?: false,
                lastMessage = sorted.lastOrNull()?.text.orEmpty(),
                lastMessageTime = sorted.lastOrNull()?.timestamp ?: ("Recent"),
                lastMessageRawTime = sorted.lastOrNull()?.rawTimestamp.orEmpty(),
                unreadCount = sorted.count { !it.isFromMe && !it.isRead },
                isVerified = prof?.verificationBadge != VerificationBadge.NONE,
                verificationBadge = prof?.verificationBadge ?: VerificationBadge.NONE,
                messages = sorted.toMutableList()
            )
        }
    }
    suspend fun sendMessage(receiverUsername: String, text: String): Result<ChatMessage> = withContext(Dispatchers.IO) {
        try {
            val uid=getCurrentUserId() ?: throw IllegalStateException("Not authenticated.")
            val target=fetchProfileByUsername(receiverUsername) ?: return@withContext Result.failure(Exception("Recipient not found."))
            if(target.id==uid) return@withContext Result.failure(Exception("Cannot message yourself."))
            val existingRaw=executeRequest(newRequestBuilder("/rest/v1/conversation_participants?user_id=eq.${encodeValue(uid)}&select=conversation_id",true).get().build()).use{r->val b=r.body?.string().orEmpty();if(!r.isSuccessful)throw IllegalStateException(parseSupabaseError(b,"Conversation lookup failed."));JSONArray(if(b.isBlank())"[]" else b)}
            var conversationId=""
            for(i in 0 until existingRaw.length()){ val cid=existingRaw.getJSONObject(i).optString("conversation_id"); val other=executeRequest(newRequestBuilder("/rest/v1/conversation_participants?conversation_id=eq.${encodeValue(cid)}&user_id=eq.${encodeValue(target.id)}&select=user_id&limit=1",true).get().build()).use{r->r.isSuccessful && r.body?.string().orEmpty()!="[]"}; if(other){conversationId=cid;break} }
            if(conversationId.isBlank()){ val cBody=JSONObject().apply{put("created_by",uid);put("is_group",false)}; val cRaw=executeRequest(newRequestBuilder("/rest/v1/conversations",true).addHeader("Prefer","return=representation").post(cBody.toString().toRequestBody(jsonMediaType)).build()).use{r->val b=r.body?.string().orEmpty();if(!r.isSuccessful||b=="[]")throw IllegalStateException(parseSupabaseError(b,"Conversation creation failed."));b}; conversationId=JSONArray(cRaw).getJSONObject(0).optString("id"); for(member in listOf(uid,target.id)){val mb=JSONObject().apply{put("conversation_id",conversationId);put("user_id",member)};executeRequest(newRequestBuilder("/rest/v1/conversation_participants",true).post(mb.toString().toRequestBody(jsonMediaType)).build()).use{r->if(!r.isSuccessful)throw IllegalStateException(parseSupabaseError(r.body?.string().orEmpty(),"Conversation membership failed."))}} }
            val mb=JSONObject().apply{put("conversation_id",conversationId);put("sender_id",uid);put("content",text.trim());put("is_read",false);put("created_at",nowIso());put("message_type","text")}; val raw=executeRequest(newRequestBuilder("/rest/v1/messages",true).addHeader("Prefer","return=representation").post(mb.toString().toRequestBody(jsonMediaType)).build()).use{r->val b=r.body?.string().orEmpty();if(!r.isSuccessful||b=="[]")throw IllegalStateException(parseSupabaseError(b,"Message send failed."));b}; val o=JSONArray(raw).getJSONObject(0); Result.success(ChatMessage(id=o.optString("id"),senderId=uid,text=o.optString("content"),timestamp=formatTimeAgo(o.optString("created_at")),isFromMe=true,conversationId=conversationId,rawTimestamp=o.optString("created_at"),isRead=false,status=MessageStatus.SENT))
        }catch(e:Exception){Log.e(TAG,"MESSAGE_SEND exception",e);Result.failure(e)}
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
            val uid=getCurrentUserId() ?: throw IllegalStateException("Not authenticated.")
            val partner=fetchProfileByUsername(partnerUsername) ?: return@withContext false
            val cids=executeRequest(newRequestBuilder("/rest/v1/conversation_participants?user_id=eq.${encodeValue(uid)}&select=conversation_id",true).get().build()).use{r->val b=r.body?.string().orEmpty();if(!r.isSuccessful)throw IllegalStateException(parseSupabaseError(b,"Conversation lookup failed."));JSONArray(if(b.isBlank())"[]" else b)}
            for(i in 0 until cids.length()){ val cid=cids.getJSONObject(i).optString("conversation_id"); val partnerIn=executeRequest(newRequestBuilder("/rest/v1/conversation_participants?conversation_id=eq.${encodeValue(cid)}&user_id=eq.${encodeValue(partner.id)}&select=user_id&limit=1",true).get().build()).use{r->r.isSuccessful&&r.body?.string().orEmpty()!="[]"}; if(!partnerIn)continue; val body=JSONObject().put("is_read",true); executeRequest(newRequestBuilder("/rest/v1/messages?conversation_id=eq.${encodeValue(cid)}&sender_id=eq.${encodeValue(partner.id)}&is_read=eq.false",true).patch(body.toString().toRequestBody(jsonMediaType)).build()).use{r->if(!r.isSuccessful)return@withContext false} }
            true
        }catch(e:Exception){Log.e(TAG,"markMessagesRead failed",e);false}
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

        val parsedVideoUrl = obj.optString("video_url", "").takeIf { it.isNotBlank() }
        val parsedType = obj.optString("type", "").lowercase(Locale.US)
        val parsedIsReel = parsedType == "reel" && !parsedVideoUrl.isNullOrBlank() && obj.optBoolean("is_reel", true)

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
            isReel = parsedIsReel,
            videoDuration = obj.optString("video_duration", "0:00"),
            videoUrl = parsedVideoUrl,
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
    private fun parseLeaderboardUser(obj:JSONObject,rank:Int=obj.optInt("rank",0)):LeaderboardUser{val badgeStr=obj.optString("verification_badge","").uppercase(Locale.US);val badge=when(badgeStr){"GOLD"->VerificationBadge.GOLD;"BLUE"->VerificationBadge.BLUE;else->VerificationBadge.NONE};return LeaderboardUser(rank=rank,username=obj.optString("username",""),fullName=obj.optString("name",obj.optString("full_name","")),avatar=obj.optString("avatar_url",""),points=obj.optInt("score",obj.optInt("points",0)),faculty=obj.optString("faculty",""),university=obj.optString("university",""),level=obj.optString("academic_level", ""),streakDays=obj.optInt("streak",0),verificationBadge=badge)}

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
        val uid=getCurrentUserId() ?: throw IllegalStateException("Not authenticated.")
        val raw=executeRequest(newRequestBuilder("/rest/v1/comments?post_id=eq.${encodeValue(postId)}&parent_comment_id=is.null&order=created_at.asc",true).get().build()).use { r ->
            val b=r.body?.string().orEmpty(); if(!r.isSuccessful) throw IllegalStateException(parseSupabaseError(b,"Comments fetch failed.")); b
        }
        val arr=JSONArray(raw); val ids=(0 until arr.length()).mapNotNull{arr.getJSONObject(it).optString("id").takeIf{v->v.isNotBlank()}}
        val liked=if(ids.isEmpty()) emptySet() else executeRequest(newRequestBuilder("/rest/v1/comment_likes?comment_id=in.(${ids.joinToString(",")})&user_id=eq.${encodeValue(uid)}",true).get().build()).use{r->val b=r.body?.string().orEmpty();if(!r.isSuccessful)throw IllegalStateException(parseSupabaseError(b,"Comment like state fetch failed."));val a=JSONArray(b);buildSet{for(i in 0 until a.length())add(a.getJSONObject(i).optString("comment_id"))}}
        buildList { for(i in 0 until arr.length()){ val o=arr.getJSONObject(i); add(Comment(id=o.optString("id"),user=o.optString("author_id"),avatar="",text=o.optString("content"),time=formatTimeAgo(o.optString("created_at")),likes=o.optInt("likes_count",0),isLiked=liked.contains(o.optString("id")))) } }
    }

    suspend fun addComment(postId: String, text: String, replyToUser: String?): Comment? = withContext(Dispatchers.IO) {
        try {
            val uid=getCurrentUserId() ?: throw IllegalStateException("Not authenticated.")
            val body=JSONObject().apply{put("post_id",postId);put("author_id",uid);put("content",text.trim())}
            executeRequest(newRequestBuilder("/rest/v1/comments",true).addHeader("Prefer","return=representation").post(body.toString().toRequestBody(jsonMediaType)).build()).use{resp->
                val raw=resp.body?.string().orEmpty();if(!resp.isSuccessful||raw.isBlank()||raw=="[]")throw IllegalStateException(parseSupabaseError(raw,"Comment creation failed."));val o=JSONArray(raw).getJSONObject(0);Comment(id=o.optString("id"),user=uid,avatar="",text=o.optString("content"),time="Just now",likes=o.optInt("likes_count",0),isLiked=false)
            }
        }catch(e:Exception){Log.e(TAG,"addComment failed",e);null}
    }

    suspend fun toggleCommentLike(commentId: String, liked: Boolean, newLikeCount: Int): Boolean = withContext(Dispatchers.IO) {
        val uid=getCurrentUserId() ?: throw IllegalStateException("Not authenticated.")
        val request=if(liked)newRequestBuilder("/rest/v1/comment_likes",true).addHeader("Prefer","resolution=merge-duplicates").post(JSONObject().apply{put("comment_id",commentId);put("user_id",uid)}.toString().toRequestBody(jsonMediaType)).build()
        else newRequestBuilder("/rest/v1/comment_likes?comment_id=eq.${encodeValue(commentId)}&user_id=eq.${encodeValue(uid)}",true).delete().build()
        executeRequest(request).use{resp->val raw=resp.body?.string().orEmpty();if(!resp.isSuccessful)throw IllegalStateException(parseSupabaseError(raw,"Comment like update failed."))};true
    }
    suspend fun fetchActivities(): Result<List<ActivityItem>> = withContext(Dispatchers.IO) {
        try {
            val uid = getCurrentUserId() ?: return@withContext Result.success(emptyList())
            val request = newRequestBuilder(
                "/rest/v1/activities?recipient_id=eq.${encodeValue(uid)}&order=created_at.desc&limit=100",
                authenticated = true
            ).get().build()
            executeRequest(request).use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception(parseSupabaseError(raw, "Activity fetch failed.")))
                }
                val array = JSONArray(if (raw.isBlank()) "[]" else raw)
                val items = buildList {
                    for (i in 0 until array.length()) {
                        val o = array.getJSONObject(i)
                        val type = o.optString("activity_type")
                        add(ActivityItem(
                            id = o.optString("id"),
                            user = o.optString("actor_id"),
                            avatar = "",
                            action = o.optString("message").ifBlank { type.replace('_', ' ') },
                            time = formatTimeAgo(o.optString("created_at")),
                            rawTimestamp = o.optString("created_at"),
                            isUnread = !o.optBoolean("is_read", false),
                            targetPostId = o.optString("entity_id").takeIf { o.optString("entity_type").equals("post", true) },
                            targetMarketId = o.optString("entity_id").takeIf { o.optString("entity_type").equals("market", true) },
                            targetType = o.optString("entity_type").takeIf { it.isNotBlank() }
                        ))
                    }
                }
                Result.success(items)
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchActivities exception", e)
            Result.failure(e)
        }
    }
    suspend fun recordActivity(recipientUsername:String,action:String,category:NotificationFilter=NotificationFilter.ALL,targetPostId:String?=null,targetMarketId:String?=null,targetUsername:String?=null,targetType:String?=null,previewText:String?=null): Boolean = withContext(Dispatchers.IO){
        try{val actor=getCurrentUserId()?:throw IllegalStateException("Not authenticated.");val target=fetchProfileByUsername(recipientUsername.removePrefix("@"))?:return@withContext false;if(target.id==actor)return@withContext false;val entityId=targetPostId?:targetMarketId;val type=targetType?:when{targetPostId!=null->"post";targetMarketId!=null->"market";else->null};val body=JSONObject().apply{put("recipient_id",target.id);put("actor_id",actor);put("activity_type",category.name);put("message",action);type?.let{put("entity_type",it)};entityId?.let{put("entity_id",it)}};executeRequest(newRequestBuilder("/rest/v1/activities",true).post(body.toString().toRequestBody(jsonMediaType)).build()).use{r->val b=r.body?.string().orEmpty();if(!r.isSuccessful)throw IllegalStateException(parseSupabaseError(b,"Activity creation failed."));true}}catch(e:Exception){Log.e(TAG,"recordActivity failed",e);false}}
    suspend fun markActivityRead(activityId:String):Boolean=withContext(Dispatchers.IO){try{val uid=getCurrentUserId()?:throw IllegalStateException("Not authenticated.");val body=JSONObject().put("is_read",true);executeRequest(newRequestBuilder("/rest/v1/activities?id=eq.${encodeValue(activityId)}&recipient_id=eq.${encodeValue(uid)}",true).patch(body.toString().toRequestBody(jsonMediaType)).build()).use{r->val b=r.body?.string().orEmpty();if(!r.isSuccessful)throw IllegalStateException(parseSupabaseError(b,"Could not mark activity read."));true}}catch(e:Exception){Log.e(TAG,"markActivityRead failed",e);false}}
    suspend fun markAllActivitiesRead(): Boolean = withContext(Dispatchers.IO){try{val uid=getCurrentUserId()?:throw IllegalStateException("Not authenticated.");val body=JSONObject().put("is_read",true);executeRequest(newRequestBuilder("/rest/v1/activities?recipient_id=eq.${encodeValue(uid)}&is_read=eq.false",true).patch(body.toString().toRequestBody(jsonMediaType)).build()).use{r->val b=r.body?.string().orEmpty();if(!r.isSuccessful)throw IllegalStateException(parseSupabaseError(b,"Could not mark activities read."));true}}catch(e:Exception){Log.e(TAG,"markAllActivitiesRead failed",e);false}}
    suspend fun recordSkillEndorsement(targetUsername:String,skillName:String,endorserUsername:String):Boolean=withContext(Dispatchers.IO){try{val actor=getCurrentUserId()?:throw IllegalStateException("Not authenticated.");val profile=fetchProfileByUsername(targetUsername.removePrefix("@"))?:return@withContext false;val skillsRaw=executeRequest(newRequestBuilder("/rest/v1/skills?normalized_name=eq.${encodeValue(skillName.trim().lowercase(Locale.US))}&select=id&limit=1",true).get().build()).use{r->val b=r.body?.string().orEmpty();if(!r.isSuccessful)throw IllegalStateException(parseSupabaseError(b,"Skill lookup failed."));b};val skillId=JSONArray(if(skillsRaw.isBlank())"[]" else skillsRaw).optJSONObject(0)?.optString("id")?:return@withContext false;val body=JSONObject().apply{put("skill_id",skillId);put("profile_user_id",profile.id);put("endorser_user_id",actor)};executeRequest(newRequestBuilder("/rest/v1/skill_endorsements",true).addHeader("Prefer","resolution=merge-duplicates").post(body.toString().toRequestBody(jsonMediaType)).build()).use{r->val b=r.body?.string().orEmpty();if(!r.isSuccessful)throw IllegalStateException(parseSupabaseError(b,"Skill endorsement failed."));true}}catch(e:Exception){Log.e(TAG,"recordSkillEndorsement failed",e);false}}

    suspend fun submitVerificationRequest(tier: String, paymentReference: String, amount: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val uid=getCurrentUserId() ?: throw IllegalStateException("Not authenticated.")
            val body=JSONObject().apply{put("user_id",uid);put("tier",tier);put("payment_reference",paymentReference);put("amount",amount);put("status","pending")}
            executeRequest(newRequestBuilder("/rest/v1/verification_requests",true).post(body.toString().toRequestBody(jsonMediaType)).build()).use{resp->val raw=resp.body?.string().orEmpty();if(!resp.isSuccessful)throw IllegalStateException(parseSupabaseError(raw,"Verification request failed."));true}
        }catch(e:Exception){Log.e(TAG,"submitVerificationRequest failed",e);false}
    }
    suspend fun updateGameStats(score:Int,coins:Int,streak:Int):Boolean=withContext(Dispatchers.IO){try{val uid=getCurrentUserId()?:throw IllegalStateException("Not authenticated.");val body=JSONObject().apply{put("user_id",uid);put("score",score);put("coins",coins);put("streak",streak);put("updated_at",nowIso())};executeRequest(newRequestBuilder("/rest/v1/game_profiles?on_conflict=user_id",true).addHeader("Prefer","resolution=merge-duplicates,return=representation").post(body.toString().toRequestBody(jsonMediaType)).build()).use{r->val b=r.body?.string().orEmpty();if(!r.isSuccessful)throw IllegalStateException(parseSupabaseError(b,"Game stats update failed."));true}}catch(e:Exception){Log.e(TAG,"updateGameStats failed",e);false}}
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
