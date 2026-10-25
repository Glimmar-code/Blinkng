package com.example.ui.screens

/**
 * Detect *new head-of-feed* IDs before live updates are merged into the stable
 * on-screen list. Pagination adds older posts to the end and must not trigger
 * the "New Posts" pill. Require a shared known anchor so replacement data or
 * account switches cannot be mistaken for newly published content.
 */
internal fun newlyPrependedFeedIds(
    knownIds: Set<String>,
    latestIds: List<String>,
): Set<String> {
    if (knownIds.isEmpty() || latestIds.isEmpty() || latestIds.none { it in knownIds }) {
        return emptySet()
    }
    return latestIds.asSequence()
        .takeWhile { it !in knownIds }
        .filter(String::isNotBlank)
        .toSet()
}
