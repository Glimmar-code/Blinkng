package com.blinkng.shared

/**
 * Cross-platform creator analytics for the posts that the client actually loaded.
 * Paid membership controls presentation, not permissions to the public counters.
 * This must not be described as lifetime, retention or unique-audience analytics.
 */
data class BlinkCreatorItem(
    val id: String,
    val caption: String,
    val isReel: Boolean,
    val qualifiedViews: Int,
    val likes: Int,
    val comments: Int,
    val shares: Int,
    val saves: Int?,
    val tags: List<String>,
    val sponsored: Boolean = false,
    val repost: Boolean = false
)

data class BlinkCreatorContentMetric(
    val id: String,
    val title: String,
    val isReel: Boolean,
    val qualifiedViews: Long,
    val likes: Long,
    val comments: Long,
    val shares: Long,
    val saves: Long?,
    val tags: List<String>
) {
    val interactions: Long get() = likes + comments + shares + (saves ?: 0L)
}

data class BlinkCreatorTagMetric(
    val tag: String,
    val contentCount: Int,
    val qualifiedViews: Long,
    val interactions: Long
)

data class BlinkCreatorStudioSummary(
    val contentCount: Int,
    val reelCount: Int,
    val qualifiedViews: Long,
    val likes: Long,
    val comments: Long,
    val shares: Long,
    val knownSaves: Long,
    val contentsWithSaveCount: Int,
    val interactionsPer100Views: Double,
    val topContent: List<BlinkCreatorContentMetric>,
    val topTags: List<BlinkCreatorTagMetric>
) {
    val postCount: Int get() = contentCount - reelCount
}

object BlinkCreatorStudioPolicy {
    fun summarize(posts: List<BlinkCreatorItem>): BlinkCreatorStudioSummary {
        val metrics = posts.asSequence()
            .filter { it.id.isNotBlank() && !it.sponsored && !it.repost }
            .distinctBy { it.id }
            .map { post ->
                BlinkCreatorContentMetric(
                    id = post.id,
                    title = post.caption.trim().replace(Regex("\\s+"), " ").take(72)
                        .ifBlank { if (post.isReel) "Reel" else "Post" },
                    isReel = post.isReel,
                    qualifiedViews = post.qualifiedViews.coerceAtLeast(0).toLong(),
                    likes = post.likes.coerceAtLeast(0).toLong(),
                    comments = post.comments.coerceAtLeast(0).toLong(),
                    shares = post.shares.coerceAtLeast(0).toLong(),
                    saves = post.saves?.coerceAtLeast(0)?.toLong(),
                    tags = post.tags.map { it.trim().removePrefix("#").lowercase() }
                        .filter { it.isNotBlank() }.distinct()
                )
            }.toList()
        val views = metrics.sumOf { it.qualifiedViews }
        val likes = metrics.sumOf { it.likes }
        val comments = metrics.sumOf { it.comments }
        val shares = metrics.sumOf { it.shares }
        val knownSaves = metrics.sumOf { it.saves ?: 0L }
        val interactions = likes + comments + shares + knownSaves
        val tags = metrics.flatMap { metric ->
            metric.tags.map { tag -> tag to metric }
        }.groupBy({ it.first }, { it.second }).map { (tag, contents) ->
            BlinkCreatorTagMetric(
                tag = tag,
                contentCount = contents.size,
                qualifiedViews = contents.sumOf { it.qualifiedViews },
                interactions = contents.sumOf { it.interactions }
            )
        }.sortedWith(
            compareByDescending<BlinkCreatorTagMetric> { it.qualifiedViews }
                .thenByDescending { it.interactions }
                .thenBy { it.tag }
        )
        return BlinkCreatorStudioSummary(
            contentCount = metrics.size,
            reelCount = metrics.count { it.isReel },
            qualifiedViews = views,
            likes = likes,
            comments = comments,
            shares = shares,
            knownSaves = knownSaves,
            contentsWithSaveCount = metrics.count { it.saves != null },
            interactionsPer100Views =
                if (views > 0L) interactions.toDouble() * 100.0 / views else 0.0,
            topContent = metrics.sortedWith(
                compareByDescending<BlinkCreatorContentMetric> { it.qualifiedViews }
                    .thenByDescending { it.interactions }
                    .thenBy { it.id }
            ).take(5),
            topTags = tags.take(8)
        )
    }
}
