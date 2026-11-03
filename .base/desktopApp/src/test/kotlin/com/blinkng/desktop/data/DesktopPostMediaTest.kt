package com.blinkng.desktop.data

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID

class DesktopPostMediaTest {
    @TempDir lateinit var root: File
    private val owner = UUID.randomUUID().toString()
    private fun client() = DesktopSupabaseClient(initialSession =
        DesktopSession("test-only", "test-only", owner, "fixture@example.invalid", Long.MAX_VALUE))

    @Test fun uploadsStreamedReelsToTheExistingAccountScopedStoragePolicy() = runBlocking {
        val id = UUID.randomUUID()
        var request: Request? = null
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            request = chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("{}".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val file = File(root, "clip.mp4").apply { writeBytes(ByteArray(128)) }
        val url = DesktopMediaService(client(), http).uploadPostMedia(file, id)
        assertTrue(url.endsWith("/post-media/users/$owner/posts/$id.mp4"))
        assertEquals("/storage/v1/object/post-media/users/$owner/posts/$id.mp4", request!!.url.encodedPath)
        assertEquals("video/mp4", request!!.body!!.contentType().toString())
        assertEquals(128, request!!.body!!.contentLength())
        assertEquals("false", request!!.header("x-upsert"))
    }

    @Test fun anUploadRetryUsesTheSameObjectButNeverIgnoresAuthorizationFailure() = runBlocking {
        val file = File(root, "photo.png").apply { writeBytes(ByteArray(128)) }
        val id = UUID.randomUUID()
        var code = 409
        val paths = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            paths += chain.request().url.encodedPath
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("Fixture")
                .body("{}".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val media = DesktopMediaService(client(), http)
        media.uploadPostMedia(file, id)
        code = 403
        assertThrows(IllegalStateException::class.java) { runBlocking { media.uploadPostMedia(file, id) } }
        assertEquals(paths.first(), paths.last())
    }
}
