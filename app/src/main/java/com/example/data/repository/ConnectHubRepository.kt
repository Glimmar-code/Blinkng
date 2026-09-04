package com.example.data.repository

import com.example.data.models.ConnectHubSnapshot
import com.example.data.models.ConnectRequestItem
import com.example.data.models.ChallengeGameType
import com.example.data.models.GameChallenge
import com.example.data.models.GameProfileStats
import com.example.data.models.HousingAgentListing
import com.example.data.models.HousingRequestListing
import com.example.data.models.MentorListing
import com.example.data.models.ReadingMateListing
import com.example.data.models.RoommateListing
import com.example.data.models.SmartMatchCandidate
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ConnectHubRepository(
    private val supabaseService: SupabaseService = SupabaseService()
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = SupabaseConfig.url.trimEnd('/')

    suspend fun fetchSnapshot(): ConnectHubSnapshot = withContext(Dispatchers.IO) {
        val roommates = getArray("/rest/v1/roommate_profiles?select=*&is_active=eq.true&order=created_at.desc&limit=30")
            .mapObjects(::parseRoommate)
        val mentors = getArray("/rest/v1/mentor_profiles?select=*&is_active=eq.true&mode=in.(mentor,both)&order=created_at.desc&limit=30")
            .mapObjects(::parseMentor)
        val reading = getArray("/rest/v1/reading_mate_profiles?select=*&is_active=eq.true&order=created_at.desc&limit=30")
            .mapObjects(::parseReadingMate)
        val agents = getArray("/rest/v1/housing_agent_profiles?select=*&is_active=eq.true&is_verified=eq.true&order=created_at.desc&limit=30")
            .mapObjects(::parseHousingAgent)
        val housingRequests = getArray("/rest/v1/housing_requests?select=*&status=eq.open&order=created_at.desc&limit=40")
            .mapObjects(::parseHousingRequest)
        val challenges = getArray("/rest/v1/game_challenges?select=*&order=created_at.desc&limit=30")
            .mapObjects(::parseChallenge)
        val smartMatches = runCatching {
            JSONArray(rpc("get_connect_matches", JSONObject().put("p_limit", 30)))
                .mapObjects(::parseSmartMatch)
        }.getOrDefault(emptyList())
        val requests = runCatching {
            JSONArray(rpc("get_connect_request_inbox", JSONObject().put("p_limit", 120)))
                .mapObjects(::parseConnectRequest)
        }.getOrDefault(emptyList())

        val uid = supabaseService.getCurrentUserId().orEmpty()
        val gameStats = if (uid.isBlank()) GameProfileStats() else {
            val arr = getArray("/rest/v1/game_profiles?select=score,coins,streak,best_streak&user_id=eq.${encode(uid)}&limit=1")
            if (arr.length() == 0) GameProfileStats() else parseGameStats(arr.getJSONObject(0))
        }

        ConnectHubSnapshot(
            roommates = roommates,
            mentors = mentors,
            readingMates = reading,
            housingAgents = agents,
            housingRequests = housingRequests,
            gameChallenges = challenges,
            smartMatches = smartMatches,
            requests = requests,
            gameStats = gameStats
        )
    }

    suspend fun upsertRoommate(
        title: String,
        description: String,
        location: String,
        budgetMin: Double?,
        budgetMax: Double?
    ): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("title", title.trim())
            .put("description", description.trim())
            .put("location", location.trim())
            .put("is_active", true)
        if (budgetMin != null) body.put("budget_min", budgetMin)
        if (budgetMax != null) body.put("budget_max", budgetMax)
        write(
            path = "/rest/v1/roommate_profiles?on_conflict=user_id",
            body = body,
            method = "POST",
            prefer = "resolution=merge-duplicates,return=minimal"
        )
    }

    suspend fun applyRoommate(profileId: String): Boolean = withContext(Dispatchers.IO) {
        write(
            "/rest/v1/roommate_applications",
            JSONObject()
                .put("roommate_profile_id", profileId)
                .put("message", "Hi, I am interested in being your roommate."),
            "POST"
        )
    }

    suspend fun upsertMentor(
        mode: String,
        subjects: List<String>,
        headline: String,
        description: String
    ): Boolean = withContext(Dispatchers.IO) {
        write(
            "/rest/v1/mentor_profiles?on_conflict=user_id",
            JSONObject()
                .put("mode", mode)
                .put("subjects", JSONArray(subjects.filter { it.isNotBlank() }))
                .put("headline", headline.trim())
                .put("description", description.trim())
                .put("is_active", true),
            "POST",
            "resolution=merge-duplicates,return=minimal"
        )
    }

    suspend fun requestMentor(mentorProfileId: String): Boolean = withContext(Dispatchers.IO) {
        write(
            "/rest/v1/mentor_requests",
            JSONObject()
                .put("mentor_profile_id", mentorProfileId)
                .put("message", "Hi, I would like you to mentor me."),
            "POST"
        )
    }

    suspend fun upsertReadingMate(
        courses: List<String>,
        studyStyle: String,
        preferredTimes: List<String>,
        location: String,
        description: String
    ): Boolean = withContext(Dispatchers.IO) {
        write(
            "/rest/v1/reading_mate_profiles?on_conflict=user_id",
            JSONObject()
                .put("courses", JSONArray(courses.filter { it.isNotBlank() }))
                .put("study_style", studyStyle.trim())
                .put("preferred_times", JSONArray(preferredTimes.filter { it.isNotBlank() }))
                .put("preferred_location", location.trim())
                .put("description", description.trim())
                .put("is_active", true),
            "POST",
            "resolution=merge-duplicates,return=minimal"
        )
    }

    suspend fun requestReadingMate(readingProfileId: String): Boolean = withContext(Dispatchers.IO) {
        write(
            "/rest/v1/reading_mate_requests",
            JSONObject()
                .put("reading_profile_id", readingProfileId)
                .put("message", "Hi, I would like us to study together."),
            "POST"
        )
    }

    suspend fun applyAsHousingAgent(
        businessName: String,
        serviceAreas: List<String>,
        bio: String
    ): Boolean = withContext(Dispatchers.IO) {
        write(
            "/rest/v1/housing_agent_profiles?on_conflict=user_id",
            JSONObject()
                .put("business_name", businessName.trim())
                .put("service_areas", JSONArray(serviceAreas.filter { it.isNotBlank() }))
                .put("bio", bio.trim())
                .put("is_active", true),
            "POST",
            "resolution=merge-duplicates,return=minimal"
        )
    }

    suspend fun createHousingRequest(
        title: String,
        location: String,
        budgetMin: Double?,
        budgetMax: Double?,
        description: String
    ): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("title", title.trim())
            .put("preferred_location", location.trim())
            .put("description", description.trim())
        if (budgetMin != null) body.put("budget_min", budgetMin)
        if (budgetMax != null) body.put("budget_max", budgetMax)
        write("/rest/v1/housing_requests", body, "POST")
    }

    suspend fun applyToHousingRequest(requestId: String, message: String = ""): Boolean =
        withContext(Dispatchers.IO) {
            rpc(
                "apply_to_housing_request",
                JSONObject()
                    .put("p_housing_request_id", requestId)
                    .put("p_message", message.trim())
            ).isNotBlank()
        }

    suspend fun challengeUser(userId: String, gameType: String = ChallengeGameType.GENERAL_KNOWLEDGE.apiName): Boolean =
        withContext(Dispatchers.IO) {
            val canonicalType = ChallengeGameType.fromApiName(gameType).apiName
            rpc(
                "create_game_challenge",
                JSONObject()
                    .put("p_opponent_id", userId)
                    .put("p_game_type", canonicalType)
            ).isNotBlank()
        }

    suspend fun respondToConnectRequest(kind: String, requestId: String, accept: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            rpc(
                "respond_connect_request",
                JSONObject()
                    .put("p_kind", kind)
                    .put("p_request_id", requestId)
                    .put("p_accept", accept)
            ).equals("true", ignoreCase = true)
        }

    suspend fun respondToChallenge(challengeId: String, accept: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            rpc(
                "respond_game_challenge",
                JSONObject()
                    .put("p_challenge_id", challengeId)
                    .put("p_accept", accept)
            ).equals("true", ignoreCase = true)
        }

    suspend fun submitChallengeScore(challengeId: String, score: Int): Boolean =
        withContext(Dispatchers.IO) {
            rpc(
                "submit_game_challenge_score",
                JSONObject()
                    .put("p_challenge_id", challengeId)
                    .put("p_score", score.coerceIn(0, 500))
            ).isNotBlank()
        }

    suspend fun recordGameSession(gameType: String, score: Int): Boolean =
        withContext(Dispatchers.IO) {
            rpc(
                "record_game_session",
                JSONObject()
                    .put("p_game_type", gameType)
                    .put("p_score", score.coerceAtLeast(0))
                    .put("p_coins_earned", 0)
            ).isNotBlank()
        }

    private fun getArray(path: String): JSONArray {
        val request = baseRequest(path).get().build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(errorMessage(raw, response.code))
            return if (raw.isBlank()) JSONArray() else JSONArray(raw)
        }
    }

    private fun write(
        path: String,
        body: JSONObject,
        method: String,
        prefer: String = "return=minimal"
    ): Boolean {
        val builder = baseRequest(path)
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", prefer)
        val requestBody = body.toString().toRequestBody(jsonType)
        val request = when (method) {
            "PATCH" -> builder.patch(requestBody).build()
            else -> builder.post(requestBody).build()
        }
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(errorMessage(raw, response.code))
            return true
        }
    }

    private fun rpc(name: String, body: JSONObject): String {
        val request = baseRequest("/rest/v1/rpc/$name")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonType))
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(errorMessage(raw, response.code))
            return raw.trim().removeSurrounding("\"")
        }
    }

    private fun baseRequest(path: String): Request.Builder {
        val token = SupabaseService.accessToken()
            ?: throw IllegalStateException("Please sign in again.")
        return Request.Builder()
            .url(baseUrl + path)
            .addHeader("apikey", SupabaseConfig.anonKey)
            .addHeader("Authorization", "Bearer $token")
    }

    private fun <T> JSONArray.mapObjects(mapper: (JSONObject) -> T): List<T> =
        buildList {
            for (i in 0 until length()) add(mapper(getJSONObject(i)))
        }

    private fun parseRoommate(o: JSONObject) = RoommateListing(
        id = o.optString("id"),
        userId = o.optString("user_id"),
        title = o.optString("title"),
        description = o.optString("description"),
        location = o.optString("location"),
        budgetMin = o.optNullableDouble("budget_min"),
        budgetMax = o.optNullableDouble("budget_max"),
        moveInDate = o.optString("move_in_date"),
        genderPreference = o.optString("gender_preference"),
        roomType = o.optString("room_type")
    )

    private fun parseMentor(o: JSONObject) = MentorListing(
        id = o.optString("id"),
        userId = o.optString("user_id"),
        mode = o.optString("mode"),
        subjects = o.optStringList("subjects"),
        headline = o.optString("headline"),
        description = o.optString("description"),
        preferredLevel = o.optString("preferred_level")
    )

    private fun parseReadingMate(o: JSONObject) = ReadingMateListing(
        id = o.optString("id"),
        userId = o.optString("user_id"),
        courses = o.optStringList("courses"),
        studyStyle = o.optString("study_style"),
        preferredTimes = o.optStringList("preferred_times"),
        preferredLocation = o.optString("preferred_location"),
        description = o.optString("description")
    )

    private fun parseHousingAgent(o: JSONObject) = HousingAgentListing(
        id = o.optString("id"),
        userId = o.optString("user_id"),
        businessName = o.optString("business_name"),
        serviceAreas = o.optStringList("service_areas"),
        bio = o.optString("bio"),
        verified = o.optBoolean("is_verified", false)
    )

    private fun parseHousingRequest(o: JSONObject) = HousingRequestListing(
        id = o.optString("id"),
        studentId = o.optString("student_id"),
        title = o.optString("title"),
        preferredLocation = o.optString("preferred_location"),
        budgetMin = o.optNullableDouble("budget_min"),
        budgetMax = o.optNullableDouble("budget_max"),
        description = o.optString("description"),
        status = o.optString("status", "open"),
        createdAt = o.optString("created_at")
    )

    private fun parseChallenge(o: JSONObject) = GameChallenge(
        id = o.optString("id"),
        challengerId = o.optString("challenger_id"),
        opponentId = o.optString("opponent_id"),
        gameType = o.optString("game_type"),
        status = o.optString("status"),
        challengerScore = o.optNullableInt("challenger_score"),
        opponentScore = o.optNullableInt("opponent_score"),
        winnerId = o.optString("winner_id").takeIf { it.isNotBlank() && it != "null" },
        createdAt = o.optString("created_at")
    )

    private fun parseSmartMatch(o: JSONObject) = SmartMatchCandidate(
        userId = o.optString("id"),
        username = o.optString("username"),
        fullName = o.optString("full_name"),
        avatarUrl = o.optString("avatar_url"),
        university = o.optString("university"),
        faculty = o.optString("faculty"),
        department = o.optString("department"),
        academicLevel = o.optString("academic_level"),
        relationshipStatus = o.optString("relationship_status"),
        onlineNow = o.optBoolean("online_now", false),
        lastSeenAt = o.optString("last_seen_at"),
        compatibilityScore = o.optInt("compatibility_score", 0),
        commonSkills = o.optStringList("common_skills"),
        commonHobbies = o.optStringList("common_hobbies")
    )

    private fun parseConnectRequest(o: JSONObject) = ConnectRequestItem(
        kind = o.optString("kind"),
        requestId = o.optString("request_id"),
        direction = o.optString("direction"),
        status = o.optString("status"),
        listingId = o.optString("listing_id").takeIf { it.isNotBlank() && it != "null" },
        otherUserId = o.optString("other_user_id"),
        createdAt = o.optString("created_at"),
        title = o.optString("title")
    )

    private fun parseGameStats(o: JSONObject) = GameProfileStats(
        score = o.optInt("score", 0),
        coins = o.optInt("coins", 0),
        streak = o.optInt("streak", 0),
        bestStreak = o.optInt("best_streak", 0)
    )

    private fun JSONObject.optStringList(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (isNull(key) || !has(key)) null else optDouble(key)

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (isNull(key) || !has(key)) null else optInt(key)

    private fun errorMessage(raw: String, code: Int): String =
        runCatching { JSONObject(raw).optString("message") }.getOrNull()
            .orEmpty().ifBlank { "Connect Hub request failed ($code)." }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private fun nowIso(): String =
        java.time.Instant.now().toString()
}
