package com.example.data.models

import com.blinkng.shared.BlinkCreatorItem
import com.blinkng.shared.BlinkCreatorStudioPolicy

typealias BlinkCreatorContentMetric = com.blinkng.shared.BlinkCreatorContentMetric
typealias BlinkCreatorTagMetric = com.blinkng.shared.BlinkCreatorTagMetric
typealias BlinkCreatorStudioSummary = com.blinkng.shared.BlinkCreatorStudioSummary

/** Android FeedPost adapter to the same aggregation policy used by Windows. */
object BlinkCreatorStudioAnalytics {
    fun summarize(posts: List<FeedPost>): BlinkCreatorStudioSummary =
        BlinkCreatorStudioPolicy.summarize(posts.map { post ->
            BlinkCreatorItem(
                id = post.id,
                caption = post.text,
                isReel = post.isReel,
                qualifiedViews = post.viewsCount,
                likes = post.likes,
                comments = post.commentsCount,
                shares = post.sharesCount,
                saves = post.savesCount,
                tags = post.tags,
                sponsored = post.isSponsored,
                repost = post.repostedById != null || post.repostId != null
            )
        })
}
