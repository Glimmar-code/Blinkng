package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import com.example.data.models.ChallengeGameType
import com.example.data.models.ConnectHubSnapshot
import com.example.data.models.ConnectRequestItem
import com.example.data.models.GameChallenge
import com.example.data.models.UserProfile
import com.example.ui.components.shimmerBackground
import com.example.ui.theme.BlinkOnlineGreen
import com.example.ui.theme.BlinkPink
import java.time.Duration
import java.time.Instant
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    // Optimistic "follow" action used from the browse and applicant cards below.
    // Wire this to your real follow endpoint; the UI updates immediately either way.
    val followUser: (String) -> Unit = {}
)

private enum class HubForm {
    NONE, ROOMMATE, MENTOR, READING, AGENT, HOUSING
}

/**
 * One swipeable page per Connect Hub category. Order here is the swipe order.
 */
private enum class ConnectCategory(
    val label: String,
    val shortLabel: String,
    val icon: ImageVector
) {
    ROOMMATE("Roommates", "Roommate", Icons.Default.HomeWork),
    MENTOR("Mentors", "Mentor", Icons.Default.School),
    READING("Reading mates", "Reading", Icons.Default.MenuBook),
    AGENTS("Housing agents", "Agents", Icons.Default.Verified),
    HOUSING("Need housing", "Housing", Icons.Default.Apartment),
    CHALLENGES("Challenges", "Games", Icons.Default.SportsEsports)
}

