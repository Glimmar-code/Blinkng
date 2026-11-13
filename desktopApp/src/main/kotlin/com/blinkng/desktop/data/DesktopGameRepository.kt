package com.blinkng.desktop.data

import org.json.JSONArray
import org.json.JSONObject

/** Typed desktop adapter for the same server-authoritative game RPCs used by Android. */
class DesktopGameRepository(private val client: DesktopSupabaseClient) {
    suspend fun startRound(gameType: String, questionCount: Int = 10): Result<DesktopGameRound> = runCatching {
        val body = JSONObject()
            .put("p_game_type", gameType)
            .put("p_question_count", questionCount.coerceIn(3, 10))
            .put("p_daily", false)
            .put("p_challenge_id", JSONObject.NULL)
        parseRound(JSONObject(client.gameRpc("start_game_round", body)))
    }

    suspend fun submitAnswer(
        roundId: String,
        questionId: String,
        selectedIndex: Int,
        responseMs: Int,
    ): Result<DesktopGameAnswer> = runCatching {
        val body = JSONObject()
            .put("p_round_id", roundId)
            .put("p_question_id", questionId)
            .put("p_selected_index", selectedIndex)
            .put("p_response_ms", responseMs.coerceIn(0, 120_000))
        val json = JSONObject(client.gameRpc("submit_game_answer", body))
        DesktopGameAnswer(
            correct = json.optBoolean("correct"),
            correctIndex = json.optInt("correctIndex", -1),
            explanation = json.optString("explanation"),
            roundScore = json.optInt("roundScore"),
            correctCount = json.optInt("correctCount"),
            completed = json.optBoolean("completed"),
        )
    }

    suspend fun fetchHistory(limit: Int = 12): Result<List<DesktopGameHistory>> = runCatching {
        val array = JSONArray(client.gameRpc(
            "get_game_history",
            JSONObject().put("p_limit", limit.coerceIn(1, 50)),
        ))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(DesktopGameHistory(
                    gameType = item.optString("gameType"),
                    score = item.optInt("score"),
                    correctCount = item.optInt("correctCount"),
                    questionCount = item.optInt("questionCount"),
                    completedAt = item.optString("completedAt"),
                ))
            }
        }
    }

    suspend fun fetchLeaderboard(limit: Int = 10): Result<List<DesktopGameLeader>> = runCatching {
        val array = JSONArray(client.gameRpc(
            "get_game_leaderboard",
            JSONObject().put("p_period", "all_time").put("p_scope", "global")
                .put("p_limit", limit.coerceIn(3, 50)),
        ))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(DesktopGameLeader(
                    rank = item.optInt("rank"),
                    name = item.optString("name").ifBlank { item.optString("username") },
                    score = item.optInt("score"),
                    isMe = item.optBoolean("isMe"),
                ))
            }
        }
    }

    private fun parseRound(json: JSONObject): DesktopGameRound {
        val items = json.optJSONArray("questions") ?: JSONArray()
        val questions = buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val options = item.optJSONArray("options") ?: JSONArray()
                add(DesktopGameQuestion(
                    id = item.optString("id"),
                    category = item.optString("category"),
                    prompt = item.optString("prompt"),
                    options = buildList {
                        for (optionIndex in 0 until options.length()) {
                            options.optString(optionIndex).takeIf(String::isNotBlank)?.let(::add)
                        }
                    },
                ))
            }
        }
        return DesktopGameRound(
            id = json.optString("roundId"),
            questions = questions,
            answeredQuestionIds = (json.optJSONArray("answeredQuestionIds") ?: JSONArray()).let { ids ->
                buildSet { for (index in 0 until ids.length()) ids.optString(index).takeIf(String::isNotBlank)?.let(::add) }
            },
            score = json.optInt("score"),
            correctCount = json.optInt("correctCount"),
        )
    }
}

data class DesktopGameRound(
    val id: String,
    val questions: List<DesktopGameQuestion>,
    val answeredQuestionIds: Set<String>,
    val score: Int,
    val correctCount: Int,
)

data class DesktopGameQuestion(
    val id: String,
    val category: String,
    val prompt: String,
    val options: List<String>,
)

data class DesktopGameAnswer(
    val correct: Boolean,
    val correctIndex: Int,
    val explanation: String,
    val roundScore: Int,
    val correctCount: Int,
    val completed: Boolean,
)

data class DesktopGameHistory(
    val gameType: String,
    val score: Int,
    val correctCount: Int,
    val questionCount: Int,
    val completedAt: String,
)

data class DesktopGameLeader(
    val rank: Int,
    val name: String,
    val score: Int,
    val isMe: Boolean,
)
