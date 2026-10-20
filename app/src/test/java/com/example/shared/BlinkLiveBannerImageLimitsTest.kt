package com.example.shared

import com.blinkng.shared.live.BlinkLiveBannerImageLimits as Limits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkLiveBannerImageLimitsTest {
    @Test fun preservesSmallArtwork() {
        assertEquals(900 to 600, Limits.targetSize(900, 600))
        assertEquals(1, Limits.androidSampleSize(900, 600))
        assertTrue(Limits.validateSource(900, 600, 100_000))
    }

    @Test fun resizesLargePhotosWithoutChangingAspectRatio() {
        assertEquals(1600 to 1200, Limits.targetSize(4000, 3000))
        assertEquals(1200 to 1600, Limits.targetSize(3000, 4000))
        assertEquals(4, Limits.androidSampleSize(12000, 9000))
        assertEquals(2, Limits.androidSampleSize(5500, 4000))
    }

    @Test fun rejectsUnsafeImageDimensionsAndBytes() {
        assertFalse(Limits.validateSource(63, 1200, 500))
        assertFalse(Limits.validateSource(12001, 1200, 500))
        assertFalse(Limits.validateSource(11000, 11000, 500))
        assertFalse(Limits.validateSource(4000, 3000, 20_971_521))
        assertTrue(Limits.validateSource(4000, 3000, Limits.MAX_SOURCE_BYTES.toLong()))
    }

    @Test fun uploadLimitRemainsSmallerThanSourceLimit() {
        assertEquals(2 * 1024 * 1024, Limits.MAX_UPLOAD_BYTES)
        assertTrue(Limits.MAX_UPLOAD_BYTES < Limits.MAX_SOURCE_BYTES)
    }
}
