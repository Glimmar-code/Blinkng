package com.example.notification

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SocialNotificationBurst(
    val count: Int,
    val actors: List<String>
) {
    val actorSummary: String
        get() = when {
            actors.isEmpty() -> ""
            actors.size == 1 -> actors.first()
            actors.size == 2 -> actors.joinToString(" and ")
            else -> actors.take(2).joinToString(", ") + " and others"
        }
}

/**
 * Small local burst window used only for presentation. Server rows stay one-per-activity, while
 * Android updates one notification for repeated activity on the same target instead of vibrating
 * for every like/comment in a burst.
 */
object SocialNotificationBurstStore {
    private const val PREFS = "blink_social_notification_bursts"
    private const val WINDOW_MS = 90_000L
    private const val MAX_ACTORS = 6

    @Synchronized
    fun register(context: Context, key: String, actor: String): SocialNotificationBurst {
        val cleanKey = key.trim()
        if (cleanKey.isBlank()) {
            return SocialNotificationBurst(
                count = 1,
                actors = listOfNotNull(actor.trim().takeIf(String::isNotBlank))
            )
        }

        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val storageKey = "burst_" + cleanKey.hashCode()
        val now = System.currentTimeMillis()
        val previous = runCatching {
            JSONObject(prefs.getString(storageKey, null).orEmpty())
        }.getOrNull()
        val previousAt = previous?.optLong("at", 0L) ?: 0L
        val inWindow = previousAt > 0L && now - previousAt <= WINDOW_MS
        val actors = mutableListOf<String>()
        if (inWindow) {
            val array = previous?.optJSONArray("actors") ?: JSONArray()
            for (i in 0 until array.length()) {
                array.optString(i).trim().takeIf(String::isNotBlank)?.let(actors::add)
            }
        }
        actor.trim().takeIf(String::isNotBlank)?.let { name ->
            actors.removeAll { it.equals(name, ignoreCase = true) }
            actors.add(0, name)
        }
        while (actors.size > MAX_ACTORS) actors.removeAt(actors.lastIndex)

        val count = if (inWindow) (previous?.optInt("count", 0) ?: 0) + 1 else 1
        val payload = JSONObject()
            .put("at", now)
            .put("count", count)
            .put("actors", JSONArray(actors))
        prefs.edit().putString(storageKey, payload.toString()).apply()
        return SocialNotificationBurst(count, actors)
    }
}
