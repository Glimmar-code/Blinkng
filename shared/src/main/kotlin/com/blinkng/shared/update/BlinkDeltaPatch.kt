package com.blinkng.shared.update

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.InflaterInputStream

/** A patch is usable only for the exact installed APK bytes, not just a matching version. */
data class BlinkDeltaEntry(
    val baseVersionCode: Int,
    val baseSha256: String,
    val filename: String,
    val patchSha256: String,
    val patchBytes: Long,
)

/**
 * Shared Android/Windows-safe BLINK delta contract.
 * A delta recreates the original, *unchanged* publisher-signed APK byte-for-byte.
 * Android validates its archive and signing identity before offering installation.
 */
object BlinkDeltaPatch {
    private val shaPattern = Regex("[0-9a-f]{64}")
    private val filePattern = Regex("delta-[1-9][0-9]{0,9}\\.bdp")
    private const val MAX_MANIFEST = 4096
    private const val MAX_COMMANDS = 1_000_000L
    private const val MAX_PATCH_BYTES = 85L * 1024 * 1024
    private val MAGIC = byteArrayOf(66, 76, 68, 49) // BLD1

    fun select(manifest: String, release: BlinkReleaseInfo, installedCode: Int): BlinkDeltaEntry? {
        if (manifest.isBlank() || manifest.length > MAX_MANIFEST) return null
        val lines = manifest.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size !in 3..13 || lines[0] != "BLINK-DELTA-1" ||
            lines[1] != "targetVersionCode=${release.versionCode}" ||
            lines[2] != "targetApkSha256=${release.apkSha256}"
        ) return null
        val seen = mutableSetOf<Int>()
        var found: BlinkDeltaEntry? = null
        for (line in lines.drop(3)) {
            if (!line.startsWith("base=")) return null
            val parts = line.removePrefix("base=").split(",")
            if (parts.size != 5) return null
            val version = parts[0].toIntOrNull() ?: return null
            val size = parts[4].toLongOrNull() ?: return null
            if (version <= 0 || version >= release.versionCode || !seen.add(version) ||
                !shaPattern.matches(parts[1]) || !filePattern.matches(parts[2]) ||
                parts[2] != "delta-$version.bdp" ||
                !shaPattern.matches(parts[3]) || size !in 1..MAX_PATCH_BYTES) return null
            if (version == installedCode) {
                found = BlinkDeltaEntry(version, parts[1], parts[2], parts[3], size)
            }
        }
        return found
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).buffered().use { stream ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * BLD1: magic(4), targetLength(u64 BE), deflated commands.
     * COPY: 1,u64 base offset,u32 length; INSERT: 2,u32 length, bytes; END: 0.
     * No native patching library/JNI. Output is always bounded, hash-checked and
     * remains outside the installer FileProvider until Android package validation.
     */
    fun reconstruct(base: File, patch: File, output: File, targetSha256: String) {
        require(shaPattern.matches(targetSha256)) { "Invalid target fingerprint" }
        require(base.isFile && patch.isFile && base.length() in 1..BlinkApkUpdatePolicy.MAX_DOWNLOAD_BYTES)
        require(patch.length() in 13..MAX_PATCH_BYTES)
        output.parentFile?.mkdirs()
        try {
            DataInputStream(BufferedInputStream(FileInputStream(patch))).use { header ->
                val magic = ByteArray(4)
                header.readFully(magic)
                require(magic.contentEquals(MAGIC)) { "Invalid patch header" }
                val targetSize = header.readLong()
                require(targetSize in 1..BlinkApkUpdatePolicy.MAX_DOWNLOAD_BYTES) {
                    "Patched APK exceeds limit"
                }
                RandomAccessFile(base, "r").use { original ->
                    DataInputStream(InflaterInputStream(header)).use { instructions ->
                        BufferedOutputStream(FileOutputStream(output)).use { rebuilt ->
                            val hash = MessageDigest.getInstance("SHA-256")
                            val buffer = ByteArray(128 * 1024)
                            var written = 0L
                            var commands = 0L
                            while (true) {
                                require(++commands <= MAX_COMMANDS) { "Too many patch commands" }
                                when (val operation = instructions.readUnsignedByte()) {
                                    0 -> {
                                        require(written == targetSize && instructions.read() == -1) {
                                            "Patched size or trailing command mismatch"
                                        }
                                        rebuilt.flush()
                                        val actual = hash.digest().joinToString("") { "%02x".format(it) }
                                        require(actual == targetSha256) { "Patched APK hash mismatch" }
                                        return
                                    }
                                    1, 2 -> {
                                        val offset = if (operation == 1) instructions.readLong() else 0L
                                        val count = instructions.readInt().toLong() and 0xffffffffL
                                        require(count in 1..(targetSize - written)) { "Invalid patch segment" }
                                        if (operation == 1) {
                                            require(offset >= 0 && offset <= original.length() &&
                                                count <= original.length() - offset) {
                                                "COPY outside installed APK"
                                            }
                                            original.seek(offset)
                                        }
                                        var remaining = count
                                        while (remaining > 0) {
                                            val step = minOf(remaining, buffer.size.toLong()).toInt()
                                            if (operation == 1) {
                                                original.readFully(buffer, 0, step)
                                            } else {
                                                instructions.readFully(buffer, 0, step)
                                            }
                                            rebuilt.write(buffer, 0, step)
                                            hash.update(buffer, 0, step)
                                            remaining -= step
                                        }
                                        written += count
                                    }
                                    else -> throw IllegalArgumentException("Unknown delta command")
                                }
                            }
                        }
                    }
                }
            }
        } catch (error: Exception) {
            output.delete()
            throw error
        }
    }
}
