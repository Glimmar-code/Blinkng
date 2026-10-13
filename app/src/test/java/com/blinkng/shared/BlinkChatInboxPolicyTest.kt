package com.blinkng.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkChatInboxPolicyTest {
    @Test fun unreadTabShowsOnlyUnreadPrimaryConversations() {
        assertTrue(BlinkChatInboxPolicy.matches(BlinkMessageInbox.UNREAD, false, "primary", unread = true))
        assertFalse(BlinkChatInboxPolicy.matches(BlinkMessageInbox.UNREAD, false, "primary", unread = false))
        assertFalse(BlinkChatInboxPolicy.matches(BlinkMessageInbox.UNREAD, true, "primary", unread = true))
        assertFalse(BlinkChatInboxPolicy.matches(BlinkMessageInbox.UNREAD, false, "requests", unread = true))
    }

    @Test fun allTabIncludesActiveChatsAndRequestsButNotArchivedThreads() {
        assertTrue(BlinkChatInboxPolicy.matches(BlinkMessageInbox.ALL, false, "primary"))
        assertTrue(BlinkChatInboxPolicy.matches(BlinkMessageInbox.ALL, false, "requests"))
        assertFalse(BlinkChatInboxPolicy.matches(BlinkMessageInbox.ALL, true, "primary"))
    }

    @Test fun existingTabsContinueToRespectRequestAndArchiveBuckets() {
        assertTrue(BlinkChatInboxPolicy.matches(BlinkMessageInbox.PRIMARY, false, "primary"))
        assertTrue(BlinkChatInboxPolicy.matches(BlinkMessageInbox.REQUESTS, false, "requests"))
        assertTrue(BlinkChatInboxPolicy.matches(BlinkMessageInbox.ARCHIVED, true, "requests"))
        assertFalse(BlinkChatInboxPolicy.matches(BlinkMessageInbox.PRIMARY, false, "requests"))
        assertFalse(BlinkChatInboxPolicy.matches(BlinkMessageInbox.REQUESTS, true, "requests"))
    }
}
