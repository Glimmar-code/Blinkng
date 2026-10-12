package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlinkConnectHealthTest {
    @Test fun allFreshHasNoWarning() {
        assertNull(BlinkConnectHealth.warning(emptyList()))
    }

    @Test fun partialFailureExplainsStaleContent() {
        assertEquals(
            "Some Connect features could not refresh (Roommates, Mentors). Showing available or previously loaded information.",
            BlinkConnectHealth.warning(listOf(" Roommates ", "Mentors", "Mentors"))
        )
    }

    @Test fun multipleFailuresAreConcise() {
        val warning = BlinkConnectHealth.warning(listOf("Roommates", "Mentors", "Housing", "Spin"))
        assertTrue(warning!!.contains("previously loaded"))
        assertTrue(!warning.contains("Roommates"))
    }
}
