package com.example.ui.screens

/**
 * Stable ranked Feed snapshots intentionally retain old rows while the reader
 * scrolls. Explicitly deleted, hidden, or muted content is the exception:
 * it must disappear immediately without waiting for a full Feed refresh.
 */
internal fun shouldHideFrozenFeedPost(
    postId: String,
    author: String,
    authorUsername: String,
    hiddenPostIds: Set<String>,
    mutedAuthorKeys: Set<String>
): Boolean {
    if (postId in hiddenPostIds) return true
    val names = sequenceOf(authorUsername, author)
        .map { it.trim().removePrefix("@").lowercase() }
        .filter(String::isNotBlank)
    return names.any { it in mutedAuthorKeys }
}
