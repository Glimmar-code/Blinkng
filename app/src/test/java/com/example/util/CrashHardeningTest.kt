package com.example.util

import android.app.Application
import android.content.Intent
import com.example.data.models.BlinkStoreCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CrashHardeningTest {

    @Test
    fun `safe preference readers recover values written with older types`() {
        val context = RuntimeEnvironment.getApplication<Application>()
        val prefs = context.getSharedPreferences("crash_hardening_test", 0)
        prefs.edit()
            .clear()
            .putString("int_value", "7")
            .putInt("string_value", 42)
            .putString("boolean_value", "true")
            .putLong("long_value", 99L)
            .commit()

        assertEquals(7, prefs.safeInt("int_value", 0))
        assertEquals("42", prefs.safeString("string_value", null))
        assertTrue(prefs.safeBoolean("boolean_value", false))
        assertEquals(99L, prefs.safeLong("long_value", 0L))
    }

    @Test
    fun `safe activity launcher contains missing activity failures`() {
        val context = RuntimeEnvironment.getApplication<Application>()
        val impossible = Intent().setClassName(
            context.packageName,
            "com.example.this.ActivityDoesNotExist"
        )

        assertFalse(
            context.startActivitySafely(
                impossible,
                failureMessage = "Unable to open test activity."
            )
        )
    }

    @Test
    fun `Blink Store catalog ids stay nonblank and unique`() {
        val ids = BlinkStoreCatalog.items.map { it.id }
        assertTrue(ids.all { it.isNotBlank() })
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ids.contains("blink_vip_10d"))
    }
}
