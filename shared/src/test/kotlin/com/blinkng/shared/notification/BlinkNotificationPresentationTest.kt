package com.blinkng.shared.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlinkNotificationPresentationTest {
    @Test fun socialEventsAreNotOfficialJustBecauseTheirTargetIsNotification() {
        assertFalse(BlinkNotificationPresentation.isOfficial("NOTIFICATION", "LIKE", "liked your post"))
        assertFalse(BlinkNotificationPresentation.isOfficial("NOTIFICATION", "coin_received", "You got coins"))
        assertFalse(BlinkNotificationPresentation.isOfficial("post", "follow", "started following you"))
    }
    @Test fun officialEventsStayOfficial() {
        assertTrue(BlinkNotificationPresentation.isOfficial("NOTIFICATION", "system", "New features"))
        assertTrue(BlinkNotificationPresentation.isOfficial("system", null, "BLINK announcement"))
        assertTrue(BlinkNotificationPresentation.isOfficial("notification", null, "Official update"))
    }
    @Test fun allGiftAndContestAliasesMapToAnExpectedPreference() {
        assertEquals(BlinkNotificationKind.COIN, BlinkNotificationKind.fromWire("coins_received"))
        assertEquals(BlinkNotificationKind.COIN, BlinkNotificationKind.fromWire("gift_coins"))
        assertEquals(BlinkNotificationKind.CONTEST, BlinkNotificationKind.fromWire("contest_vote"))
        assertEquals(BlinkNotificationKind.DROP, BlinkNotificationKind.fromWire("drop_reward"))
    }
}
