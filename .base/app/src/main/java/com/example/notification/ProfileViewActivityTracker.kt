package com.example.notification

import com.example.data.models.NotificationFilter
import com.example.data.supabase.SupabaseService

/**
 * Records a profile-view activity through the existing authenticated activity RPC.
 * Supabase de-duplicates repeat viewer -> profile notifications server-side.
 */
object ProfileViewActivityTracker {
    private val service = SupabaseService()

    suspend fun recordViewedProfile(username: String): Boolean {
        val cleanUsername = username.trim().removePrefix("@")
        if (cleanUsername.isBlank()) return false

        return service.recordActivity(
            recipientUsername = cleanUsername,
            action = "viewed your profile",
            category = NotificationFilter.ALL,
            targetUsername = cleanUsername,
            targetType = "PROFILE"
        )
    }
}
