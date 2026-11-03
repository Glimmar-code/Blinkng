package com.example.call

/**
 * Bounded, account-scoped retry rules for a hangup or declined call when
 * Android briefly loses network connectivity.
 *
 * This never retries permission failures or attempts to revive terminal calls.
 */
internal object CallEndRecoveryPolicy {
    const val MAX_ATTEMPTS = 6

    enum class Action(val wire: String) {
        END("end"), DECLINE("decline"), FAIL("fail");

        companion object {
            fun fromWire(raw: String?): Action? =
                entries.firstOrNull { it.wire == raw }
        }
    }

    fun shouldRetry(attempt: Int, statusCode: Int?): Boolean =
        attempt < MAX_ATTEMPTS &&
            (statusCode == null || statusCode == 408 || statusCode == 429 ||
                statusCode in 500..599)

    fun canApply(action: Action, status: CallStatus): Boolean = when (action) {
        Action.DECLINE -> status == CallStatus.RINGING
        Action.END, Action.FAIL -> !status.isTerminal
    }
}