private val PagerHeight = 620.dp

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
    var hubQuery by rememberSaveable { mutableStateOf("") }
    var isMatching by remember { mutableStateOf(false) }
    var challengeTarget by remember { mutableStateOf<UserProfile?>(null) }
    var followingIds by remember { mutableStateOf(setOf<String>()) }
    val coroutineScope = rememberCoroutineScope()

    val toggleFollow: (String) -> Unit = { id ->
        followingIds = if (id in followingIds) followingIds - id else followingIds + id
        actions.followUser(id)
    }

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
                        lastSeenAt = candidate.lastSeenAt,
                        coreSkills = candidate.commonSkills.toMutableList(),
                        hobbies = candidate.commonHobbies
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

    val filteredRoommates = remember(hub.roommates, hubQuery) {
        hub.roommates.filter {
            matchesHubQuery(hubQuery, it.title, it.description, it.location, it.roomType)
        }
    }
    val filteredMentors = remember(hub.mentors, hubQuery) {
        hub.mentors.filter {
            matchesHubQuery(hubQuery, it.headline, it.description, it.subjects.joinToString(" "))
        }
    }
    val filteredReadingMates = remember(hub.readingMates, hubQuery) {
        hub.readingMates.filter {
            matchesHubQuery(
                hubQuery,
                it.courses.joinToString(" "),
                it.studyStyle,
                it.preferredLocation,
                it.description
            )
        }
    }
    val filteredHousingAgents = remember(hub.housingAgents, hubQuery) {
        hub.housingAgents.filter {
            matchesHubQuery(hubQuery, it.businessName, it.bio, it.serviceAreas.joinToString(" "))
        }
    }
    val filteredHousingRequests = remember(hub.housingRequests, hubQuery) {
        hub.housingRequests.filter {
            matchesHubQuery(hubQuery, it.title, it.description, it.preferredLocation)
        }
    }

    // "Who applied to you", split by category. Adjust the kind-matching keywords below
    // if your backend uses different request.kind strings.
    val incomingRoommateRequests = remember(hub.requests) {
        hub.requests.filter { it.direction.equals("incoming", true) && it.kind.contains("room", true) }
    }
    val incomingMentorRequests = remember(hub.requests) {
        hub.requests.filter { it.direction.equals("incoming", true) && it.kind.contains("mentor", true) }
    }
    val incomingReadingRequests = remember(hub.requests) {
        hub.requests.filter { it.direction.equals("incoming", true) && it.kind.contains("read", true) }
    }
    val incomingHousingRequests = remember(hub.requests) {
        hub.requests.filter {
            it.direction.equals("incoming", true) &&
                it.kind.contains("hous", true) &&
                !it.kind.contains("agent", true)
        }
    }
    val incomingChallenges = remember(hub.gameChallenges, current?.id) {
        hub.gameChallenges.filter { it.opponentId == current?.id && it.status == "pending" }
    }

    val currentIsVerifiedAgent = remember(hub.housingAgents, current?.id) {
        hub.housingAgents.any { it.userId == current?.id && it.verified }
    }

    val categories = remember { ConnectCategory.entries }
    val badgeCounts = remember(
        incomingRoommateRequests,
        incomingMentorRequests,
        incomingReadingRequests,
        incomingHousingRequests,
        incomingChallenges
    ) {
        listOf(
            incomingRoommateRequests.size,
            incomingMentorRequests.size,
            incomingReadingRequests.size,
            0,
            incomingHousingRequests.size,
            incomingChallenges.size
        )
    }
    val pagerState = rememberPagerState(pageCount = { categories.size })

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        SmartMatchHero(
            candidates = candidates,
            current = current,
            match = match,
            isMatching = isMatching,
            onSpin = {
                val pool = candidates.take(8)
                if (pool.isNotEmpty() && !isMatching) {
                    coroutineScope.launch {
                        isMatching = true
                        match = null
                        delay(450)
                        val weighted = pool.flatMap { candidate ->
                            List((candidate.second / 10).coerceAtLeast(1)) { candidate }
                        }
                        match = weighted.random()
                        isMatching = false
                    }
                }
            },
            onProfileClick = onProfileClick,
            onMessage = { p -> onMessageUser(p.username, p.fullName, p.avatarUrl) },
            onChallenge = { p -> challengeTarget = p }
        )

        Spacer(Modifier.height(18.dp))

        Text("Connect Hub", fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(
            "Swipe between roommates, mentors, reading mates, housing and challenges.",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = hubQuery,
            onValueChange = { hubQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search roommates, mentors, courses or areas") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp)
        )

        if (isLoading) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .shimmerBackground(
                        shape = RoundedCornerShape(16.dp),
                        baseColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
                        highlightColor = MaterialTheme.colorScheme.surface.copy(alpha = .95f)
                    )
            )
        }

        Spacer(Modifier.height(14.dp))

        CategoryTabBar(
            categories = categories,
            badgeCounts = badgeCounts,
            pagerState = pagerState,
            coroutineScope = coroutineScope
        )

        Spacer(Modifier.height(12.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(PagerHeight),
            pageSpacing = 14.dp
        ) { page ->
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
            val settle = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = lerp(0.45f, 1f, settle)
                        scaleY = lerp(0.95f, 1f, settle)
                    }
            ) {
                when (categories[page]) {
                    ConnectCategory.ROOMMATE -> CategoryScaffold(
                        title = "Roommates",
                        subtitle = "Students actively searching for a place to share",
                        browseLabel = "Listings",
                        browseCount = filteredRoommates.size,
                        applicantsLabel = "Applied to you",
                        applicantsCount = incomingRoommateRequests.size,
                        createLabel = "Post listing",
                        onCreateClick = { form = HubForm.ROOMMATE },
                        browseContent = {
                            if (filteredRoommates.isEmpty()) {
                                item { SectionEmptyHint("No roommate listings match yet. Be the first to post.") }
                            }
                            itemsIndexed(filteredRoommates, key = { _, it -> it.id }) { index, listing ->
                                val owner = profiles.firstOrNull { it.id == listing.userId }
                                StaggeredItem(index) {
                                    HubListingCard(
                                        title = owner?.fullName?.ifBlank { owner.username } ?: listing.title,
                                        subtitle = listOf(listing.location, listing.title)
                                            .filter { it.isNotBlank() }.joinToString(" • "),
                                        body = listing.description,
                                        avatarUrl = owner?.avatarUrl.orEmpty(),
                                        online = owner?.onlineNow == true,
                                        primaryLabel = "Apply",
                                        onPrimary = { actions.applyRoommate(listing.id) },
                                        isFollowing = owner != null && owner.id in followingIds,
                                        onFollow = owner?.let { o -> { toggleFollow(o.id) } },
                                        onMessage = owner?.let { o -> { onMessageUser(o.username, o.fullName, o.avatarUrl) } },
                                        onOpen = owner?.let { o -> { onProfileClick(o.username) } }
                                    )
                                }
                            }
                        },
                        applicantsContent = {
                            if (incomingRoommateRequests.isEmpty()) {
                                item { SectionEmptyHint("Nobody has applied to your roommate listing yet.") }
                            }
                            itemsIndexed(incomingRoommateRequests, key = { _, it -> it.requestId }) { index, request ->
                                val applicant = profiles.firstOrNull { it.id == request.otherUserId }
                                StaggeredItem(index) {
                                    ApplicantCard(
                                        request = request,
                                        applicant = applicant,
                                        isFollowing = applicant != null && applicant.id in followingIds,
                                        onAccept = { actions.respondRequest(request.kind, request.requestId, true) },
                                        onDecline = { actions.respondRequest(request.kind, request.requestId, false) },
                                        onFollow = { applicant?.let { toggleFollow(it.id) } },
                                        onMessage = { applicant?.let { onMessageUser(it.username, it.fullName, it.avatarUrl) } },
                                        onOpenProfile = { applicant?.let { onProfileClick(it.username) } }
                                    )
                                }
                            }
                        }
                    )

                    ConnectCategory.MENTOR -> CategoryScaffold(
                        title = "Mentors",
                        subtitle = "Senior students and skilled peers ready to help",
                        browseLabel = "Mentors",
                        browseCount = filteredMentors.size,
                        applicantsLabel = "Requested you",
                        applicantsCount = incomingMentorRequests.size,
                        createLabel = "Become a mentor",
                        onCreateClick = { form = HubForm.MENTOR },
                        browseContent = {
                            if (filteredMentors.isEmpty()) {
                                item { SectionEmptyHint("No mentors match yet.") }
                            }
                            itemsIndexed(filteredMentors, key = { _, it -> it.id }) { index, listing ->
                                val owner = profiles.firstOrNull { it.id == listing.userId }
                                StaggeredItem(index) {
                                    HubListingCard(
                                        title = owner?.fullName?.ifBlank { owner.username }
                                            ?: listing.headline.ifBlank { "Mentor" },
                                        subtitle = listing.subjects.take(3).joinToString(" • "),
                                        body = listing.description,
                                        avatarUrl = owner?.avatarUrl.orEmpty(),
                                        online = owner?.onlineNow == true,
                                        primaryLabel = "Request mentor",
                                        onPrimary = { actions.requestMentor(listing.id) },
                                        isFollowing = owner != null && owner.id in followingIds,
                                        onFollow = owner?.let { o -> { toggleFollow(o.id) } },
                                        onMessage = owner?.let { o -> { onMessageUser(o.username, o.fullName, o.avatarUrl) } },
                                        onOpen = owner?.let { o -> { onProfileClick(o.username) } }
                                    )
                                }
                            }
                        },
                        applicantsContent = {
                            if (incomingMentorRequests.isEmpty()) {
                                item { SectionEmptyHint("Nobody has requested your mentorship yet.") }
                            }
                            itemsIndexed(incomingMentorRequests, key = { _, it -> it.requestId }) { index, request ->
                                val applicant = profiles.firstOrNull { it.id == request.otherUserId }
                                StaggeredItem(index) {
                                    ApplicantCard(
                                        request = request,
                                        applicant = applicant,
                                        isFollowing = applicant != null && applicant.id in followingIds,
                                        onAccept = { actions.respondRequest(request.kind, request.requestId, true) },
                                        onDecline = { actions.respondRequest(request.kind, request.requestId, false) },
                                        onFollow = { applicant?.let { toggleFollow(it.id) } },
                                        onMessage = { applicant?.let { onMessageUser(it.username, it.fullName, it.avatarUrl) } },
                                        onOpenProfile = { applicant?.let { onProfileClick(it.username) } }
                                    )
                                }
                            }
                        }
                    )

                    ConnectCategory.READING -> CategoryScaffold(
                        title = "Reading mates",
                        subtitle = "Find someone to study with",
                        browseLabel = "Reading mates",
                        browseCount = filteredReadingMates.size,
                        applicantsLabel = "Wants to study",
                        applicantsCount = incomingReadingRequests.size,
                        createLabel = "Find a reading mate",
                        onCreateClick = { form = HubForm.READING },
                        browseContent = {
                            if (filteredReadingMates.isEmpty()) {
                                item { SectionEmptyHint("No reading mates match yet.") }
                            }
                            itemsIndexed(filteredReadingMates, key = { _, it -> it.id }) { index, listing ->
                                val owner = profiles.firstOrNull { it.id == listing.userId }
                                StaggeredItem(index) {
                                    HubListingCard(
                                        title = owner?.fullName?.ifBlank { owner.username } ?: "Reading mate",
                                        subtitle = listing.courses.take(3).joinToString(" • "),
                                        body = listOf(listing.studyStyle, listing.preferredLocation)
                                            .filter { it.isNotBlank() }.joinToString(" • "),
                                        avatarUrl = owner?.avatarUrl.orEmpty(),
                                        online = owner?.onlineNow == true,
                                        primaryLabel = "Study together",
                                        onPrimary = { actions.requestReadingMate(listing.id) },
                                        isFollowing = owner != null && owner.id in followingIds,
                                        onFollow = owner?.let { o -> { toggleFollow(o.id) } },
                                        onMessage = owner?.let { o -> { onMessageUser(o.username, o.fullName, o.avatarUrl) } },
                                        onOpen = owner?.let { o -> { onProfileClick(o.username) } }
                                    )
                                }
                            }
                        },
                        applicantsContent = {
                            if (incomingReadingRequests.isEmpty()) {
                                item { SectionEmptyHint("Nobody has asked to study with you yet.") }
                            }
                            itemsIndexed(incomingReadingRequests, key = { _, it -> it.requestId }) { index, request ->
                                val applicant = profiles.firstOrNull { it.id == request.otherUserId }
                                StaggeredItem(index) {
                                    ApplicantCard(
                                        request = request,
                                        applicant = applicant,
                                        isFollowing = applicant != null && applicant.id in followingIds,
                                        onAccept = { actions.respondRequest(request.kind, request.requestId, true) },
                                        onDecline = { actions.respondRequest(request.kind, request.requestId, false) },
                                        onFollow = { applicant?.let { toggleFollow(it.id) } },
                                        onMessage = { applicant?.let { onMessageUser(it.username, it.fullName, it.avatarUrl) } },
                                        onOpenProfile = { applicant?.let { onProfileClick(it.username) } }
                                    )
                                }
                            }
                        }
                    )

                    ConnectCategory.AGENTS -> CategoryScaffold(
                        title = "Housing agents",
                        subtitle = "Chat only with reviewed, verified agents",
                        browseLabel = "Agents",
                        browseCount = filteredHousingAgents.size,
                        createLabel = "Become an agent",
                        onCreateClick = { form = HubForm.AGENT },
                        browseContent = {
                            if (filteredHousingAgents.isEmpty()) {
                                item { SectionEmptyHint("No verified agents match yet.") }
                            }
                            itemsIndexed(filteredHousingAgents, key = { _, it -> it.userId }) { index, agent ->
                                val owner = profiles.firstOrNull { it.id == agent.userId }
                                StaggeredItem(index) {
                                    HubListingCard(
                                        title = agent.businessName,
                                        subtitle = agent.serviceAreas.take(3).joinToString(" • "),
                                        body = agent.bio,
                                        avatarUrl = owner?.avatarUrl.orEmpty(),
                                        online = owner?.onlineNow == true,
                                        primaryLabel = "Chat",
                                        onPrimary = {
                                            owner?.let { onMessageUser(it.username, it.fullName, it.avatarUrl) }
                                        },
                                        isFollowing = owner != null && owner.id in followingIds,
                                        onFollow = owner?.let { o -> { toggleFollow(o.id) } },
                                        onOpen = owner?.let { o -> { onProfileClick(o.username) } }
                                    )
                                }
                            }
                        }
                    )

                    ConnectCategory.HOUSING -> CategoryScaffold(
                        title = "Need housing",
                        subtitle = if (currentIsVerifiedAgent) "Open requests you can help with"
                        else "Students looking for accommodation",
                        browseLabel = "Requests",
                        browseCount = filteredHousingRequests.size,
                        applicantsLabel = "Agents offering help",
                        applicantsCount = incomingHousingRequests.size,
                        createLabel = "Request accommodation",
                        onCreateClick = { form = HubForm.HOUSING },
                        browseContent = {
                            if (filteredHousingRequests.isEmpty()) {
                                item { SectionEmptyHint("No housing requests match yet.") }
                            }
                            itemsIndexed(filteredHousingRequests, key = { _, it -> it.id }) { index, request ->
                                val student = profiles.firstOrNull { it.id == request.studentId }
                                val budget = listOfNotNull(
                                    request.budgetMin?.let { "₦${it.toInt()}" },
                                    request.budgetMax?.let { "₦${it.toInt()}" }
                                ).joinToString(" – ")
                                StaggeredItem(index) {
                                    HubListingCard(
                                        title = request.title,
                                        subtitle = listOf(request.preferredLocation, budget)
                                            .filter { it.isNotBlank() }.joinToString(" • "),
                                        body = request.description,
                                        avatarUrl = student?.avatarUrl.orEmpty(),
                                        online = student?.onlineNow == true,
                                        primaryLabel = if (currentIsVerifiedAgent) "Apply to help" else null,
                                        onPrimary = if (currentIsVerifiedAgent) {
                                            {
                                                actions.applyToHousingRequest(
                                                    request.id,
                                                    "Hi, I'm a verified Blink housing agent and I can help with this request."
                                                )
                                            }
                                        } else null,
                                        isFollowing = student != null && student.id in followingIds,
                                        onFollow = student?.let { s -> { toggleFollow(s.id) } },
                                        onMessage = student?.let { s -> { onMessageUser(s.username, s.fullName, s.avatarUrl) } },
                                        onOpen = student?.let { s -> { onProfileClick(s.username) } }
                                    )
                                }
                            }
                        },
                        applicantsContent = {
                            if (incomingHousingRequests.isEmpty()) {
                                item { SectionEmptyHint("No agents have offered to help yet.") }
                            }
                            itemsIndexed(incomingHousingRequests, key = { _, it -> it.requestId }) { index, request ->
                                val agentProfile = profiles.firstOrNull { it.id == request.otherUserId }
                                StaggeredItem(index) {
                                    ApplicantCard(
                                        request = request,
                                        applicant = agentProfile,
                                        isFollowing = agentProfile != null && agentProfile.id in followingIds,
                                        onAccept = { actions.respondRequest(request.kind, request.requestId, true) },
                                        onDecline = { actions.respondRequest(request.kind, request.requestId, false) },
                                        onFollow = { agentProfile?.let { toggleFollow(it.id) } },
                                        onMessage = { agentProfile?.let { onMessageUser(it.username, it.fullName, it.avatarUrl) } },
                                        onOpenProfile = { agentProfile?.let { onProfileClick(it.username) } }
                                    )
                                }
                            }
                        }
                    )

                    ConnectCategory.CHALLENGES -> Column(Modifier.fillMaxSize()) {
                        Text("Game challenges", fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Text(
                            "Accept a challenge to play a quick five-question game",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 28.dp)
                        ) {
                            if (incomingChallenges.isEmpty()) {
                                item { SectionEmptyHint("No pending challenges right now.") }
                            }
                            itemsIndexed(incomingChallenges, key = { _, it -> it.id }) { index, challenge ->
                                val challenger = profiles.firstOrNull { it.id == challenge.challengerId }
                                StaggeredItem(index) {
                                    ChallengeCard(
                                        challenge = challenge,
                                        challenger = challenger,
                                        onAccept = { actions.respondChallenge(challenge.id, true) },
                                        onDecline = { actions.respondChallenge(challenge.id, false) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
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

    challengeTarget?.let { target ->
        ChallengeModeDialog(
            targetName = target.fullName.ifBlank { target.username },
            onDismiss = { challengeTarget = null },
            onSelect = { mode ->
                actions.challengeUser(target.id, mode.apiName)
                challengeTarget = null
            }
        )
    }
}

@Composable
private fun SmartMatchHero(
    candidates: List<Pair<UserProfile, Int>>,
    current: UserProfile?,
    match: Pair<UserProfile, Int>?,
    isMatching: Boolean,
    onSpin: () -> Unit,
    onProfileClick: (String) -> Unit,
    onMessage: (UserProfile) -> Unit,
    onChallenge: (UserProfile) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "matchGlow")
    val glow by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "glowScale"
    )
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(900, easing = LinearEasing)),
        label = "spinAngle"
    )

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = .18f),
                        BlinkPink.copy(alpha = .10f),
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = .32f)
                    )
                ),
                shape = RoundedCornerShape(26.dp)
            )
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .22f), RoundedCornerShape(26.dp))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = .16f),
                    modifier = Modifier.graphicsLayer { scaleX = glow; scaleY = glow }
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(9.dp)
                            .graphicsLayer { rotationZ = if (isMatching) spinAngle else 0f }
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
                    onClick = onSpin,
                    enabled = candidates.isNotEmpty() && !isMatching,
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(if (isMatching) "Matching…" else "Spin")
                }
            }

            AnimatedContent(
                targetState = match,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = .94f)) togetherWith (fadeOut() + scaleOut(targetScale = .96f))
                },
                label = "smartMatchResult"
            ) { result ->
                if (result != null) {
                    val (person, serverScore) = result
                    Column {
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(12.dp))
                        MatchResultCard(
                            current = current,
                            person = person,
                            compatibilityOverride = serverScore,
                            onProfileClick = { onProfileClick(person.username) },
                            onMessage = { onMessage(person) },
                            onChallenge = { onChallenge(person) }
                        )
                    }
                } else if (isMatching) {
                    Text(
                        "Finding your strongest campus match…",
                        modifier = Modifier.padding(top = 14.dp),
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryTabBar(
    categories: List<*>,
    badgeCounts: List<Int>,
    pagerState: PagerState,
    coroutineScope: CoroutineScope
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEachIndexed { index, categoryAny ->
            val category = categoryAny as ConnectCategory
            val selected = pagerState.currentPage == index
            val bg by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
                animationSpec = tween(260),
                label = "tabBg"
            )
            val fg by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(260),
                label = "tabFg"
            )
            val scale by animateFloatAsState(
                targetValue = if (selected) 1f else 0.96f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "tabScale"
            )
            Surface(
                modifier = Modifier
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clickable {
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                    },
                shape = RoundedCornerShape(100.dp),
                color = bg
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(category.icon, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        category.shortLabel,
                        color = fg,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                    val count = badgeCounts.getOrElse(index) { 0 }
                    if (count > 0) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = CircleShape, color = fg.copy(alpha = .22f)) {
                            Text(
                                "$count",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = fg
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedModeSwitch(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)
    ) {
        Row(Modifier.padding(4.dp)) {
            options.forEachIndexed { i, label ->
                val isSelected = selected == i
                val bg by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    animationSpec = tween(200),
                    label = "segBg"
                )
                val elevation by animateDpAsState(if (isSelected) 2.dp else 0.dp, label = "segElevation")
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(i) },
                    shape = RoundedCornerShape(11.dp),
                    color = bg,
                    shadowElevation = elevation
                ) {
                    Text(
                        label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryScaffold(
    title: String,
    subtitle: String,
    browseLabel: String,
    browseCount: Int,
    applicantsLabel: String? = null,
    applicantsCount: Int = 0,
    createLabel: String? = null,
    onCreateClick: (() -> Unit)? = null,
    browseContent: LazyListScope.() -> Unit,
    applicantsContent: (LazyListScope.() -> Unit)? = null
) {
    var mode by rememberSaveable(title) { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text(
                    subtitle,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (createLabel != null && onCreateClick != null) {
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onCreateClick,
                    shape = RoundedCornerShape(100.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(createLabel, fontSize = 11.sp)
                }
            }
        }

        if (applicantsContent != null && applicantsLabel != null) {
            SegmentedModeSwitch(
                options = listOf("$browseLabel ($browseCount)", "$applicantsLabel ($applicantsCount)"),
                selected = mode,
                onSelect = { mode = it }
            )
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            if (mode == 0 || applicantsContent == null) browseContent() else applicantsContent()
        }
    }
}

@Composable
private fun StaggeredItem(index: Int, content: @Composable () -> Unit) {
    var shown by remember(index) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(index) {
        delay(35L * index.coerceAtMost(8))
        shown = true
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(260)) + slideInVertically(
            initialOffsetY = { it / 6 },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        )
    ) {
        content()
    }
}

@Composable
private fun SectionEmptyHint(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f),
            modifier = Modifier.size(30.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatusPill(status: String) {
    val (bg, label) = when (status.lowercase()) {
        "pending" -> BlinkPink.copy(alpha = .14f) to "Pending"
        "accepted" -> BlinkOnlineGreen.copy(alpha = .16f) to "Accepted"
        "declined" -> MaterialTheme.colorScheme.surfaceVariant to "Declined"
        else -> MaterialTheme.colorScheme.surfaceVariant to status.replaceFirstChar { it.uppercase() }
    }
    Surface(shape = RoundedCornerShape(100.dp), color = bg) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
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
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .clickable(onClick = onProfileClick),
            contentAlignment = Alignment.Center
        ) {
            if (person.avatarUrl.isBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(62.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = person.fullName,
                        modifier = Modifier.padding(17.dp)
                    )
                }
            } else {
                AsyncImage(
                    model = person.avatarUrl,
                    contentDescription = person.fullName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(62.dp)
                )
            }
        }
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
            val shared = sharedTraits(current, person)
            if (shared.isNotEmpty()) {
                Text(
                    "In common: ${shared.take(3).joinToString(" • ")}",
                    fontSize = 10.sp,
                    color = BlinkPink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                presenceLabel(person),
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
    online: Boolean = false,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    isFollowing: Boolean = false,
    onFollow: (() -> Unit)? = null,
    onMessage: (() -> Unit)? = null,
    onOpen: (() -> Unit)?
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "listingScale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
                ) {
                    if (avatarUrl.isBlank()) {
                        Surface(Modifier.fillMaxSize(), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                            Icon(Icons.Default.Person, contentDescription = title, modifier = Modifier.padding(12.dp))
                        }
                    } else {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (online) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .align(Alignment.BottomEnd)
                                .background(BlinkOnlineGreen, CircleShape)
                        )
                    }
                }
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
                if (onFollow != null) {
                    Surface(
                        shape = CircleShape,
                        color = if (isFollowing) MaterialTheme.colorScheme.primary.copy(alpha = .16f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable(onClick = onFollow)
                    ) {
                        Icon(
                            if (isFollowing) Icons.Default.Check else Icons.Default.PersonAdd,
                            contentDescription = if (isFollowing) "Following" else "Follow",
                            tint = if (isFollowing) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(7.dp).size(16.dp)
                        )
                    }
                }
            }

            if (body.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    body,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onPrimary != null && primaryLabel != null || onMessage != null) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onMessage != null) {
                        OutlinedButton(
                            onClick = onMessage,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(100.dp),
                            contentPadding = PaddingValues(vertical = 7.dp)
                        ) {
                            Icon(Icons.Outlined.ChatBubbleOutline, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Message", fontSize = 11.sp)
                        }
                    }
                    if (onPrimary != null && primaryLabel != null) {
                        Button(
                            onClick = onPrimary,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(100.dp),
                            contentPadding = PaddingValues(vertical = 7.dp)
                        ) {
                            Text(primaryLabel, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApplicantCard(
    request: ConnectRequestItem,
    applicant: UserProfile?,
    isFollowing: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onFollow: () -> Unit,
    onMessage: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "applicantScale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenProfile)
                ) {
                    if (applicant?.avatarUrl.isNullOrBlank()) {
                        Surface(Modifier.fillMaxSize(), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.padding(11.dp))
                        }
                    } else {
                        AsyncImage(
                            model = applicant?.avatarUrl,
                            contentDescription = applicant?.fullName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        applicant?.fullName?.ifBlank { applicant.username } ?: "A student",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        request.title.ifBlank { "Applied to your listing" },
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StatusPill(status = request.status)
            }

            val pending = request.status.equals("pending", ignoreCase = true)
            if (pending) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Decline")
                    }
                    Button(onClick = onAccept, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp)) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Accept")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onFollow, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp)) {
                    Icon(
                        if (isFollowing) Icons.Default.Check else Icons.Default.PersonAdd,
                        null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(if (isFollowing) "Following" else "Follow")
                }
                OutlinedButton(onClick = onMessage, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp)) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Message")
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "${challenger?.fullName?.ifBlank { challenger.username } ?: "A student"} challenged you",
                fontWeight = FontWeight.Bold
            )
            Text(
                ChallengeGameType.fromApiName(challenge.gameType).label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp)) {
                    Text("Decline")
                }
                Button(onClick = onAccept, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp)) {
                    Text("Accept & play")
                }
            }
        }
    }
}

