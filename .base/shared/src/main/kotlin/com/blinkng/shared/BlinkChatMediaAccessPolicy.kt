package com.blinkng.shared

/**
 * Defense-in-depth client filter for conversational media browsers.
 * Backend bucket authorization remains mandatory and authoritative.
 */
object BlinkChatMediaAccessPolicy {
    fun mayBrowse(
        viewOnce: Boolean,
        deletedForEveryone: Boolean,
        hasMedia: Boolean
    ): Boolean = !viewOnce && !deletedForEveryone && hasMedia

    fun mayKeepInSavedMedia(
        viewOnce: Boolean,
        deletedForEveryone: Boolean,
        starred: Boolean
    ): Boolean = mayBrowse(viewOnce, deletedForEveryone, starred)
}
