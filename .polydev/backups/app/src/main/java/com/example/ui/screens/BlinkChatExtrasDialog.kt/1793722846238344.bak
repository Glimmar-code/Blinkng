package com.example.ui.screens

import android.net.Uri
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import com.blinkng.shared.BlinkChatPollPolicy
import com.blinkng.shared.BlinkChatContactCardPolicy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Consent-first chat additions. These send ordinary supported BLINK text messages
 * rather than claiming a native interactive-poll or live-location backend exists.
 */
@Composable
internal fun BlinkChatExtrasDialog(
    mode: String,
    contact: Pair<String, String>? = null,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
    nativePollEnabled: Boolean = false,
    onNativePoll: (com.blinkng.shared.BlinkChatPollDraft) -> Unit = {}
) {
    var title by remember(mode) { mutableStateOf("") }
    var details by remember(mode) { mutableStateOf("") }
    var other by remember(mode) { mutableStateOf("") }
    val context = LocalContext.current
    var locationStatus by remember(mode) { mutableStateOf<String?>(null) }
    var locating by remember(mode) { mutableStateOf(false) }
    var locationSignal by remember(mode) { mutableStateOf<CancellationSignal?>(null) }
    DisposableEffect(mode) {
        onDispose { locationSignal?.cancel() }
    }
    @SuppressLint("MissingPermission")
    fun locateOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            locationStatus = "Enter a place or coordinates on this device."
            return
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null) {
            locationStatus = "Location service unavailable. Enter a place instead."
            return
        }
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (provider == null) {
            locationStatus = "Turn on device location or enter a place."
            return
        }
        locationSignal?.cancel()
        val signal = CancellationSignal()
        locationSignal = signal
        locating = true
        locationStatus = "Getting your current location…"
        runCatching {
            manager.getCurrentLocation(
                provider, signal, ContextCompat.getMainExecutor(context)
            ) { location ->
                locating = false
                if (location == null) {
                    locationStatus = "Location unavailable. Enter a place manually."
                } else {
                    // Approximate coordinates: still shown to the sender before Send.
                    title = String.format(Locale.US, "%.4f,%.4f",
                        location.latitude, location.longitude)
                    locationStatus = "Approximate coordinates added. Tap Send to share."
                }
            }
        }.onFailure {
            locating = false
            locationStatus = "Location unavailable. Enter a place manually."
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed ->
        if (allowed) locateOnce()
        else locationStatus = "Location permission denied. You can enter a place instead."
    }
    val isContact = mode == "contact"
    val isLocation = mode == "location"
    val isArticle = mode == "article"
    val isPoll = mode == "poll"
    val pollDraft = if (isPoll) runCatching {
        BlinkChatPollPolicy.prepare(title, details.lines().map(String::trim).filter(String::isNotEmpty))
    }.getOrNull() else null
    val heading = when(mode) {
        "location" -> "Share a place"
        "contact" -> "Share contact"
        "article" -> "Share article"
        "poll" -> "Compose poll"
        else -> "Share"
    }
    val text = when {
        isLocation && title.isNotBlank() -> {
            val place = title.trim()
            "📍 $place\nhttps://maps.google.com/?q=${Uri.encode(place)}"
        }
        isArticle && details.startsWith("https://", ignoreCase=true) ->
            "📰 ${title.trim()}\n${details.trim()}"
        isContact && contact != null ->
            runCatching {
                BlinkChatContactCardPolicy.encode(contact.first, contact.second)
            }.getOrDefault("")
        isPoll && pollDraft != null -> {
            // Until the server/native clients are deployed, send an honest text
            // fallback, never silently drop duplicate or ninth poll options.
            BlinkChatPollPolicy.textFallback(pollDraft)
        }
        else -> ""
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .background(Color(0xFF111B2A), androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                .verticalScroll(rememberScrollState()).padding(20.dp)
        ) {
            Text(heading, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(12.dp))
            when {
                isLocation -> {
                    Text("Enter a place or coordinates to share. No location permission is needed.",
                        color = Color.LightGray)
                    OutlinedTextField(
                        value=title, onValueChange={title=it.take(200)},
                        label={Text("Place, address or coordinates")}, singleLine=true,
                        modifier=Modifier.fillMaxWidth()
                    )
                    TextButton(
                        enabled = !locating,
                        onClick = {
                            if (ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                            ) locateOnce()
                            else locationPermissionLauncher.launch(
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        }
                    ) { Text(if (locating) "Finding location…" else "Use my current location") }
                    locationStatus?.let { Text(it, color = Color.LightGray) }
                    Text("Your location is never sent until you tap Send. This is a one-time place link, not live tracking.",
                        color = Color.LightGray)
                }
                isArticle -> {
                    OutlinedTextField(
                        value=title, onValueChange={title=it.take(150)},
                        label={Text("Article title")},modifier=Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value=details, onValueChange={details=it.take(1000)},
                        label={Text("HTTPS article link")},singleLine=true,
                        modifier=Modifier.fillMaxWidth(),
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri)
                    )
                }
                isContact -> {
                    Text(contact?.first.orEmpty(), color=Color.White,
                        style=MaterialTheme.typography.titleMedium)
                    Text(contact?.second.orEmpty(), color=Color.LightGray)
                    Text("Only the selected name and phone number will be shared.",
                        color=Color.LightGray)
                }
                isPoll -> {
                    Text(
                        if (nativePollEnabled)
                            "Interactive poll: votes are recorded privately by the server. One choice per account."
                        else "Text poll: reply with an option number. Native voting needs a verified server upgrade.",
                        color=Color.LightGray)
                    OutlinedTextField(
                        value=title, onValueChange={title=it.take(200)},
                        label={Text("Question")},modifier=Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value=details,onValueChange={details=it.take(1400)},
                        label={Text("Options (one per line, 2–8)")},
                        minLines=4,maxLines=10,
                        modifier=Modifier.fillMaxWidth()
                    )
                    if ((title.isNotBlank() || details.isNotBlank()) && pollDraft == null) {
                        Text(
                            "Enter a question (1–200 characters) and 2–8 different options (1–80 characters each).",
                            color = Color(0xFFFFC4BB),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                TextButton(onClick=onDismiss) { Text("Cancel") }
                Button(
                    enabled=text.isNotBlank(),
                    onClick={
                        if (isPoll && nativePollEnabled && pollDraft != null)
                            onNativePoll(pollDraft)
                        else onSend(text)
                    },
                    colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF0879F8))
                ) { Text("Send") }
            }
        }
    }
}
