package com.blinkng.shared.update

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.DeflaterOutputStream

class BlinkDeltaPatchTest {
    private val release = BlinkReleaseInfo(
        "com.aistudio.blink.appvtwo", 111, "1.0.111", "a".repeat(40),
        "b".repeat(64), "c".repeat(64),
    )

    @Test fun selectAnyOfTenHistoricalSources() {
        val rows = (101..110).joinToString("\n") { code ->
            "base=$code,${"d".repeat(64)},delta-$code.bdp,${"e".repeat(64)},2048"
        }
        val manifest = "BLINK-DELTA-1\ntargetVersionCode=111\n" +
            "targetApkSha256=${release.apkSha256}\n$rows\n"
        for (installed in 101..110) {
            assertEquals(installed, BlinkDeltaPatch.select(manifest, release, installed)?.baseVersionCode)
        }
        assertNull(BlinkDeltaPatch.select(manifest, release, 100))
        assertNull(BlinkDeltaPatch.select(manifest.replace("targetVersionCode=111",
            "targetVersionCode=110"), release, 101))
        assertNull(BlinkDeltaPatch.select(manifest.replace("delta-103.bdp", "delta-777.bdp"),
            release, 103))
    }

    @Test fun reconstructExactApkFromCopyAndLiteral() {
        val dir = createTempDir(prefix = "blink-delta-")
        try {
            val base = File(dir, "old.apk").apply { writeBytes("ABCDEFghij".toByteArray()) }
            val target = "ABCDEF__ghij".toByteArray()
            val digestFile = File(dir, "expected").apply { writeBytes(target) }
            val bytes = ByteArrayOutputStream()
            val commands = ByteArrayOutputStream()
            DataOutputStream(DeflaterOutputStream(commands)).use { output ->
                output.writeByte(1); output.writeLong(0L); output.writeInt(6)
                output.writeByte(2); output.writeInt(2); output.write("__".toByteArray())
                output.writeByte(1); output.writeLong(6L); output.writeInt(4)
                output.writeByte(0)
            }
            DataOutputStream(bytes).use { output ->
                output.write(byteArrayOf(66, 76, 68, 49))
                output.writeLong(target.size.toLong())
                output.write(commands.toByteArray())
            }
            val patch = File(dir, "patch.bdp").apply { writeBytes(bytes.toByteArray()) }
            val result = File(dir, "reconstructed.apk")
            BlinkDeltaPatch.reconstruct(base, patch, result, BlinkDeltaPatch.sha256(digestFile))
            assertArrayEquals(target, result.readBytes())
            val tampered = File(dir, "tampered.apk")
            try {
                BlinkDeltaPatch.reconstruct(base, patch, tampered, "0".repeat(64))
                fail("Must reject the wrong target fingerprint")
            } catch (_: IllegalArgumentException) {
                assertFalse(tampered.exists())
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
