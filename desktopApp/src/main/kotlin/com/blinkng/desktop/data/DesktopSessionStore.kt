package com.blinkng.desktop.data

import com.sun.jna.platform.win32.Crypt32Util
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.prefs.Preferences

/**
 * Persists the Blinkng desktop Supabase session using Windows DPAPI.
 *
 * The encrypted blob is tied to the current Windows user account. Tokens are never
 * written to Preferences in plaintext. If DPAPI is unavailable, persistence is
 * deliberately disabled rather than falling back to insecure plaintext storage.
 */
class DesktopSessionStore {
    private val prefs = Preferences.userRoot().node("com/blinkng/desktop/session")

    fun save(session: DesktopSession) {
        runCatching {
            val json = JSONObject()
                .put("access_token", session.accessToken)
                .put("refresh_token", session.refreshToken)
                .put("user_id", session.userId)
                .put("email", session.email)
                .put("expires_at", session.expiresAtEpochSeconds)
                .toString()
            val protected = Crypt32Util.cryptProtectData(json.toByteArray(StandardCharsets.UTF_8))
            prefs.put(KEY_SESSION, Base64.getEncoder().encodeToString(protected))
            prefs.flush()
        }.onFailure {
            clear()
        }
    }

    fun load(): DesktopSession? = runCatching {
        val encoded = prefs.get(KEY_SESSION, null) ?: return null
        val protected = Base64.getDecoder().decode(encoded)
        val jsonText = String(Crypt32Util.cryptUnprotectData(protected), StandardCharsets.UTF_8)
        val json = JSONObject(jsonText)
        DesktopSession(
            accessToken = json.getString("access_token"),
            refreshToken = json.getString("refresh_token"),
            userId = json.getString("user_id"),
            email = json.optString("email"),
            expiresAtEpochSeconds = json.optLong("expires_at", 0L),
        )
    }.getOrNull()

    fun clear() {
        runCatching {
            prefs.remove(KEY_SESSION)
            prefs.flush()
        }
    }

    companion object {
        private const val KEY_SESSION = "supabase_session_v1"
    }
}
