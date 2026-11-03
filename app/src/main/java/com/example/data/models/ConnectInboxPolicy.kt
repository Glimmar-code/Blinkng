package com.example.data.models

/** The backend's request inbox includes game rows, but game challenges are also
 * fetched separately. Display each game only once and count pending invitations once. */
object ConnectInboxPolicy {
    fun requestsWithoutGames(requests: List<ConnectRequestItem>): List<ConnectRequestItem> =
        requests.filterNot { it.kind.equals("game", ignoreCase = true) }

    fun incomingPendingCount(
        requests: List<ConnectRequestItem>,
        challenges: List<GameChallenge>,
        userId: String?
    ): Int {
        val requestCount = requestsWithoutGames(requests).count {
            it.direction.equals("incoming", ignoreCase = true) &&
                it.status.equals("pending", ignoreCase = true)
        }
        val gameCount = if (userId.isNullOrBlank()) 0 else challenges.count {
            it.opponentId == userId && it.status.equals("pending", ignoreCase = true)
        }
        return requestCount + gameCount
    }
}
