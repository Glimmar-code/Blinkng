package com.example

import com.blinkng.shared.BlinkAdaptiveLayoutPolicy
import com.blinkng.shared.BlinkFeedScrollMotion
import org.junit.Assert.assertEquals
import org.junit.Test

/** These run in :app:testDebugUnitTest, not unconfigured shared/src/test. */
class BlinkAdaptiveMotionContractTest {
    @Test fun fontScaleAndWidthSelectCompactToolbars() {
        assertEquals(true, BlinkAdaptiveLayoutPolicy.compactToolbar(320, 1f))
        assertEquals(false, BlinkAdaptiveLayoutPolicy.compactToolbar(412, 1f))
        assertEquals(true, BlinkAdaptiveLayoutPolicy.compactToolbar(412, 1.5f))
        assertEquals(true, BlinkAdaptiveLayoutPolicy.compactToolbar(412, 2f))
        assertEquals(1, BlinkAdaptiveLayoutPolicy.marketColumns(320, 1f))
        assertEquals(1, BlinkAdaptiveLayoutPolicy.marketColumns(412, 1.5f))
        assertEquals(2, BlinkAdaptiveLayoutPolicy.marketColumns(412, 1f))
    }

    @Test fun exactEightyTwentyFiftyProportions() {
        val swipe = 80f
        assertEquals(20f, BlinkFeedScrollMotion.advance(0f, swipe,
            BlinkFeedScrollMotion.TOP_PER_POST, 200f), 0.001f)
        assertEquals(50f, BlinkFeedScrollMotion.advance(0f, swipe,
            BlinkFeedScrollMotion.NAV_PER_POST, 50f), 0.001f)
    }

    @Test fun reversingSameSwipeReturnsBothRailsAndClamps() {
        val top = BlinkFeedScrollMotion.advance(0f, 80f,
            BlinkFeedScrollMotion.TOP_PER_POST, 120f)
        val nav = BlinkFeedScrollMotion.advance(0f, 80f,
            BlinkFeedScrollMotion.NAV_PER_POST, 50f)
        assertEquals(0f, BlinkFeedScrollMotion.advance(top, -80f,
            BlinkFeedScrollMotion.TOP_PER_POST, 120f), 0.001f)
        assertEquals(0f, BlinkFeedScrollMotion.advance(nav, -80f,
            BlinkFeedScrollMotion.NAV_PER_POST, 50f), 0.001f)
        assertEquals(50f, BlinkFeedScrollMotion.advance(nav, 160f,
            BlinkFeedScrollMotion.NAV_PER_POST, 50f), 0.001f)
        assertEquals(0f, BlinkFeedScrollMotion.advance(20f, -200f,
            BlinkFeedScrollMotion.TOP_PER_POST, 120f), 0.001f)
    }
}
