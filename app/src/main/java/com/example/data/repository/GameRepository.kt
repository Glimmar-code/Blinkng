package com.example.data.repository

import com.example.data.models.GameDashboard
import com.example.data.models.GameHistoryItem
import com.example.data.models.GameLeaderboardEntry
import com.example.data.models.ServerGameAnswerResult
import com.example.data.models.ServerGameQuestion
import com.example.data.models.ServerGameRound
import com.example.data.models.VerificationBadge
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

class GameRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = SupabaseConfig.url.trimEnd('/')

    suspend fun startRound(
        gameType: String,
        challengeId: String? = null,
        daily: Boolean = false,
        questionCount: Int = 5
    ): Result<ServerGameRound> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("p_game_type", gameType)
                .put("p_question_count", questionCount.coerceIn(3, 10))
                .put("p_daily", daily)
            if (challengeId.isNullOrBlank()) body.put("p_challenge_id", JSONObject.NULL)
            else body.put("p_challenge_id", challengeId)
            parseRound(JSONObject(rpc("start_game_round", body)))
        }
    }

    suspend fun submitAnswer(
        roundId: String,
        questionId: String,
        selectedIndex: Int,
        responseMs: Int
    ): Result<ServerGameAnswerResult> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("p_round_id", roundId)
                .put("p_question_id", questionId)
                .put("p_selected_index", selectedIndex)
                .put("p_response_ms", responseMs.coerceIn(0, 120_000))
            parseAnswer(JSONObject(rpc("submit_game_answer", body)))
        }
    }

    suspend fun fetchDashboard(): Result<GameDashboard> = withContext(Dispatchers.IO) {
        runCatching { parseDashboard(JSONObject(rpc("get_game_dashboard", JSONObject()))) }
    }

    suspend fun fetchHistory(limit: Int = 12): Result<List<GameHistoryItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = rpc("get_game_history", JSONObject().put("p_limit", limit.coerceIn(1, 50)))
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        GameHistoryItem(
                            id = item.optString("id"),
                            gameType = item.optString("gameType"),
                            score = item.optInt("score"),
                            coinsEarned = item.optInt("coinsEarned"),
                            correctCount = item.optInt("correctCount"),
                            questionCount = item.optInt("questionCount"),
                            completedAt = item.optString("completedAt")
                        )
                    )
                }
            }
        }
    }

    suspend fun fetchLeaderboard(
        period: String = "all_time",
        scope: String = "global",
        limit: Int = 20
    ): Result<List<GameLeaderboardEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = rpc(
                "get_game_leaderboard",
                JSONObject()
                    .put("p_period", period)
                    .put("p_scope", scope)
                    .put("p_limit", limit.coerceIn(3, 50))
            )
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        GameLeaderboardEntry(
                            rank = item.optInt("rank"),
                            userId = item.optString("userId"),
                            name = item.optString("name"),
                            username = item.optString("username"),
                            avatarUrl = item.optString("avatarUrl"),
                            university = item.optString("university"),
                            faculty = item.optString("faculty"),
                            department = item.optString("department"),
                            score = item.optInt("score"),
                            streak = item.optInt("streak"),
                            verificationBadge = parseBadge(item.optString("verificationBadge")),
                            isMe = item.optBoolean("isMe")
                        )
                    )
                }
            }
        }
    }

    suspend fun toggleSavedQuestion(questionId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            JSONObject(
                rpc(
                    "toggle_saved_game_question",
                    JSONObject().put("p_question_id", questionId)
                )
            ).optBoolean("saved")
        }
    }

    suspend fun reportQuestion(
        questionId: String,
        reason: String,
        details: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            rpc(
                "report_game_question",
                JSONObject()
                    .put("p_question_id", questionId)
                    .put("p_reason", reason.take(80))
                    .put("p_details", details.take(500))
            )
            Unit
        }
    }

    private fun parseRound(json: JSONObject): ServerGameRound {
        val questionsJson = json.optJSONArray("questions") ?: JSONArray()
        val questions = buildList {
            for (i in 0 until questionsJson.length()) {
                val q = questionsJson.getJSONObject(i)
                add(
                    ServerGameQuestion(
                        id = q.optString("id"),
                        gameType = q.optString("gameType"),
                        category = q.optString("category"),
                        prompt = q.optString("prompt"),
                        options = (q.optJSONArray("options") ?: JSONArray()).toStringList(),
                        difficulty = q.optInt("difficulty", 1),
                        timeLimitSeconds = q.optInt("timeLimitSeconds", 0).takeIf { it > 0 },
                        stimulus = q.optString("stimulus").takeIf { it.isNotBlank() && !it.equals("null", true) },
                        saved = q.optBoolean("saved")
                    )
                )
            }
        }
        val answered = (json.optJSONArray("answeredQuestionIds") ?: JSONArray()).toStringList().toSet()
        return ServerGameRound(
            id = json.optString("roundId"),
            gameType = json.optString("gameType"),
            questions = questions,
            answeredQuestionIds = answered,
            score = json.optInt("score"),
            coinsEarned = json.optInt("coinsEarned"),
            correctCount = json.optInt("correctCount"),
            isDaily = json.optBoolean("isDaily"),
            resumed = json.optBoolean("resumed")
        )
    }

    private fun parseAnswer(json: JSONObject) = ServerGameAnswerResult(
        correct = json.optBoolean("correct"),
        correctIndex = json.optInt("correctIndex", -1),
        explanation = json.optString("explanation"),
        awardedScore = json.optInt("awardedScore"),
        awardedCoins = json.optInt("awardedCoins"),
        streak = json.optInt("streak"),
        bestStreak = json.optInt("bestStreak"),
        totalScore = json.optInt("totalScore"),
        totalCoins = json.optInt("totalCoins"),
        roundScore = json.optInt("roundScore"),
        roundCoins = json.optInt("roundCoins"),
        correctCount = json.optInt("correctCount"),
        completed = json.optBoolean("completed"),
        duplicate = json.optBoolean("duplicate")
    )

    private fun parseDashboard(json: JSONObject) = GameDashboard(
        score = json.optInt("score"),
        coins = json.optInt("coins"),
        streak = json.optInt("streak"),
        bestStreak = json.optInt("bestStreak"),
        worldRank = json.optInt("worldRank"),
        todayAnswers = json.optInt("todayAnswers"),
        todayCorrect = json.optInt("todayCorrect"),
        dailyGoal = json.optInt("dailyGoal", 15).coerceAtLeast(1)
    )

    private fun parseBadge(raw: String): VerificationBadge =
        VerificationBadge.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
            ?: VerificationBadge.NONE

    private fun rpc(name: String, body: JSONObject): String {
        val requestBody = body.toString().toRequestBody(jsonType)
        val request = baseRequest("/rest/v1/rpc/$name")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(raw).optString("message") }
                    .getOrNull()
                    .orEmpty()
                    .ifBlank { "Game service error (${response.code})" }
                throw IllegalStateException(message)
            }
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

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (i in 0 until length()) {
            val value = optString(i)
            if (value.isNotBlank()) add(value)
        }
    }
}
