package com.example.ui.screens

/**
 * UI request deduplication; the repository remains responsible for server cursors.
 * Use organic content identifiers so sponsored rows and live counters do not
 * provoke repeated database requests when the user reaches the end.
 */
internal fun feedPaginationCursor(ids: List<String>): String =
    ids.lastOrNull { it.isNotBlank() }?.let { last ->
        "${ids.size}:${last}"
    } ?: ""

internal fun shouldRequestFeedPage(
    currentCursor: String,
    previouslyRequestedCursor: String?,
): Boolean = currentCursor != previouslyRequestedCursor
