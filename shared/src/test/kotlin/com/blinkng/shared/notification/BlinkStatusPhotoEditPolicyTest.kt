package com.blinkng.shared.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BlinkStatusPhotoEditPolicyTest {
    private val p = BlinkStatusPhotoEditPolicy

    @Test fun portraitCenterCropMaintainsVerticalProportions() {
        val crop = p.portraitCrop(4000, 3000)
        assertEquals(p.Crop(1156, 0, 1687, 3000), crop)
        assertTrue(crop.width.toDouble() / crop.height in 0.562..0.563)
    }

    @Test fun narrowPhotoCropsHeightNotWidth() {
        assertEquals(p.Crop(0, 116, 900, 1600), p.portraitCrop(900, 1832))
        assertEquals(p.Crop(0, 0, 900, 1600), p.portraitCrop(900, 1600))
    }

    @Test fun rotateAndSampleSizeStayBounded() {
        assertEquals(3, p.quarterTurns(-1))
        assertEquals(1, p.quarterTurns(5))
        assertEquals(1, p.decodeSampleSize(1800, 1800))
        assertEquals(4, p.decodeSampleSize(6000, 2000))
        assertFailsWith<IllegalArgumentException> { p.portraitCrop(0, 100) }
    }
}
