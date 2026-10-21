package com.example.call

/**
 * Pure, unit-tested boundaries for mobile call setup.
 *
 * The backend's call timeout remains authoritative when reachable. These guards
 * prevent the Android UI hanging forever if signaling/network reconciliation fails.
 */
internal object CallStartupPolicy {
    const val MAX_SIGNAL_ATTEMPTS = 3
    const val MAX_RINGING_MILLIS = 75_000L

    fun signalRetryDelayMillis(failedAttemptIndex: Int): Long =
        when (failedAttemptIndex.coerceIn(0, MAX_SIGNAL_ATTEMPTS - 1)) {
            0 -> 700L
            else -> 1_500L
        }

    fun shouldFailUnanswered(status: CallStatus?, ringingElapsedMillis: Long): Boolean =
        status == CallStatus.RINGING && ringingElapsedMillis >= MAX_RINGING_MILLIS
}
