package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class BlinkChatMediaGesturePolicyTest {
    private val threshold = 84f

    @Test fun vertical_swipes_dismiss_in_both_directions() {
        assertEquals(BlinkChatMediaSwipe.DISMISS,
            BlinkChatMediaGesturePolicy.action(2f, 150f, threshold, 1f, true, true))
        assertEquals(BlinkChatMediaSwipe.DISMISS,
            BlinkChatMediaGesturePolicy.action(-2f, -150f, threshold, 1f, true, true))
    }

    @Test fun horizontal_swipes_respect_direction_and_available_media() {
        assertEquals(BlinkChatMediaSwipe.NEXT,
            BlinkChatMediaGesturePolicy.action(-125f, 4f, threshold, 1f, true, true))
        assertEquals(BlinkChatMediaSwipe.PREVIOUS,
            BlinkChatMediaGesturePolicy.action(125f, 4f, threshold, 1f, true, true))
        assertEquals(BlinkChatMediaSwipe.NONE,
            BlinkChatMediaGesturePolicy.action(-125f, 4f, threshold, 1f, true, false))
        assertEquals(BlinkChatMediaSwipe.NONE,
            BlinkChatMediaGesturePolicy.action(125f, 4f, threshold, 1f, false, true))
    }

    @Test fun zoomed_images_pan_instead_of_navigating() {
        assertEquals(BlinkChatMediaSwipe.NONE,
            BlinkChatMediaGesturePolicy.action(0f, -300f, threshold, 2f, true, true))
        assertEquals(BlinkChatMediaSwipe.NONE,
            BlinkChatMediaGesturePolicy.action(-300f, 0f, threshold, 3f, true, true))
    }

    @Test fun diagonal_swipes_and_short_drags_do_not_navigate() {
        assertEquals(BlinkChatMediaSwipe.NONE,
            BlinkChatMediaGesturePolicy.action(95f, 92f, threshold, 1f, true, true))
        assertEquals(BlinkChatMediaSwipe.NONE,
            BlinkChatMediaGesturePolicy.action(20f, 25f, threshold, 1f, true, true))
    }

    @Test fun bad_input_never_causes_navigation() {
        assertEquals(BlinkChatMediaSwipe.NONE,
            BlinkChatMediaGesturePolicy.action(Float.NaN, 200f, threshold, 1f, true, true))
        assertEquals(BlinkChatMediaSwipe.NONE,
            BlinkChatMediaGesturePolicy.action(0f, 200f, 0f, 1f, true, true))
    }

    @Test fun backdrop_reveals_chat_and_is_bounded() {
        assertEquals(1f, BlinkChatMediaGesturePolicy.backdropAlpha(0f, 800f))
        assertEquals(.25f, BlinkChatMediaGesturePolicy.backdropAlpha(-800f, 800f))
        assertEquals(1f, BlinkChatMediaGesturePolicy.backdropAlpha(400f, 0f))
    }
}
