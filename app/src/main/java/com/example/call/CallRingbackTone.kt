package com.example.call

import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Local caller-side ringback feedback. It is deliberately separate from incoming
 * notification ringtone playback and stops as soon as the remote party answers,
 * the call fails, or the activity is destroyed.
 */
class CallRingbackTone {
    private var toneGenerator: ToneGenerator? = null
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        toneGenerator = runCatching { ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70) }
            .getOrNull()
        if (toneGenerator == null) return

        job = scope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                runCatching { toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1_000) }
                delay(2_000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { toneGenerator?.stopTone() }
        runCatching { toneGenerator?.release() }
        toneGenerator = null
    }
}
