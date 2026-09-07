package com.example.call

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

/**
 * Device-local incoming-call sound preferences.
 *
 * Android 8+ notification-channel sounds are immutable once a channel is created, so the
 * effective channel id includes a compact settings fingerprint. Changing a ringtone creates
 * a new channel and the next incoming call immediately uses the new sound without requiring
 * an app reinstall.
 */
object CallSoundPreferences {
    private const val PREFS = "blink_call_sound_preferences"
    private const val KEY_VOICE_TONE = "voice_tone_uri"
    private const val KEY_VIDEO_TONE = "video_tone_uri"
    private const val KEY_VIBRATE = "call_vibrate"
    private const val KEY_VOICE_RING_ENABLED = "voice_ring_enabled"
    private const val KEY_VIDEO_RING_ENABLED = "video_ring_enabled"

    fun ringtoneUri(context: Context, type: CallType): Uri? {
        if (!ringEnabled(context, type)) return null
        val key = if (type == CallType.VIDEO) KEY_VIDEO_TONE else KEY_VOICE_TONE
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key, null)
            ?.takeIf { it.isNotBlank() }
        return stored?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
    }

    fun setRingtoneUri(context: Context, type: CallType, uri: Uri?) {
        val key = if (type == CallType.VIDEO) KEY_VIDEO_TONE else KEY_VOICE_TONE
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (uri == null) editor.remove(key) else editor.putString(key, uri.toString())
        editor.apply()
    }

    fun ringtoneLabel(context: Context, type: CallType): String {
        if (!ringEnabled(context, type)) return "Silent"
        val uri = ringtoneUri(context, type) ?: return "Silent"
        return runCatching {
            RingtoneManager.getRingtone(context, uri)?.getTitle(context)
        }.getOrNull().orEmpty().ifBlank { "Device ringtone" }
    }

    fun vibrateEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_VIBRATE, true)

    fun setVibrateEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VIBRATE, enabled)
            .apply()
    }

    fun ringEnabled(context: Context, type: CallType): Boolean {
        val key = if (type == CallType.VIDEO) KEY_VIDEO_RING_ENABLED else KEY_VOICE_RING_ENABLED
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(key, true)
    }

    fun setRingEnabled(context: Context, type: CallType, enabled: Boolean) {
        val key = if (type == CallType.VIDEO) KEY_VIDEO_RING_ENABLED else KEY_VOICE_RING_ENABLED
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, enabled)
            .apply()
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    fun channelId(context: Context, type: CallType): String {
        val tone = ringtoneUri(context, type)?.toString().orEmpty()
        val fingerprint = "$tone|${vibrateEnabled(context)}|${ringEnabled(context, type)}"
            .hashCode()
            .toUInt()
            .toString(16)
        val kind = if (type == CallType.VIDEO) "video" else "voice"
        return "blink_incoming_${kind}_$fingerprint"
    }
}
