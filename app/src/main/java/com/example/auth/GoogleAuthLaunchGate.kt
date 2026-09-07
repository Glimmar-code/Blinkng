package com.example.auth

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide state for the explicit Google credential flow. */
object GoogleAuthLaunchGate {
    private val started = AtomicBoolean(false)
    private val _inFlight = MutableStateFlow(false)
    val inFlight: StateFlow<Boolean> = _inFlight.asStateFlow()

    fun tryStart(): Boolean {
        if (!started.compareAndSet(false, true)) return false
        _inFlight.value = true
        return true
    }

    fun end() {
        started.set(false)
        _inFlight.value = false
    }
}
