package com.blinkng.shared

import org.junit.Assert.*
import org.junit.Test

class BlinkChatMediaAccessPolicyTest {
    @Test fun privateAndDeletedMessagesCannotAppearInBrowseOrStarredCollections() {
        for (once in listOf(true, false)) for (deleted in listOf(true, false)) {
            val allowed = !once && !deleted
            assertEquals(allowed, BlinkChatMediaAccessPolicy.mayBrowse(
                once, deleted, true
            ))
            assertEquals(allowed, BlinkChatMediaAccessPolicy.mayKeepInSavedMedia(
                once, deleted, true
            ))
        }
    }

    @Test fun emptyAndUnstarredItemsStayOutOfCollections() {
        assertFalse(BlinkChatMediaAccessPolicy.mayBrowse(false,false,false))
        assertFalse(BlinkChatMediaAccessPolicy.mayKeepInSavedMedia(false,false,false))
    }
}
