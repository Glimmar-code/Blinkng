package com.example.viewmodel

import com.example.data.models.FeedPost

/**
 * Liked and Saved tabs are membership lists, not a history of posts that were
 * once liked/saved. Preserve unrelated entries when toggling or rolling back.
 */
internal fun syncInteractionCollection(
    current: List<FeedPost>,
    target: FeedPost,
    shouldInclude: Boolean,
    mapTarget: (FeedPost) -> FeedPost
): List<FeedPost> {
    if (!shouldInclude) return current.filterNot { it.id == target.id }
    return (current + target)
        .distinctBy { it.id }
        .map { if (it.id == target.id) mapTarget(it) else it }
}
