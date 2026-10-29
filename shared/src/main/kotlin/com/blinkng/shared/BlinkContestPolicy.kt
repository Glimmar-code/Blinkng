package com.blinkng.shared

/** Shared Android and Windows contest rules. Voting cannot be bought or boosted. */
object BlinkContestPolicy {
    const val MAX_STATEMENT = 500
    const val MIN_STATEMENT = 10
    fun section(status: String): String = when (status) {
        "completed", "cancelled" -> "Completed"
        "upcoming" -> "Upcoming"
        else -> "Ongoing"
    }
    fun statusLabel(status: String): String = when (status) {
        "registration" -> "Applications open"
        "voting" -> "Voting open"
        "review" -> "Results under review"
        "paused" -> "Paused"
        "completed" -> "Results confirmed"
        "cancelled" -> "Cancelled"
        else -> "Coming soon"
    }
    fun validStatement(text: String) = text.trim().length in MIN_STATEMENT..MAX_STATEMENT
    fun canVote(status: String, voteId: String?, ownId: String, candidateId: String): Boolean =
        status == "voting" && (voteId.isNullOrBlank() || voteId == "null") &&
            candidateId.isNotBlank() && candidateId != "null" && ownId != candidateId
}
