package com.example.ui.screens

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BlinkWalletLedgerTest {
    @Test
    fun walletHistoryNeverInventsMissingTransactionIds() {
        val payload = JSONObject().put("transactions", JSONArray()
            .put(JSONObject().put("id", "tx-positive").put("kind", "reward")
                .put("amount", 30).put("item_name", "Mission reward")
                .put("created_at", "2026-10-24T10:00:00Z"))
            .put(JSONObject().put("id", "tx-negative").put("kind", "purchase")
                .put("amount", -12).put("item_name", "Store item")
                .put("created_at", "2026-10-24T11:00:00Z"))
            .put(JSONObject().put("amount", 100000))
        )
        val rows = decodeWalletLedger(payload)
        assertEquals(2, rows.size)
        assertEquals(listOf(30L, -12L), rows.map { it.amount })
        assertEquals("Mission reward", rows.first().description)
        assertEquals("2026-10-24 10:00", rows.first().createdAt)
    }

    @Test
    fun emptyServerLedgerRemainsEmpty() {
        assertTrue(decodeWalletLedger(JSONObject()).isEmpty())
        assertTrue(decodeWalletLedger(JSONObject().put("transactions", JSONArray())).isEmpty())
    }
}
