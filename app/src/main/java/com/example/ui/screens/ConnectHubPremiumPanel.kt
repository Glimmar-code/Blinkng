package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.ConnectHubSnapshot
import com.example.data.models.ConnectRequestItem
import com.example.data.models.GameChallenge
import com.example.data.models.UserProfile
import com.example.ui.theme.BlinkOnlineGreen
import com.example.ui.theme.BlinkPink
import kotlin.math.min

data class ConnectHubActions(
    val refresh: () -> Unit = {},
    val publishRoommate: (String, String, String, Double?, Double?) -> Unit = { _, _, _, _, _ -> },
    val applyRoommate: (String) -> Unit = {},
    val publishMentor: (List<String>, String, String, String) -> Unit = { _, _, _, _ -> },
    val requestMentor: (String) -> Unit = {},
    val publishReadingMate: (List<String>, String, List<String>, String, String) -> Unit = { _, _, _, _, _ -> },
    val requestReadingMate: (String) -> Unit = {},
    val applyHousingAgent: (String, List<String>, String) -> Unit = { _, _, _ -> },
    val publishHousingRequest: (String, String, Double?, Double?, String) -> Unit = { _, _, _, _, _ -> },
    val applyToHousingRequest: (String, String) -> Unit = { _, _ -> },
    val challengeUser: (String, String) -> Unit = { _, _ -> },
    val respondChallenge: (String, Boolean) -> Unit = { _, _ -> },
    val respondRequest: (String, String, Boolean) -> Unit = { _, _, _ -> },
    val submitChallengeScore: (String, Int) -> Unit = { _, _ -> },
    val recordGameResult: (String, Int) -> Unit = { _, _ -> },
    val claimDailySpin: () -> Unit = {}
)

private enum class HubForm {
    NONE, ROOMMATE, MENTOR, READING, AGENT, HOUSING
}

