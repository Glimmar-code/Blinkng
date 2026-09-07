package com.example.auth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

internal object GoogleNonce {
    fun generate(byteLength: Int = 32): String {
        require(byteLength > 0) { "Nonce length must be positive." }
        val bytes = ByteArray(byteLength)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
        )
    }

    fun sha256Hex(rawNonce: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawNonce.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
