package com.example.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectHubSnapshotTest {
    @Test
    fun emptySnapshotDoesNotPretendThatBackendRequestsFailed() {
        val snapshot = ConnectHubSnapshot()
        assertTrue(snapshot.roommates.isEmpty())
        assertTrue(snapshot.requests.isEmpty())
        assertTrue(snapshot.failedSections.isEmpty())
    }

    @Test
    fun individualBackendFailuresRemainVisibleWhenOtherSectionsAreEmpty() {
        val snapshot = ConnectHubSnapshot(failedSections = listOf("Roommates", "Connect inbox"))
        assertEquals(listOf("Roommates", "Connect inbox"), snapshot.failedSections)
        assertTrue(snapshot.roommates.isEmpty())
        assertFalse(snapshot.failedSections.isEmpty())
    }
    @Test
    fun refreshKeepsLastKnownSectionOnFailureAndStillShowsWarning() {
        val cached = ConnectHubSnapshot(
            roommates = listOf(RoommateListing(id = "r1", userId = "user", title = "Room A")),
            mentors = listOf(MentorListing(id = "m1", userId = "mentor", mode = "mentor"))
        )
        val partial = ConnectHubSnapshot(
            failedSections = listOf("Roommates"),
            mentors = emptyList()
        ).retainFailedSectionsFrom(cached)
        assertEquals(listOf("r1"), partial.roommates.map { it.id })
        assertTrue(partial.mentors.isEmpty()) // A successful empty fetch must be honored.
        assertEquals(listOf("Roommates"), partial.failedSections)
    }

    @Test
    fun successfulEmptyRefreshClearsStaleListings() {
        val cached = ConnectHubSnapshot(
            readingMates = listOf(ReadingMateListing(id = "reading1", userId = "user"))
        )
        val fresh = ConnectHubSnapshot().retainFailedSectionsFrom(cached)
        assertTrue(fresh.readingMates.isEmpty())
        assertTrue(fresh.failedSections.isEmpty())
    }

}
