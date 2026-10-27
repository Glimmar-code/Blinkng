package com.example.call

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.webrtc.PeerConnectionFactory

/**
 * This test must run against the R8-minified rtcSmoke build type, not debug.
 * WebRTC JNI can SIGABRT before any Kotlin exception handler runs when the
 * packaged native library cannot find its non-obfuscated org.webrtc classes.
 */
@RunWith(AndroidJUnit4::class)
class WebRtcJniReleaseSmokeTest {
    @Test
    fun nativeLibraryInitializesAndCreatesFactoryAfterR8Shrinking() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull(context.classLoader.loadClass("org.webrtc.NativeLibrary"))
        assertNotNull(context.classLoader.loadClass("org.webrtc.PeerConnectionFactory"))
        // The native Chromium JNI Zero bridge is not referenced by ordinary Kotlin
        // calls; without an explicit keep rule R8 can remove it and JNI_OnLoad aborts.
        assertNotNull(context.classLoader.loadClass("org.jni_zero.JniZero"))

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        // JNI_OnLoad is not a sufficient check on its own: exercise factory
        // creation to verify Java/native API compatibility in the same APK.
        val factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        assertNotNull(factory)
        factory.dispose()
    }
}
