package com.example.ui.components

/** Preserve usable cached data; distinguish pending requests from empty/error states. */
object BlinkSkeletonPolicy {
    fun shouldShow(isLoading: Boolean, hasContent: Boolean, hasError: Boolean = false): Boolean =
        isLoading && !hasContent && !hasError

    fun placeholderRows(requested: Int): Int = requested.coerceIn(1, 8)
}
