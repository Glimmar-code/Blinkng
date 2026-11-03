package com.example.ui.screens

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BlinkChatPollResultPolicyTest {
    private val id = "b24ef6d1-9085-43c7-99db-6f8848a300a9"

    private fun result(total: Int = 25): JSONObject =
        JSONObject().put("message_id", id)
            .put("question", "Which design do you prefer?")
            .put("options", JSONArray().put(
                JSONObject().put("label", "Design 1").put("votes", 7)
            ).put(
                JSONObject().put("label", "Design 2").put("votes", 15)
            ).put(
                JSONObject().put("label", "Design 3").put("votes", 3)
            ))
            .put("total_votes", total)
            .put("my_vote", 1)
            .put("closed", false)

    @Test fun validServerResultDisplaysOnlyActualCounts() {
        val parsed = BlinkChatPollResultPolicy.parse(result(), id)
        assertNotNull(parsed)
        assertEquals(listOf(28, 60, 12), (0..2).map { parsed!!.percent(it) })
        assertEquals(25, parsed!!.totalVotes)
        assertEquals(1, parsed.myVote)
    }

    @Test fun absentOrContradictoryResultNeverBecomesZeroPercent() {
        assertNull(BlinkChatPollResultPolicy.parse(result().removeField("total_votes"), id))
        assertNull(BlinkChatPollResultPolicy.parse(result(26), id))
        assertNull(BlinkChatPollResultPolicy.parse(result().put("message_id", "different"), id))
        assertNull(BlinkChatPollResultPolicy.parse(result().put("options", JSONArray()), id))
        assertNull(BlinkChatPollResultPolicy.parse(result().put("my_vote", 19), id))
    }

    @Test fun zeroVotesIsValidOnlyWhenOptionsAreActuallyZero() {
        val payload = result(0)
        val options = payload.getJSONArray("options")
        for (index in 0 until options.length()) options.getJSONObject(index).put("votes", 0)
        payload.put("my_vote", JSONObject.NULL)
        val parsed = BlinkChatPollResultPolicy.parse(payload, id)
        assertNotNull(parsed)
        assertEquals(listOf(0, 0, 0), (0..2).map { parsed!!.percent(it) })
        assertNull(parsed!!.myVote)
    }

    private fun JSONObject.removeField(key: String): JSONObject = apply { remove(key) }
}