@Composable
private fun ChallengeModeDialog(
    targetName: String,
    onDismiss: () -> Unit,
    onSelect: (ChallengeGameType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Challenge $targetName", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    "Choose a five-question game. Both players receive the same mode.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ChallengeGameType.entries.forEach { mode ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(mode) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(mode.emoji, fontSize = 18.sp)
                            Spacer(Modifier.width(9.dp))
                            Text(mode.label, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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

internal fun presenceLabel(profile: UserProfile): String {
    if (profile.onlineNow) return "Active now"
    val lastSeen = profile.lastSeenAt.trim()
    if (lastSeen.isBlank()) return "Offline"
    val elapsed = runCatching { Duration.between(Instant.parse(lastSeen), Instant.now()) }.getOrNull()
        ?: return "Last seen recently"
    val minutes = elapsed.toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "Last seen just now"
        minutes < 60 -> "Last seen ${minutes}m ago"
        minutes < 1_440 -> "Last seen ${elapsed.toHours()}h ago"
        minutes < 10_080 -> "Last seen ${elapsed.toDays()}d ago"
        else -> "Last seen over a week ago"
    }
}

private fun sharedTraits(current: UserProfile?, candidate: UserProfile): List<String> {
    val me = current ?: return emptyList()
    val sharedSkills = me.coreSkills.map { it.trim().lowercase() }.toSet()
        .intersect(candidate.coreSkills.map { it.trim().lowercase() }.toSet())
    val sharedHobbies = me.hobbies.map { it.trim().lowercase() }.toSet()
        .intersect(candidate.hobbies.map { it.trim().lowercase() }.toSet())
    val academic = buildList {
        if (same(me.department, candidate.department)) add(candidate.department)
        if (same(me.university, candidate.university)) add(candidate.university)
        if (same(me.academicLevel, candidate.academicLevel)) add(candidate.academicLevel)
    }
    return (academic + sharedSkills + sharedHobbies)
        .filter { it.isNotBlank() }
        .distinct()
}

private fun same(a: String, b: String): Boolean =
    a.isNotBlank() && b.isNotBlank() &&
        !a.equals("null", true) && !b.equals("null", true) &&
        a.equals(b, ignoreCase = true)

private fun matchesHubQuery(query: String, vararg values: String): Boolean {
    val clean = query.trim()
    return clean.isBlank() || values.any { it.contains(clean, ignoreCase = true) }
}
