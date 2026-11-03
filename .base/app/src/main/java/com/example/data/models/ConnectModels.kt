package com.example.data.models

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.ui.graphics.vector.ImageVector

data class CampusPeer(
    val id: String,
    val name: String,
    val username: String,
    val avatarUrl: String,
    val university: String,
    val faculty: String,
    val department: String,
    val level: String,
    val bio: String,
    val interests: List<String>,
    val badge: VerificationBadge = VerificationBadge.NONE,
    val mutualFriends: Int = 3
)

data class RoommateApplicant(
    val id: String,
    val name: String,
    val username: String,
    val avatarUrl: String,
    val university: String,
    val faculty: String,
    val level: String,
    val gender: String, // "Male" | "Female"
    val budget: String,
    val preferredLocation: String,
    val moveInDate: String,
    val bio: String,
    val badge: VerificationBadge = VerificationBadge.NONE
)

data class StudyCircle(
    val id: String,
    val name: String,
    val faculty: String,
    val membersCount: Int,
    val description: String,
    val icon: ImageVector = Icons.Default.Code,
    val iconName: String = "Code",
    val isJoined: Boolean = false
)
