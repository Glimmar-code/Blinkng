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
    // A display name is not a stable account identity. Prefer @username
    // when provided; only fall back to legacy author when the handle is absent.
    // Otherwise a different account sharing a display name could be hidden.
    val identity = authorUsername.ifBlank { author }
        .trim().removePrefix("@").lowercase()
    return identity.isNotBlank() && identity in mutedAuthorKeys
}
