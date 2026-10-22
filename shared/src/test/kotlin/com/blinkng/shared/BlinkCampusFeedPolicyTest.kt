package com.blinkng.shared

import com.blinkng.shared.discovery.BlinkCampusFeedPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlinkCampusFeedPolicyTest {
    @Test
    fun legacyUniversityAcronymsMatchCanonicalNames() {
        assertTrue(BlinkCampusFeedPolicy.isSameCampus("FUTA", "Federal University of Technology, Akure"))
        assertTrue(BlinkCampusFeedPolicy.isSameCampus("UNILAG", "University of Lagos"))
    }

    @Test
    fun missingAndDifferentUniversitiesCannotLeakPostsIntoCampusFeed() {
        assertFalse(BlinkCampusFeedPolicy.isSameCampus("", "FUTA"))
        assertFalse(BlinkCampusFeedPolicy.isSameCampus("FUTA", null))
        assertFalse(BlinkCampusFeedPolicy.isSameCampus("FUTA", "UNILAG"))
    }

    @Test
    fun previewScoreIsBoundedForNegativeCountsAndStableAcrossPlatforms() {
        assertEquals(27L, BlinkCampusFeedPolicy.previewScore(8, 5, 3))
        assertEquals(0L, BlinkCampusFeedPolicy.previewScore(-1, -5, -8))
    }
}
