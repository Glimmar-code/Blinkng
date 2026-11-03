package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkSmartTagsTest {
    @Test fun canonicalizationAndInvalidTags() {
        assertEquals("campuslife", BlinkSmartTags.normalize(" #CampusLife "))
        assertEquals("futa_2026", BlinkSmartTags.normalize("FUTA_2026"))
        assertNull(BlinkSmartTags.normalize("bad tag"))
        assertNull(BlinkSmartTags.normalize("#"))
        assertNull(BlinkSmartTags.normalize("#a"))
        assertNull(BlinkSmartTags.normalize("bad-tag"))
    }

    @Test fun selectedAndInlineHashtagsRemainUnique() {
        val tags = BlinkSmartTags.allTags(
            "News from #FUTA and #CampusLife. #futa!",
            listOf("FUTA", "#Tech")
        )
        assertEquals(listOf("futa", "tech", "campuslife"), tags)
        assertEquals("#futa", BlinkSmartTags.display("FUTA"))
    }

    @Test fun moreThanFiveTagsMustBeRejectedBeforePublishing() {
        val all = BlinkSmartTags.allTags(
            "#aa #bb #cc #dd #ee #ff", emptyList()
        )
        assertEquals(6, all.size)
        assertEquals(5, BlinkSmartTags.merge("#aa #bb #cc #dd #ee #ff", emptyList()).size)
        assertFalse(BlinkSmartTags.add(listOf("aa", "bb", "cc", "dd", "ee"), "ff").contains("ff"))
    }

    @Test fun plainWordsAndDuplicateSuggestionsDoNotCreateExtraTags() {
        assertTrue(BlinkSmartTags.fromText("Plain news with no hashtags").isEmpty())
        assertEquals(listOf("futa"), BlinkSmartTags.add(listOf("#FUTA"), "futa"))
    }
    @Test fun recommendationsFavorRelevanceAndPreserveTrendingOrder() {
        assertEquals(
            listOf("futa", "football"),
            BlinkSmartTags.suggest(
                text = "Football discussions at FUTA today",
                trending = listOf("unrelated", "futa", "football", "football", "tech"),
                selected = emptyList()
            )
        )
        assertEquals(
            listOf("futa"),
            BlinkSmartTags.suggest(
                text = "New students arriving",
                trending = listOf("viral", "futa", "tech"),
                university = "FUTA"
            )
        )
    }

    @Test fun recommendationsDoNotDuplicateExistingOrSuggestSpam() {
        assertEquals(
            listOf("campus_life"),
            BlinkSmartTags.suggest(
                text = "Campus life update #FUTA",
                trending = listOf("futa", "campus_life", "trending", "bad-tag"),
                selected = listOf("futa")
            )
        )
        assertTrue(BlinkSmartTags.suggest("Random text", listOf("viral", "sports")).isEmpty())
        assertTrue(BlinkSmartTags.suggest("FUTA", listOf("futa"), limit = 0).isEmpty())
    }

}
