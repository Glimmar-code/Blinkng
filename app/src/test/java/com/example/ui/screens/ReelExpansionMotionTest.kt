package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelExpansionMotionTest {
    @Test fun previewFollowsFingerAndClampsAtViewportEdges() {
        assertEquals(.25f, ReelExpansionMotion.progressAfterDrag(0f, -250f, 1000f), .0001f)
        assertEquals(.85f, ReelExpansionMotion.progressAfterDrag(.25f, -600f, 1000f), .0001f)
        assertEquals(1f, ReelExpansionMotion.progressAfterDrag(.85f, -500f, 1000f), .0001f)
        assertEquals(0f, ReelExpansionMotion.progressAfterDrag(.25f, 500f, 1000f), .0001f)
    }

    @Test fun fastUpExpandsAndFastDownCollapses() {
        assertTrue(ReelExpansionMotion.shouldCompleteFastSwipe(70f, 2000f, 28f, 1100f))
        assertTrue(ReelExpansionMotion.shouldCompleteFastSwipe(70f, 1100f, 28f, 1100f))
        assertTrue(ReelExpansionMotion.shouldCompleteFastSwipe(-70f, -1900f, 28f, 1100f))
    }

    @Test fun slowOrWrongDirectionSpringBack() {
        assertFalse(ReelExpansionMotion.shouldCompleteFastSwipe(400f, 80f, 28f, 1100f))
        assertFalse(ReelExpansionMotion.shouldCompleteFastSwipe(12f, 2500f, 28f, 1100f))
        assertFalse(ReelExpansionMotion.shouldCompleteFastSwipe(70f, -2500f, 28f, 1100f))
        assertFalse(ReelExpansionMotion.shouldCompleteFastSwipe(-70f, 2500f, 28f, 1100f))
    }

    @Test fun slightIntentionalSwipeExpandsWithoutFastFlick() {
        assertTrue(ReelExpansionMotion.shouldExpandFromPreview(.10f, false))
        assertTrue(ReelExpansionMotion.shouldExpandFromPreview(.01f, true))
        assertFalse(ReelExpansionMotion.shouldExpandFromPreview(.03f, false))
        assertFalse(ReelExpansionMotion.shouldExpandFromPreview(Float.NaN, false))
    }

    @Test fun deliberateDownwardPullRestoresPreview() {
        assertTrue(ReelExpansionMotion.shouldCollapseFromFullscreen(.80f, false))
        assertTrue(ReelExpansionMotion.shouldCollapseFromFullscreen(.99f, true))
        assertFalse(ReelExpansionMotion.shouldCollapseFromFullscreen(.96f, false))
    }

    @Test fun horizontalNavigationRequiresDistanceSpeedAndSameDirection() {
        assertTrue(ReelExpansionMotion.shouldCompleteFastSwipe(110f, 1600f, 84f, 900f))
        assertTrue(ReelExpansionMotion.shouldCompleteFastSwipe(-110f, -1600f, 84f, 900f))
        assertFalse(ReelExpansionMotion.shouldCompleteFastSwipe(110f, 200f, 84f, 900f))
        assertFalse(ReelExpansionMotion.shouldCompleteFastSwipe(40f, 1700f, 84f, 900f))
        assertFalse(ReelExpansionMotion.shouldCompleteFastSwipe(-120f, 1700f, 84f, 900f))
    }

    @Test fun invalidMotionCannotTriggerNavigation() {
        assertFalse(ReelExpansionMotion.shouldCompleteFastSwipe(Float.NaN, 1500f, 28f, 1100f))
        assertFalse(ReelExpansionMotion.shouldCompleteFastSwipe(30f, Float.NaN, 28f, 1100f))
        assertFalse(ReelExpansionMotion.shouldCompleteFastSwipe(30f, 1500f, 28f, 0f))
        assertEquals(.5f, ReelExpansionMotion.progressAfterDrag(.5f, 100f, 0f), .0001f)
    }
}
