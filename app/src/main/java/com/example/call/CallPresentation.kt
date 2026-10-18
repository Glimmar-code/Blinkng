package com.example.call

/**
 * Pure presentation rules for BLINK's voice and video calling screen.
 * Keep UI labels consistent without exposing internal WebRTC state to users.
 */
internal object CallPresentation {
    fun elapsed(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val remainingSeconds = safe % 60
        return if (hours > 0) {
            "%02d:%02d:%02d".format(java.util.Locale.US, hours, minutes, remainingSeconds)
        } else {
            "%02d:%02d".format(java.util.Locale.US, minutes, remainingSeconds)
        }
    }

    fun displayStatus(status: String, elapsedSeconds: Int): String =
        if (status == "Connected") "Connected · ${elapsed(elapsedSeconds)}" else status

    fun controlsEnabled(mediaReady: Boolean, incomingRinging: Boolean, ending: Boolean): Boolean =
        mediaReady && !incomingRinging && !ending

    fun iconActionLabel(type: String, on: Boolean): String = when (type) {
        "microphone" -> if (on) "Turn microphone off" else "Turn microphone on"
        "camera" -> if (on) "Turn camera off" else "Turn camera on"
        "speaker" -> if (on) "Turn speaker off" else "Turn speaker on"
        else -> type
    }
}
