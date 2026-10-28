package com.example.auth

import com.example.util.safeString

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** Legacy encrypted remembered login, now identifier-only; passwords must never be persisted. */
object RememberedLoginStore {
    private const val PREFS = "blink_remembered_login_v1"
    private const val VALUE = "credentials"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "blink_remembered_login_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    data class Login(val identifier: String, val password: String = "")

    @Volatile
    private var cachedKey: SecretKey? = null

    private fun key(): SecretKey {
        cachedKey?.let { return it }
        synchronized(this) {
            cachedKey?.let { return it }
            val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let {
                cachedKey = it
                return it
            }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            return generator.generateKey().also { cachedKey = it }
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(
            cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)),
            Base64.NO_WRAP
        )
    }

    private fun decrypt(value: String): String {
        val combined = Base64.decode(value, Base64.NO_WRAP)
        require(combined.size > 12) { "Invalid remembered login" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, combined.copyOfRange(0, 12))
        )
        return String(cipher.doFinal(combined.copyOfRange(12, combined.size)), StandardCharsets.UTF_8)
    }

    /** Retain the old call shape for older callers, but NEVER store the password. */
    @Suppress("UNUSED_PARAMETER")
    fun save(context: Context, identifier: String, password: String) {
        val clean = identifier.trim()
        if (clean.isBlank()) return
        val raw = JSONObject().put("identifier", clean).toString()
        runCatching { encrypt(raw) }
            .onSuccess { encrypted ->
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(VALUE, encrypted)
                    .apply()
            }
    }

    fun load(context: Context): Login? = try {
        val encrypted = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .safeString(VALUE, null)
            ?: return null
        val obj = JSONObject(decrypt(encrypted))
        val identifier = obj.optString("identifier").trim()
        // Rewrite once to remove passwords left by old BLINK versions. Never
        // expose a legacy password to a Composable or restore it into fields.
        if (obj.has("password")) {
            if (identifier.isBlank()) clear(context)
            else save(context, identifier, "")
        }
        Login(identifier).takeIf { it.identifier.isNotBlank() }
    } catch (_: Exception) {
        clear(context)
        null
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(VALUE)
            .apply()
    }
}
