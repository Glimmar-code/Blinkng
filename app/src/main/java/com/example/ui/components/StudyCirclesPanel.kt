package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.StudyCircleJoinRequest
import com.example.data.models.StudyCircleSummary
import com.example.data.repository.StudyCircleRepository
import com.example.ui.theme.BlinkPink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StudyCirclesPanel(
    repository: StudyCircleRepository = remember { StudyCircleRepository() }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var circles by remember { mutableStateOf<List<StudyCircleSummary>>(emptyList()) }
    var ownerRequests by remember { mutableStateOf<List<StudyCircleJoinRequest>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by rememberSaveable { mutableStateOf(false) }

    suspend fun refresh() {
        loading = true
        error = null
        runCatching {
            circles = repository.fetchCircles(query = query, limit = 50)
            ownerRequests = repository.fetchOwnerRequests()
        }.onFailure { error = it.message ?: "Couldn't load study circles." }
        loading = false
    }

    LaunchedEffect(query) {
        delay(250)
        refresh()
    }

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .18f)
            ),
            border = BorderStroke(1.dp, BlinkPink.copy(alpha = .22f))
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = BlinkPink.copy(alpha = .13f)) {
                        Icon(
                            Icons.Default.Groups,
                            contentDescription = null,
                            tint = BlinkPink,
                            modifier = Modifier.padding(9.dp)
                        )
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Study Circles", fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Text(
                            "Create, discover and moderate course-based study groups.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = { showCreate = true }, shape = RoundedCornerShape(100.dp)) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Create", fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Search circle, course or faculty") },
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }

        AnimatedVisibility(visible = loading, enter = fadeIn(), exit = fadeOut()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Syncing study circles…", fontSize = 10.sp)
            }
        }

        error?.let {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    it,
                    modifier = Modifier.padding(11.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 10.sp
                )
            }
        }

        if (ownerRequests.isNotEmpty()) {
            Text("Join requests", fontWeight = FontWeight.Black, fontSize = 13.sp)
            ownerRequests.take(8).forEach { request ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(Modifier.padding(11.dp)) {
                        Text(request.circleName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            "${request.requesterFullName.ifBlank { request.requesterUsername }} • @${request.requesterUsername}",
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(7.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        busyId = request.requestId
                                        runCatching { repository.respondRequest(request.requestId, false) }
                                            .onSuccess { refresh() }
                                            .onFailure { Toast.makeText(context, it.message ?: "Decline failed", Toast.LENGTH_SHORT).show() }
                                        busyId = null
                                    }
                                },
                                enabled = busyId == null,
                                modifier = Modifier.weight(1f)
                            ) { Text("Decline", fontSize = 10.sp) }
                            Button(
                                onClick = {
                                    scope.launch {
                                        busyId = request.requestId
                                        runCatching { repository.respondRequest(request.requestId, true) }
                                            .onSuccess { refresh() }
                                            .onFailure { Toast.makeText(context, it.message ?: "Accept failed", Toast.LENGTH_SHORT).show() }
                                        busyId = null
                                    }
                                },
                                enabled = busyId == null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Accept", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        if (!loading && circles.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Groups, contentDescription = null)
                    Spacer(Modifier.height(6.dp))
                    Text("No circles found", fontWeight = FontWeight.Bold)
                    Text("Create one for your course or try another search.", fontSize = 9.5.sp)
                }
            }
        } else {
            circles.forEach { circle ->
                StudyCircleCard(
                    circle = circle,
                    busy = busyId == circle.id,
                    onAction = {
                        scope.launch {
                            busyId = circle.id
                            runCatching {
                                when {
                                    circle.isOwner -> "owner"
                                    circle.isMember -> {
                                        repository.leave(circle.id)
                                        "left"
                                    }
                                    circle.requestStatus == "pending" && !circle.requestId.isNullOrBlank() -> {
                                        repository.cancelRequest(circle.requestId)
                                        "cancelled"
                                    }
                                    else -> repository.joinOrRequest(circle.id)
                                }
                            }.onSuccess { result ->
                                if (result != "owner") {
                                    Toast.makeText(
                                        context,
                                        when (result) {
                                            "requested" -> "Join request sent."
                                            "joined" -> "Joined ${circle.name}."
                                            "left" -> "Left ${circle.name}."
                                            "cancelled" -> "Join request cancelled."
                                            else -> "Study circle updated."
                                        },
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    refresh()
                                }
                            }.onFailure {
                                Toast.makeText(context, it.message ?: "Study circle action failed", Toast.LENGTH_SHORT).show()
                            }
                            busyId = null
                        }
                    }
                )
            }
        }
    }

    if (showCreate) {
        CreateStudyCircleDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, description, faculty, course, maxMembers, isPrivate ->
                scope.launch {
                    runCatching {
                        repository.createCircle(name, description, faculty, course, maxMembers, isPrivate)
                    }.onSuccess {
                        showCreate = false
                        Toast.makeText(context, "Study circle created.", Toast.LENGTH_SHORT).show()
                        refresh()
                    }.onFailure {
                        Toast.makeText(context, it.message ?: "Couldn't create study circle", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

@Composable
private fun StudyCircleCard(
    circle: StudyCircleSummary,
    busy: Boolean,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (circle.isPrivate) Icons.Default.Lock else Icons.Default.Public,
                    contentDescription = null,
                    tint = if (circle.isPrivate) BlinkPink else MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(circle.name, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(circle.course, circle.faculty)
                            .filter { it.isNotBlank() && it != "null" }
                            .joinToString(" • ")
                            .ifBlank { if (circle.isPrivate) "Private circle" else "Open circle" },
                        fontSize = 9.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "${circle.memberCount}/${circle.maxMembers}",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (circle.description.isNotBlank() && circle.description != "null") {
                Spacer(Modifier.height(7.dp))
                Text(
                    circle.description,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onAction,
                enabled = !busy && !circle.isOwner,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    when {
                        circle.isOwner -> "Owner"
                        circle.isMember -> "Leave circle"
                        circle.requestStatus == "pending" -> "Cancel request"
                        circle.isPrivate -> "Request to join"
                        else -> "Join circle"
                    },
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun CreateStudyCircleDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, Int, Boolean) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var faculty by rememberSaveable { mutableStateOf("") }
    var course by rememberSaveable { mutableStateOf("") }
    var maxMembers by rememberSaveable { mutableStateOf("30") }
    var isPrivate by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Study Circle", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("Circle name") }, singleLine = true)
                OutlinedTextField(course, { course = it.take(80) }, label = { Text("Course / topic") }, singleLine = true)
                OutlinedTextField(faculty, { faculty = it.take(80) }, label = { Text("Faculty") }, singleLine = true)
                OutlinedTextField(description, { description = it.take(500) }, label = { Text("Description") }, maxLines = 3)
                OutlinedTextField(
                    maxMembers,
                    { maxMembers = it.filter(Char::isDigit).take(3) },
                    label = { Text("Maximum members") },
                    singleLine = true
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Private circle", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text("Owner approves join requests.", fontSize = 9.sp)
                    }
                    Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        name.trim(),
                        description.trim(),
                        faculty.trim(),
                        course.trim(),
                        maxMembers.toIntOrNull()?.coerceIn(2, 200) ?: 30,
                        isPrivate
                    )
                },
                enabled = name.trim().length >= 3
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
