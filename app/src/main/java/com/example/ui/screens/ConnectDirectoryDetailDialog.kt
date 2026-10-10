package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.UserProfile
import com.example.data.repository.ConnectDirectoryCategory
import com.example.data.repository.ConnectDirectoryListingsRepository
import com.example.data.repository.DirectoryConnectApplication
import com.example.data.repository.DirectoryConnectListing
import kotlinx.coroutines.launch

/**
 * Each specialist directory row has its own persisted listings, requests and
 * incoming applications instead of pretending every category is "Mentors".
 */
@Composable
fun ConnectDirectoryDetailDialog(
    category: ConnectDirectoryCategory,
    currentUserId: String,
    profiles: List<UserProfile>,
    onDismiss: () -> Unit,
    onProfileClick: (String) -> Unit,
    onMessageUser: (String, String?, String?) -> Unit
) {
    val repository = remember { ConnectDirectoryListingsRepository() }
    val scope = rememberCoroutineScope()
    var refreshKey by remember(category.slug) { mutableIntStateOf(0) }
    var listings by remember(category.slug) { mutableStateOf<List<DirectoryConnectListing>>(emptyList()) }
    var applications by remember(category.slug) {
        mutableStateOf<List<DirectoryConnectApplication>>(emptyList())
    }
    var loading by remember(category.slug) { mutableStateOf(true) }
    var error by remember(category.slug) { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var applicationTarget by remember { mutableStateOf<DirectoryConnectListing?>(null) }
    var applicationMessage by remember { mutableStateOf("") }

    LaunchedEffect(category.slug, refreshKey, currentUserId) {
        loading = true
        error = null
        runCatching {
            val fetched = repository.listings(category.slug)
            val inbox = repository.applicationsForOwnListings(fetched, currentUserId)
            fetched to inbox
        }.onSuccess { (fetched, inbox) ->
            listings = fetched
            applications = inbox
        }.onFailure { exception ->
            error = exception.message ?: "Could not load this category."
        }
        loading = false
    }

    fun submit(action: suspend () -> Unit, onSuccess: () -> Unit) {
        if (submitting) return
        submitting = true
        error = null
        scope.launch {
            runCatching { action() }
                .onSuccess {
                    onSuccess()
                    refreshKey += 1
                }
                .onFailure { exception ->
                    error = exception.message ?: "Unable to save. Please try again."
                }
            submitting = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(.95f).fillMaxHeight(.88f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(category.title, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Text(
                            category.description,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Button(
                        onClick = { showCreate = true },
                        enabled = currentUserId.isNotBlank()
                    ) { Text("Create listing") }
                    OutlinedButton(onClick = { refreshKey += 1 }, enabled = !loading) {
                        Text("Refresh")
                    }
                }
                if (error != null) {
                    Text(
                        error.orEmpty(),
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (loading) {
                    CircularProgressIndicator()
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (listings.isEmpty()) {
                            item {
                                Text(
                                    "No listings in ${category.title} yet. Be the first to create one.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 20.dp)
                                )
                            }
                        }
                        items(listings, key = { it.id }) { listing ->
                            val isOwn = listing.userId == currentUserId
                            val owner = profiles.firstOrNull { it.id == listing.userId }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(listing.title, fontWeight = FontWeight.Bold)
                                    if (owner != null) {
                                        Text(
                                            "@${owner.username}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (listing.description.isNotBlank()) {
                                        Text(
                                            listing.description,
                                            modifier = Modifier.padding(vertical = 7.dp),
                                            fontSize = 12.sp
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (isOwn) {
                                            Text(
                                                "Your listing",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            Button(
                                                onClick = {
                                                    applicationTarget = listing
                                                    applicationMessage = "Hi, I'm interested in ${listing.title}."
                                                },
                                                enabled = !submitting
                                            ) { Text("Request") }
                                            if (owner != null) {
                                                OutlinedButton(onClick = {
                                                    onMessageUser(
                                                        owner.username,
                                                        owner.fullName,
                                                        owner.avatarUrl
                                                    )
                                                }) { Text("Message") }
                                            }
                                        }
                                        if (owner != null) {
                                            TextButton(onClick = { onProfileClick(owner.username) }) {
                                                Text("Profile")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        val pending = applications.filter { it.status == "pending" }
                        if (pending.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider()
                                Text(
                                    "Requests to your listings",
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            items(pending, key = { "request-${it.id}" }) { application ->
                                val person = profiles.firstOrNull { it.id == application.applicantId }
                                Surface(
                                    shape = RoundedCornerShape(15.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(person?.fullName ?: "Student request", fontWeight = FontWeight.Bold)
                                        if (application.message.isNotBlank()) {
                                            Text(application.message, fontSize = 12.sp)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                            Button(
                                                onClick = {
                                                    submit(
                                                        { repository.respond(application.id, true) },
                                                        {}
                                                    )
                                                },
                                                enabled = !submitting
                                            ) { Text("Accept") }
                                            OutlinedButton(
                                                onClick = {
                                                    submit(
                                                        { repository.respond(application.id, false) },
                                                        {}
                                                    )
                                                },
                                                enabled = !submitting
                                            ) { Text("Decline") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { if (!submitting) showCreate = false },
            title = { Text("Post in ${category.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(90) },
                        label = { Text("Title") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it.take(800) },
                        label = { Text("Describe what you're looking for") },
                        minLines = 3,
                        maxLines = 5
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        submit(
                            { repository.create(category.slug, title, description) },
                            {
                                title = ""
                                description = ""
                                showCreate = false
                            }
                        )
                    },
                    enabled = !submitting && title.trim().length >= 3
                ) { Text(if (submitting) "Posting…" else "Publish") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }, enabled = !submitting) {
                    Text("Cancel")
                }
            }
        )
    }

    applicationTarget?.let { listing ->
        AlertDialog(
            onDismissRequest = { if (!submitting) applicationTarget = null },
            title = { Text("Request to connect") },
            text = {
                OutlinedTextField(
                    value = applicationMessage,
                    onValueChange = { applicationMessage = it.take(500) },
                    label = { Text("Your message") },
                    minLines = 2,
                    maxLines = 5
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        submit(
                            { repository.apply(listing.id, applicationMessage) },
                            { applicationTarget = null }
                        )
                    },
                    enabled = !submitting
                ) { Text(if (submitting) "Sending…" else "Send request") }
            },
            dismissButton = {
                TextButton(
                    onClick = { applicationTarget = null },
                    enabled = !submitting
                ) { Text("Cancel") }
            }
        )
    }
}
