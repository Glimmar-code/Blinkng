package com.blinkng.shared

/** A contest link always targets one approved entry, not just the contest overview. */
object BlinkContestShare {
    private val uuid = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    fun isValid(contestId: String, entryId: String): Boolean =
        uuid.matches(contestId) && uuid.matches(entryId)

    fun link(contestId: String, entryId: String): String {
        require(isValid(contestId, entryId)) { "Invalid BLINK contest invitation." }
        return "https://blink.com.ng/contest/${contestId.lowercase()}/${entryId.lowercase()}"
    }

    fun message(name: String, title: String, rank: Int, votes: Int, url: String): String {
        val who = name.trim().take(60).ifBlank { "a BLINK contestant" }
        val contest = title.trim().take(100).ifBlank { "BLINK Contest" }
        val position = if (rank > 0) "I'm currently ranked #$rank with $votes votes." else "I'm contesting for a top spot."
        return "I'm $who, and I'm contesting in $contest! 🏆\n$position\n" +
            "Please support me with your FREE vote on BLINK. One vote per account.\n$url"
    }
}
