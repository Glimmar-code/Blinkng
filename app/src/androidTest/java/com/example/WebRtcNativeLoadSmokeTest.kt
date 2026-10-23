package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.webrtc.PeerConnectionFactory

/**
 * Exercises actual libjingle_peerconnection_so.so JNI_OnLoad on Android 15.
 * The minified release separately gets a DEX/ARM64 retention check in CI.
 */
@RunWith(AndroidJUnit4::class)
class WebRtcNativeLoadSmokeTest {
    @Test
    fun androidCanInitializeNativeWebRtcPeerConnectionFactory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        val factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        assertNotNull(factory)
        factory.dispose()
    }
}
