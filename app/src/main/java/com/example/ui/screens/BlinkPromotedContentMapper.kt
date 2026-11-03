package com.example.ui.screens

import com.example.data.models.FeedPost
import com.example.data.models.MarketItem
import com.example.data.models.VerificationBadge
import org.json.JSONArray
import org.json.JSONObject

internal data class BlinkPromotedFeedPlacement(
    val campaignId: String,
    val post: FeedPost,
)

internal data class BlinkPromotedDiscoveryPlacement(
    val campaignId: String,
    val targetType: String,
    val targetId: String,
    val ownerId: String,
    val ownerUsername: String,
    val ownerName: String,
    val ownerAvatar: String,
    val ownerUniversity: String,
    val post: FeedPost?,
    val listing: MarketItem?,
    val profileBio: String?,
)

internal fun parseBlinkPromotedPlacements(payload: JSONObject?): List<BlinkPromotedDiscoveryPlacement> {
    return payload?.optJSONArray("items").blinkObjects().mapNotNull { raw ->
        val campaignId = raw.optString("campaign_id")
        val targetType = raw.optString("target_type").uppercase()
        val targetId = raw.optString("target_id")
        val owner = raw.optJSONObject("owner") ?: JSONObject()
        if (campaignId.isBlank() || targetId.isBlank() || targetType.isBlank()) return@mapNotNull null

        BlinkPromotedDiscoveryPlacement(
            campaignId = campaignId,
            targetType = targetType,
            targetId = targetId,
            ownerId = owner.optString("id"),
            ownerUsername = owner.optString("username"),
            ownerName = owner.optString("full_name").ifBlank { owner.optString("username") },
            ownerAvatar = owner.optString("avatar_url"),
            ownerUniversity = owner.optString("university"),
            post = raw.optJSONObject("post")?.let { parseBlinkPromotedPost(it, owner) },
            listing = raw.optJSONObject("listing")?.let(::parseBlinkPromotedListing),
            profileBio = raw.optJSONObject("profile")?.optString("bio")?.takeIf(String::isNotBlank),
        )
    }
}

internal fun parseBlinkPromotedFeedPlacements(payload: JSONObject?): List<BlinkPromotedFeedPlacement> =
    parseBlinkPromotedPlacements(payload)
        .mapNotNull { item ->
            val post = item.post ?: return@mapNotNull null
            if (post.isReel) return@mapNotNull null
            BlinkPromotedFeedPlacement(item.campaignId, post)
        }

private fun parseBlinkPromotedPost(raw: JSONObject, owner: JSONObject): FeedPost {
    val images = buildList {
        raw.optJSONArray("images").blinkStrings().filter(String::isNotBlank).forEach(::add)
        raw.optString("image_url")
            .takeIf { it.isNotBlank() && it != "null" }
            ?.let(::add)
    }.distinct()

    val badge = runCatching {
        VerificationBadge.valueOf(owner.optString("verification_badge", "NONE").uppercase())
    }.getOrDefault(VerificationBadge.NONE)

    return FeedPost(
        id = raw.optString("id"),
        author = owner.optString("full_name").ifBlank { owner.optString("username") },
        authorAvatar = owner.optString("avatar_url"),
        facultyTag = raw.optString("faculty"),
        isVerified = badge != VerificationBadge.NONE,
        verificationBadge = badge,
        timeAgo = "Promoted",
        text = raw.optString("text").ifBlank { raw.optString("caption") },
        images = images,
        likes = raw.optInt("like_count", 0),
        commentsCount = raw.optInt("comment_count", 0),
        sharesCount = raw.optInt("share_count", 0),
        repostsCount = raw.optInt("repost_count", 0),
        viewsCount = raw.optInt("view_count", 0),
        isReel = raw.optBoolean("is_reel", false),
        videoUrl = raw.optString("video_url").takeIf { it.isNotBlank() && it != "null" },
        audience = raw.optString("audience", "Everyone"),
        category = raw.optString("category", "Campus Life"),
        location = raw.optString("location").takeIf(String::isNotBlank),
        linkUrl = raw.optString("link_url").takeIf(String::isNotBlank),
        allowComments = raw.optBoolean("allow_comments", true),
        hideLikes = raw.optBoolean("hide_likes", false),
        isPinned = raw.optBoolean("is_pinned", false),
        isDisappearing = raw.optBoolean("is_disappearing", false),
        audioTitle = raw.optString("audio_title").takeIf(String::isNotBlank),
        altText = raw.optString("alt_text").takeIf(String::isNotBlank),
        isSponsored = true,
        adLabel = "Promoted",
        createdAt = raw.optString("created_at"),
        authorUsername = owner.optString("username"),
    )
}

private fun parseBlinkPromotedListing(raw: JSONObject): MarketItem {
    val images = buildList {
        raw.optJSONArray("image_urls").blinkStrings().filter(String::isNotBlank).forEach(::add)
        raw.optString("image_url")
            .takeIf { it.isNotBlank() && it != "null" }
            ?.let(::add)
    }.distinct()

    return MarketItem(
        id = raw.optString("id"),
        title = raw.optString("title"),
        price = raw.optLong("price", 0L),
        images = images,
        sellerUsername = raw.optString("seller_username"),
        sellerAvatar = raw.optString("seller_avatar"),
        sellerName = raw.optString("seller_name"),
        sellerPhone = raw.optString("seller_phone"),
        sellerWhatsapp = raw.optString("seller_whatsapp"),
        sellerIsVerified = raw.optBoolean("seller_is_verified", false),
        sellerRating = raw.optDouble("seller_rating", 0.0),
        sellerReviewCount = raw.optInt("seller_review_count", 0),
        university = raw.optString("university"),
        location = raw.optString("location"),
        category = raw.optString("category"),
        condition = raw.optString("condition", "Used"),
        description = raw.optString("description"),
        postedTime = "Promoted",
        isFeatured = true,
        isSold = raw.optBoolean("is_sold", false),
    )
}

private fun JSONArray?.blinkObjects(): List<JSONObject> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) optJSONObject(index)?.let(::add)
    }
}

private fun JSONArray?.blinkStrings(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) optString(index).takeIf(String::isNotBlank)?.let(::add)
    }
}
