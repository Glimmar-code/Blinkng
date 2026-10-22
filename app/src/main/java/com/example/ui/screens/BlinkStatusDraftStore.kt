package com.example.ui.screens

import android.content.Context
import com.blinkng.shared.notification.BlinkStatusTextPolicy

/** Text-only drafts stay on this device, private to the signed-in user.
 * Media URIs are deliberately never persisted without durable SAF permissions.
 */
internal object BlinkStatusDraftStore {
    data class Draft(
        val text: String = "",
        val caption: String = "",
        val background: String = BlinkStatusTextPolicy.backgrounds.first(),
        val textMode: Boolean = false
    )

    private fun prefs(context: Context, uid: String) =
        context.applicationContext.getSharedPreferences(
            "blink_status_drafts_${uid.take(64)}", Context.MODE_PRIVATE
        )

    fun load(context: Context, uid: String): Draft {
        if (uid.isBlank()) return Draft()
        val data = prefs(context, uid)
        return Draft(
            text = data.getString("text", "").orEmpty().take(BlinkStatusTextPolicy.MAX_LENGTH),
            caption = data.getString("caption", "").orEmpty().take(500),
            background = BlinkStatusTextPolicy.background(
                data.getString("background", "").orEmpty()
            ),
            textMode = data.getBoolean("text_mode", false)
        )
    }

    fun save(context: Context, uid: String, draft: Draft) {
        if (uid.isBlank()) return
        prefs(context, uid).edit()
            .putString("text", draft.text.take(BlinkStatusTextPolicy.MAX_LENGTH))
            .putString("caption", draft.caption.take(500))
            .putString("background", BlinkStatusTextPolicy.background(draft.background))
            .putBoolean("text_mode", draft.textMode)
            .apply()
    }

    fun clear(context: Context, uid: String) {
        if (uid.isNotBlank()) prefs(context, uid).edit().clear().apply()
    }
}
