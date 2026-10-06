package com.blinkng.shared

enum class ProfileVisibilityScope {
    PUBLIC,
    FOLLOWERS,
    PRIVATE;

    companion object {
        fun fromWire(value: String?): ProfileVisibilityScope =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: PRIVATE
    }
}

fun canViewProfileField(
    scope: ProfileVisibilityScope,
    isOwner: Boolean,
    isFollowing: Boolean
): Boolean = when {
    isOwner -> true
    scope == ProfileVisibilityScope.PUBLIC -> true
    scope == ProfileVisibilityScope.FOLLOWERS -> isFollowing
    else -> false
}

fun normalizeProfileNotificationMode(value: String?): String = when (value?.trim()?.uppercase()) {
    "ALL" -> "ALL"
    "REELS" -> "REELS"
    "IMPORTANT" -> "IMPORTANT"
    else -> "OFF"
}
