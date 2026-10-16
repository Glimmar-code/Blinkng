package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import com.blinkng.shared.BlinkDesignTokens
import com.blinkng.shared.ConnectDiscoveryPolicy
import com.blinkng.shared.ConnectDiscoverySignals

import com.example.data.local.rememberPersistentTextState
import com.example.R
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.ConnectHubSnapshot
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.data.repository.FollowStateStore
import com.example.ui.components.VerifiedMark
import com.example.ui.components.PremiumPullRefreshIndicator
import com.example.ui.theme.BlinkOnlineGreen
import com.blinkng.shared.BlinkActivityPulseDefaults
import com.blinkng.shared.BlinkActivityPulsePolicy
import com.blinkng.shared.BlinkPulseTrend
import com.blinkng.shared.campusActivityLabel
import com.blinkng.shared.connectActivityScoreRange
import com.blinkng.shared.pulseTrend
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class LivePeopleFilter(val label: String) {
    ALL("All"),
    SAME_CAMPUS("Same campus"),
    SAME_DEPARTMENT("Same department"),
    ONLINE("Online"),
    VERIFIED("Verified")
}

private enum class LivePeopleSort(val label: String) {
    RECOMMENDED("Recommended"),
    RECENTLY_ACTIVE("Recently active"),
    SAME_CAMPUS("Campus first"),
    NAME("A–Z")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectSection(
    profiles: List<UserProfile>,
    currentProfile: UserProfile? = null,
    currentUsername: String,
    userAvatar: String,
    isDark: Boolean,
    onOpenMenu: () -> Unit,
    onOpenActivity: () -> Unit,
    onProfileClick: (String) -> Unit,
    onDirectMessage: (partner: String, partnerName: String?, partnerAvatar: String?) -> Unit,
    connectHub: ConnectHubSnapshot = ConnectHubSnapshot(),
    connectHubActions: ConnectHubActions = ConnectHubActions(),
    isConnectHubLoading: Boolean = false,
    isLiveDataAvailable: Boolean = true,
    activityPulsePolicy: BlinkActivityPulsePolicy = BlinkActivityPulseDefaults.policy,
    onActivityPulseEvent: (surface: String, eventType: String, realCount: Int, displayedValue: Int?, metadata: Map<String, String>) -> Unit = { _, _, _, _, _ -> },
    selectedTopTab: Int,
    onHomeClick: () -> Unit,
    onReelClick: () -> Unit,
    onConnectClick: () -> Unit,
    onGameClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberPersistentTextState(key = "connect:students:query:${currentUsername.trim().lowercase()}")
    var filter by rememberSaveable { mutableStateOf(LivePeopleFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(LivePeopleSort.RECOMMENDED) }
    // Local, session-safe "Not interested" feedback. Backend remains responsible for block/privacy rules.
    var dismissedIds by rememberSaveable(currentUsername) { mutableStateOf<List<String>>(emptyList()) }
    val pullToRefreshState = rememberPullToRefreshState()
    val followScope = rememberCoroutineScope()
    val followingIds by FollowStateStore.followingIds.collectAsState()

    val current = remember(profiles, currentUsername, currentProfile) {
        currentProfile?.takeIf { it.username.equals(currentUsername, ignoreCase = true) }
            ?: profiles.firstOrNull { it.username.equals(currentUsername, ignoreCase = true) }
    }

    val liveProfiles = remember(profiles, currentUsername) {
        profiles
            .asSequence()
            .filter { it.username.isNotBlank() }
            .filterNot { it.username.equals(currentUsername, ignoreCase = true) }
            .distinctBy { it.id.ifBlank { it.username.lowercase() } }
            .toList()
    }

    val visible = remember(
        liveProfiles,
        query,
        filter,
        sort,
        current?.university,
        current?.department,
        followingIds,
        current?.interests,
        dismissedIds,
        isLiveDataAvailable
    ) {
        val clean = query.trim()
        val myCampus = current?.university.orEmpty()
        val myDepartment = current?.department.orEmpty()

        val filtered = liveProfiles.filter { profile ->
            val key = profile.id.ifBlank { profile.username }
            if (dismissedIds.any { it.equals(key, ignoreCase = true) }) return@filter false
            val matchesQuery = clean.isBlank() ||
                profile.fullName.contains(clean, ignoreCase = true) ||
                profile.username.contains(clean, ignoreCase = true) ||
                profile.university.contains(clean, ignoreCase = true) ||
                profile.faculty.contains(clean, ignoreCase = true) ||
                profile.department.contains(clean, ignoreCase = true) ||
                profile.academicLevel.contains(clean, ignoreCase = true)

            val matchesFilter = when (filter) {
                LivePeopleFilter.ALL -> true
                LivePeopleFilter.ONLINE -> isLiveDataAvailable && profile.onlineNow
                LivePeopleFilter.VERIFIED -> profile.verificationBadge != VerificationBadge.NONE
                LivePeopleFilter.SAME_CAMPUS -> myCampus.isNotBlank() &&
                    !myCampus.equals("null", ignoreCase = true) &&
                    profile.university.equals(myCampus, ignoreCase = true)
                LivePeopleFilter.SAME_DEPARTMENT -> myDepartment.isNotBlank() &&
                    !myDepartment.equals("null", ignoreCase = true) &&
                    profile.department.equals(myDepartment, ignoreCase = true)
            }

            matchesQuery && matchesFilter
        }

        when (sort) {
            LivePeopleSort.RECOMMENDED -> ConnectDiscoveryPolicy.rank(
                candidates = filtered,
                campus = myCampus,
                department = myDepartment,
                myInterests = current?.interests.orEmpty()
            ) { profile ->
                ConnectDiscoverySignals(
                    id = profile.id.ifBlank { profile.username },
                    displayName = profile.fullName.ifBlank { profile.username },
                    university = profile.university,
                    department = profile.department,
                    interests = profile.interests,
                    online = isLiveDataAvailable && profile.onlineNow,
                    following = profile.id in followingIds
                )
            }
            LivePeopleSort.RECENTLY_ACTIVE -> filtered.sortedWith(
                compareByDescending<UserProfile> { isLiveDataAvailable && it.onlineNow }
                    .thenByDescending { it.lastSeenAt }
                    .thenBy { it.fullName.lowercase() }
            )
            LivePeopleSort.SAME_CAMPUS -> filtered.sortedWith(
                compareByDescending<UserProfile> {
                    myCampus.isNotBlank() &&
                        it.university.equals(myCampus, ignoreCase = true)
                }.thenByDescending { isLiveDataAvailable && it.onlineNow }
                    .thenBy { it.fullName.lowercase() }
            )
            LivePeopleSort.NAME -> filtered.sortedBy {
                it.fullName.ifBlank { it.username }.lowercase()
            }
        }
    }

    val policy = remember(activityPulsePolicy) { activityPulsePolicy.normalized() }
    val windowActive = rememberWindowActive()
    val activeNowCount = remember(liveProfiles, isLiveDataAvailable) {
        if (isLiveDataAvailable) liveProfiles.count { it.onlineNow } else 0
    }
    // The signed-in, foreground Connect session counts as one actual active session.
    // Other people are counted only when their profile reports online presence.
    val isCurrentSessionActive = isLiveDataAvailable && currentUsername.isNotBlank() && windowActive
    val realOnlineCount = activeNowCount + if (isCurrentSessionActive) 1 else 0
    val reduceMotion = rememberReducedMotionEnabled()
    val activityScoreRange = remember(realOnlineCount) { connectActivityScoreRange(realOnlineCount) }
    val activityScoreState = rememberManagedPulse(
        key = "connect_activity_score_${currentUsername.lowercase()}",
        range = activityScoreRange,
        tickMillis = policy.connectTickMillis,
        policy = policy,
        liveDataAvailable = isLiveDataAvailable,
        windowActive = windowActive,
        reduceMotion = reduceMotion,
    )
    // Always show values from the current band (even when an older session has a
    // cached pulse value). This score is never shown as a count of people.
    val activityScore = if (isLiveDataAvailable) {
        (activityScoreState.value ?: activityScoreRange.first).coerceIn(activityScoreRange)
    } else null
    var previousRealOnline by rememberSaveable(currentUsername) { mutableStateOf(realOnlineCount) }
    var activityTrend by rememberSaveable(currentUsername) { mutableStateOf(BlinkPulseTrend.STABLE) }
    LaunchedEffect(realOnlineCount) {
        activityTrend = pulseTrend(previousRealOnline, realOnlineCount)
        previousRealOnline = realOnlineCount
    }

    val displayedOnlineCount by animateIntAsState(
        targetValue = realOnlineCount,
        animationSpec = tween(durationMillis = 720),
        label = "connectLiveActiveCount"
    )
    val campusName = current?.university.orEmpty().takeUnless { it.equals("null", true) }.orEmpty()
    val realCampusOnline = remember(liveProfiles, campusName, isCurrentSessionActive) {
        val others = liveProfiles.count {
            it.onlineNow && campusName.isNotBlank() && it.university.equals(campusName, ignoreCase = true)
        }
        others + if (isCurrentSessionActive && campusName.isNotBlank()) 1 else 0
    }
    val campusLabel = remember(realCampusOnline, policy) {
        campusActivityLabel(realCampusOnline, policy)
    }
    val onlinePreview = remember(liveProfiles, policy.onlinePreviewLimit, isLiveDataAvailable, dismissedIds) {
        if (!isLiveDataAvailable) emptyList() else liveProfiles
            .filter { it.onlineNow && dismissedIds.none { hidden -> hidden.equals(it.id.ifBlank { it.username }, ignoreCase = true) } }
            .take(policy.onlinePreviewLimit)
    }

    LaunchedEffect(realOnlineCount, currentUsername, isLiveDataAvailable) {
        if (isLiveDataAvailable) {
            onActivityPulseEvent(
                "connect",
                "impression",
                realOnlineCount,
                activityScore,
                mapOf(
                    "trend" to activityTrend.label,
                    "metric" to "estimated_activity_points",
                    "min" to activityScoreRange.first.toString(),
                    "max" to activityScoreRange.last.toString(),
                ),
            )
        }
    }

    LaunchedEffect(Unit) {
        FollowStateStore.refresh()
    }

    LaunchedEffect(query, currentUsername) {
        val search = query.trim()
        if (search.length >= 2) delay(350)
        // Even a cleared query invalidates any older in-flight search.
        connectHubActions.searchStudents(search)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PullToRefreshBox(
            isRefreshing = isConnectHubLoading,
            onRefresh = {
                connectHubActions.refresh()
                connectHubActions.refreshStudents()
            },
            state = pullToRefreshState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            indicator = {
                PremiumPullRefreshIndicator(
                    state = pullToRefreshState,
                    isRefreshing = isConnectHubLoading,
                    modifier = Modifier.align(Alignment.TopCenter),
                    refreshingLabel = "Updating Connect"
                )
            }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 10.dp, bottom = 120.dp)
            ) {
                if (policy.enabled) {
                    item(key = "connect_community_activity", contentType = "activity_pulse") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "Community activity",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Text(
                                            when {
                                                !isLiveDataAvailable -> "Online status temporarily unavailable"
                                                else -> "Estimated activity points • not people"
                                            },
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(100.dp),
                                        color = BlinkOnlineGreen.copy(alpha = .13f)
                                    ) {
                                        if (reduceMotion) {
                                            Text(
                                                activityScore?.toString() ?: "—",
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                                color = BlinkOnlineGreen,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        } else {
                                            Crossfade(
                                                targetState = activityScore,
                                                label = "communityActivityPulse"
                                            ) { pulseValue ->
                                                Text(
                                                    pulseValue?.toString() ?: "—",
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                                    color = BlinkOnlineGreen,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    if (isLiveDataAvailable) "Online among loaded profiles (including yours): $realOnlineCount" else "Online count unavailable",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (campusName.isNotBlank()) {
                                    Text(
                                        if (isLiveDataAvailable) "$campusName activity: $campusLabel • $realCampusOnline online among loaded profiles" else "$campusName activity is temporarily unavailable",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (onlinePreview.isNotEmpty()) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Text(
                                        "Other students online",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(onlinePreview, key = { it.id.ifBlank { it.username } }) { profile ->
                                            Column(
                                                modifier = Modifier
                                                    .width(58.dp)
                                                    .clickable {
                                                        onActivityPulseEvent(
                                                            "connect",
                                                            "online_preview_open",
                                                            realOnlineCount,
                                                            if (isLiveDataAvailable) realOnlineCount else null,
                                                            mapOf("username" to profile.username)
                                                        )
                                                        onProfileClick(profile.username)
                                                    },
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                AsyncImage(
                                                    model = profile.avatarUrl,
                                                    error = painterResource(R.drawable.ic_default_profile),
                                                    fallback = painterResource(R.drawable.ic_default_profile),
                                                    contentDescription = profile.fullName,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(CircleShape)
                                                )
                                                Text(
                                                    "@${profile.username}",
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontSize = 9.5.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }

                item(key = "connect_hub_and_students") {
                    ConnectHubPremiumPanel(
                        current = current,
                        profiles = liveProfiles,
                        hub = connectHub,
                        actions = connectHubActions,
                        isLoading = isConnectHubLoading,
                        onProfileClick = onProfileClick,
                        onMessageUser = onDirectMessage,
                    discoveryContent = {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Discover students",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        "Search Blink, then refine by campus, department, activity or verification.",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = BlinkOnlineGreen.copy(alpha = .13f)
                                ) {
                                    Text(
                                        "${if (isLiveDataAvailable) "Online here" else "Presence unavailable"} · ${if (isLiveDataAvailable) (if (reduceMotion) realOnlineCount else displayedOnlineCount) else "—"}",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        color = BlinkOnlineGreen,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Search name, username, campus, department or level") },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(18.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LivePeopleFilter.values().forEach { option ->
                                    FilterChip(
                                        selected = filter == option,
                                        onClick = { filter = option },
                                        label = { Text(option.label) }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LivePeopleSort.values().forEach { option ->
                                    FilterChip(
                                        selected = sort == option,
                                        onClick = { sort = option },
                                        label = { Text(option.label) }
                                    )
                                }
                            }

                            if (dismissedIds.isNotEmpty()) {
                                TextButton(
                                    onClick = { dismissedIds = emptyList() },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Restore hidden suggestions (${dismissedIds.size})")
                                }
                            }

                            if (visible.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        LivePeopleEmptyState(
                                            hasProfiles = liveProfiles.isNotEmpty(),
                                            query = query,
                                            filter = filter
                                        )
                                        if (query.isNotBlank() || filter != LivePeopleFilter.ALL) {
                                            TextButton(
                                                onClick = {
                                                    query = ""
                                                    filter = LivePeopleFilter.ALL
                                                    sort = LivePeopleSort.RECOMMENDED
                                                }
                                            ) {
                                                Text("Clear filters")
                                            }
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentPadding = PaddingValues(bottom = 18.dp)
                                ) {
                                    items(
                                        items = visible,
                                        key = { it.id.ifBlank { it.username } }
                                    ) { profile ->
                                        val profileId = profile.id
                                        val isFollowing = profileId.isNotBlank() && profileId in followingIds
                                        LiveProfileCard(
                                            profile = profile,
                                            isFollowing = isFollowing,
                                            presenceFresh = isLiveDataAvailable,
                                            recommendation = if (sort == LivePeopleSort.RECOMMENDED) {
                                                ConnectDiscoveryPolicy.reasons(
                                                    ConnectDiscoverySignals(
                                                        id = profile.id.ifBlank { profile.username },
                                                        displayName = profile.fullName,
                                                        university = profile.university,
                                                        department = profile.department,
                                                        interests = profile.interests,
                                                        online = isLiveDataAvailable && profile.onlineNow
                                                    ),
                                                    current?.university.orEmpty(),
                                                    current?.department.orEmpty(),
                                                    current?.interests.orEmpty()
                                                ).take(2).joinToString(" • ").takeIf { it.isNotBlank() }
                                            } else null,
                                            onNotInterested = {
                                                dismissedIds = (dismissedIds + profile.id.ifBlank { profile.username }).distinct().takeLast(200)
                                            },
                                            onFollow = {
                                                if (profileId.isNotBlank()) {
                                                    followScope.launch {
                                                        FollowStateStore.setFollowing(
                                                            profileId,
                                                            !FollowStateStore.isFollowing(profileId)
                                                        )
                                                    }
                                                }
                                            },
                                            onProfileClick = { onProfileClick(profile.username) },
                                            onMessage = {
                                                onDirectMessage(
                                                    profile.username,
                                                    profile.fullName,
                                                    profile.avatarUrl
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ConnectHeader(
    userAvatar: String,
    onMenuClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 38.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMenuClick, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.MoreHoriz, contentDescription = "Menu")
        }

        Spacer(Modifier.weight(1f))

        Text(
            "Connect",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(Modifier.weight(1f))

        IconButton(onClick = onNotificationClick, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications")
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onProfileClick),
            contentAlignment = Alignment.Center
        ) {
            if (userAvatar.isBlank()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Profile",
                        modifier = Modifier.padding(9.dp)
                    )
                }
            } else {
                AsyncImage(
                    model = userAvatar,
                    error = painterResource(R.drawable.ic_default_profile),
                    fallback = painterResource(R.drawable.ic_default_profile),
                    contentDescription = "Profile",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun ConnectTopNavigation(
    selected: Int,
    onHome: () -> Unit,
    onReel: () -> Unit,
    onConnect: () -> Unit,
    onGame: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        ConnectTopTab("Home", selected == 0, onHome)
        Spacer(Modifier.width(8.dp))
        ConnectTopTab("Reel", selected == 1, onReel)
        Spacer(Modifier.width(8.dp))
        ConnectTopTab("Connect", selected == 2, onConnect)
        Spacer(Modifier.width(8.dp))
        ConnectTopTab("Game", selected == 3, onGame)
    }
}

@Composable
private fun ConnectTopTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(BlinkDesignTokens.Motion.ContentReveal),
        label = "connectTopTabBackground"
    )
    val foreground by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(BlinkDesignTokens.Motion.ContentReveal),
        label = "connectTopTabText"
    )
    Surface(
        modifier = Modifier.clickable(onClick = onClick).height(48.dp),
        shape = RoundedCornerShape(100.dp),
        color = background
    ) {
        Text(
            text = text,
            color = foreground,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun LiveProfileCard(
    profile: UserProfile,
    isFollowing: Boolean,
    presenceFresh: Boolean,
    recommendation: String?,
    onNotInterested: () -> Unit,
    onFollow: () -> Unit,
    onProfileClick: () -> Unit,
    onMessage: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onProfileClick),
                    contentAlignment = Alignment.Center
                ) {
                    if (profile.avatarUrl.isBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = profile.fullName,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else {
                        AsyncImage(
                            model = profile.avatarUrl,
                            error = painterResource(R.drawable.ic_default_profile),
                            fallback = painterResource(R.drawable.ic_default_profile),
                            contentDescription = profile.fullName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (presenceFresh && profile.onlineNow) {
                        Box(
                            Modifier
                                .size(13.dp)
                                .align(Alignment.BottomEnd)
                                .background(BlinkOnlineGreen, CircleShape)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            profile.fullName.ifBlank { profile.username },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (profile.verificationBadge != VerificationBadge.NONE) {
                            Spacer(Modifier.width(4.dp))
                            VerifiedMark(profile.verificationBadge, size = 12.dp)
                        }
                    }

                    Text(
                        "@${profile.username}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val detail = listOf(
                        profile.academicLevel.takeUnless { it.equals("null", true) }.orEmpty(),
                        profile.department.takeUnless { it.equals("null", true) }.orEmpty(),
                        profile.university.takeUnless { it.equals("null", true) }.orEmpty(),
                        profile.relationshipStatus.takeUnless { it.equals("null", true) }.orEmpty()
                    ).filter { it.isNotBlank() }.joinToString(" • ")

                    if (detail.isNotBlank()) {
                        Text(
                            detail,
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = if (presenceFresh) presenceLabel(profile) else "Presence not current",
                        fontSize = 10.sp,
                        color = if (presenceFresh && profile.onlineNow) BlinkOnlineGreen
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!recommendation.isNullOrBlank()) {
                Text(
                    "Why suggested: $recommendation",
                    modifier = Modifier.padding(top = 8.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            TextButton(onClick = onNotInterested) {
                Text("Not interested", fontSize = 11.sp)
            }

            if (profile.bio.isNotBlank() && !profile.bio.equals("null", true)) {
                Spacer(Modifier.height(10.dp))
                Text(
                    profile.bio,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                OutlinedButton(
                    onClick = onProfileClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(100.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Profile", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = onFollow,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(100.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(
                        if (isFollowing) Icons.Default.Check else Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (isFollowing) "Following" else "Follow", fontSize = 11.sp)
                }

                Button(
                    onClick = onMessage,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(100.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Message", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun LivePeopleEmptyState(
    hasProfiles: Boolean,
    query: String,
    filter: LivePeopleFilter
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier
                    .size(58.dp)
                    .padding(15.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            when {
                !hasProfiles -> "No live students yet"
                query.isNotBlank() -> "No matching students"
                filter == LivePeopleFilter.SAME_CAMPUS -> "No campus matches yet"
                filter == LivePeopleFilter.SAME_DEPARTMENT -> "No department matches yet"
                filter == LivePeopleFilter.ONLINE -> "Nobody is marked online right now"
                filter == LivePeopleFilter.VERIFIED -> "No verified students match yet"
                else -> "No students found"
            },
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp
        )

        Spacer(Modifier.height(6.dp))

        Text(
            "This screen only shows profiles returned by Supabase. No sample users are inserted.",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