@Composable
fun ConnectHubPremiumPanel(
    current: UserProfile?,
    profiles: List<UserProfile>,
    hub: ConnectHubSnapshot,
    actions: ConnectHubActions,
    isLoading: Boolean,
    onProfileClick: (String) -> Unit,
    onMessageUser: (String, String?, String?) -> Unit
) {
    var match by remember { mutableStateOf<Pair<UserProfile, Int>?>(null) }
    var form by rememberSaveable { mutableStateOf(HubForm.NONE) }

    val candidates = remember(hub.smartMatches, profiles, current) {
        if (hub.smartMatches.isNotEmpty()) {
            hub.smartMatches.mapNotNull { candidate ->
                if (candidate.username.isBlank()) null else {
                    UserProfile(
                        id = candidate.userId,
                        fullName = candidate.fullName,
                        username = candidate.username,
                        avatarUrl = candidate.avatarUrl,
                        university = candidate.university,
                        faculty = candidate.faculty,
                        department = candidate.department,
                        academicLevel = candidate.academicLevel,
                        relationshipStatus = candidate.relationshipStatus,
                        onlineNow = candidate.onlineNow,
                        lastSeenAt = candidate.lastSeenAt
                    ) to candidate.compatibilityScore
                }
            }
        } else {
            val me = current
            if (me == null) emptyList() else profiles
                .filter { it.id.isNotBlank() && it.id != me.id && it.username.isNotBlank() }
                .sortedByDescending { compatibilityScore(me, it) }
                .map { it to compatibilityScore(me, it) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .42f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .22f))
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = .14f)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(9.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Smart Match Spin", fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Text(
                            "Find someone based on what you have most in common.",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            val pool = candidates.take(8)
                            if (pool.isNotEmpty()) {
                                val weighted = pool.flatMap { candidate ->
                                    List((candidate.second / 10).coerceAtLeast(1)) { candidate }
                                }
                                match = weighted.random()
                            }
                        },
                        enabled = candidates.isNotEmpty(),
                        shape = RoundedCornerShape(100.dp)
                    ) {
                        Text("Spin")
                    }
                }

                AnimatedContent(
                    targetState = match,
                    transitionSpec = { (fadeIn() + scaleIn(initialScale = .94f)) togetherWith (fadeOut() + scaleOut(targetScale = .96f)) },
                    label = "smartMatchResult"
                ) { result ->
                    result?.let { (person, serverScore) ->
                        Column {
                            Spacer(Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(12.dp))
                            MatchResultCard(
                                current = current,
                                person = person,
                                compatibilityOverride = serverScore,
                                onProfileClick = { onProfileClick(person.username) },
                                onMessage = { onMessageUser(person.username, person.fullName, person.avatarUrl) },
                                onChallenge = { actions.challengeUser(person.id, "trivia") }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Text("Connect Hub", fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(
            "Roommates, mentors, reading mates, housing and challenges.",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HubActionChip("Roommate", Icons.Default.HomeWork) { form = HubForm.ROOMMATE }
            HubActionChip("Mentor", Icons.Default.School) { form = HubForm.MENTOR }
            HubActionChip("Reading mate", Icons.Default.PersonSearch) { form = HubForm.READING }
            HubActionChip("Become agent", Icons.Default.Verified) { form = HubForm.AGENT }
            HubActionChip("Need housing", Icons.Default.HomeWork) { form = HubForm.HOUSING }
        }

        if (isLoading) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Refreshing live Connect Hub…",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (hub.roommates.isNotEmpty()) {
            HubSectionTitle("Roommate requests", "Students actively searching")
            hub.roommates.take(3).forEach { listing ->
                val owner = profiles.firstOrNull { it.id == listing.userId }
                HubListingCard(
                    title = owner?.fullName?.ifBlank { owner.username } ?: listing.title,
                    subtitle = listOf(listing.location, listing.title).filter { it.isNotBlank() }.joinToString(" • "),
                    body = listing.description,
                    avatarUrl = owner?.avatarUrl.orEmpty(),
                    primaryLabel = "Apply",
                    onPrimary = { actions.applyRoommate(listing.id) },
                    onOpen = owner?.let { { onProfileClick(it.username) } }
                )
            }
        }

        if (hub.mentors.isNotEmpty()) {
            HubSectionTitle("Mentors", "Senior students and skilled peers")
            hub.mentors.take(3).forEach { listing ->
                val owner = profiles.firstOrNull { it.id == listing.userId }
                HubListingCard(
                    title = owner?.fullName?.ifBlank { owner.username } ?: listing.headline.ifBlank { "Mentor" },
                    subtitle = listing.subjects.take(3).joinToString(" • "),
                    body = listing.description,
                    avatarUrl = owner?.avatarUrl.orEmpty(),
                    primaryLabel = "Request mentor",
                    onPrimary = { actions.requestMentor(listing.id) },
                    onOpen = owner?.let { { onProfileClick(it.username) } }
                )
            }
        }

        if (hub.readingMates.isNotEmpty()) {
            HubSectionTitle("Reading mates", "Find someone to study with")
            hub.readingMates.take(3).forEach { listing ->
                val owner = profiles.firstOrNull { it.id == listing.userId }
                HubListingCard(
                    title = owner?.fullName?.ifBlank { owner.username } ?: "Reading mate",
                    subtitle = listing.courses.take(3).joinToString(" • "),
                    body = listOf(listing.studyStyle, listing.preferredLocation)
                        .filter { it.isNotBlank() }
                        .joinToString(" • "),
                    avatarUrl = owner?.avatarUrl.orEmpty(),
                    primaryLabel = "Study together",
                    onPrimary = { actions.requestReadingMate(listing.id) },
                    onOpen = owner?.let { { onProfileClick(it.username) } }
                )
            }
        }

        val currentIsVerifiedAgent = remember(hub.housingAgents, current?.id) {
            hub.housingAgents.any { it.userId == current?.id && it.verified }
        }
        if (currentIsVerifiedAgent && hub.housingRequests.isNotEmpty()) {
            HubSectionTitle("Students needing housing", "Verified agents can apply to help")
            hub.housingRequests.take(6).forEach { request ->
                val student = profiles.firstOrNull { it.id == request.studentId }
                val budget = listOfNotNull(
                    request.budgetMin?.let { "₦${it.toInt()}" },
                    request.budgetMax?.let { "₦${it.toInt()}" }
                ).joinToString(" – ")
                HubListingCard(
                    title = request.title,
                    subtitle = listOf(request.preferredLocation, budget).filter { it.isNotBlank() }.joinToString(" • "),
                    body = request.description,
                    avatarUrl = student?.avatarUrl.orEmpty(),
                    primaryLabel = "Apply to help",
                    onPrimary = {
                        actions.applyToHousingRequest(
                            request.id,
                            "Hi, I'm a verified Blink housing agent and I can help with this request."
                        )
                    },
                    onOpen = student?.let { { onProfileClick(it.username) } }
                )
            }
        }

        if (hub.housingAgents.isNotEmpty()) {
            HubSectionTitle("Verified housing agents", "Chat only with reviewed agents")
            hub.housingAgents.take(3).forEach { agent ->
                val owner = profiles.firstOrNull { it.id == agent.userId }
                HubListingCard(
                    title = agent.businessName,
                    subtitle = agent.serviceAreas.take(3).joinToString(" • "),
                    body = agent.bio,
                    avatarUrl = owner?.avatarUrl.orEmpty(),
                    primaryLabel = "Chat agent",
                    onPrimary = {
                        owner?.let { onMessageUser(it.username, it.fullName, it.avatarUrl) }
                    },
                    onOpen = owner?.let { { onProfileClick(it.username) } }
                )
            }
        }

        if (hub.requests.isNotEmpty()) {
            HubSectionTitle("Request inbox", "Incoming and outgoing Connect requests")
            hub.requests.take(8).forEach { request ->
                ConnectRequestCard(
                    request = request,
                    other = profiles.firstOrNull { it.id == request.otherUserId },
                    onAccept = { actions.respondRequest(request.kind, request.requestId, true) },
                    onDecline = { actions.respondRequest(request.kind, request.requestId, false) }
                )
            }
        }

        val incoming = remember(hub.gameChallenges, current?.id) {
            hub.gameChallenges.filter {
                it.challengedId == current?.id && it.status == "pending"
            }
        }
        if (incoming.isNotEmpty()) {
            HubSectionTitle("Game challenges", "People who challenged you")
            incoming.take(3).forEach { challenge ->
                val challenger = profiles.firstOrNull { it.id == challenge.challengerId }
                ChallengeCard(
                    challenge = challenge,
                    challenger = challenger,
                    onAccept = { actions.respondChallenge(challenge.id, true) },
                    onDecline = { actions.respondChallenge(challenge.id, false) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = actions.refresh, modifier = Modifier.align(Alignment.End)) {
            Text("Refresh Connect Hub")
        }
    }

    if (form != HubForm.NONE) {
        HubFormDialog(
            form = form,
            onDismiss = { form = HubForm.NONE },
            onSubmit = { a, b, c ->
                when (form) {
                    HubForm.ROOMMATE -> actions.publishRoommate(
                        a.ifBlank { "Looking for a roommate" },
                        "",
                        b,
                        null,
                        c.toDoubleOrNull()
                    )
                    HubForm.MENTOR -> actions.publishMentor(
                        a.split(",").map { it.trim() }.filter { it.isNotBlank() },
                        b,
                        c,
                        "mentor"
                    )
                    HubForm.READING -> actions.publishReadingMate(
                        a.split(",").map { it.trim() }.filter { it.isNotBlank() },
                        "Focused",
                        b.split(",").map { it.trim() }.filter { it.isNotBlank() },
                        c,
                        ""
                    )
                    HubForm.AGENT -> actions.applyHousingAgent(
                        a,
                        b.split(",").map { it.trim() }.filter { it.isNotBlank() },
                        c
                    )
                    HubForm.HOUSING -> actions.publishHousingRequest(
                        a.ifBlank { "Need accommodation" },
                        b,
                        null,
                        c.toDoubleOrNull(),
                        ""
                    )
                    HubForm.NONE -> Unit
                }
                form = HubForm.NONE
            }
        )
    }
}

@Composable
private fun HubActionChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp)) }
    )
}

