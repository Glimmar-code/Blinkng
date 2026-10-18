package com.example.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallPresentationTest {
    @Test fun `duration includes hours and avoids negatives`() {
        assertEquals("00:00", CallPresentation.elapsed(-10))
        assertEquals("00:05", CallPresentation.elapsed(5))
        assertEquals("02:05", CallPresentation.elapsed(125))
        assertEquals("01:01:01", CallPresentation.elapsed(3661))
    }

    @Test fun `connected status always shows duration and failures stay readable`() {
        assertEquals("Connected · 00:00", CallPresentation.displayStatus("Connected", 0))
        assertEquals("Reconnecting…", CallPresentation.displayStatus("Reconnecting…", 5))
        assertEquals("Calling…", CallPresentation.displayStatus("Calling…", 20))
    }

    @Test fun `media controls require active media and unlocked call state`() {
        assertFalse(CallPresentation.controlsEnabled(false, false, false))
        assertFalse(CallPresentation.controlsEnabled(true, true, false))
        assertFalse(CallPresentation.controlsEnabled(true, false, true))
        assertTrue(CallPresentation.controlsEnabled(true, false, false))
    }

    @Test fun `accessibility announcements describe action not ambiguous state`() {
        assertEquals("Turn microphone off", CallPresentation.iconActionLabel("microphone", true))
        assertEquals("Turn microphone on", CallPresentation.iconActionLabel("microphone", false))
        assertEquals("Turn camera off", CallPresentation.iconActionLabel("camera", true))
        assertEquals("Turn speaker on", CallPresentation.iconActionLabel("speaker", false))
    }
}
