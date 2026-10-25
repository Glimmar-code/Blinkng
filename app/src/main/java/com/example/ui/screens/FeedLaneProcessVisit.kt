package com.example.ui.screens

/**
 * Preserve Home scroll while moving between screens inside the same Android task,
 * but never resurrect a previously dismissed task's scroll after reopening BLINK.
 */
internal object FeedLaneProcessVisit {
    private val visited = mutableSetOf<String>()

    fun shouldRestore(key: String): Boolean = !visited.add(key)

    fun resetForNewTask() {
        visited.clear()
    }
}
