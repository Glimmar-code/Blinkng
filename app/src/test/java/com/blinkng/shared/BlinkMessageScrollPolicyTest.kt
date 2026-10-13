package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class BlinkMessageScrollPolicyTest {
    private fun action(
        hasMessages: Boolean = true,
        filtering: Boolean = false,
        hasSeenLatest: Boolean = true,
        userHasScrolled: Boolean = true,
        nearLatest: Boolean = false,
        latestChanged: Boolean = true
    ) = BlinkMessageScrollPolicy.decide(
        hasMessages, filtering, hasSeenLatest, userHasScrolled, nearLatest, latestChanged
    )

    @Test fun initialLoadJumpsWithoutAnimatingLongHistories() {
        assertEquals(BlinkMessageScrollAction.JUMP_TO_LATEST, action(hasSeenLatest = false))
        assertEquals(BlinkMessageScrollAction.JUMP_TO_LATEST, action(userHasScrolled = false))
    }

    @Test fun readingOldMessagesPreservesScrollAndShowsNewIndicator() {
        assertEquals(BlinkMessageScrollAction.SHOW_NEW_MESSAGE, action())
    }

    @Test fun nearBottomAnimatesOnlyWhenLatestMessageActuallyChanges() {
        assertEquals(BlinkMessageScrollAction.ANIMATE_TO_LATEST, action(nearLatest = true))
        assertEquals(BlinkMessageScrollAction.KEEP, action(nearLatest = true, latestChanged = false))
    }

    @Test fun filtersNeverAutomaticallyJumpToEnd() {
        assertEquals(BlinkMessageScrollAction.KEEP, action(filtering = true))
        assertEquals(BlinkMessageScrollAction.KEEP, action(hasMessages = false))
    }
}
