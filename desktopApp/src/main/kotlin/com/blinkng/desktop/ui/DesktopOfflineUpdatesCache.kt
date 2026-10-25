package com.blinkng.desktop.ui

import com.blinkng.desktop.data.DesktopNotification
import com.blinkng.shared.notification.BlinkAnnouncementInboxRules
import com.blinkng.shared.notification.BlinkAnnouncementPreview
import org.json.JSONObject
import java.security.MessageDigest
import java.util.prefs.Preferences

/**
 * Windows equivalent of Android's account-scoped offline official-update history.
 * Windows Preferences restrict individual values to 8KB, so each sanitized preview
 * is stored separately, with a small ordered index, not a single oversized JSON list.
 */
object DesktopOfflineUpdatesCache {
    private val preferences: Preferences = Preferences.userNodeForPackage(DesktopOfflineUpdatesCache::class.java)

    private fun key(raw: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.take(12).joinToString("") { "%02x".format(it) }
    }

    private fun indexKey(accountId: String) = "offline-index-" + key(accountId)
    private fun itemKey(accountId: String, id: String) = "offline-" + key(accountId) + "-" + key(id)

    @Synchronized
    fun read(accountId: String): List<BlinkAnnouncementPreview> {
        if (accountId.isBlank()) return emptyList()
        val ids = preferences.get(indexKey(accountId), "")
            .lineSequence().filter(String::isNotBlank).take(BlinkAnnouncementInboxRules.MAX_CACHED)
        return ids.mapNotNull { id ->
            runCatching {
                val data = JSONObject(preferences.get(itemKey(accountId, id), ""))
                BlinkAnnouncementInboxRules.create(
                    id = data.optString("id"),
                    title = data.optString("title"),
                    message = data.optString("message"),
                    publishedAt = data.optString("publishedAt"),
                )
            }.getOrNull()
        }.toList()
    }

    @Synchronized
    fun recordFromServer(accountId: String, entries: List<DesktopNotification>) {
        if (accountId.isBlank() || entries.isEmpty()) return
        var cached = read(accountId)
        for (entry in entries.asReversed()) {
            if (!BlinkAnnouncementInboxRules.isOfficialCategory(entry.type, entry.targetType)) continue
            val preview = BlinkAnnouncementInboxRules.create(
                entry.id, entry.text, entry.subText.orEmpty(), entry.createdAt,
            ) ?: continue
            cached = BlinkAnnouncementInboxRules.upsert(cached, preview)
        }
        val currentIds = read(accountId).map { it.id }
        val freshIds = cached.map { it.id }.toSet()
        currentIds.filterNot { it in freshIds }.forEach { preferences.remove(itemKey(accountId, it)) }
        for (entry in cached) {
            val data = JSONObject()
                .put("id", entry.id)
                .put("title", entry.title)
                .put("message", entry.message)
                .put("publishedAt", entry.publishedAt)
            preferences.put(itemKey(accountId, entry.id), data.toString())
        }
        preferences.put(indexKey(accountId), cached.joinToString("\n") { it.id })
    }
}
