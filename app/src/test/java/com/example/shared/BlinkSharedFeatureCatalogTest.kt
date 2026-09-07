package com.example.shared

import com.blinkng.shared.BlinkFeatureCatalog
import com.blinkng.shared.BlinkFeatureId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BlinkSharedFeatureCatalogTest {
    @Test
    fun routeIdsAreUnique() {
        val routeIds = BlinkFeatureCatalog.all.map { it.id.routeId }
        assertEquals(routeIds.size, routeIds.toSet().size)
    }

    @Test
    fun coreCrossPlatformRoutesAreRegistered() {
        listOf(
            BlinkFeatureId.HOME,
            BlinkFeatureId.REELS,
            BlinkFeatureId.CONNECT,
            BlinkFeatureId.MESSAGES,
            BlinkFeatureId.MARKETPLACE,
            BlinkFeatureId.GAMES,
            BlinkFeatureId.NOTIFICATIONS,
            BlinkFeatureId.STORE,
            BlinkFeatureId.LEADERBOARD,
            BlinkFeatureId.PROFILE,
            BlinkFeatureId.ADMIN,
            BlinkFeatureId.SETTINGS,
        ).forEach { feature ->
            assertNotNull(BlinkFeatureCatalog.find(feature.routeId))
        }
    }
}
