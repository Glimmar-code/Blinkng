package com.example.data.models

data class StudyCircleSummary(
    val id: String,
    val ownerId: String,
    val name: String,
    val description: String,
    val faculty: String,
    val course: String,
    val maxMembers: Int,
    val isPrivate: Boolean,
    val memberCount: Int,
    val isMember: Boolean,
    val isOwner: Boolean,
    val requestId: String? = null,
    val requestStatus: String? = null,
    val createdAt: String = ""
)

data class StudyCircleJoinRequest(
    val requestId: String,
    val circleId: String,
    val circleName: String,
    val requesterId: String,
    val requesterUsername: String,
    val requesterFullName: String,
    val requesterAvatarUrl: String,
    val status: String,
    val createdAt: String
)
