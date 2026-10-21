package com.example.call

/** Bounds unanswered calls and signaling retries; no server state is fabricated. */
internal object CallStartupPolicy {
    const val MAX_RINGING_MS = 75_000L
    const val MAX_SDP_ATTEMPTS = 3

    fun unansweredCallTimedOut(status: CallStatus?, isCaller: Boolean, elapsedMs: Long): Boolean =
        isCaller && status == CallStatus.RINGING && elapsedMs >= MAX_RINGING_MS

    fun sdpRetryDelayMs(attempt: Int): Long =
        700L * (attempt.coerceIn(0, MAX_SDP_ATTEMPTS - 2) + 1)
}
