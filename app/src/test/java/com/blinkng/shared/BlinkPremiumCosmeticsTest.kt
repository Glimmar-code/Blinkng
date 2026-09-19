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
    fun completeProfileThemeSuppliesCoordinatedLayers() {
        val layers = BlinkPremiumCosmetics.profileLayers(listOf("profile_theme_bundle"))

        assertEquals("profile_theme_bundle", layers.theme?.catalogId)
        assertEquals("profile_theme_bundle", layers.aura?.catalogId)
        assertEquals("profile_theme_bundle", layers.frame?.catalogId)
        assertEquals("profile_theme_bundle", layers.name?.catalogId)
        assertEquals("profile_theme_bundle", layers.entrance?.catalogId)
        assertTrue(layers.hasVisibleProfileEffect)
    }

    @Test
    fun serverDrivenCampusAndLevelRewardsHaveRealPublicVisuals() {
        val campusTheme = BlinkPremiumCosmetics.spec("campus_signature_theme")
        val levelFrame = BlinkPremiumCosmetics.spec("level_10_neon_frame")
        val legendAura = BlinkPremiumCosmetics.spec("level_50_legend_aura")

        assertEquals(BlinkPremiumSurface.PROFILE_THEME, campusTheme.surface)
        assertTrue(campusTheme.fullSurface)
        assertEquals(BlinkPremiumSurface.AVATAR_FRAME, levelFrame.surface)
        assertEquals(BlinkPremiumSurface.PROFILE_AURA, legendAura.surface)
        assertTrue(legendAura.fullSurface)
    }

    @Test
    fun seasonalCosmeticsUseTheirActualProfileSurfaces() {
        assertEquals(
            BlinkPremiumSurface.PROFILE_THEME,
            BlinkPremiumCosmetics.spec("christmas_2026_profile_theme").surface,
        )
        assertEquals(
            BlinkPremiumSurface.NAME_SIGNATURE,
            BlinkPremiumCosmetics.spec("christmas_2026_nameplate").surface,
        )
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
