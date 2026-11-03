package com.blinkng.desktop.data

import com.blinkng.shared.BlinkChatAttachmentKind
import com.blinkng.shared.BlinkChatImageQuality
import com.blinkng.shared.BlinkChatPhotoAlbum
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Per-user desktop photo outbox. Each staged photo and its stable server ID survive
 * app exit or a Windows reboot, and retry when BLINK next opens.
 *
 * This is durable re-queueing, NOT a Windows service: uploads cannot keep running
 * after the desktop process has fully exited.
 */
object DesktopChatImageOutbox {
    data class Record(
        val ownerId: String,
        val conversationId: String,
        val messageId: String,
        val file: File,
        val caption: String,
        val quality: BlinkChatImageQuality,
        val album: BlinkChatPhotoAlbum
    )

    private fun userFolder(ownerId: String): File {
        val uid = UUID.fromString(ownerId).toString()
        val root = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
            ?: System.getProperty("user.home")
        val home = File(root, "BLINK/chat-image-outbox/$uid").apply { mkdirs() }
        return home.canonicalFile
    }

    fun enqueue(
        ownerId: String, conversationId: String, messageId: String, original: File,
        caption: String, quality: BlinkChatImageQuality,
        album: BlinkChatPhotoAlbum = BlinkChatPhotoAlbum.single()
    ): Record {
        UUID.fromString(conversationId)
        val mid = UUID.fromString(messageId).toString()
        val extension = original.extension.lowercase().let {
            when (it) {
                "jpg", "jpeg" -> "jpg"
                "png" -> "png"
                "gif" -> "gif"
                "webp" -> "webp"
                "heic" -> "heic"
                "heif" -> "heif"
                else -> throw IllegalArgumentException("Unsupported picture format.")
            }
        }
        val mime = when (extension) {
            "jpg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            else -> "image/webp"
        }
        BlinkChatAttachmentKind.IMAGE.verify(mime, original.length())
        val folder = userFolder(ownerId)
        val staged = File(folder, "$mid.$extension")
        val temporary = File(folder, "$mid.staging")
        require(!staged.exists() && !File(folder, "$mid.json").exists()) {
            "This upload was already queued."
        }
        Files.copy(original.toPath(), temporary.toPath(), StandardCopyOption.REPLACE_EXISTING)
        require(temporary.length() == original.length()) { "Photo copy was incomplete." }
        try {
            Files.move(temporary.toPath(), staged.toPath(), StandardCopyOption.ATOMIC_MOVE)
            val record = Record(
                ownerId, conversationId, mid, staged, caption.trim().take(1024), quality, album
            )
            val metadata = JSONObject()
                .put("ownerId", ownerId).put("conversationId", conversationId)
                .put("messageId", mid).put("filename", staged.name)
                .put("caption", record.caption).put("quality", quality.name)
                .put("albumId", album.id ?: "")
                .put("albumPosition", album.position).put("albumSize", album.size)
            val sidecar = File(folder, "$mid.json")
            val pending = File(folder, "$mid.json.tmp")
            pending.writeText(metadata.toString(), Charsets.UTF_8)
            Files.move(pending.toPath(), sidecar.toPath(), StandardCopyOption.ATOMIC_MOVE)
            return record
        } catch (failure: Exception) {
            temporary.delete()
            staged.delete()
            throw failure
        }
    }

    fun pending(ownerId: String): List<Record> {
        val folder = userFolder(ownerId)
        return folder.listFiles()?.filter { it.name.endsWith(".json") }?.mapNotNull { info ->
            runCatching {
                val row = JSONObject(info.readText(Charsets.UTF_8))
                require(row.optString("ownerId") == ownerId)
                val mid = UUID.fromString(row.getString("messageId")).toString()
                val conversationId = UUID.fromString(row.getString("conversationId")).toString()
                val selected = File(folder, row.getString("filename")).canonicalFile
                require(selected.parentFile == folder && selected.name.startsWith("$mid.") &&
                    selected.isFile && selected.length() > 0L)
                val albumId = row.optString("albumId")
                val album = if (albumId.isBlank()) BlinkChatPhotoAlbum.single()
                else BlinkChatPhotoAlbum.of(
                    albumId, row.optInt("albumPosition"), row.optInt("albumSize")
                )
                Record(
                    ownerId, conversationId, mid, selected, row.optString("caption").take(1024),
                    BlinkChatImageQuality.valueOf(row.optString("quality")), album
                )
            }.getOrNull()
        }?.sortedBy { it.file.lastModified() }.orEmpty()
    }

    fun progress(ownerId: String, messageId: String, percentage: Int) {
        val id = UUID.fromString(messageId).toString()
        runCatching {
            File(userFolder(ownerId), "$id.progress")
                .writeText(percentage.coerceIn(0, 100).toString(), Charsets.UTF_8)
        }
    }

    fun pendingProgress(ownerId: String): Int? {
        val active = pending(ownerId).firstOrNull() ?: return null
        return runCatching {
            File(userFolder(ownerId), "${active.messageId}.progress")
                .takeIf { it.isFile }?.readText(Charsets.UTF_8)
                ?.trim()?.toIntOrNull()?.coerceIn(0, 100)
        }.getOrNull()
    }

    /** Remove only this signed-out user's staged pictures, never another account's. */
    fun discardOwner(ownerId: String) {
        val folder = userFolder(ownerId)
        folder.listFiles()?.forEach { item ->
            if (item.isFile && item.name != ".transfer.lock") {
                runCatching { item.delete() }
            }
        }
    }

    fun complete(record: Record) {
        val folder = userFolder(record.ownerId)
        File(folder, "${record.messageId}.progress").delete()
        val file = record.file.canonicalFile
        if (file.parentFile != folder) return
        File(folder, "${record.messageId}.json").delete()
        file.delete()
    }
}
