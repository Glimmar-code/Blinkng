package com.example.data.local

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.FileProvider
import com.example.data.supabase.SupabaseService
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Device-local, explicitly saved, AES-256-GCM media vault.
 *
 * Ciphertext lives in app-private files (never MediaStore). Each authenticated
 * account uses a different non-exportable Android Keystore key and folder. A
 * separate signed-in account cannot access another account's vault through this
 * API. Files are intentionally NOT cloud synced or backed up.
 *
 * The viewer must never call this for deleted or view-once pictures.
 */
internal object BlinkPrivateMediaVault {
    private const val MAX_ITEMS = 100
    private const val MAX_BYTES = 30L * 1024 * 1024
    private const val PREFIX = "blink_vault_"
    private val magic = byteArrayOf('B'.code.toByte(), 'L'.code.toByte(),
        'V'.code.toByte(), '1'.code.toByte())

    private fun owner(): String = UUID.fromString(
        SupabaseService().getCurrentUserId()
            ?: error("Sign in before using your media vault.")
    ).toString()

    private fun ownerDir(context: Context, uid: String): File =
        File(context.filesDir, "blink-media-vault/$uid")

    private fun key(uid: String): SecretKey {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val alias = PREFIX + hash
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun checkedFile(context: Context, uid: String, itemId: String): File {
        val id = UUID.fromString(itemId).toString()
        val dir = ownerDir(context, uid).canonicalFile
        val file = File(dir, "$id.blv").canonicalFile
        require(file.parentFile == dir) { "Invalid vault item." }
        return file
    }

    suspend fun items(context: Context): List<String> = withContext(Dispatchers.IO) {
        val folder = ownerDir(context, owner())
        folder.listFiles().orEmpty().asSequence()
            .filter { it.isFile && it.extension == "blv" &&
                it.nameWithoutExtension.toUUIDOrNull() != null &&
                it.length() in 33..(MAX_BYTES + 128) }
            .sortedByDescending { it.lastModified() }
            .take(MAX_ITEMS)
            .map { it.nameWithoutExtension }
            .toList()
    }

    private fun String.toUUIDOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

    suspend fun add(context: Context, source: Uri): String = withContext(Dispatchers.IO) {
        val uid = owner()
        val folder = ownerDir(context, uid)
        check(folder.isDirectory || folder.mkdirs()) { "Vault storage unavailable." }
        require(folder.listFiles().orEmpty().count { it.extension == "blv" } < MAX_ITEMS) {
            "Your media vault is full."
        }
        val item = UUID.randomUUID().toString()
        val target = checkedFile(context, uid, item)
        val temp = File.createTempFile("writing-", ".tmp", folder)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12).also(SecureRandom()::nextBytes)
            cipher.init(Cipher.ENCRYPT_MODE, key(uid), GCMParameterSpec(128, iv))
            val sourceStream = context.contentResolver.openInputStream(source)
                ?: error("Photo is unavailable.")
            sourceStream.use { input ->
                FileOutputStream(temp).use { stream ->
                    stream.write(magic)
                    stream.write(iv)
                    CipherOutputStream(stream, cipher).use { encrypted ->
                        val buffer = ByteArray(16384)
                        var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n == -1) break
                            total += n
                            require(total <= MAX_BYTES) { "Picture exceeds vault limit." }
                            encrypted.write(buffer, 0, n)
                        }
                        require(total > 0L) { "Cannot save an empty picture." }
                    }
                }
            }
            check(temp.renameTo(target)) { "Could not finish the encrypted vault save." }
            item
        } finally {
            temp.delete()
        }
    }

    suspend fun open(context: Context, itemId: String): Uri = withContext(Dispatchers.IO) {
        val uid = owner()
        val input = checkedFile(context, uid, itemId)
        require(input.isFile && input.length() in 33..(MAX_BYTES + 128)) {
            "Vault item unavailable."
        }
        val shareDir = File(context.cacheDir, "blink-share").apply {
            check(isDirectory || mkdirs()) { "Could not open private cache." }
        }
        val temporary = File.createTempFile("blink-vault-view-", ".jpg", shareDir)
        try {
            FileInputStream(input).use { stream ->
                val header = ByteArray(4)
                require(stream.read(header) == 4 && header.contentEquals(magic)) {
                    "Unsupported vault format."
                }
                val iv = ByteArray(12)
                require(stream.read(iv) == 12) { "Vault file is incomplete." }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(uid), GCMParameterSpec(128, iv))
                CipherInputStream(stream, cipher).use { decrypted ->
                    FileOutputStream(temporary).use { output ->
                        var total = 0L
                        val buffer = ByteArray(16384)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = decrypted.read(buffer)
                            if (count == -1) break
                            total += count
                            require(total <= MAX_BYTES) { "Decrypted picture is too large." }
                            output.write(buffer, 0, count)
                        }
                        require(total > 0L)
                    }
                }
            }
            FileProvider.getUriForFile(
                context, "${context.packageName}.chatfiles", temporary
            )
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    suspend fun remove(context: Context, itemId: String): Boolean =
        withContext(Dispatchers.IO) {
            checkedFile(context, owner(), itemId).delete()
        }
}
