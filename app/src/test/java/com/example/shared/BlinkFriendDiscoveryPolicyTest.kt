package com.example.shared

import com.blinkng.shared.discovery.BlinkFriendDiscoveryPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkFriendDiscoveryPolicyTest {
    @Test fun contactsNeverSyncWithoutUserOptInBackend() {
        assertFalse(BlinkFriendDiscoveryPolicy.CONTACT_MATCHING_ENABLED)
    }

    @Test fun campusSuggestionsUseOnlyActualVisibleProfiles() {
        val records = listOf(
            BlinkFriendDiscoveryPolicy.Candidate("me", "self", "FUTA"),
            BlinkFriendDiscoveryPolicy.Candidate("a", "Zara", "futa"),
            BlinkFriendDiscoveryPolicy.Candidate("b", "Anna", "FUTA"),
            BlinkFriendDiscoveryPolicy.Candidate("c", "Other", "UNILAG"),
            BlinkFriendDiscoveryPolicy.Candidate("a", "Zara", "FUTA"),
        )
        val names = BlinkFriendDiscoveryPolicy.campusSuggestions("me", " FUTA ", records).map { it.username }
        assertEquals(listOf("Anna", "Zara"), names)
        assertTrue(BlinkFriendDiscoveryPolicy.campusSuggestions("me", null, records).isEmpty())
        assertTrue(BlinkFriendDiscoveryPolicy.campusSuggestions("me", "null", records).isEmpty())
    }

    @Test fun invitesDoNotReferencePersonalContacts() {
        assertEquals(
            "Join me on BLINK! Find your campus community and connect with friends: https://www.blink.com.ng",
            BlinkFriendDiscoveryPolicy.inviteMessage(" https://www.blink.com.ng "),
        )
    }
}
