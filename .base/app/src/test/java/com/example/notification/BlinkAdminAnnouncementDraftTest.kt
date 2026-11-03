package com.example.notification

import com.blinkng.shared.notification.*
import org.junit.Assert.*
import org.junit.Test

class BlinkAdminAnnouncementDraftTest {
    @Test fun validImmediateAnnouncementCanUseCurrentServerRpc() {
        val message = BlinkAdminAnnouncementDraft(title = "Release", body = "New version available")
        assertNull(message.validate())
        assertEquals("Release — New version available", message.formattedMessage())
        assertTrue(message.canPublishWithExistingBackend())
    }

    @Test fun cannotClaimUndeliverableChannelsAreLive() {
        val push = BlinkAdminAnnouncementDraft("New", "Features", delivery = BlinkAnnouncementDelivery.PUSH)
        val both = push.copy(delivery = BlinkAnnouncementDelivery.BOTH)
        val scheduled = push.copy(delivery = BlinkAnnouncementDelivery.IN_APP, sendLater = true)
        assertFalse(push.canPublishWithExistingBackend())
        assertFalse(both.canPublishWithExistingBackend())
        assertFalse(scheduled.canPublishWithExistingBackend())
        assertTrue(push.unavailableReason()!!.contains("require"))
        assertTrue(scheduled.unavailableReason()!!.contains("scheduler"))
    }

    @Test fun protectAudienceAgainstEmptyOrUnselectedUser() {
        assertNotNull(BlinkAdminAnnouncementDraft("Title", "Message", audience = BlinkAnnouncementAudience.USER).validate())
        val selected = BlinkAdminAnnouncementDraft(
            "Title", "Message", audience = BlinkAnnouncementAudience.USER,
            userId = "00112233-4455-6677-8899-aabbccddeeff"
        )
        assertNull(selected.validate())
        assertNotNull(selected.copy(audience = BlinkAnnouncementAudience.UNIVERSITY).validate())
    }

    @Test fun preventEmptyOversizedBroadcast() {
        assertNotNull(BlinkAdminAnnouncementDraft("", "Body").validate())
        assertNotNull(BlinkAdminAnnouncementDraft("Title", " ").validate())
        assertNotNull(BlinkAdminAnnouncementDraft("Title", "a".repeat(2000)).validate())
    }
}
