package com.blinkng.desktop.data

import com.blinkng.shared.BlinkPostComposerPolicy
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.UUID

/** Exercises the real client serializer/retry path using an in-process HTTP ledger. */
class DesktopPostPublishingTest {
    private class Ledger : Interceptor {
        val posts = mutableListOf<JSONObject>()
        val polls = mutableListOf<JSONObject>()
        val options = mutableListOf<JSONObject>()
        var calls = 0
        var loseInsertResponse = false
        var failSecondOption = false
        override fun intercept(chain: Interceptor.Chain): Response {
            calls++
            val req = chain.request()
            val table = req.url.encodedPath.substringAfterLast('/')
            val target = when (table) { "feed_posts" -> posts; "polls" -> polls; "poll_options" -> options; else -> null }
            var response = "[]"
            if (target != null) {
                when (req.method) {
                    "GET" -> {
                        val id = req.url.queryParameter("id")?.removePrefix("eq.")
                        val postId = req.url.queryParameter("post_id")?.removePrefix("eq.")
                        val pollId = req.url.queryParameter("poll_id")?.removePrefix("eq.")
                        response = JSONArray(target.filter {
                            (id == null || it.optString("id") == id) &&
                                (postId == null || it.optString("post_id") == postId) &&
                                (pollId == null || it.optString("poll_id") == pollId)
                        }).toString()
                    }
                    "POST" -> {
                        if (table == "poll_options" && options.size == 1 && failSecondOption) {
                            failSecondOption = false
                            throw IOException("Simulated lost connection during option creation")
                        }
                        val row = JSONObject(Buffer().also { req.body!!.writeTo(it) }.readUtf8())
                        if (table == "feed_posts") row.put("created_at", "2026-10-29T09:00:00Z")
                        target.add(row)
                        if (table == "feed_posts" && loseInsertResponse) {
                            loseInsertResponse = false
                            throw IOException("Simulated response loss after insert")
                        }
                        response = JSONArray().put(row).toString()
                    }
                    "PATCH" -> {
                        val body = JSONObject(Buffer().also { req.body!!.writeTo(it) }.readUtf8())
                        target.firstOrNull()?.put("poll_id", body.getString("poll_id"))
                    }
                }
            }
            return Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(response.toResponseBody("application/json".toMediaType())).build()
        }
    }

    private fun client(ledger: Ledger) = DesktopSupabaseClient(
        httpClient = OkHttpClient.Builder().addInterceptor(ledger).build(),
        initialSession = DesktopSession("test-only", "test-only", UUID.randomUUID().toString(), "fixture@example.invalid", Long.MAX_VALUE),
    )

    @Test fun aLostInsertResponseAndRetryNeverCreateASecondPost() = runBlocking {
        val ledger = Ledger().apply { loseInsertResponse = true }
        val client = client(ledger)
        val id = UUID.randomUUID().toString()
        val first = client.createPost("No hashtags are required", clientRequestId = id)
        val retry = client.createPost("No hashtags are required", clientRequestId = id)
        assertEquals(id, first.id)
        assertEquals(first.id, retry.id)
        assertEquals(1, ledger.posts.size)
    }

    @Test fun aPartiallyCreatedPollIsCompletedBeforeSuccessWithoutDuplicateOptions() = runBlocking {
        val ledger = Ledger().apply { failSecondOption = true }
        val client = client(ledger)
        val id = UUID.randomUUID().toString()
        val poll = BlinkPostComposerPolicy.preparePoll("Which design?", listOf("One", "Two"), 24)
        assertThrows(IOException::class.java) { runBlocking { client.createPost("", poll = poll, clientRequestId = id) } }
        assertEquals(1, ledger.options.size)
        val result = client.createPost("", poll = poll, clientRequestId = id)
        assertTrue(result.hasPoll)
        assertEquals(1, ledger.posts.size)
        assertEquals(1, ledger.polls.size)
        assertEquals(2, ledger.options.size)
        assertEquals("2026-10-30T09:00:00Z", ledger.polls.single().getString("expires_at"))
        assertEquals(ledger.polls.single().getString("id"), ledger.posts.single().getString("poll_id"))
    }

    @Test fun privatePostingFailsBeforeAnyNetworkRequest() {
        val ledger = Ledger()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { client(ledger).createPost("Private", audience = "Only me") } }
        assertEquals(0, ledger.calls)
    }

    @Test fun publishesTheValidatedPollInsteadOfBlankOrUntrimmedOptions() = runBlocking {
        val ledger = Ledger()
        val poll = com.blinkng.shared.BlinkFeedPollDraft(" Choose? ", listOf(" Yes ", "No", " "), 24)
        client(ledger).createPost("", poll = poll)
        assertEquals("Choose?", ledger.polls.single().getString("question"))
        assertEquals(listOf("Yes", "No"), ledger.options.map { it.getString("option_text") })
    }
}