@Composable
private fun HubSectionTitle(title: String, subtitle: String) {
    Spacer(Modifier.height(16.dp))
    Text(title, fontWeight = FontWeight.Black, fontSize = 15.sp)
    Text(
        subtitle,
        fontSize = 10.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(7.dp))
}

@Composable
private fun MatchResultCard(
    current: UserProfile?,
    person: UserProfile,
    compatibilityOverride: Int? = null,
    onProfileClick: () -> Unit,
    onMessage: () -> Unit,
    onChallenge: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = person.avatarUrl,
            contentDescription = person.fullName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .clickable(onClick = onProfileClick)
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                person.fullName.ifBlank { person.username },
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "@${person.username} • ${compatibilityOverride ?: compatibilityScore(current, person)}% match",
                fontSize = 11.sp,
                color = BlinkPink,
                fontWeight = FontWeight.Bold
            )
            val info = listOf(
                person.academicLevel,
                person.department,
                person.university,
                person.relationshipStatus
            ).filter { it.isNotBlank() && !it.equals("null", true) }
            if (info.isNotEmpty()) {
                Text(
                    info.joinToString(" • "),
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                if (person.onlineNow) "Active now"
                else person.lastSeenAt.takeIf { it.isNotBlank() }?.let { "Last seen ${it.replace("T", " ").take(16)}" }
                    ?: "Offline",
                fontSize = 10.sp,
                color = if (person.onlineNow) BlinkOnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onProfileClick, modifier = Modifier.weight(1f)) {
            Text("Profile")
        }
        OutlinedButton(onClick = onChallenge, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.SportsEsports, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text("Challenge")
        }
        Button(onClick = onMessage, modifier = Modifier.weight(1f)) {
            Text("Message")
        }
    }
}

