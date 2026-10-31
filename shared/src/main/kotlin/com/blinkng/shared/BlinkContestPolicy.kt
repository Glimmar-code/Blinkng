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
    /** Reject responses from a previous contest after rapid navigation. */
    fun isCurrentContest(requestId: String, selectedId: String?): Boolean =
        requestId.isNotBlank() && selectedId != null && requestId.equals(selectedId, ignoreCase = true)

    /** A share flyer must match the requested contest and candidate. */
    fun isRequestedCandidate(
        requestedContestId: String, requestedEntryId: String, selectedContestId: String?,
        responseContestId: String, responseEntryId: String
    ): Boolean = isCurrentContest(requestedContestId, selectedContestId) &&
        requestedEntryId.isNotBlank() &&
        requestedContestId.equals(responseContestId, ignoreCase = true) &&
        requestedEntryId.equals(responseEntryId, ignoreCase = true)

    fun validStatement(text: String) = text.trim().length in MIN_STATEMENT..MAX_STATEMENT
    fun validEntryMediaUrl(value: String): Boolean = runCatching {
        val uri = java.net.URI(value.trim())
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
    fun canApply(text: String, hasSelectedMedia: Boolean, mediaUrl: String): Boolean =
        validStatement(text) && (hasSelectedMedia || validEntryMediaUrl(mediaUrl))
    /** Voting flyers should never invite people to closed or cancelled contests. */
    fun canShareVoteInvite(status: String): Boolean =
        status in setOf("upcoming", "registration", "voting")

    fun canVote(status: String, voteId: String?, ownId: String, candidateId: String): Boolean =
        status == "voting" && (voteId.isNullOrBlank() || voteId == "null") &&
            candidateId.isNotBlank() && candidateId != "null" && ownId != candidateId
}
