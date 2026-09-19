package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkPremiumCosmeticsTest {
    @Test
    fun profileHighlightIsPresentedAsAFullProfileAura() {
        val spec = BlinkPremiumCosmetics.spec("profile_highlight_1h")

        assertEquals("Profile Aura", spec.displayName)
        assertEquals(BlinkPremiumSurface.PROFILE_AURA, spec.surface)
        assertTrue(spec.fullSurface)
    }

    @Test
    fun commentHighlightIsAFullCommentSpotlight() {
        val spec = BlinkPremiumCosmetics.spec("comment_highlight")

        assertEquals("Comment Spotlight", spec.displayName)
        assertEquals(BlinkPremiumSurface.COMMENT_SPOTLIGHT, spec.surface)
        assertEquals(BlinkPremiumMotion.EDGE_REVEAL, spec.motion)
        assertTrue(spec.fullSurface)
    }

    @Test
    fun profileLayersKeepCompatibleCosmeticsTogether() {
        val layers = BlinkPremiumCosmetics.profileLayers(
            listOf("profile_highlight_1h", "premium_profile_frame", "animated_name", "creator_badge")
        )

        assertEquals("profile_highlight_1h", layers.aura?.catalogId)
        assertEquals("premium_profile_frame", layers.frame?.catalogId)
        assertEquals("animated_name", layers.name?.catalogId)
        assertEquals("creator_badge", layers.badge?.catalogId)
        assertNotNull(layers.strongest)
    }
}
