package com.example.call

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
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
    private val listener: Listener,
    private val suppliedIceServers: List<PeerConnection.IceServer> = emptyList()
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
    private val previousAudioMode = audioManager.mode
    private val captureProfile: CallMediaQualityPolicy.CaptureProfile by lazy {
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        CallMediaQualityPolicy.select(
            isLowRamDevice = activityManager?.isLowRamDevice ?: false,
            memoryClassMb = activityManager?.memoryClass ?: 256,
            isMeteredNetwork = runCatching { connectivity?.isActiveNetworkMetered ?: true }
                .getOrDefault(true)
        )
    }
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { }
    private var audioFocusRequest: AudioFocusRequest? = null
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
    private var cameraOperational = false
    private var speakerEnabled = type == CallType.VIDEO
    @Volatile private var remoteDescriptionReady = false
    private val pendingRemoteIceCandidates = mutableListOf<IceCandidate>()
    private var released = false

    init {
        initializeFactoryOnce()
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        requestCallAudioFocus()
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

        val initialServers = suppliedIceServers.ifEmpty { buildIceServers() }
        val rtcConfiguration = configurationFor(initialServers)

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
        synchronized(pendingRemoteIceCandidates) {
            remoteDescriptionReady = false
        }
        peerConnection.setRemoteDescription(object : BaseSdpObserver() {
            override fun onSetSuccess() {
                val queued = synchronized(pendingRemoteIceCandidates) {
                    remoteDescriptionReady = true
                    pendingRemoteIceCandidates.toList().also { pendingRemoteIceCandidates.clear() }
                }
                queued.forEach(::addIceCandidateNow)
                if (descriptionType == SessionDescription.Type.OFFER) createAnswer()
            }
        }, description)
    }

    fun addRemoteIceCandidate(sdpMid: String?, lineIndex: Int, candidate: String) {
        if (released || candidate.isBlank()) return
        val iceCandidate = IceCandidate(sdpMid, lineIndex, candidate)
        synchronized(pendingRemoteIceCandidates) {
            if (!remoteDescriptionReady) {
                pendingRemoteIceCandidates.add(iceCandidate)
                return
            }
        }
        addIceCandidateNow(iceCandidate)
    }

    private fun addIceCandidateNow(candidate: IceCandidate) {
        if (released) return
        val accepted = peerConnection.addIceCandidate(candidate)
        if (!accepted) Log.w(TAG, "Remote ICE candidate was rejected")
    }

    /** Refresh TURN credentials before renegotiation when the backend is reachable. */
    fun updateIceServers(servers: List<PeerConnection.IceServer>): Boolean {
        if (released || servers.isEmpty()) return false
        return runCatching { peerConnection.setConfiguration(configurationFor(servers)) }
            .getOrElse {
                Log.w(TAG, "Unable to refresh ICE relay configuration", it)
                false
            }
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
        if (!CallMediaQualityPolicy.canEnableCamera(
                trackAvailable = cameraOperational && videoTrack != null,
                videoCall = type == CallType.VIDEO
            )) return false
        cameraEnabled = enabled
        videoTrack?.setEnabled(enabled)
        return cameraEnabled
    }

    fun toggleCamera(): Boolean = setCameraEnabled(!cameraEnabled)

    fun isCameraEnabled(): Boolean = cameraEnabled && cameraOperational && videoTrack != null

    fun switchCamera() {
        if (released || type != CallType.VIDEO || !cameraOperational) return
        runCatching {
            videoCapturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
                override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                    // Self-view mirrors the front camera only, not the rear
                    // camera. This avoids flipped text and landmarks.
                    localRenderer?.setMirror(isFrontCamera)
                }

                override fun onCameraSwitchError(errorDescription: String) {
                    Log.w(TAG, "Call camera switch failed")
                    listener.onError("Unable to switch camera.")
                }
            })
        }.onFailure { listener.onError("Unable to switch camera.") }
    }

    fun setSpeakerEnabled(enabled: Boolean): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val target = if (enabled) {
                    devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                } else {
                    // Preserve headphones, Bluetooth and hearing aids when
                    // speaker is turned off. Earpiece is the last fallback.
                    val priorities = listOf(
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        AudioDeviceInfo.TYPE_BLE_HEADSET,
                        AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                        AudioDeviceInfo.TYPE_USB_HEADSET,
                        AudioDeviceInfo.TYPE_HEARING_AID,
                        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    )
                    priorities.firstNotNullOfOrNull { requested ->
                        devices.firstOrNull { it.type == requested }
                    }
                }
                if (target != null) {
                    val accepted = audioManager.setCommunicationDevice(target)
                    if (!accepted) Log.w(TAG, "Requested call audio route unavailable")
                }
                speakerEnabled = audioManager.communicationDevice?.type ==
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = enabled
                @Suppress("DEPRECATION")
                speakerEnabled = audioManager.isSpeakerphoneOn
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
        abandonCallAudioFocus()
        // Restore the actual pre-call mode, not always MODE_NORMAL: another
        // media or communication app may have configured a different route.
        runCatching {
            if (audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) {
                audioManager.mode = previousAudioMode
            }
        }
    }

    private fun requestCallAudioFocus() {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(TAG, "Call audio focus was not granted")
        }
    }

    private fun abandonCallAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { request ->
                runCatching { audioManager.abandonAudioFocusRequest(request) }
            }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            runCatching { audioManager.abandonAudioFocus(audioFocusListener) }
        }
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

    private fun configurationFor(servers: List<PeerConnection.IceServer>) =
        PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
        }

    /** Temporary legacy fallback until every environment deploys the TURN endpoint. */
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
        val profile = captureProfile
        // Prefer the device/network preset but attempt the lightweight profile
        // if a phone's camera driver refuses the first capture format.
        val captureStarted = runCatching {
            capturer.startCapture(profile.width, profile.height, profile.framesPerSecond)
        }.recoverCatching {
            if (profile == CallMediaQualityPolicy.LOW) throw it
            Log.w(TAG, "Preferred camera capture unavailable; retrying low resolution")
            val fallback = CallMediaQualityPolicy.LOW
            capturer.startCapture(
                fallback.width, fallback.height, fallback.framesPerSecond
            )
        }
        cameraOperational = captureStarted.isSuccess
        if (!cameraOperational) {
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

        // Only the call coordinator may restart ICE and negotiate a new offer.
        // Restarting here as well caused duplicate restarts on both peers and
        // could race with an in-flight SDP exchange.
        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) = Unit

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
