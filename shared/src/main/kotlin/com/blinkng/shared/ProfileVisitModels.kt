package com.blinkng.shared

/** Data returned by the owner-only profile visitor RPCs.
 * An absent RPC or network error is not the same as an opted-out account.
 */
data class ProfileVisitSettings(
    val participate: Boolean,
    val notifyVisits: Boolean
)

data class ProfileVisitor(
    val id: String,
    val username: String,
    val fullName: String,
    val avatarUrl: String,
    val visitedAt: String
)

data class ProfileVisitHistory(
    val enabled: Boolean,
    val seenThrough: String,
    val visitors: List<ProfileVisitor>
)
