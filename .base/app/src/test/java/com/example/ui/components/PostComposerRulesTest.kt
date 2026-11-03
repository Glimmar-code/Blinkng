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

    private fun publishingAllowed(
        hasContent: Boolean = true,
        isReelMode: Boolean = false,
        hasVideo: Boolean = false,
        tagCount: Int = 0,
        maxTags: Int = 5,
        linkValid: Boolean = true,
        pollSelected: Boolean = false,
        pollValid: Boolean = false,
        allowedInFeed: Boolean = true,
        hasUnappliedVideoEdits: Boolean = false,
        isSubmitting: Boolean = false
    ) = PostComposerRules.canPublish(
        hasContent, isReelMode, hasVideo, tagCount, maxTags, linkValid,
        pollSelected, pollValid, allowedInFeed, hasUnappliedVideoEdits, isSubmitting
    )

    @Test fun hashtagsAreOptionalForNormalPostsAndReels() {
        assertTrue(publishingAllowed(tagCount = 0))
        assertTrue(publishingAllowed(isReelMode = true, hasVideo = true, tagCount = 0))
        assertFalse(publishingAllowed(tagCount = 6))
    }

    @Test fun reelRequiresVideoAndCompletedEditing() {
        assertFalse(publishingAllowed(isReelMode = true))
        assertFalse(publishingAllowed(isReelMode = true, hasVideo = true, hasUnappliedVideoEdits = true))
        assertTrue(publishingAllowed(isReelMode = true, hasVideo = true))
    }

    @Test fun invalidContentMustNotEnablePublication() {
        assertFalse(publishingAllowed(hasContent = false))
        assertFalse(publishingAllowed(linkValid = false))
        assertFalse(publishingAllowed(pollSelected = true, pollValid = false))
        assertFalse(publishingAllowed(allowedInFeed = false))
        assertFalse(publishingAllowed(isSubmitting = true))
        assertTrue(publishingAllowed(pollSelected = true, pollValid = true))
    }

    @Test fun pollAcceptsAtMostFourOptionsWithSafeLengths() {
        assertFalse(PostComposerRules.validPoll("Pick", listOf("A", "B", "C", "D", "E")))
        assertFalse(PostComposerRules.validPoll("Q".repeat(241), listOf("A", "B")))
        assertFalse(PostComposerRules.validPoll("Pick", listOf("A".repeat(121), "B")))
        assertTrue(PostComposerRules.validPoll("Pick", listOf("A", "B", "")))
    }

    @Test fun rejectsMalformedPublicWebLinks() {
        assertNull(PostComposerRules.normalizedLink("not a valid host"))
        assertNull(PostComposerRules.normalizedLink("ftp://blink.com.ng"))
    }
}
