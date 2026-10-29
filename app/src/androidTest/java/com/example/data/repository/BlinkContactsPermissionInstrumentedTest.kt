package com.example.data.repository

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android 15 instrumented privacy regression: a denied permission MUST prevent
 * access to the device address book and must not attempt any network sync.
 *
 * This test does not require a BLINK account, real contacts or production API.
 */
@RunWith(AndroidJUnit4::class)
class BlinkContactsPermissionInstrumentedTest {
    @Test fun deniedContactsPermissionPreventsSyncBeforeReadingAnything() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val permission = Manifest.permission.READ_CONTACTS
        val wasGranted = ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
        try {
            if (wasGranted) {
                instrumentation.uiAutomation.revokeRuntimePermission(context.packageName, permission)
            }
            assertEquals(PackageManager.PERMISSION_DENIED,
                ContextCompat.checkSelfPermission(context, permission))
            val error = runCatching {
                runBlocking { BlinkContactsDiscoveryClient().syncFromDevice(context) }
            }.exceptionOrNull()
            assertTrue("Permission denial must fail before reading contacts", error is IllegalStateException)
            assertTrue(error?.message?.contains("Contacts permission was not granted") == true)
        } finally {
            if (wasGranted) {
                instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
            }
        }
    }
}
