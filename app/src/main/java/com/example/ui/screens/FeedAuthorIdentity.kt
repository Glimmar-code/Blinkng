package com.example.ui.screens

import com.example.data.models.FeedPost
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge

/**
 * Resolve ownership using a stable server ID whenever possible. A display name
 * is not an identity: multiple people can share the same name.
 *
 * If a post supplies an ID which isn't in the profile directory, do not guess
 * another user's identity from the handle or visible name.
 */
internal fun findFeedAuthorProfile(
    post: FeedPost,
    profilesById: Map<String, UserProfile>,
    profilesByHandle: Map<String, UserProfile>
): UserProfile? {
    val id = post.authorProfileId.trim()
    if (id.isNotBlank()) return profilesById[id]
    val handle = post.authorUsername.trim().removePrefix("@").lowercase()
    return if (handle.isBlank()) null else profilesByHandle[handle]
}

internal fun resolvedFeedAuthorId(post: FeedPost, profile: UserProfile?): String =
    profile?.id?.trim()?.takeIf { it.isNotBlank() } ?: post.authorProfileId.trim()

/**
 * Current profile verification, including NONE, supersedes an older post's
 * badge snapshot. When no profile is available, keep the server post badge.
 */
internal fun resolvedFeedAuthorBadge(
    post: FeedPost,
    profile: UserProfile?
): VerificationBadge = profile?.verificationBadge ?: when {
    post.verificationBadge != VerificationBadge.NONE -> post.verificationBadge
    post.isVerified -> VerificationBadge.BLUE
    else -> VerificationBadge.NONE
}

internal fun resolvedFeedAuthorAvatar(post: FeedPost, profile: UserProfile?): String =
    profile?.avatarUrl?.takeIf { it.isNotBlank() } ?: post.authorAvatar
