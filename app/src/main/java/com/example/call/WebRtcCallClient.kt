package com.example.call

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.atomic.AtomicBoolean

class WebRtcCallClient(
    context: Context,
    private val type: CallType,
    private val listener: Listener
) {
    interface Listener {
        fun onLocalDescription(kind: String, description: SessionDescription)
        fun onLocalIceCandidate(candidate: IceCandidate)
        fun onConnectionStateChanged(state: PeerConnection.PeerConnectionState)
        fun onError(message: String)
    }

    companion object {
        private const val TAG = "WebRtcCallClient"
        private const val STREAM_ID = "blink_call"
        private const val AUDIO_TRACK_ID = "blink_audio"
        private const val VIDEO_TRACK_ID = "blink_video"
        private val factoryInitialized = AtomicBoolean(false)
    }

    private val appContext = context.applicationContext
    private val eglBase: EglBase = EglBase.create()
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioDeviceModule: AudioDeviceModule
    private val factory: PeerConnectionFactory
    private val peerConnection: PeerConnection
    private val audioSource: AudioSource
    private val audioTrack: AudioTrack
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private var microphoneEnabled = true
    private var cameraEnabled = type == CallType.VIDEO
    private var speakerEnabled = type == CallType.VIDEO
    private var released = false

    init {
        initializeFactoryOnce()
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        setSpeakerEnabled(speakerEnabled)

        audioDeviceModule = JavaAudioDeviceModule.builder(appContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
            )
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()

        val rtcConfiguration = PeerConnection.RTCConfiguration(buildIceServers()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
        }

        peerConnection = factory.createPeerConnection(rtcConfiguration, createPeerObserver())
            ?: throw IllegalStateException("Unable to create WebRTC peer connection")

        audioSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack(AUDIO_TRACK_ID, audioSource).apply {
            setEnabled(true)
        }
        peerConnection.addTrack(audioTrack, listOf(STREAM_ID))

        if (type == CallType.VIDEO) createLocalVideoTrack()
    }

    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        if (released) return
        if (localRenderer !== renderer) {
            localRenderer?.let { old -> runCatching { videoTrack?.removeSink(old) } }
            localRenderer = renderer
            renderer.init(eglBase.eglBaseContext, null)
            renderer.setMirror(true)
            renderer.setEnableHardwareScaler(true)
            videoTrack?.addSink(renderer)
        }
    }

    fun attachRemoteRenderer(renderer: SurfaceViewRenderer) {
        if (released) return
        if (remoteRenderer !== renderer) {
            remoteRenderer?.let { old -> runCatching { remoteVideoTrack?.removeSink(old) } }
            remoteRenderer = renderer
            renderer.init(eglBase.eglBaseContext, null)
            renderer.setMirror(false)
            renderer.setEnableHardwareScaler(true)
            remoteVideoTrack?.addSink(renderer)
        }
    }

    fun createOffer() {
        if (released) return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(
                MediaConstraints.KeyValuePair(
                    "OfferToReceiveVideo",
                    (type == CallType.VIDEO).toString()
                )
            )
        }
        peerConnection.createOffer(object : BaseSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription) {
                setLocalDescriptionAndEmit("offer", description)
            }
        }, constraints)
    }

    fun applyRemoteDescription(kind: String, sdp: String) {
        if (released || sdp.isBlank()) return
        val descriptionType = when (kind.lowercase()) {
            "offer" -> SessionDescription.Type.OFFER
            "answer" -> SessionDescription.Type.ANSWER
            else -> return
        }
        val description = SessionDescription(descriptionType, sdp)
        peerConnection.setRemoteDescription(object : BaseSdpObserver() {
            override fun onSetSuccess() {
                if (descriptionType == SessionDescription.Type.OFFER) createAnswer()
            }
        }, description)
    }

    fun addRemoteIceCandidate(sdpMid: String?, lineIndex: Int, candidate: String) {
        if (released || candidate.isBlank()) return
        val accepted = peerConnection.addIceCandidate(IceCandidate(sdpMid, lineIndex, candidate))
        if (!accepted) Log.w(TAG, "Remote ICE candidate was rejected")
    }

    fun restartIce() {
        if (!released) runCatching { peerConnection.restartIce() }
    }

    fun setMicrophoneEnabled(enabled: Boolean): Boolean {
        microphoneEnabled = enabled
        audioTrack.setEnabled(enabled)
        return microphoneEnabled
    }

    fun toggleMicrophone(): Boolean = setMicrophoneEnabled(!microphoneEnabled)

    fun setCameraEnabled(enabled: Boolean): Boolean {
        if (type != CallType.VIDEO) return false
        cameraEnabled = enabled
        videoTrack?.setEnabled(enabled)
        return cameraEnabled
    }

    fun toggleCamera(): Boolean = setCameraEnabled(!cameraEnabled)

    fun switchCamera() {
        if (type == CallType.VIDEO) {
            runCatching { videoCapturer?.switchCamera(null) }
                .onFailure { listener.onError("Unable to switch camera.") }
        }
    }

    fun setSpeakerEnabled(enabled: Boolean): Boolean {
        speakerEnabled = enabled
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val targetType = if (enabled) {
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                } else {
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }
                val target = audioManager.availableCommunicationDevices.firstOrNull { it.type == targetType }
                if (target != null) audioManager.setCommunicationDevice(target)
            } else {
                @Suppress("DEPRECATION")
                run { audioManager.isSpeakerphoneOn = enabled }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Audio route update failed", error)
        }
        return speakerEnabled
    }

    fun toggleSpeaker(): Boolean = setSpeakerEnabled(!speakerEnabled)

    fun release() {
        if (released) return
        released = true
        runCatching { videoCapturer?.stopCapture() }
        localRenderer?.let { renderer -> runCatching { videoTrack?.removeSink(renderer) } }
        remoteRenderer?.let { renderer -> runCatching { remoteVideoTrack?.removeSink(renderer) } }
        runCatching { localRenderer?.release() }
        runCatching { remoteRenderer?.release() }
        localRenderer = null
        remoteRenderer = null
        remoteVideoTrack = null
        runCatching { videoTrack?.dispose() }
        runCatching { videoSource?.dispose() }
        runCatching { textureHelper?.dispose() }
        runCatching { videoCapturer?.dispose() }
        runCatching { audioTrack.dispose() }
        runCatching { audioSource.dispose() }
        runCatching { peerConnection.close() }
        runCatching { peerConnection.dispose() }
        runCatching { factory.dispose() }
        runCatching { audioDeviceModule.release() }
        runCatching { eglBase.release() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { audioManager.clearCommunicationDevice() }
        } else {
            @Suppress("DEPRECATION")
            runCatching { audioManager.isSpeakerphoneOn = false }
        }
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    private fun initializeFactoryOnce() {
        if (factoryInitialized.compareAndSet(false, true)) {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(appContext)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )
        }
    }

    private fun buildIceServers(): List<PeerConnection.IceServer> = buildList {
        add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer())
        val turnUrl = BuildConfig.BLINK_TURN_URL.trim()
        if (turnUrl.isNotBlank()) {
            val builder = PeerConnection.IceServer.builder(turnUrl)
            if (BuildConfig.BLINK_TURN_USERNAME.isNotBlank()) {
                builder.setUsername(BuildConfig.BLINK_TURN_USERNAME)
            }
            if (BuildConfig.BLINK_TURN_CREDENTIAL.isNotBlank()) {
                builder.setPassword(BuildConfig.BLINK_TURN_CREDENTIAL)
            }
            add(builder.createIceServer())
        }
    }

    private fun createLocalVideoTrack() {
        val capturer = createCameraCapturer() ?: run {
            cameraEnabled = false
            listener.onError("No camera is available on this device.")
            return
        }
        videoCapturer = capturer
        videoSource = factory.createVideoSource(false)
        textureHelper = SurfaceTextureHelper.create("BlinkCameraThread", eglBase.eglBaseContext)
        capturer.initialize(textureHelper, appContext, videoSource?.capturerObserver)
        runCatching { capturer.startCapture(720, 1280, 30) }
            .onFailure {
                cameraEnabled = false
                listener.onError("Unable to start the camera.")
            }
        videoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource).apply {
            setEnabled(cameraEnabled)
        }
        peerConnection.addTrack(videoTrack, listOf(STREAM_ID))
        localRenderer?.let { videoTrack?.addSink(it) }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator: CameraEnumerator = if (Camera2Enumerator.isSupported(appContext)) {
            Camera2Enumerator(appContext)
        } else {
            Camera1Enumerator(false)
        }
        val front = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
        val fallback = enumerator.deviceNames.firstOrNull()
        val device = front ?: fallback ?: return null
        return enumerator.createCapturer(device, null) as? CameraVideoCapturer
    }

    private fun createAnswer() {
        val constraints = MediaConstraints()
        peerConnection.createAnswer(object : BaseSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription) {
                setLocalDescriptionAndEmit("answer", description)
            }
        }, constraints)
    }

    private fun setLocalDescriptionAndEmit(kind: String, description: SessionDescription) {
        peerConnection.setLocalDescription(object : BaseSdpObserver() {
            override fun onSetSuccess() {
                listener.onLocalDescription(kind, description)
            }
        }, description)
    }

    private fun bindRemoteTrack(track: VideoTrack?) {
        if (track == null || remoteVideoTrack === track) return
        remoteRenderer?.let { renderer -> runCatching { remoteVideoTrack?.removeSink(renderer) } }
        remoteVideoTrack = track
        remoteRenderer?.let { track.addSink(it) }
    }

    private fun createPeerObserver(): PeerConnection.Observer = object : PeerConnection.Observer {
        override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
            if (newState == PeerConnection.IceConnectionState.FAILED) restartIce()
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            listener.onConnectionStateChanged(newState)
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) = Unit

        override fun onIceCandidate(candidate: IceCandidate) {
            listener.onLocalIceCandidate(candidate)
        }

        override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) {
            bindRemoteTrack(stream.videoTracks.firstOrNull())
        }
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(dataChannel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<MediaStream>) {
            bindRemoteTrack(receiver.track() as? VideoTrack)
        }
        override fun onTrack(transceiver: RtpTransceiver) {
            bindRemoteTrack(transceiver.receiver.track() as? VideoTrack)
        }
    }

    private open inner class BaseSdpObserver : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String) {
            Log.e(TAG, "SDP create failed: $error")
            listener.onError("Unable to negotiate the call.")
        }
        override fun onSetFailure(error: String) {
            Log.e(TAG, "SDP set failed: $error")
            listener.onError("Unable to apply call connection details.")
        }
    }
}
