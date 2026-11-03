package com.example

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sharing.ContestVoteRoute
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * User chooses Vote after opening their candidate. No parser, storage, or
 * install continuation step is allowed to submit a vote automatically.
 */
@RunWith(AndroidJUnit4::class)
class ContestVoteRouteFlowTest {
    private val contest = "11111111-1111-4111-8111-111111111111"
    private val candidate = "22222222-2222-4222-8222-222222222222"

    @Test fun sharedLinkRetainsCandidateThroughLoginAndRejectsImpostors() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ContestVoteRoute.clear(context)
        val link = Uri.parse("https://blink.com.ng/contest/" + contest + "/" + candidate)
        val expected = ContestVoteRoute.parse(link)
        assertNotNull(expected)
        assertEquals(contest, expected!!.contestId)
        assertEquals(candidate, expected.entryId)
        ContestVoteRoute.store(context, expected)
        // Simulates reading the invitation again after a login/signup transition.
        val resumed = ContestVoteRoute.pending(context)
        assertEquals(expected, resumed)
        assertNull(ContestVoteRoute.parse(Uri.parse(
            "https://blink.com.ng.evil.example/contest/" + contest + "/" + candidate)))
        assertNull(ContestVoteRoute.parse(Uri.parse(
            "https://blink.com.ng/contest/" + contest + "/" + candidate + "/other")))
        assertNull(ContestVoteRoute.parse(Uri.parse(
            "https://random.example/contest/" + contest + "/" + candidate)))
        ContestVoteRoute.clear(context)
        assertNull(ContestVoteRoute.pending(context))
    }
}
