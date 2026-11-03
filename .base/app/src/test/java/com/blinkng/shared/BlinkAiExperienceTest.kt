package com.blinkng.shared

import com.blinkng.shared.ai.BlinkAiExperienceCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkAiExperienceTest {
    @Test
    fun exploreCatalog_hasStableUniqueIdsAndSupportedModes() {
        val categories = BlinkAiExperienceCatalog.categories

        assertTrue(categories.size >= 10)
        assertEquals(categories.size, categories.map { it.id }.distinct().size)
        assertTrue(
            categories.all {
                it.mode in setOf("fast", "deep", "code", "research", "write", "study")
            }
        )
    }

    @Test
    fun exploreCatalog_hasUsefulStarterPrompts() {
        assertTrue(
            BlinkAiExperienceCatalog.categories.all {
                it.title.isNotBlank() &&
                    it.subtitle.isNotBlank() &&
                    it.starterPrompt.isNotBlank()
            }
        )
    }
}
