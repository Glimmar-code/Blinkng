package com.example.notification

import com.blinkng.shared.notification.BlinkNotificationTimelinePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkNotificationTimelinePolicyTest {
    @Test fun chatActivityIsNotPartOfSocialBadge() {
        assertFalse(BlinkNotificationTimelinePolicy.belongsInActivityTimeline("CHAT"))
        assertTrue(BlinkNotificationTimelinePolicy.belongsInActivityTimeline("post"))
    }

    @Test fun officialNoticeMirrorUsesExactId() {
        assertTrue(BlinkNotificationTimelinePolicy.isOfficialMirror("notification", "id-1", setOf("id-1")))
        assertFalse(BlinkNotificationTimelinePolicy.isOfficialMirror("notification", "id-2", setOf("id-1")))
    }

    @Test fun socialMirrorRequiresSameActorPostTypeAndTime() {
        fun matches(actor: String, post: String) =
            BlinkNotificationTimelinePolicy.isSameSocialEvent(
                "author", actor, "LIKES", "like",
                "2026-10-27T12:34:56.111Z", "2026-10-27T12:34:56.222Z",
                "post-1", post, "post"
            )
        assertTrue(matches("author", "post-1"))
        assertFalse(matches("other", "post-1"))
        assertFalse(matches("author", "post-2"))
    }
}
