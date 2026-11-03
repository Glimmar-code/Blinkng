package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlinkGroupRouteTest {
    @Test fun groupRouteNeverLooksLikeUsername() {
        val id = "11111111-2222-4333-8444-555555555555"
        val route = BlinkGroupRoute.key(id)
        assertEquals("group_" + id, route)
        assertEquals(id, BlinkGroupRoute.conversationId(route))
        assertNull(BlinkGroupRoute.conversationId("studentname"))
    }
}
