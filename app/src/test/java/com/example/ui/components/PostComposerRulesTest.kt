package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostComposerRulesTest {
    @Test
    fun normalizedLinkAddsHttpsAndRejectsInvalidHosts() {
        assertEquals("https://blink.com.ng", PostComposerRules.normalizedLink("blink.com.ng"))
        assertEquals(
            "https://www.blink.com.ng/post/123",
            PostComposerRules.normalizedLink("https://www.blink.com.ng/post/123")
        )
        assertNull(PostComposerRules.normalizedLink("not a valid host"))
        assertNull(PostComposerRules.normalizedLink("ftp://blink.com.ng/file"))
    }

    @Test
    fun tagsAreNormalizedDistinctAndLimited() {
        assertEquals(
            listOf("futa", "blink_community"),
            PostComposerRules.tags("#FUTA news #futa #Blink_Community")
        )
        val many = (1..25).joinToString(" ") { "#tag$it" }
        assertEquals(20, PostComposerRules.tags(many).size)
    }

    @Test
    fun mentionsAreCaseInsensitiveDistinct() {
        assertEquals(
            listOf("Glimmar", "friend.one"),
            PostComposerRules.mentions("Hi @Glimmar @glimmar and @friend.one")
        )
    }

    @Test
    fun pollRequiresQuestionTwoUniqueOptions() {
        assertTrue(PostComposerRules.validPoll("Pick one", listOf("Yes", "No")))
        assertFalse(PostComposerRules.validPoll("", listOf("Yes", "No")))
        assertFalse(PostComposerRules.validPoll("Pick one", listOf("Yes", "")))
        assertFalse(PostComposerRules.validPoll("Pick one", listOf("Yes", " yes ")))
    }
}
