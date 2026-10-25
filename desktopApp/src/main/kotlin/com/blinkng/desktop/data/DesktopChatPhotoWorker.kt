package com.blinkng.desktop.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Headless, per-Windows-user photo transfer process.
 *
 * A separate process keeps uploading after the BLINK window closes. Only the
 * signed-in user's DPAPI-protected session is loaded: no tokens, file paths,
 * or passwords are passed on the command line. A per-user OS file lock avoids
 * competing workers, and each upload uses its persisted stable message UUID.
 *
 * On machines without an executable launcher (some development environments),
 * callers can retain the existing foreground upload fallback.
 */
object DesktopChatPhotoWorker {
    const val FLAG = "--blink-photo-outbox"

    fun start(ownerId: String): Boolean {
        val safeOwner = runCatching { UUID.fromString(ownerId).toString() }.getOrNull()
            ?: return false
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return false
        val executable = ProcessHandle.current().info().command().orElse(null)
            ?: return false
        val binary = File(executable)
        val command = if (binary.name.endsWith(".exe", ignoreCase = true) &&
            !binary.name.equals("java.exe", true) &&
            !binary.name.equals("javaw.exe", true)) {
            listOf(binary.absolutePath, FLAG, safeOwner)
        } else {
            val classPath = System.getProperty("java.class.path").orEmpty()
            if (classPath.isBlank()) return false
            val javaw = File(System.getProperty("java.home"), "bin/javaw.exe")
            listOf(if (javaw.isFile) javaw.absolutePath else executable,
                "-cp", classPath, "com.blinkng.desktop.MainKt", FLAG, safeOwner)
        }
        return runCatching {
            ProcessBuilder(command)
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            true
        }.getOrDefault(false)
    }

    fun run(ownerArgument: String) = runBlocking {
        val owner = runCatching { UUID.fromString(ownerArgument).toString() }.getOrNull()
            ?: return@runBlocking
        val sessionStore = DesktopSessionStore()
        if (sessionStore.load()?.userId != owner) return@runBlocking
        val home = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
            ?: System.getProperty("user.home")
        val lockFile = File(home, "BLINK/chat-image-outbox/$owner/.transfer.lock")
        lockFile.parentFile?.mkdirs()
        FileChannel.open(lockFile.toPath(),
            StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            val lock = runCatching { channel.tryLock() }.getOrNull()
                ?: return@use
            lock.use {
                val client = DesktopSupabaseClient(sessionStore)
                if (client.session?.userId != owner) return@use
                val startAt = Instant.now()
                var idle = 0
                var failures = 0
                // Bound the worker lifetime. Relaunching BLINK or queuing another
                // picture can start a new worker after this process exits.
                while (Duration.between(startAt, Instant.now()).toMinutes() < 90) {
                    if (sessionStore.load()?.userId != owner) break
                    val pending = DesktopChatImageOutbox.pending(owner)
                    if (pending.isEmpty()) {
                        idle++
                        if (idle >= 12) break
                        delay(5_000)
                        continue
                    }
                    idle = 0
                    val next = pending.first()
                    try {
                        client.sendChatImage(
                            next.conversationId, next.file, next.caption, next.quality,
                            clientMessageId = next.messageId,
                            album = next.album,
                            onProgress = { progress ->
                                // Re-read DPAPI state periodically during streaming to
                                // abort immediately if the owner signs out/switches.
                                if (sessionStore.load()?.userId != owner) {
                                    throw IllegalStateException("Account changed during upload.")
                                }
                                DesktopChatImageOutbox.progress(owner, next.messageId, progress)
                            }
                        )
                        if (sessionStore.load()?.userId != owner) break
                        DesktopChatImageOutbox.complete(next)
                        failures = 0
                    } catch (_: Exception) {
                        if (sessionStore.load()?.userId != owner) break
                        failures++
                        if (failures >= 15) break
                        delay((2_000L * (1L shl failures.coerceAtMost(5))).coerceAtMost(60_000L))
                    }
                }
            }
        }
    }
}