@Composable
private fun HubListingCard(
    title: String,
    subtitle: String,
    body: String,
    avatarUrl: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onOpen: (() -> Unit)?
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle,
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Button(
                    onClick = onPrimary,
                    shape = RoundedCornerShape(100.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(primaryLabel, fontSize = 10.5.sp)
                }
            }
            if (body.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                Text(
                    body,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ConnectRequestCard(
    request: ConnectRequestItem,
    other: UserProfile?,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val incoming = request.direction.equals("incoming", true)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = other?.avatarUrl,
                    contentDescription = other?.fullName ?: "Student",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(38.dp).clip(CircleShape)
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        request.title.ifBlank { request.kind.replaceFirstChar { it.uppercase() } },
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${if (incoming) "From" else "To"} @${other?.username ?: "student"} • ${request.status}",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = if (incoming) BlinkPink.copy(alpha = .12f) else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        if (incoming) "Incoming" else "Sent",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            AnimatedVisibility(visible = incoming && request.status == "pending") {
                Row(
                    Modifier.padding(top = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f)) { Text("Decline") }
                    Button(onClick = onAccept, modifier = Modifier.weight(1f)) { Text("Accept") }
                }
            }
        }
    }
}

@Composable
private fun ChallengeCard(
    challenge: GameChallenge,
    challenger: UserProfile?,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "${challenger?.fullName?.ifBlank { challenger.username } ?: "A student"} challenged you",
                fontWeight = FontWeight.Bold
            )
            Text(
                challenge.gameType.replaceFirstChar { it.uppercase() },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f)) { Text("Decline") }
                Button(onClick = onAccept, modifier = Modifier.weight(1f)) { Text("Accept") }
            }
        }
    }
}

@Composable
private fun HubFormDialog(
    form: HubForm,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String) -> Unit
) {
    var first by rememberSaveable(form) { mutableStateOf("") }
    var second by rememberSaveable(form) { mutableStateOf("") }
    var third by rememberSaveable(form) { mutableStateOf("") }

    val labels = when (form) {
        HubForm.ROOMMATE -> Triple("Listing title", "Preferred location", "Maximum budget")
        HubForm.MENTOR -> Triple("Subjects / skills (comma separated)", "Headline", "About your mentoring")
        HubForm.READING -> Triple("Courses (comma separated)", "Preferred times (comma separated)", "Study location")
        HubForm.AGENT -> Triple("Business / agent name", "Service areas (comma separated)", "About your service")
        HubForm.HOUSING -> Triple("What accommodation do you need?", "Preferred location", "Maximum budget")
        HubForm.NONE -> Triple("", "", "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (form) {
                    HubForm.ROOMMATE -> "Find a roommate"
                    HubForm.MENTOR -> "Become a mentor"
                    HubForm.READING -> "Find a reading mate"
                    HubForm.AGENT -> "Apply as housing agent"
                    HubForm.HOUSING -> "Request accommodation"
                    HubForm.NONE -> ""
                },
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(first, { first = it }, label = { Text(labels.first) }, singleLine = true)
                OutlinedTextField(second, { second = it }, label = { Text(labels.second) }, singleLine = true)
                OutlinedTextField(third, { third = it }, label = { Text(labels.third) })
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(first.trim(), second.trim(), third.trim()) },
                enabled = first.isNotBlank()
            ) { Text("Submit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun compatibilityScore(current: UserProfile?, candidate: UserProfile): Int {
    val me = current ?: return 0
    var score = 0
    if (same(me.university, candidate.university)) score += 30
    if (same(me.department, candidate.department)) score += 25
    if (same(me.faculty, candidate.faculty)) score += 10
    if (same(me.academicLevel, candidate.academicLevel)) score += 10

    val mySkills = me.coreSkills.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
    val theirSkills = candidate.coreSkills.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
    score += min(15, mySkills.intersect(theirSkills).size * 5)

    val myHobbies = me.hobbies.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
    val theirHobbies = candidate.hobbies.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
    score += min(5, myHobbies.intersect(theirHobbies).size * 2)

    if (candidate.onlineNow) score += 5
    return score.coerceIn(0, 100)
}

private fun same(a: String, b: String): Boolean =
    a.isNotBlank() && b.isNotBlank() &&
        !a.equals("null", true) && !b.equals("null", true) &&
        a.equals(b, ignoreCase = true)
