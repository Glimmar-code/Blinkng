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
}
