package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkStoreJourneysTest {
    @Test fun liveJourneysOnlyReferenceRealStoreGroups() {
        val knownGroups = BlinkStoreProductGroups.all.map { it.id }.toSet()
        assertTrue(BlinkStoreJourneys.live.isNotEmpty())
        BlinkStoreJourneys.live.forEach { journey ->
            assertTrue(journey.id, journey.groupIds.isNotEmpty())
            assertTrue(journey.id, journey.groupIds.all { it in knownGroups })
            assertEquals(journey.groupIds.distinct(), journey.groupIds)
            assertEquals(journey.groupIds, BlinkStoreJourneys.availableGroups(journey.id).map { it.id })
        }
    }

    @Test fun notImplementedPurchasesAreNeverAdvertisedAsLive() {
        listOf("game", "university", "seasonal").forEach { id ->
            assertEquals(BlinkStoreJourneyAvailability.PLANNED, BlinkStoreJourneys.byId(id)?.availability)
            assertTrue(BlinkStoreJourneys.availableGroups(id).isEmpty())
            assertFalse(BlinkStoreJourneys.live.any { it.id == id })
        }
        assertTrue(BlinkStoreJourneys.availableGroups("unknown-journey").isEmpty())
        assertEquals(BlinkStoreProductGroups.all, BlinkStoreJourneys.availableGroups(null))
    }

    @Test fun identityLooksLinkToBrowseableGroupsNotNewProducts() {
        val groups = BlinkStoreProductGroups.all.map { it.id }.toSet()
        BlinkStoreJourneys.looks.forEach { look ->
            assertTrue(look.entryGroupId in look.groupIds)
            assertTrue(look.groupIds.all { it in groups })
        }
        assertEquals(
            BlinkStoreJourneys.all.size,
            BlinkStoreJourneys.all.map { it.id }.distinct().size
        )
    }
}
