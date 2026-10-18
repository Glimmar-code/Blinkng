package com.example.call

/**
 * Bounds call connection recovery so a failed ICE transport neither ends a
 * recoverable call immediately nor leaves the UI reconnecting forever.
 * Delays are deterministic so unit tests can verify the policy.
 */
internal object CallConnectionRecoveryPolicy {
    const val ICE_RESTART_ATTEMPTS = 3
    const val SDP_SEND_ATTEMPTS = 3
    const val FINAL_RECOVERY_GRACE_MS = 8_000L

    private val iceRetryDelays = longArrayOf(1_500L, 4_000L, 7_000L)

    fun iceRetryDelayMillis(attempt: Int): Long =
        iceRetryDelays[attempt.coerceIn(0, iceRetryDelays.lastIndex)]

    fun sdpRetryDelayMillis(attempt: Int): Long =
        350L * (attempt.coerceIn(0, SDP_SEND_ATTEMPTS - 1) + 1)

    fun realtimeReconnectDelayMillis(consecutiveFailures: Int): Long {
        val exponent = (consecutiveFailures - 1).coerceIn(0, 3)
        return (1_500L shl exponent).coerceAtMost(12_000L)
    }
}
