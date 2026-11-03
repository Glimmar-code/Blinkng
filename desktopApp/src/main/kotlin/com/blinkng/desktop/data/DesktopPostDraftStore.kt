package com.blinkng.desktop.data

import com.sun.jna.platform.win32.Crypt32Util
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

data class DesktopPostDraft(
    val requestId: String = UUID.randomUUID().toString(),
    val kind: String = "Text",
    val text: String = "",
    val photos: List<String> = emptyList(),
    val video: String? = null,
    val question: String = "",
    val options: List<String> = listOf("", ""),
    val durationHours: Int = 24,
    val audience: String = "Everyone",
    val location: String = "",
    val allowComments: Boolean = true,
    val textStyle: String? = null,
    // A failed/ambiguous request must be retried with the same payload and ID.
    val submitted: Boolean = false,
) {
    fun json(): JSONObject = JSONObject().put("requestId", requestId).put("kind", kind)
        .put("text", text).put("photos", JSONArray(photos)).put("video", video ?: JSONObject.NULL)
        .put("question", question).put("options", JSONArray(options)).put("durationHours", durationHours)
        .put("audience", audience).put("location", location).put("allowComments", allowComments)
        .put("textStyle", textStyle ?: JSONObject.NULL).put("submitted", submitted)

    companion object {
        fun fromJson(json: JSONObject): DesktopPostDraft {
            fun strings(key: String): List<String> = json.getJSONArray(key).let { rows ->
                (0 until rows.length()).map { rows.getString(it) }
            }
            UUID.fromString(json.getString("requestId"))
            return DesktopPostDraft(json.getString("requestId"), json.getString("kind"),
                json.getString("text"), strings("photos"), json.optString("video").takeUnless { it == "null" || it.isBlank() },
                json.getString("question"), strings("options"), json.getInt("durationHours"),
                json.getString("audience"), json.getString("location"), json.getBoolean("allowComments"),
                json.optString("textStyle").takeUnless { it == "null" || it.isBlank() }, json.optBoolean("submitted"))
        }
    }
}

/** Account-scoped draft, protected with Windows DPAPI. Never fall back to plaintext. */
class DesktopPostDraftStore(
    private val root: File = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "BLINK/post-drafts"),
    private val protect: (ByteArray) -> ByteArray = { Crypt32Util.cryptProtectData(it) },
    private val unprotect: (ByteArray) -> ByteArray = { Crypt32Util.cryptUnprotectData(it) },
) {
    private fun file(ownerId: String) = File(root, "${UUID.fromString(ownerId)}.draft")

    fun load(ownerId: String): DesktopPostDraft? {
        val source = file(ownerId)
        if (!source.exists()) return null
        require(source.length() in 1..1_048_576) { "Saved draft is invalid; it has been preserved for recovery." }
        return DesktopPostDraft.fromJson(JSONObject(String(unprotect(source.readBytes()), Charsets.UTF_8)))
    }

    fun save(ownerId: String, draft: DesktopPostDraft) {
        val destination = file(ownerId)
        val protected = protect(draft.json().toString().toByteArray(Charsets.UTF_8))
        Files.createDirectories(root.toPath())
        val temporary = Files.createTempFile(root.toPath(), "draft-", ".encrypted")
        try {
            Files.write(temporary, protected)
            Files.move(temporary, destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    fun clear(ownerId: String) { Files.deleteIfExists(file(ownerId).toPath()) }
}
