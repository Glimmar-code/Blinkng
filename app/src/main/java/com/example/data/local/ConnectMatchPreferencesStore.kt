package com.example.data.local

import android.content.Context
import com.example.data.models.MatchSpinPreferences
import org.json.JSONObject

/** Stores only the signed-in user's optional Connect discovery preferences, not spin results. */
class ConnectMatchPreferencesStore(context: Context) {
    private val store = context.applicationContext.getSharedPreferences(
        "blink_connect_match_preferences", Context.MODE_PRIVATE
    )

    fun load(userId: String?): MatchSpinPreferences? {
        if (userId.isNullOrBlank()) return null
        val serialized = store.getString("preferences_$userId", null) ?: return null
        return runCatching {
            val json = JSONObject(serialized)
            fun optional(key: String) =
                json.optString(key).takeIf { json.has(key) && !json.isNull(key) && it.isNotBlank() }
            MatchSpinPreferences(
                university = optional("university"),
                faculty = optional("faculty"),
                department = optional("department"),
                academicLevel = optional("academicLevel"),
                relationshipStatus = optional("relationshipStatus"),
                connectionIntent = optional("connectionIntent"),
                typePrompt = json.optString("typePrompt"),
                onlineOnly = json.optBoolean("onlineOnly", false)
            )
        }.getOrNull()
    }

    fun save(userId: String?, preferences: MatchSpinPreferences) {
        if (userId.isNullOrBlank()) return
        val json = JSONObject()
            .put("university", preferences.university)
            .put("faculty", preferences.faculty)
            .put("department", preferences.department)
            .put("academicLevel", preferences.academicLevel)
            .put("relationshipStatus", preferences.relationshipStatus)
            .put("connectionIntent", preferences.connectionIntent)
            .put("typePrompt", preferences.typePrompt)
            .put("onlineOnly", preferences.onlineOnly)
        store.edit().putString("preferences_$userId", json.toString()).apply()
    }
}
