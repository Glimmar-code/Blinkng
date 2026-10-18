package com.example.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CallIceConfigParserTest {
    @Test fun `call scoped response includes STUN and TURN`() {
        val config = CallIceConfigParser.parse(
            """{"ice_servers":[{"urls":["stun:stun.example.org"]},
            {"urls":["turns:relay.example.org:5349"],"username":"expiry:user","credential":"signed"}]}"""
        )
        assertEquals(2, config.size)
        assertEquals("turns:relay.example.org:5349", config[1].urls.single())
        assertEquals("signed", config[1].credential)
    }

    @Test fun `existing authenticated endpoint remains compatible`() {
        val config = CallIceConfigParser.parse(
            """{"urls":["turn:relay.example.org:3478?transport=udp"],
            "username":"expiry:pseudonym","credential":"signed"}"""
        )
        assertEquals(1, config.size)
        assertEquals("expiry:pseudonym", config[0].username)
    }

    @Test fun `STUN alone cannot pass relay verification`() {
        assertThrows(IllegalArgumentException::class.java) {
            CallIceConfigParser.parse("""{"ice_servers":[{"urls":["stun:stun.example.org"]}]}""")
        }
    }

    @Test fun `TURN without credentials is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            CallIceConfigParser.parse("""{"urls":["turn:relay.example.org:3478"]}""")
        }
    }

    @Test fun `untrusted URLs and duplicate empty endpoints are ignored`() {
        val config = CallIceConfigParser.parse(
            """{"urls":["https://example.org","turn:relay.example.org:3478","", "turn:bad relay"],
            "username":"u","credential":"p"}"""
        )
        assertEquals(listOf("turn:relay.example.org:3478"), config.single().urls)
        assertTrue(config.single().credential.isNotBlank())
    }

    @Test fun `legacy fallback only accepts absent or unconfigured endpoint`() {
        assertTrue(CallIceConfigParser.allowLegacyFallback(
            404, """{"code":"NOT_FOUND","message":"Requested function was not found"}"""
        ))
        assertTrue(CallIceConfigParser.allowLegacyFallback(
            503, """{"error":"TURN relay is not configured"}"""
        ))
        assertEquals(false, CallIceConfigParser.allowLegacyFallback(
            404, """{"error":"Call not found"}"""
        ))
        assertEquals(false, CallIceConfigParser.allowLegacyFallback(
            401, """{"error":"Authentication failed"}"""
        ))
        assertEquals(false, CallIceConfigParser.allowLegacyFallback(
            503, """{"error":"Call lookup failed"}"""
        ))
    }
}
