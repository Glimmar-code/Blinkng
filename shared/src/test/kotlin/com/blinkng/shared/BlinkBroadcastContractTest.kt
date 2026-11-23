package com.blinkng.shared

import com.blinkng.shared.broadcast.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.time.Instant

class BlinkBroadcastContractTest {
    private val starts = Instant.parse("2026-01-01T10:00:00Z")

    private fun validDraft(
        type: BroadcastType = BroadcastType.ANNOUNCEMENT,
        audience: BroadcastAudienceFilter = BroadcastAudienceFilter(BroadcastAudienceKind.CAMPUS),
        rules: BroadcastRules = BroadcastRules(),
    ) = BroadcastDraft(
        type = type,
        title = "Campus update",
        body = "A short, readable broadcast.",
        startsAt = starts,
        endsAt = starts.plusSeconds(3600),
        audience = audience,
        rules = rules,
    )

    @Test
    fun everyoneAudienceRequiresExplicitConfirmation() {
        val draft = validDraft(audience = BroadcastAudienceFilter(BroadcastAudienceKind.EVERYONE))
        assertEquals(listOf("confirm_everyone"), BlinkBroadcastValidation.validate(draft))
        assertTrue(BlinkBroadcastValidation.validate(draft, "CONFIRM").isEmpty())
    }

    @Test
    fun campaignAndPromotionRulesAreValidated() {
        assertTrue("campaign_rules" in BlinkBroadcastValidation.validate(validDraft(BroadcastType.CAMPAIGN)))
        val campaignRules = BroadcastRules(goalType = CampaignGoalType.INVITES, target = 5)
        assertTrue(BlinkBroadcastValidation.validate(
            validDraft(BroadcastType.CAMPAIGN, rules = campaignRules)
        ).isEmpty())

        assertTrue("promo_rules" in BlinkBroadcastValidation.validate(validDraft(BroadcastType.PROMO)))
        val promoRules = BroadcastRules(
            rewardType = PromotionRewardType.COINS,
            rewardAmount = 20,
            perUserLimit = 1,
        )
        assertTrue(BlinkBroadcastValidation.validate(
            validDraft(BroadcastType.PROMO, rules = promoRules)
        ).isEmpty())
    }

    @Test
    fun deepLinksMapToAppRoutesAndUnknownLinksFallBackHome() {
        assertEquals("spin", BlinkBroadcastRoutes.resolveDeepLink("blink://spin"))
        assertEquals("market", BlinkBroadcastRoutes.resolveDeepLink("blink://market"))
        assertEquals("wallet", BlinkBroadcastRoutes.resolveDeepLink("blink://wallet"))
        assertEquals("broadcasts/campaign/spring-1",
            BlinkBroadcastRoutes.resolveDeepLink("blink://campaign/spring-1"))
        assertEquals("broadcasts/promo/offer-2",
            BlinkBroadcastRoutes.resolveDeepLink("blink://promo/offer-2"))
        assertEquals("profile/person-3",
            BlinkBroadcastRoutes.resolveDeepLink("blink://profile/person-3"))
        assertEquals(BlinkBroadcastRoutes.HOME,
            BlinkBroadcastRoutes.resolveDeepLink("https://example.org/unknown"))
    }
}
