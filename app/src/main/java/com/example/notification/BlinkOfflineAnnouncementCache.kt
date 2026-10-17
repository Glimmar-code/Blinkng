package com.example.notification

import android.content.Context
import com.blinkng.shared.notification.BlinkAnnouncementInboxRules
import com.blinkng.shared.notification.BlinkAnnouncementPreview
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bounded per-account *offline previews* of official notifications already received.
 * Never caches direct messages, full private payloads, tokens or attachments.
 * Supabase remains authoritative for current read/unread state and full details.
 */
object BlinkOfflineAnnouncementCache {
    private const val PREFS = "blink_offline_announcements_v1"

    @Synchronized
    fun read(context: Context, userId: String): List<BlinkAnnouncementPreview> {
        if (userId.isBlank()) return emptyList()
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("account:$userId", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until minOf(array.length(), BlinkAnnouncementInboxRules.MAX_CACHED)) {
                    val item = array.optJSONObject(i) ?: continue
                    BlinkAnnouncementInboxRules.create(
                        id = item.optString("id"),
                        title = item.optString("title"),
                        message = item.optString("message"),
                        publishedAt = item.optString("publishedAt"),
                    )?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun record(
        context: Context,
        userId: String,
        id: String,
        title: String,
        message: String,
        publishedAt: String = "",
    ) {
        if (userId.isBlank()) return
        val preview = BlinkAnnouncementInboxRules.create(id, title, message, publishedAt) ?: return
        val current = read(context, userId)
        val next = BlinkAnnouncementInboxRules.upsert(current, preview)
        val array = JSONArray()
        next.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("message", item.message)
                    .put("publishedAt", item.publishedAt)
            )
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("account:$userId", array.toString()).apply()
    }
}
