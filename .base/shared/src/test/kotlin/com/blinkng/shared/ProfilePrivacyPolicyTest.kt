package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class ProfilePrivacyPolicyTest {
    @Test
    fun privateFieldsAreOwnerOnly() {
        assertTrue(canViewProfileField(ProfileVisibilityScope.PRIVATE, isOwner = true, isFollowing = false))
        assertFalse(canViewProfileField(ProfileVisibilityScope.PRIVATE, isOwner = false, isFollowing = true))
    }

    @Test
    fun followerFieldsRequireRelationship() {
        assertTrue(canViewProfileField(ProfileVisibilityScope.FOLLOWERS, isOwner = false, isFollowing = true))
        assertFalse(canViewProfileField(ProfileVisibilityScope.FOLLOWERS, isOwner = false, isFollowing = false))
    }

    @Test
    fun notificationModesAreNormalized() {
        assertEquals("REELS", normalizeProfileNotificationMode("reels"))
        assertEquals("OFF", normalizeProfileNotificationMode("anything-else"))
    }
}
