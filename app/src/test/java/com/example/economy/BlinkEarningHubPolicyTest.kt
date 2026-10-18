package com.example.economy

import com.blinkng.shared.BlinkDailyMission
import com.blinkng.shared.BlinkEarningHubPolicy
import org.junit.Assert.*
import org.junit.Test

class BlinkEarningHubPolicyTest {
    private fun mission(progress: Int, target: Int, claimed: Boolean) =
        BlinkDailyMission("safe_task", "Task", "Complete an eligible action",
            progress, target, 10, 1, claimed)

    @Test fun missionButtonsDoNotFakeRewards() {
        assertEquals(BlinkEarningHubPolicy.MissionAction.IN_PROGRESS,
            BlinkEarningHubPolicy.action(mission(0, 5, false)))
        assertEquals(BlinkEarningHubPolicy.MissionAction.CLAIM,
            BlinkEarningHubPolicy.action(mission(5, 5, false)))
        assertEquals(BlinkEarningHubPolicy.MissionAction.COMPLETED,
            BlinkEarningHubPolicy.action(mission(5, 5, true)))
    }

    @Test fun noFabricatedEarnings() {
        assertFalse(BlinkEarningHubPolicy.REFERRAL_COIN_CREDITS_ENABLED)
        assertFalse(BlinkEarningHubPolicy.CASH_WITHDRAWALS_ENABLED)
        assertEquals("+10", BlinkEarningHubPolicy.coinChangeLabel(10L))
        assertEquals("-50", BlinkEarningHubPolicy.coinChangeLabel(-50L))
        assertEquals("Wallet activity", BlinkEarningHubPolicy.readableTransaction("unfamiliar"))
    }
}
