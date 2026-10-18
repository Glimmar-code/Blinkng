package com.example.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import org.webrtc.SurfaceViewRenderer

/**
 * BLINK Phase 4 call surface. Voice and video controls stay reachable on
 * compact Android screens and expose their actual on/off state.
 */
@Composable
internal fun CallScreen(
    callType: CallType,
    peerName: String,
    peerUsername: String,
    peerAvatar: String,
    statusText: String,
    errorText: String?,
    incomingRinging: Boolean,
    mediaReady: Boolean,
    microphoneOn: Boolean,
    cameraOn: Boolean,
    speakerOn: Boolean,
    elapsedSeconds: Int,
    rtcClient: WebRtcCallClient?,
    endingAction: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onEnd: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleSpeaker: () -> Unit
) {
    val videoActive = callType == CallType.VIDEO && mediaReady
    val ink = if (videoActive) Color.White else MaterialTheme.colorScheme.onBackground
    val subInk = if (videoActive) Color.White.copy(alpha = 0.8f)
        else MaterialTheme.colorScheme.onSurfaceVariant
    val name = peerName.ifBlank { "BLINK user" }
    val controlsEnabled = CallPresentation.controlsEnabled(
        mediaReady, incomingRinging, endingAction
    )
    val panelInk = if (videoActive) Color.White else MaterialTheme.colorScheme.onSurface

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (videoActive) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context -> SurfaceViewRenderer(context) },
                    update = { renderer -> rtcClient?.attachRemoteRenderer(renderer) }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                            )
                        )
                        .height(230.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                        .height(300.dp)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 128.dp, end = 16.dp)
                        .size(width = 108.dp, height = 154.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF161616))
                ) {
                    // Keep this AndroidView alive when the camera is muted;
                    // otherwise each toggle can discard a native video surface.
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            SurfaceViewRenderer(context).apply { setZOrderMediaOverlay(true) }
                        },
                        update = { renderer -> rtcClient?.attachLocalRenderer(renderer) }
                    )
                    if (!cameraOn) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color(0xFF161616)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Camera off",
                                color = Color.White,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = peerAvatar.takeIf { it.isNotBlank() },
                        contentDescription = "Profile photo of $name",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(126.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(Modifier.size(18.dp))
                    Text(
                        name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    if (peerUsername.isNotBlank()) {
                        Text(
                            "@${peerUsername.trimStart('@')}",
                            color = subInk,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (callType == CallType.VIDEO) "BLINK · VIDEO CALL" else "BLINK · VOICE CALL",
                        color = subInk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (videoActive) {
                        Text(
                            text = name,
                            color = ink,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    CallStatusBadge(
                        label = CallPresentation.displayStatus(statusText, elapsedSeconds),
                        videoBackground = videoActive
                    )
                    if (!errorText.isNullOrBlank()) {
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = errorText,
                            fontSize = 14.sp,
                            color = if (videoActive) Color(0xFFFFB4AB) else MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            maxLines = 3
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (videoActive) Color(0xEB171717)
                        else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = if (videoActive) 0.dp else 3.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (incomingRinging) {
                            Text(
                                "Incoming ${if (callType == CallType.VIDEO) "video" else "voice"} call",
                                color = if (videoActive) Color.White
                                    else MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                CallPrimaryButton(
                                    icon = Icons.Default.CallEnd,
                                    label = "Decline",
                                    color = Color(0xFFCE3232),
                                    enabled = !endingAction,
                                    labelColor = panelInk,
                                    onClick = onDecline
                                )
                                CallPrimaryButton(
                                    icon = Icons.Default.Call,
                                    label = "Answer",
                                    color = Color(0xFF15803D),
                                    enabled = !endingAction,
                                    labelColor = panelInk,
                                    onClick = onAnswer
                                )
                            }
                        } else {
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val controlItems = @Composable {
                                    CallToggleButton(
                                        icon = if (microphoneOn) Icons.Default.Mic else Icons.Default.MicOff,
                                        label = if (microphoneOn) "Mute" else "Unmute",
                                        accessibilityLabel = CallPresentation.iconActionLabel("microphone", microphoneOn),
                                        active = !microphoneOn,
                                        enabled = controlsEnabled,
                                        labelColor = panelInk,
                                        onClick = onToggleMic
                                    )
                                    CallToggleButton(
                                        icon = if (speakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                        label = if (speakerOn) "Speaker on" else "Speaker off",
                                        accessibilityLabel = CallPresentation.iconActionLabel("speaker", speakerOn),
                                        active = speakerOn,
                                        enabled = controlsEnabled,
                                        labelColor = panelInk,
                                        onClick = onToggleSpeaker
                                    )
                                }
                                if (callType == CallType.AUDIO) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) { controlItems() }
                                } else {
                                    val cameraControls = @Composable {
                                        CallToggleButton(
                                            icon = if (cameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                            label = if (cameraOn) "Camera on" else "Camera off",
                                            accessibilityLabel = CallPresentation.iconActionLabel("camera", cameraOn),
                                            active = !cameraOn,
                                            enabled = controlsEnabled,
                                            labelColor = panelInk,
                                            onClick = onToggleCamera
                                        )
                                        CallToggleButton(
                                            icon = Icons.Default.Cameraswitch,
                                            label = "Flip camera",
                                            accessibilityLabel = "Switch front or rear camera",
                                            active = false,
                                            enabled = controlsEnabled && cameraOn,
                                            labelColor = panelInk,
                                            onClick = onSwitchCamera
                                        )
                                    }
                                    if (maxWidth < 350.dp) {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceEvenly
                                            ) { controlItems() }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceEvenly
                                            ) { cameraControls() }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            controlItems()
                                            cameraControls()
                                        }
                                    }
                                }
                            }
                            CallPrimaryButton(
                                icon = Icons.Default.CallEnd,
                                label = if (endingAction) "Ending…" else "End call",
                                color = Color(0xFFCE3232),
                                enabled = !endingAction,
                                labelColor = panelInk,
                                onClick = onEnd
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallStatusBadge(label: String, videoBackground: Boolean) {
    val reconnecting = label.startsWith("Reconnect", ignoreCase = true)
    val connected = label.startsWith("Connected", ignoreCase = true)
    val chipColor = when {
        reconnecting -> Color(0xFF8B6000)
        connected -> Color(0xFF196334)
        videoBackground -> Color(0xCC222222)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (videoBackground || reconnecting || connected) Color.White
        else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(color = chipColor, shape = RoundedCornerShape(24.dp)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CallToggleButton(
    icon: ImageVector,
    label: String,
    accessibilityLabel: String,
    active: Boolean,
    enabled: Boolean,
    labelColor: Color,
    onClick: () -> Unit
) {
    val foreground = if (enabled) Color.White else Color.White.copy(alpha = 0.46f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = if (active) Color(0xFF555555) else Color(0xFF292929)
        ) {
            IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(58.dp)) {
                Icon(icon, contentDescription = accessibilityLabel, tint = foreground)
            }
        }
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            label,
            color = if (enabled) labelColor else labelColor.copy(alpha = 0.56f),
            fontSize = 13.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CallPrimaryButton(
    icon: ImageVector,
    label: String,
    color: Color,
    enabled: Boolean,
    labelColor: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = color),
            modifier = Modifier.size(68.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = label,
            color = labelColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}
