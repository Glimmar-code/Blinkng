package com.blinkng.shared.update

/** Public update notification dedupe, shared with Windows-friendly unit tests. */
object BlinkReleaseAlertPolicy {
    fun shouldNotify(installedVersionCode: Int, publishedVersionCode: Int, lastNotifiedCode: Int): Boolean {
        return publishedVersionCode > 0 &&
            publishedVersionCode > installedVersionCode &&
            publishedVersionCode > lastNotifiedCode
    }
}
