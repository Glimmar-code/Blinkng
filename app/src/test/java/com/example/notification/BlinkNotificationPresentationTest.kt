package com.example.notification

import com.blinkng.shared.notification.BlinkNotificationKind
import com.blinkng.shared.notification.BlinkNotificationPresentation
import org.junit.Assert.assertEquals
import org.junit.Test

class BlinkNotificationPresentationTest {
    @Test fun missingPayloadsNeverBecomeVisibleCopy() {
        assertEquals("BLINK notification", BlinkNotificationPresentation.title("null"))
        assertEquals("New follower", BlinkNotificationPresentation.title(" undefined ", "New follower"))
        assertEquals("New message", BlinkNotificationPresentation.body("null", "New message"))
        assertEquals("Blink user", BlinkNotificationPresentation.displayName("null"))
        assertEquals("", BlinkNotificationPresentation.username("@null"))
        assertEquals("", BlinkNotificationPresentation.username("undefined"))
    }

    @Test fun oldMalformedActorPrefixesHaveAReadableFallback() {
        assertEquals("Someone liked your post", BlinkNotificationPresentation.title("null liked your post"))
        assertEquals("Someone started following you", BlinkNotificationPresentation.title("@null started following you"))
        assertEquals("Someone commented on your post", BlinkNotificationPresentation.title("undefined commented on your post"))
    }

    @Test fun legitimateMessagesAndUsernamesArePreserved() {
        assertEquals("the null hypothesis", BlinkNotificationPresentation.body("the null hypothesis"))
        assertEquals("Check the null hypothesis", BlinkNotificationPresentation.title("Check the null hypothesis"))
        assertEquals("futa_student", BlinkNotificationPresentation.username("@futa_student"))
        assertEquals("Adebayo", BlinkNotificationPresentation.displayName("Adebayo"))
    }

    @Test fun missingTitlesFallbackByCategory() {
        assertEquals("New message", BlinkNotificationPresentation.defaultTitle(BlinkNotificationKind.MESSAGE))
        assertEquals("Someone liked your post", BlinkNotificationPresentation.defaultTitle(BlinkNotificationKind.LIKE))
        assertEquals("New follower", BlinkNotificationPresentation.defaultTitle(BlinkNotificationKind.FOLLOW))
    }
}
