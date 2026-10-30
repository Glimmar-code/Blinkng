package com.example.ui.components

/**
 * Determines what the Create Post Photo tile should do before altering media.
 * Opening a blank review on the first tap strands users with a disabled Next
 * button. A type change must be confirmed before opening the picker.
 */
internal enum class BlinkPhotoEntryAction {
    CONFIRM_REPLACEMENT,
    REVIEW_EXISTING,
    OPEN_SYSTEM_PICKER
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
        else -> BlinkPhotoEntryAction.OPEN_SYSTEM_PICKER
    }
}
