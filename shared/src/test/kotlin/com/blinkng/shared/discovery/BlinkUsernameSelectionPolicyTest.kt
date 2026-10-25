package com.blinkng.shared.discovery

import org.junit.Assert.*
import org.junit.Test

class BlinkUsernameSelectionPolicyTest {
    @Test fun mentionUsesTrailingTokenOnly() {
        assertEquals("gli", BlinkUsernameSelectionPolicy.trailingMention("hello @gli"))
        assertNull(BlinkUsernameSelectionPolicy.trailingMention("hello @gli and more"))
        assertNull(BlinkUsernameSelectionPolicy.trailingMention("no mention"))
        assertEquals("hello @glimmar ", BlinkUsernameSelectionPolicy.insertMention("hello @gli","glimmar"))
    }

    @Test fun invitationSelectionDeduplicatesAndCaps() {
        assertEquals(setOf("glimmar"),BlinkUsernameSelectionPolicy.addInvite(setOf("glimmar"),"@glimmar"))
        assertEquals(setOf("glimmar","alex"),BlinkUsernameSelectionPolicy.addInvite(setOf("glimmar"),"Alex"))
        assertEquals(setOf("u"),BlinkUsernameSelectionPolicy.addInvite(setOf("u"),"extra",1))
    }
}
