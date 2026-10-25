package com.blinkng.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkConversationSearchPolicyTest {
    @Test fun searchesUserNameHandleAndLatestMessage() {
        assertTrue(BlinkConversationSearchPolicy.matches("sarah", "Sarah Doe", "sx_doe", "Hello"))
        assertTrue(BlinkConversationSearchPolicy.matches("@sx_", "Sarah Doe", "@sx_doe", "Hello"))
        assertTrue(BlinkConversationSearchPolicy.matches("HEllo", "Sarah Doe", "sx_doe", "Hello there"))
        assertFalse(BlinkConversationSearchPolicy.matches("nobody", "Sarah Doe", "sx_doe", "Hello"))
    }

    @Test fun ignoresWhitespaceAndHandlesMissingMetadata() {
        assertTrue(BlinkConversationSearchPolicy.matches("  ", "Test", null, null))
        assertTrue(BlinkConversationSearchPolicy.matches("  Sarah  ", "Sarah Doe", null, null))
        assertFalse(BlinkConversationSearchPolicy.matches("unmatched", "", null, null))
    }
}
