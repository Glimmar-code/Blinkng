package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.blinkng.shared.discovery.BlinkFriendDiscoveryPolicy
import com.example.data.models.UserProfile
import com.example.data.repository.FollowStateStore
import kotlinx.coroutines.launch

/**
 * Friend suggestions use only authenticated profile results already shown in Connect.
 * No READ_CONTACTS permission, full phonebook scans, or contact uploads.
 */
@Composable
fun BlinkFindFriendsDialog(
    accountId: String,
    university: String,
    profiles: List<UserProfile>,
    onDismiss: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val followingIds by FollowStateStore.followingIds.collectAsState()
    var pendingId by remember(accountId) { mutableStateOf<String?>(null) }
    var feedback by remember(accountId) { mutableStateOf<String?>(null) }

    val candidates = remember(accountId, university, profiles) {
        val eligible = BlinkFriendDiscoveryPolicy.campusSuggestions(
            accountId, university,
            profiles.map { BlinkFriendDiscoveryPolicy.Candidate(it.id, it.username, it.university) },
        ).map { it.id }.toSet()
        profiles.filter { it.id in eligible }
            .distinctBy { it.id }
            .sortedBy { it.username.lowercase() }
            .take(BlinkFriendDiscoveryPolicy.MAX_SUGGESTIONS)
    }

    LaunchedEffect(accountId) { if (accountId.isNotBlank()) FollowStateStore.refresh() }

    fun shareInvite() {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, BlinkFriendDiscoveryPolicy.inviteMessage("https://www.blink.com.ng"))
        }
        runCatching { context.startActivity(Intent.createChooser(share, "Invite friends to BLINK")) }
            .onFailure { feedback = "No sharing app is available on this device." }
    }

    val contactPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val contactUri = result.data?.data ?: return@rememberLauncherForActivityResult
        // ACTION_PICK grants one temporary contact URI. Read that ONE selected
        // phone number locally; never transfer it to the BLINK backend.
        val phone = runCatching {
            context.contentResolver.query(
                contactUri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }.orEmpty()
        }.getOrDefault("")
        val number = phone.filter { it.isDigit() || it == '+' }.take(20)
        if (number.count(Char::isDigit) < 7) {
            feedback = "This contact has no usable phone number. Try Share Link."
            return@rememberLauncherForActivityResult
        }
        val sms = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).apply {
            putExtra("sms_body", BlinkFriendDiscoveryPolicy.inviteMessage("https://www.blink.com.ng"))
        }
        try {
            context.startActivity(sms)
        } catch (_: ActivityNotFoundException) {
            feedback = "No SMS app found. Use Share Link instead."
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 570.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(18.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("FIND YOUR FRIENDS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Meet students from your university and invite friends you know.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Your contacts stay on your phone. BLINK only accesses the one contact you choose for an invitation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val pick = Intent(
                                Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            )
                            runCatching { contactPicker.launch(pick) }
                                .onFailure { feedback = "Your phone's contact picker is unavailable." }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Choose Contact") }
                    OutlinedButton(onClick = ::shareInvite, modifier = Modifier.fillMaxWidth()) {
                        Text("Share Link")
                    }
                }
                Text("STUDENTS FROM YOUR CAMPUS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                if (candidates.isEmpty()) {
                    Text(
                        "No campus suggestions available yet. Visit Connect to discover more people.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        candidates.forEach { profile ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Surface(shape = CircleShape, modifier = Modifier.size(40.dp)) {
                                    AsyncImage(
                                        model = profile.avatarUrl.takeIf(String::isNotBlank),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        profile.fullName.ifBlank { profile.username },
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text("@${profile.username}", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                                }
                                if (profile.id in followingIds) {
                                    OutlinedButton(onClick = { onOpenProfile(profile.username) }) { Text("Following") }
                                } else {
                                    Button(
                                        enabled = pendingId == null,
                                        onClick = {
                                            pendingId = profile.id
                                            scope.launch {
                                                val success = runCatching {
                                                    FollowStateStore.setFollowing(profile.id, true)
                                                }.getOrDefault(false)
                                                if (!success) feedback = "Couldn't follow this account. Please retry."
                                                pendingId = null
                                            }
                                        },
                                    ) {
                                        if (pendingId == profile.id) CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp), strokeWidth = 2.dp,
                                        ) else Text("Follow")
                                    }
                                }
                            }
                        }
                    }
                }
                feedback?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Not Now")
                }
            }
        }
    }
}
