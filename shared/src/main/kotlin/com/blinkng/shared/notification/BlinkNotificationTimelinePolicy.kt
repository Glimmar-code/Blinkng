package com.blinkng.shared.notification

/** Stable cross-platform policy for mirrored activity and canonical notifications. */
object BlinkNotificationTimelinePolicy {
    fun belongsInActivityTimeline(targetType: String?): Boolean =
        !targetType.equals("CHAT", ignoreCase = true)

    fun isOfficialMirror(
        legacyTargetType: String?,
        legacyEntityId: String?,
        canonicalIds: Set<String>
    ): Boolean = legacyTargetType.equals("notification", ignoreCase = true) &&
        !legacyEntityId.isNullOrBlank() && legacyEntityId in canonicalIds

    fun isSameSocialEvent(
        legacyActorId: String?,
        canonicalActorId: String?,
        legacyType: String?,
        canonicalType: String?,
        legacyCreatedAt: String,
        canonicalCreatedAt: String,
        legacyPostId: String?,
        canonicalPostId: String?,
        canonicalTargetType: String?
    ): Boolean {
        if (legacyActorId.isNullOrBlank() || legacyActorId != canonicalActorId) return false
        if (legacyCreatedAt.take(19) != canonicalCreatedAt.take(19)) return false
        fun normalized(value: String?): String = when (value?.uppercase()) {
            "LIKES" -> "LIKE"
            "COMMENTS" -> "COMMENT"
            "BOOKMARK" -> "SAVE"
            else -> value?.uppercase().orEmpty()
        }
        val type = normalized(legacyType)
        if (type != normalized(canonicalType)) return false
        if (legacyPostId != null && legacyPostId == canonicalPostId) return true
        return type == "FOLLOW" && legacyPostId == null && canonicalPostId == null &&
            canonicalTargetType.equals("profile", ignoreCase = true)
    }
}
