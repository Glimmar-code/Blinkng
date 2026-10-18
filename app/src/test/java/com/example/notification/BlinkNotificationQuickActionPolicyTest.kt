package com.example.notification

import com.blinkng.shared.notification.BlinkNotificationKind
import com.blinkng.shared.notification.BlinkNotificationQuickAction
import com.blinkng.shared.notification.BlinkNotificationQuickActionPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkNotificationQuickActionPolicyTest {
    private val id = "96523d45-0bb9-4fe9-a2f7-ec72d9874b81"

    @Test fun followsRequireTrustedActorAndMatchingRecipientToMutate() {
        val safe = BlinkNotificationQuickActionPolicy.available(BlinkNotificationKind.FOLLOW, true, senderId=id)
        assertTrue(safe.contains(BlinkNotificationQuickAction.FOLLOW_BACK))
        assertTrue(safe.contains(BlinkNotificationQuickAction.VIEW_PROFILE))
        val mismatch = BlinkNotificationQuickActionPolicy.available(BlinkNotificationKind.FOLLOW, false, senderId=id)
        assertFalse(mismatch.contains(BlinkNotificationQuickAction.FOLLOW_BACK))
        assertFalse(BlinkNotificationQuickActionPolicy.available(BlinkNotificationKind.FOLLOW, true, senderId="null").contains(BlinkNotificationQuickAction.FOLLOW_BACK))
    }

    @Test fun likesDoNotAppearForSomeoneLikingOurOwnPost() {
        val incoming = BlinkNotificationQuickActionPolicy.available(BlinkNotificationKind.LIKE, true, postId=id)
        assertTrue(incoming.contains(BlinkNotificationQuickAction.VIEW_POST))
        assertFalse(incoming.contains(BlinkNotificationQuickAction.LIKE_POST))
        val mention = BlinkNotificationQuickActionPolicy.available(BlinkNotificationKind.MENTION, true, postId=id)
        assertTrue(mention.contains(BlinkNotificationQuickAction.LIKE_POST))
        assertFalse(BlinkNotificationQuickActionPolicy.available(BlinkNotificationKind.MENTION, false, postId=id).contains(BlinkNotificationQuickAction.LIKE_POST))
    }

    @Test fun commentLikeAndMessageReadActionsRequireAccountBoundData() {
        assertTrue(BlinkNotificationQuickActionPolicy.available(BlinkNotificationKind.COMMENT, true, commentId=id).contains(BlinkNotificationQuickAction.LIKE_COMMENT))
        assertFalse(BlinkNotificationQuickActionPolicy.available(BlinkNotificationKind.COMMENT, false, commentId=id).contains(BlinkNotificationQuickAction.LIKE_COMMENT))
        assertTrue(BlinkNotificationQuickActionPolicy.available(BlinkNotificationKind.MESSAGE, true).contains(BlinkNotificationQuickAction.MARK_READ))
        assertFalse(BlinkNotificationQuickActionPolicy.available(BlinkNotificationKind.MESSAGE, false).contains(BlinkNotificationQuickAction.MARK_READ))
    }
}
