package com.example.call

/**
 * Conservative camera capture presets for BLINK video calls.
 *
 * A 720p/30fps front camera preview can overload low-memory phones and
 * metered mobile links. WebRTC can still reduce encoder bitrate dynamically,
 * but these presets cap the work done by the camera from the outset.
 */
internal object CallMediaQualityPolicy {
    data class CaptureProfile(
        val width: Int,
        val height: Int,
        val framesPerSecond: Int
    )

    val LOW = CaptureProfile(360, 640, 20)
    val BALANCED = CaptureProfile(540, 960, 24)
    val HIGH = CaptureProfile(720, 1280, 30)

    fun select(
        isLowRamDevice: Boolean,
        memoryClassMb: Int,
        isMeteredNetwork: Boolean
    ): CaptureProfile = when {
        isLowRamDevice || memoryClassMb < 192 -> LOW
        isMeteredNetwork || memoryClassMb < 384 -> BALANCED
        else -> HIGH
    }

    /** Signal to user-visible calling UI when video cannot start. */
    fun canEnableCamera(trackAvailable: Boolean, videoCall: Boolean): Boolean =
        videoCall && trackAvailable
}
