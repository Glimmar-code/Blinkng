package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlinkPostGrowthTipsMarketRuleTest {
    @Test
    fun marketplaceCategoriesCannotBeUsedForOrdinaryFeedPosts() {
        for (category in listOf("Marketplace", "MARKET", " Buy & Sell ")) {
            assertEquals(
                BlinkPostGrowthTips.marketHint,
                BlinkPostGrowthTips.marketOnlyReason(category, "Campus announcement")
            )
        }
    }

    @Test
    fun ordinaryStudentDiscussionsRemainAllowed() {
        assertNull(BlinkPostGrowthTips.marketOnlyReason("Academic", "What laptop is best for statistics?"))
        assertNull(BlinkPostGrowthTips.marketOnlyReason("Campus Life", "Our class meets after lunch."))
    }

    @Test
    fun unknownMediaWithoutSalesCopyIsNotAutomaticallyCalledAnAdvert() {
        // Image pixels require server-side visual moderation; do not claim OCR here.
        assertNull(BlinkPostGrowthTips.marketOnlyReason("Campus Life", "Campus photo"))
    }

    @Test
    fun categoryRestrictionAppliesToLegacyScheduledPosts() {
        assertEquals(
            BlinkPostGrowthTips.marketHint,
            BlinkPostGrowthTips.marketOnlyReason("Marketplace", "Old scheduled post")
        )
    }
}
