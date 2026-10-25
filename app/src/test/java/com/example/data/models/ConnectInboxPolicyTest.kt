package com.example.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectInboxPolicyTest {
    private fun request(kind: String, status: String = "pending") =
        ConnectRequestItem(kind = kind, requestId = "r-$kind", direction = "incoming", status = status)

    private fun game(status: String = "pending") =
        GameChallenge(id = "g1", challengerId = "other", opponentId = "me", gameType = "logic", status = status)

    @Test
    fun duplicateGameRowsDoNotDoubleCountInvitations() {
        val requests = listOf(request("game"), request("roommate"), request("mentor", "accepted"))
        val challenges = listOf(game())
        assertEquals(listOf("roommate", "mentor"), ConnectInboxPolicy.requestsWithoutGames(requests).map { it.kind })
        assertEquals(2, ConnectInboxPolicy.incomingPendingCount(requests, challenges, "me"))
    }

    @Test
    fun outgoingAndAlreadyAcceptedChallengesDoNotCountAsPending() {
        val outgoing = request("roommate").copy(direction = "outgoing")
        assertEquals(0, ConnectInboxPolicy.incomingPendingCount(
            listOf(outgoing, request("game")), listOf(game("accepted")), "me"
        ))
        assertTrue(ConnectInboxPolicy.requestsWithoutGames(listOf(request("GaMe"))).isEmpty())
    }

    @Test
    fun gameCountsOnlyForCurrentRecipient() {
        assertEquals(0, ConnectInboxPolicy.incomingPendingCount(emptyList(), listOf(game()), "somebody-else"))
    }
}
