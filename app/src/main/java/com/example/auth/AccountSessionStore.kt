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
    private const val KEY_ACCOUNTS = "accounts"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "blink_recent_account_tokens_v1"
    private const val MAX_ACCOUNTS = 5
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

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

    fun list(context: Context): List<Account> = load(context).sortedByDescending { it.lastUsedAt }

    fun switchTo(context: Context, account: Account, accessToken: String, refreshToken: String) {
        SupabaseService.saveSession(accessToken, refreshToken)
        val reordered = load(context).filterNot { it.userId == account.userId }.toMutableList().apply {
            add(0, account.copy(accessToken = accessToken, refreshToken = refreshToken, lastUsedAt = System.currentTimeMillis()))
        }
        save(context, reordered.take(MAX_ACCOUNTS))
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
