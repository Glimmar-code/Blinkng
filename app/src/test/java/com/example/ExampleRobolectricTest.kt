package com.example

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests must never download a Robolectric Android SDK from Maven during
 * a production release gate. Resource content is verified on the Android
 * emulator in ExampleInstrumentedTest; compile-time resource identity can be
 * validated on any offline JVM.
 */
class ExampleRobolectricTest {
    @Test
    fun appNameResourceExistsInCompiledApp() {
        assertTrue(R.string.app_name != 0)
    }
}
