package com.example.performance

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.Choreographer
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Low-overhead, content-free feed scroll telemetry.
 *
 * It records only aggregate frame timing for sampled scroll sessions. No post IDs,
 * usernames, search text, media URLs, screen content or interaction payloads are logged.
 */
class FeedScrollPerformanceMonitor(context: Context) : Choreographer.FrameCallback {
    private val appContext = context.applicationContext
    private val choreographer = Choreographer.getInstance()

    private var running = false
    private var sessionCounter = 0
    private var startMs = 0L
    private var lastFrameNs = 0L
    private var frameCount = 0
    private var slowFrames = 0
    private var verySlowFrames = 0

    fun start() {
        if (running) return
        running = true
        startMs = SystemClock.elapsedRealtime()
        lastFrameNs = 0L
        frameCount = 0
        slowFrames = 0
        verySlowFrames = 0
        choreographer.postFrameCallback(this)
    }

    fun stop() {
        if (!running) return
        running = false
        choreographer.removeFrameCallback(this)

        val durationMs = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(0L)
        sessionCounter += 1

        // Avoid event noise from taps/tiny drags and sample one in four meaningful sessions.
        if (durationMs < 750L || frameCount < 20 || sessionCounter % 4 != 0) return

        runCatching {
            FirebaseAnalytics.getInstance(appContext).logEvent(
                "feed_scroll_perf",
                Bundle().apply {
                    putLong("duration_ms", durationMs)
                    putLong("frame_count", frameCount.toLong())
                    putLong("slow_frames", slowFrames.toLong())
                    putLong("very_slow_frames", verySlowFrames.toLong())
                }
            )
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return

        if (lastFrameNs > 0L) {
            val deltaNs = (frameTimeNanos - lastFrameNs).coerceAtLeast(0L)
            frameCount += 1
            if (deltaNs >= 24_000_000L) slowFrames += 1
            if (deltaNs >= 50_000_000L) verySlowFrames += 1
        }
        lastFrameNs = frameTimeNanos
        choreographer.postFrameCallback(this)
    }
}
