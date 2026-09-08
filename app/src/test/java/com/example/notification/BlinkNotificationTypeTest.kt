package com.example.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class BlinkNotificationTypeTest {
    @Test
    fun `direct message aliases route to message`() {
        assertEquals(BlinkNotificationType.MESSAGE, BlinkNotificationType.fromWire("message"))
        assertEquals(BlinkNotificationType.MESSAGE, BlinkNotificationType.fromWire("dm"))
        assertEquals(BlinkNotificationType.MESSAGE, BlinkNotificationType.fromWire("direct_message"))
    }

    @Test
    fun `call payloads keep realtime priority`() {
        assertEquals(BlinkNotificationType.INCOMING_CALL, BlinkNotificationType.fromWire("incoming_call"))
        assertEquals(BlinkNotificationType.CALL_UPDATE, BlinkNotificationType.fromWire("call_update"))
    }

    @Test
    fun `social payloads route to dedicated preference categories`() {
        assertEquals(BlinkNotificationType.COMMENT, BlinkNotificationType.fromWire("comment"))
        assertEquals(BlinkNotificationType.REPLY, BlinkNotificationType.fromWire("reply"))
        assertEquals(BlinkNotificationType.MENTION, BlinkNotificationType.fromWire("mention"))
        assertEquals(BlinkNotificationType.FOLLOW, BlinkNotificationType.fromWire("follow"))
        assertEquals(BlinkNotificationType.STORY, BlinkNotificationType.fromWire("story_like"))
        assertEquals(BlinkNotificationType.REEL, BlinkNotificationType.fromWire("reel_comment"))
    }

    @Test
    fun `future unknown payloads stay visible as safe fallback`() {
        assertEquals(BlinkNotificationType.UNKNOWN, BlinkNotificationType.fromWire("future_notification_type"))
        assertEquals(BlinkNotificationType.SOCIAL, BlinkNotificationType.fromWire(null))
    }
}
