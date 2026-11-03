package com.example.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedLaneProcessVisitTest {
    @Test
    fun firstVisitStartsAtTopAndNavigationWithinTaskCanRestore() {
        FeedLaneProcessVisit.resetForNewTask()
        assertFalse(FeedLaneProcessVisit.shouldRestore("member1:0"))
        assertTrue(FeedLaneProcessVisit.shouldRestore("member1:0"))
        assertFalse(FeedLaneProcessVisit.shouldRestore("member1:1"))
    }

    @Test
    fun reopeningNewTaskResetsPriorFeedPositionForEachUser() {
        FeedLaneProcessVisit.resetForNewTask()
        FeedLaneProcessVisit.shouldRestore("member1:0")
        FeedLaneProcessVisit.shouldRestore("member2:0")
        FeedLaneProcessVisit.resetForNewTask()
        assertFalse(FeedLaneProcessVisit.shouldRestore("member1:0"))
        assertFalse(FeedLaneProcessVisit.shouldRestore("member2:0"))
    }
}
