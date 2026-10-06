package com.example.data.models

enum class ProfileNotificationMode {
    OFF,
    ALL,
    REELS,
    IMPORTANT;

    companion object {
        fun fromWire(value: String?): ProfileNotificationMode =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: OFF
    }
}

enum class ProfileConnectionKind {
    FOLLOWERS,
    FOLLOWING
}

data class ProfileFollowerPoint(
    val date: String,
    val followerCount: Int
)
