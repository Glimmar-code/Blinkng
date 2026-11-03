package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class BlinkPhotoEntryPolicyTest {
    @Test fun firstPhotoTapOpensSelectMediaWithPickerFallback() {
        assertEquals(
            BlinkPhotoEntryAction.OPEN_MEDIA_REVIEW,
            BlinkPhotoEntryPolicy.decide(
                hasVideo = false, hasPoll = false, hasVideoClips = false, hasPhotos = false
            )
        )
    }

    @Test fun existingPhotosOpenReviewWithoutForcingAnotherPicker() {
        assertEquals(
            BlinkPhotoEntryAction.REVIEW_EXISTING,
            BlinkPhotoEntryPolicy.decide(false, false, false, true)
        )
    }

    @Test fun switchingFromPollOrReelRequiresExplicitConfirmation() {
        for (state in listOf(
            listOf(true, false, false, false),
            listOf(false, true, false, false),
            listOf(false, false, true, false),
            listOf(true, true, true, true),
            listOf(false, true, false, true)
        )) {
            assertEquals(
                BlinkPhotoEntryAction.CONFIRM_REPLACEMENT,
                BlinkPhotoEntryPolicy.decide(state[0], state[1], state[2], state[3])
            )
        }
    }

    @Test fun cancellingFirstPhotoPickerRestoresLandingButAddingMoreDoesNot() {
        assertEquals(true, BlinkPhotoEntryPolicy.returnToLandingAfterPicker(true, 0))
        assertEquals(false, BlinkPhotoEntryPolicy.returnToLandingAfterPicker(false, 0))
        assertEquals(false, BlinkPhotoEntryPolicy.returnToLandingAfterPicker(true, 1))
        assertEquals(false, BlinkPhotoEntryPolicy.returnToLandingAfterPicker(true, 10))
    }
}
