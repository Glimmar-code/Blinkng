package com.blinkng.shared.notification

import kotlin.test.Test
import kotlin.test.assertEquals

class BlinkStatusPhotoDecorPolicyTest {
    private val p = BlinkStatusPhotoDecorPolicy

    @Test fun unknownFiltersOrStickerAreNotPersisted() {
        assertEquals(p.ORIGINAL, p.filter("untrusted filter"))
        assertEquals(p.COOL, p.filter(p.COOL))
        assertEquals("", p.sticker("<script>"))
        assertEquals("🎓", p.sticker("🎓"))
    }

    @Test fun draggableStickerPositionsAreAlwaysOnCanvas() {
        assertEquals(p.Position(0.10f, 0.90f), p.position(-123f, 999f))
        assertEquals(p.Position(0.50f, 0.50f), p.position(Float.NaN, Float.POSITIVE_INFINITY))
        assertEquals(p.Position(0.27f, 0.64f), p.position(0.27f, 0.64f))
    }
}
