package com.example.call

/**
 * Client-side protection against stale/out-of-order call snapshots.
 *
 * Supabase remains authoritative, but realtime and REST reconciliation can arrive in a
 * different order on weak networks. Once BLINK observes a later lifecycle state, an older
 * snapshot must never move the UI/media engine backwards.
 */
object CallLifecyclePolicy {
    fun canApply(previous: CallStatus, next: CallStatus): Boolean {
        if (previous == next) return true
        if (previous.isTerminal) return false

        return when (previous) {
            CallStatus.RINGING ->
                next == CallStatus.CONNECTING ||
                    next == CallStatus.CONNECTED ||
                    next.isTerminal

            CallStatus.CONNECTING ->
                next == CallStatus.CONNECTED ||
                    next.isTerminal

            CallStatus.CONNECTED -> next.isTerminal
            else -> false
        }
    }
}
