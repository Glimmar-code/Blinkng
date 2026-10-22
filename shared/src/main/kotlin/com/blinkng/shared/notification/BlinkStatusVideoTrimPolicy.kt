package com.blinkng.shared.notification

/** Validates on-device status-video clipping. Times are always milliseconds. */
object BlinkStatusVideoTrimPolicy {
    const val MAX_CLIP_MS = 30_000L
    const val MIN_CLIP_MS = 1_000L
    const val MAX_INPUT_BYTES = 50L * 1024L * 1024L

    data class Window(val startMs: Long, val endMs: Long) {
        val durationMs: Long get() = endMs - startMs
    }

    fun window(durationMs: Long, requestedStartMs: Long, requestedEndMs: Long = 0): Window {
        require(durationMs >= MIN_CLIP_MS) { "Video must be at least one second long." }
        val start = requestedStartMs.coerceIn(0, durationMs - MIN_CLIP_MS)
        val defaultEnd = (start + MAX_CLIP_MS).coerceAtMost(durationMs)
        val end = (if (requestedEndMs <= 0) defaultEnd else requestedEndMs)
            .coerceIn(start + MIN_CLIP_MS, defaultEnd)
        return Window(start, end)
    }

    fun needsExport(durationMs: Long, window: Window): Boolean =
        window.startMs != 0L || window.endMs < durationMs
}
