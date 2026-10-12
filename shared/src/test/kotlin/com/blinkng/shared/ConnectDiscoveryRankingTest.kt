package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectDiscoveryRankingTest {
    @Test
    fun sharedInterestsBeatOnlineOnlyButNeverDependOnCoins() {
        val studyPeer = ConnectDiscoveryRanking.score(
            ConnectDiscoverySignals(sameCampus = true, sameDepartment = true)
        )
        val onlineStranger = ConnectDiscoveryRanking.score(
            ConnectDiscoverySignals(onlineNow = true)
        )
        assertTrue(studyPeer > onlineStranger)
    }

    @Test
    fun followingDoesNotCrowdOutDiscoverableStudents() {
        val newConnection = ConnectDiscoveryRanking.score(
            ConnectDiscoverySignals(onlineNow = true, sameCampus = true)
        )
        val followedPerson = ConnectDiscoveryRanking.score(
            ConnectDiscoverySignals(onlineNow = true, sameCampus = true, alreadyFollowing = true)
        )
        assertTrue(newConnection > followedPerson)
    }

    @Test
    fun failedSectionsAreVisibleWithoutRawServerErrors() {
        assertNull(ConnectDataHealth.message(emptySet()))
        assertEquals(
            "Roommates and Mentors couldn't refresh. Other sections are still available.",
            ConnectDataHealth.message(setOf("roommates", "mentors"))
        )
        assertTrue(
            ConnectDataHealth.message(setOf("roommates", "mentors", "inbox"))!!
                .contains("3 Connect sections")
        )
    }
}
