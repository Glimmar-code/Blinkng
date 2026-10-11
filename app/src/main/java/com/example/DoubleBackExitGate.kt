package com.example

/**
 * Requires a second Back press within the confirmation window before leaving
 * the main application. Monotonic elapsed time is supplied by the Android host.
 */
internal class DoubleBackExitGate(private val windowMillis: Long = 2_000L) {
    private var lastPressMillis: Long? = null

    fun shouldExit(nowMillis: Long): Boolean {
        val previous = lastPressMillis
        if (previous != null && nowMillis >= previous && nowMillis - previous <= windowMillis) {
            lastPressMillis = null
            return true
        }
        lastPressMillis = nowMillis
        return false
    }

    fun reset() {
        lastPressMillis = null
    }
}
