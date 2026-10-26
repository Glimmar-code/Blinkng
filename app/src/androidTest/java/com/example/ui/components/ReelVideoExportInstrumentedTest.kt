package com.example.ui.components

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real API-35 MP4 export: fixture is original generated blank test video, never user data. */
@RunWith(AndroidJUnit4::class)
class ReelVideoExportInstrumentedTest {
    @Test
    fun trimExportsPlayableVideoAndAccessiblePrivateUri() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixture = File(context.cacheDir, "blink_reel_trim_fixture.mp4")
        fixture.writeBytes(Base64.decode(TEST_CLIP, Base64.DEFAULT))
        try {
            val uri = withTimeout(90_000) {
                exportEditedReel(
                    context = context,
                    sourceUri = Uri.fromFile(fixture).toString(),
                    durationMs = 2_000L,
                    spec = ReelEditSpec(startMs = 500L, endMs = 1_500L)
                )
            }
            val output = Uri.parse(uri)
            assertTrue("Export output must use private content URI", output.scheme == "content")
            assertTrue("Export must contain encoded video", (context.contentResolver
                .openAssetFileDescriptor(output, "r")?.use { it.length } ?: 0L) > 128L)
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, output)
                val resultMs = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )!!.toLong()
                assertTrue("Trim changed actual output duration: $resultMs", resultMs in 200L..1700L)
            } finally {
                retriever.release()
            }
        } finally {
            fixture.delete()
        }
    }

    companion object {
        private const val TEST_CLIP = "AAAAIGZ0eXBpc29tAAACAGlzb21pc28yYXZjMW1wNDEAAAMybW9vdgAAAGxtdmhkAAAAAAAAAAAAAAAAAAAD6AAAB9AAAQAAAQAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgAAAl10cmFrAAAAXHRraGQAAAADAAAAAAAAAAAAAAABAAAAAAAAB9AAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAABAAAAAAEAAAABgAAAAAAAkZWR0cwAAABxlbHN0AAAAAAAAAAEAAAfQAAAAAAABAAAAAAHVbWRpYQAAACBtZGhkAAAAAAAAAAAAAAAAAABAAAAAgABVxAAAAAAALWhkbHIAAAAAAAAAAHZpZGUAAAAAAAAAAAAAAABWaWRlb0hhbmRsZXIAAAABgG1pbmYAAAAUdm1oZAAAAAEAAAAAAAAAAAAAACRkaW5mAAAAHGRyZWYAAAAAAAAAAQAAAAx1cmwgAAAAAQAAAUBzdGJsAAAAuHN0c2QAAAAAAAAAAQAAAKhhdmMxAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAAEAAYABIAAAASAAAAAAAAAABFUxhdmM2MS4xOS4xMDEgbGlieDI2NAAAAAAAAAAAAAAAGP//AAAALmF2Y0MBQsAK/+EAFmdCwAraENsBEAAAAwAQAAADAEDxImoBAAVozgFnIAAAABBwYXNwAAAAAQAAAAEAAAAUYnRydAAAAAAAAAr0AAAAAAAAABhzdHRzAAAAAAAAAAEAAAAEAAAgAAAAABRzdHNzAAAAAAAAAAEAAAABAAAAHHN0c2MAAAAAAAAAAQAAAAEAAAAEAAAAAQAAACRzdHN6AAAAAAAAAAAAAAAEAAACfAAAAC0AAAAKAAAACgAAABRzdGNvAAAAAAAAAAEAAANiAAAAYXVkdGEAAABZbWV0YQAAAAAAAAAhaGRscgAAAAAAAAAAbWRpcmFwcGwAAAAAAAAAAAAAAAAsaWxzdAAAACSpdG9vAAAAHGRhdGEAAAABAAAAAExhdmY2MS43LjEwMwAAAAhmcmVlAAACxW1kYXQAAAJTBgX//0/cRem95tlIt5Ys2CDZI+7veDI2NCAtIGNvcmUgMTY0IHIzMTA4IDMxZTE5ZjkgLSBILjI2NC9NUEVHLTQgQVZDIGNvZGVjIC0gQ29weWxlZnQgMjAwMy0yMDIzIC0gaHR0cDovL3d3dy52aWRlb2xhbi5vcmcveDI2NC5odG1sIC0gb3B0aW9uczogY2FiYWM9MCByZWY9MSBkZWJsb2NrPTA6MDowIGFuYWx5c2U9MDowIG1lPWRpYSBzdWJtZT0wIHBzeT0xIHBzeV9yZD0xLjAwOjAuMDAgbWl4ZWRfcmVmPTAgbWVfcmFuZ2U9MTYgY2hyb21hX21lPTEgdHJlbGxpcz0wIDh4OGRjdD0wIGNxbT0wIGRlYWR6b25lPTIxLDExIGZhc3RfcHNraXA9MSBjaHJvbWFfcXBfb2Zmc2V0PTAgdGhyZWFkcz0zIGxvb2thaGVhZF90aHJlYWRzPTEgc2xpY2VkX3RocmVhZHM9MCBucj0wIGRlY2ltYXRlPTEgaW50ZXJsYWNlZD0wIGJsdXJheV9jb21wYXQ9MCBjb25zdHJhaW5lZF9pbnRyYT0wIGJmcmFtZXM9MCB3ZWlnaHRwPTAga2V5aW50PTI1MCBrZXlpbnRfbWluPTIgc2NlbmVjdXQ9MCBpbnRyYV9yZWZyZXNoPTAgcmM9Y3JmIG1idHJlZT0wIGNyZj00OC4wIHFjb21wPTAuNjAgcXBtaW49MCBxcG1heD02OSBxcHN0ZXA9NCBpcF9yYXRpbz0xLjQwIGFxPTAAgAAAACFliIQ6EYoABeOAAIAEcZOTk666666666666666666668AAAAApQZogFq8Zd3d3d3d3d3eJ8T4nz+fz+fz+fz+fz+fz+fz+fz+fz+fz+fwAAAAGQZpAFqDMAAAABkGaYBegzA=="
    }
}
