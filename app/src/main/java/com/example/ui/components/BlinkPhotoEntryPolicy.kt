package com.example.ui.components

/**
 * Select Media is the first Photo screen in the reference design.
 * Its permission-aware empty state offers a working Android Photo Picker,
 * so an empty gallery never traps users behind a disabled Next button.
 * Changing from an existing Reel or poll still requires confirmation.
 */
internal enum class BlinkPhotoEntryAction {
    CONFIRM_REPLACEMENT,
    REVIEW_EXISTING,
    OPEN_MEDIA_REVIEW
}

internal object BlinkPhotoEntryPolicy {
    fun decide(
        hasVideo: Boolean,
        hasPoll: Boolean,
        hasVideoClips: Boolean,
        hasPhotos: Boolean
    ): BlinkPhotoEntryAction = when {
        hasVideo || hasPoll || hasVideoClips -> BlinkPhotoEntryAction.CONFIRM_REPLACEMENT
        hasPhotos -> BlinkPhotoEntryAction.REVIEW_EXISTING
        else -> BlinkPhotoEntryAction.OPEN_MEDIA_REVIEW
    }

    fun returnToLandingAfterPicker(initialPicker: Boolean, chosenCount: Int): Boolean =
        initialPicker && chosenCount == 0
}
