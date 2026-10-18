package com.example.notification

/**
 * Profile visit identity is sensitive. The new server RPC records visits only
 * when both accounts have explicitly enabled reciprocal visitor visibility.
 * Legacy create_activity(PROFILE) is intentionally not used by new clients.
 */
object ProfileViewActivityTracker {
    suspend fun recordViewedProfile(profileId: String): Boolean =
        runCatching { BlinkProfileVisitorClient.record(profileId) }.getOrDefault(false)
}
