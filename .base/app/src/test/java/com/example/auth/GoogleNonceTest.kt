package com.example.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleNonceTest {
    @Test
    fun sha256Hex_matchesKnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            GoogleNonce.sha256Hex("abc")
        )
    }
}
