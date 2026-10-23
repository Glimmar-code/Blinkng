package com.example.call

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Android 15 real WebRTC/JNI offer-answer/ICE smoke with two local peers.
 *
 * This does NOT simulate Supabase authorization, FCM delivery, a second
 * physical phone, a cellular NAT or actual bidirectional audio/video frames.
 * Those must pass the documented two-phone acceptance gate before main.
 */
@RunWith(AndroidJUnit4::class)
class WebRtcCallPairSmokeTest {
    @Test
    fun voiceAndVideoPeersCanExchangeOffersAnswersAndConnect() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (type in listOf(CallType.AUDIO, CallType.VIDEO)) {
            connectPeers(context, type)
        }
    }

    private fun connectPeers(context: android.content.Context, type: CallType) {
        val caller = AtomicReference<WebRtcCallClient?>()
        val callee = AtomicReference<WebRtcCallClient?>()
        val connected = CountDownLatch(2)
        val callerConnected = AtomicBoolean(false)
        val calleeConnected = AtomicBoolean(false)
        val offers = AtomicInteger(0)
        val answers = AtomicInteger(0)
        val failures = AtomicReference<String?>(null)

        fun observer(
            peer: String,
            opposite: AtomicReference<WebRtcCallClient?>,
            once: AtomicBoolean
        ) = object : WebRtcCallClient.Listener {
            override fun onLocalDescription(kind: String, description: SessionDescription) {
                if (kind == "offer") offers.incrementAndGet()
                if (kind == "answer") answers.incrementAndGet()
                opposite.get()?.applyRemoteDescription(kind, description.description)
                    ?: failures.compareAndSet(null, "$peer: receiving peer was absent during $kind")
            }

            override fun onLocalIceCandidate(candidate: IceCandidate) {
                opposite.get()?.addRemoteIceCandidate(
                    candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp
                )
            }

            override fun onConnectionStateChanged(state: PeerConnection.PeerConnectionState) {
                Log.i("BlinkPairSmoke", "$type $peer transport=$state")
                if (state == PeerConnection.PeerConnectionState.CONNECTED &&
                    once.compareAndSet(false, true)
                ) {
                    connected.countDown()
                }
                if (state == PeerConnection.PeerConnectionState.FAILED) {
                    failures.compareAndSet(null, "$peer ICE connection failed for $type")
                    connected.countDown()
                }
            }

            override fun onError(message: String) {
                // Camera capture may be unavailable on headless CI emulators.
                // This test proves media-track negotiation and transport, not
                // physical camera frames or permission-driven UI behavior.
                Log.w("BlinkPairSmoke", "$type $peer: $message")
            }
        }

        try {
            caller.set(WebRtcCallClient(
                context, type, observer("caller", callee, callerConnected)
            ))
            callee.set(WebRtcCallClient(
                context, type, observer("callee", caller, calleeConnected)
            ))
            caller.get()!!.createOffer()

            val bothFinished = connected.await(35, TimeUnit.SECONDS)
            assertTrue("$type negotiation timed out: ${failures.get()}", bothFinished)
            assertTrue("$type has no offer", offers.get() > 0)
            assertTrue("$type has no answer", answers.get() > 0)
            assertTrue("$type caller did not connect: ${failures.get()}", callerConnected.get())
            assertTrue("$type callee did not connect: ${failures.get()}", calleeConnected.get())
        } finally {
            caller.getAndSet(null)?.release()
            callee.getAndSet(null)?.release()
        }
    }
}
