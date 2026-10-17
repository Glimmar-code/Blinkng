package com.example.notification

import com.blinkng.shared.notification.BlinkNotificationKind
import com.blinkng.shared.notification.BlinkNotificationPresenter
import org.junit.Assert.assertEquals
import org.junit.Test

class BlinkNotificationPresenterTest {
    @Test fun literalNullValuesNeverReachNotification() {
        val result = BlinkNotificationPresenter.render(
            BlinkNotificationKind.LIKE, "null liked your post", "undefined", "null", "olamide"
        )
        assertEquals("@olamide liked your post", result.title)
        assertEquals("Open BLINK to view your post.", result.body)
    }

    @Test fun missingFollowerKeepsIdentityAnonymous() {
        val result = BlinkNotificationPresenter.render(BlinkNotificationKind.FOLLOW, null, "null")
        assertEquals("New follower", result.title)
        assertEquals("View your latest follower on BLINK.", result.body)
    }

    @Test fun validServerCopyIsPreserved() {
        val result = BlinkNotificationPresenter.render(
            BlinkNotificationKind.ADMIN, "Registration is open", "Check your campus dashboard"
        )
        assertEquals("Registration is open", result.title)
        assertEquals("Check your campus dashboard", result.body)
    }

    @Test fun rawSenderNamesAreSanitizedAndNotInvented() {
        val result = BlinkNotificationPresenter.render(
            BlinkNotificationKind.MESSAGE, "", "", senderName = "undefined", senderUsername = "null"
        )
        assertEquals("New message", result.title)
        assertEquals("Open BLINK to view your message.", result.body)
    }

    @Test fun whitespaceAndFakeValuesAreNormalized() {
        assertEquals("", BlinkNotificationPresenter.clean("   NULL "))
        assertEquals("", BlinkNotificationPresenter.clean("null  commented on your post"))
        assertEquals("New message today", BlinkNotificationPresenter.clean(" New\nmessage\t today "))
    }
}
