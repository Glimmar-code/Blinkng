package com.example.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.data.supabase.SupabaseService
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

/** Stores recently used account metadata and encrypted session tokens. */
object AccountSessionStore {
    private const val PREFS = "blink_recent_accounts"
    private const val AUTH_PREFS = "blink_auth_prefs"
    private const val USER_SESSION_PREFS = "blink_user_session"
    private const val KEY_ACCOUNTS = "accounts"
    private const val KEY_LAST_IDENTIFIER = "last_identifier"
    private const val KEY_REQUIRE_SIGN_IN = "require_sign_in"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "blink_recent_account_tokens_v1"
    private const val MAX_ACCOUNTS = 5
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_EMAIL = "email"
    private const val KEY_FULL_NAME = "full_name"
    private const val KEY_USERNAME = "username"
    private const val KEY_AVATAR = "avatar_url"
    private const val KEY_FACULTY = "faculty"
    private const val KEY_UNIVERSITY = "university"
    private const val KEY_COVER = "cover_url"

    data class Account(
        val userId: String,
        val username: String,
        val fullName: String,
        val email: String,
        val avatarUrl: String,
        val accessToken: String,
        val refreshToken: String,
        val lastUsedAt: Long
    )

    fun recordCurrentSession(context: Context, userId: String, username: String, fullName: String, email: String, avatarUrl: String) {
        val access = SupabaseService.accessToken().orEmpty()
        val refresh = SupabaseService.refreshToken().orEmpty()
        if (userId.isBlank() || username.isBlank() || access.isBlank() || refresh.isBlank()) return
        val updated = load(context).filterNot { it.userId == userId }.toMutableList().apply {
            add(0, Account(userId, username, fullName, email, avatarUrl, access, refresh, System.currentTimeMillis()))
        }.take(MAX_ACCOUNTS)
        save(context, updated)
    }

    /**
     * Copies the currently active Supabase tokens back into the matching saved account.
     * Supabase rotates refresh tokens. Without this sync the account switcher can retain
     * an older one-time refresh token and later report a false "expired" session.
     */
    fun syncCurrentTokens(context: Context) {
        val access = SupabaseService.accessToken().orEmpty()
        val refresh = SupabaseService.refreshToken().orEmpty()
        if (access.isBlank() || refresh.isBlank()) return

        val auth = context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
        val currentEmail = auth.getString(KEY_EMAIL, "").orEmpty().trim()
        val currentUsername = auth.getString(KEY_USERNAME, "").orEmpty().trim().removePrefix("@")
        if (currentEmail.isBlank() && currentUsername.isBlank()) return

        val accounts = load(context)
        var changed = false
        val updated = accounts.map { account ->
            val matches =
                (currentEmail.isNotBlank() && account.email.equals(currentEmail, ignoreCase = true)) ||
                    (currentUsername.isNotBlank() && account.username.equals(currentUsername, ignoreCase = true))
            if (matches && (account.accessToken != access || account.refreshToken != refresh)) {
                changed = true
                account.copy(accessToken = access, refreshToken = refresh)
            } else {
                account
            }
        }
        if (changed) save(context, updated)
    }

    fun list(context: Context): List<Account> = load(context).sortedByDescending { it.lastUsedAt }

    fun rememberIdentifier(context: Context, identifier: String) {
        val clean = identifier.trim()
        if (clean.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_IDENTIFIER, clean)
            .apply()
    }

    fun lastIdentifier(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_IDENTIFIER, "")
            .orEmpty()

    fun setSignInRequired(context: Context, required: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_REQUIRE_SIGN_IN, required)
            .apply()
    }

    fun isSignInRequired(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_REQUIRE_SIGN_IN, false)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ACCOUNTS)
            .apply()
    }

    fun remove(context: Context, userId: String) {
        if (userId.isBlank()) return
        save(context, load(context).filterNot { it.userId == userId })
    }

    fun switchTo(context: Context, account: Account, accessToken: String, refreshToken: String) {
        SupabaseService.saveSession(accessToken, refreshToken)
        rememberIdentifier(context, account.email.ifBlank { account.username })
        setSignInRequired(context, false)
        persistSelectedAccount(context, account)
        val reordered = load(context).filterNot { it.userId == account.userId }.toMutableList().apply {
            add(0, account.copy(accessToken = accessToken, refreshToken = refreshToken, lastUsedAt = System.currentTimeMillis()))
        }
        save(context, reordered.take(MAX_ACCOUNTS))
    }

    /** Keep BlinkViewModel's two legacy auth stores aligned with the selected JWT. */
    internal fun persistSelectedAccount(context: Context, account: Account) {
        listOf(AUTH_PREFS, USER_SESSION_PREFS).forEach { prefsName ->
            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .putString(KEY_EMAIL, account.email)
                .putString(KEY_FULL_NAME, account.fullName.ifBlank { account.username })
                .putString(KEY_USERNAME, account.username)
                .putString(KEY_AVATAR, account.avatarUrl)
                // These values belong to the previous account unless they are fetched
                // again from Supabase after the switch.
                .remove(KEY_FACULTY)
                .remove(KEY_UNIVERSITY)
                .remove(KEY_COVER)
                .apply()
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val combined = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val combined = Base64.decode(value, Base64.NO_WRAP)
        require(combined.size > 12) { "Invalid encrypted token" }
        val iv = combined.copyOfRange(0, 12)
        val ciphertext = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    private fun load(context: Context): List<Account> = try {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACCOUNTS, "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                try {
                    val access = decrypt(o.optString("accessToken"))
                    val refresh = decrypt(o.optString("refreshToken"))
                    add(Account(o.optString("userId"), o.optString("username"), o.optString("fullName"), o.optString("email"), o.optString("avatarUrl"), access, refresh, o.optLong("lastUsedAt", 0L)))
                } catch (_: Exception) { /* discard legacy/plaintext or corrupted credentials */ }
            }
        }
    } catch (_: Exception) { emptyList() }

    private fun save(context: Context, accounts: List<Account>) {
        val array = JSONArray()
        accounts.forEach { a ->
            array.put(JSONObject().apply {
                put("userId", a.userId)
                put("username", a.username)
                put("fullName", a.fullName)
                put("email", a.email)
                put("avatarUrl", a.avatarUrl)
                put("accessToken", encrypt(a.accessToken))
                put("refreshToken", encrypt(a.refreshToken))
                put("lastUsedAt", a.lastUsedAt)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ACCOUNTS, array.toString()).apply()
    }
}
