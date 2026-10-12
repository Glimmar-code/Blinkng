package com.example.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectHubSnapshotRecoveryTest {
    @Test
    fun `failed public directories retain last known data`() {
        val previous = ConnectHubSnapshot(
            roommates = listOf(RoommateListing("old-room", "owner", "Room nearby")),
            mentors = listOf(MentorListing("old-mentor", "owner", "mentor")),
            readingMates = listOf(ReadingMateListing("old-study", "owner")),
            housingRequests = listOf(HousingRequestListing("old-house", "owner", "House")),
        )
        val fresh = ConnectHubSnapshot(
            roommates = emptyList(),
            mentors = emptyList(),
            readingMates = listOf(ReadingMateListing("new-study", "other")),
            housingRequests = emptyList(),
            failedSections = setOf("roommates", "mentors", "housing")
        )
        val recovered = fresh.retainFailedPublicSections(previous)
        assertEquals(previous.roommates, recovered.roommates)
        assertEquals(previous.mentors, recovered.mentors)
        assertEquals(previous.housingRequests, recovered.housingRequests)
        assertEquals(fresh.readingMates, recovered.readingMates)
        assertEquals(fresh.failedSections, recovered.failedSections)
    }

    @Test
    fun `never restore account specific data after reconnect or account switch`() {
        val previous = ConnectHubSnapshot(
            smartMatches = listOf(SmartMatchCandidate("person", "person", "Previous suggestion")),
            requests = listOf(ConnectRequestItem("mentor", "old-request", "incoming", "pending")),
            gameChallenges = listOf(GameChallenge("old-challenge", "previous", "other", "math_sprint", "pending")),
            gameStats = GameProfileStats(score = 100)
        )
        val fresh = ConnectHubSnapshot(
            failedSections = setOf("matches", "inbox", "challenges", "game_stats")
        )
        val recovered = fresh.retainFailedPublicSections(previous)
        assertTrue(recovered.smartMatches.isEmpty())
        assertTrue(recovered.requests.isEmpty())
        assertTrue(recovered.gameChallenges.isEmpty())
        assertEquals(GameProfileStats(), recovered.gameStats)
    }

    @Test
    fun `successful empty response clears previously cached public listings`() {
        val old = ConnectHubSnapshot(roommates = listOf(RoommateListing("old", "owner", "Expired")))
        val fresh = ConnectHubSnapshot(roommates = emptyList())
        assertTrue(fresh.retainFailedPublicSections(old).roommates.isEmpty())
    }
}
