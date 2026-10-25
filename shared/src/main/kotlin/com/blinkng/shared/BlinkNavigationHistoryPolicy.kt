package com.blinkng.shared

/** Small, bounded route stack. State is owned by the authenticated ViewModel session. */
object BlinkNavigationHistoryPolicy {
    const val MAX_DEPTH = 40

    fun <T> push(history: List<T>, current: T, destination: T): List<T> =
        if (current == destination) history
        else (history + current).takeLast(MAX_DEPTH)

    fun <T> pop(history: List<T>): Pair<List<T>, T>? =
        history.lastOrNull()?.let { history.dropLast(1) to it }
}
