package com.example.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatNotificationPreviewPolicyTest {
    @Test fun hiddenHidesSenderBodyAndHistory() {
        assertEquals("BLINK Messages", ChatNotificationPreviewPolicy.title("hidden", "Alice"))
        assertEquals("New message", ChatNotificationPreviewPolicy.body("hidden", "Private content"))
        assertFalse(ChatNotificationPreviewPolicy.allowHistory("hidden"))
    }

    @Test fun senderOnlyKeepsSenderButHidesBodyAndHistory() {
        assertEquals("Alice", ChatNotificationPreviewPolicy.title("sender_only", "Alice"))
        assertEquals("New message", ChatNotificationPreviewPolicy.body("sender_only", "Private content"))
        assertFalse(ChatNotificationPreviewPolicy.allowHistory("sender_only"))
    }

    @Test fun fullUsesSavedPreference() {
        assertEquals("Alice", ChatNotificationPreviewPolicy.title("full", "Alice"))
        assertEquals("Private content", ChatNotificationPreviewPolicy.body("full", "Private content"))
        assertTrue(ChatNotificationPreviewPolicy.allowHistory("full"))
    }

    @Test fun unknownAndMissingModesFailClosed() {
        for (mode in listOf<String?>(null, "", "unexpected")) {
            assertEquals("hidden", ChatNotificationPreviewPolicy.normalized(mode))
            assertEquals("BLINK Messages", ChatNotificationPreviewPolicy.title(mode, "Alice"))
            assertEquals("New message", ChatNotificationPreviewPolicy.body(mode, "Secret"))
            assertFalse(ChatNotificationPreviewPolicy.allowHistory(mode))
        }
    }
}
