package com.example.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray

/**
 * Durable per-account drafts for text the user is actively typing.
 *
 * Values are encrypted with an AndroidKeyStore AES key before they are written to
 * private SharedPreferences. Assigning an empty value removes the stored draft, so
 * existing send/submit handlers that clear their field also clear the durable copy.
 */
object PersistentTextDraftStore {
    private const val PREFS = "blink_persistent_text_drafts_v1"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "blink_persistent_text_drafts_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    @Volatile
    private var cachedKey: SecretKey? = null

    private fun accountScope(context: Context): String {
        fun from(name: String): String {
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            return prefs.getString("email", "").orEmpty().trim().lowercase().ifBlank {
                prefs.getString("username", "").orEmpty().trim().removePrefix("@").lowercase()
            }
        }
        return from("blink_auth_prefs").ifBlank { from("blink_user_session") }.ifBlank { "guest" }
    }

    internal fun storageKey(context: Context, key: String, scope: String): String {
        val raw = "${accountScope(context)}|${scope.trim()}|${key.trim()}"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

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
        val bytes = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > 12) { "Invalid encrypted draft" }
        val iv = bytes.copyOfRange(0, 12)
        val encrypted = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }

    internal fun readStorageKey(context: Context, storageKey: String): String? = try {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(storageKey, null)
            ?: return null
        decrypt(raw)
    } catch (_: Exception) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(storageKey).apply()
        null
    }

    internal fun writeStorageKey(context: Context, storageKey: String, value: String) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (value.isEmpty()) {
            editor.remove(storageKey).apply()
        } else {
            runCatching { encrypt(value) }
                .onSuccess { encrypted -> editor.putString(storageKey, encrypted).apply() }
        }
    }
}

@Composable
fun rememberPersistentTextState(
    key: String,
    initialValue: String = "",
    scope: String = ""
): MutableState<String> {
    val context = LocalContext.current.applicationContext
    val storageKey = remember(key, scope) {
        PersistentTextDraftStore.storageKey(context, key, scope)
    }
    val backing = remember(storageKey) {
        mutableStateOf(PersistentTextDraftStore.readStorageKey(context, storageKey) ?: initialValue)
    }
    return remember(storageKey, backing) {
        object : MutableState<String> {
            override var value: String
                get() = backing.value
                set(newValue) {
                    backing.value = newValue
                    PersistentTextDraftStore.writeStorageKey(context, storageKey, newValue)
                }
            override fun component1(): String = value
            override fun component2(): (String) -> Unit = { value = it }
        }
    }
}

@Composable
fun rememberPersistentStringListState(
    key: String,
    initialValue: List<String>,
    scope: String = ""
): MutableState<List<String>> {
    val context = LocalContext.current.applicationContext
    val storageKey = remember(key, scope) {
        PersistentTextDraftStore.storageKey(context, key, scope)
    }
    fun decode(raw: String?): List<String>? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) add(array.optString(i))
            }
        }.getOrNull()
    }
    fun encode(values: List<String>): String {
        val array = JSONArray()
        values.forEach(array::put)
        return array.toString()
    }
    val backing = remember(storageKey) {
        mutableStateOf(decode(PersistentTextDraftStore.readStorageKey(context, storageKey)) ?: initialValue)
    }
    return remember(storageKey, backing) {
        object : MutableState<List<String>> {
            override var value: List<String>
                get() = backing.value
                set(newValue) {
                    backing.value = newValue
                    val hasTypedContent = newValue.any { it.isNotBlank() }
                    PersistentTextDraftStore.writeStorageKey(
                        context,
                        storageKey,
                        if (hasTypedContent) encode(newValue) else ""
                    )
                }
            override fun component1(): List<String> = value
            override fun component2(): (List<String>) -> Unit = { value = it }
        }
    }
}
