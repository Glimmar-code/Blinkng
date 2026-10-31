package com.example.ui.screens

import org.json.JSONObject

/** Display-only projection of the authenticated native-poll results RPC. */
internal data class BlinkChatPollResult(
    val question: String,
    val options: List<BlinkChatPollResultOption>,
    val totalVotes: Int,
    val myVote: Int?,
    val closed: Boolean
) {
    fun percent(index: Int): Int =
        if (totalVotes == 0) 0 else ((options[index].votes.toLong() * 100L) / totalVotes).toInt()

    fun progress(index: Int): Float =
        if (totalVotes == 0) 0f else options[index].votes.toFloat() / totalVotes
}

internal data class BlinkChatPollResultOption(val label: String, val votes: Int)

/**
 * Only an internally consistent, message-matched RPC response may show counted
 * percentages. Missing data must NOT silently become fabricated 0% results.
 * Membership and vote permissions remain enforced by the Supabase RPC.
 */
internal object BlinkChatPollResultPolicy {
    fun parse(json: JSONObject, messageId: String): BlinkChatPollResult? {
        if (json.optString("message_id") != messageId || messageId.isBlank()) return null
        val question = json.optString("question").trim()
        if (question.isEmpty() || !json.has("closed") ||
            !json.has("total_votes") || json.isNull("total_votes")
        ) return null
        val total = json.optInt("total_votes", -1)
        if (total < 0) return null
        val raw = json.optJSONArray("options") ?: return null
        if (raw.length() !in 2..8) return null
        val options = mutableListOf<BlinkChatPollResultOption>()
        for (index in 0 until raw.length()) {
            val item = raw.optJSONObject(index) ?: return null
            val label = item.optString("label").trim()
            if (label.isBlank() || !item.has("votes") || item.isNull("votes")) return null
            val votes = item.optInt("votes", -1)
            if (votes < 0) return null
            options.add(BlinkChatPollResultOption(label, votes))
        }
        if (options.sumOf { it.votes.toLong() } != total.toLong()) return null
        val mine: Int? = if (!json.has("my_vote") || json.isNull("my_vote")) {
            null
        } else {
            json.optInt("my_vote", -1).takeIf { it in options.indices } ?: return null
        }
        return BlinkChatPollResult(
            question = question, options = options, totalVotes = total,
            myVote = mine, closed = json.optBoolean("closed")
        )
    }
}
