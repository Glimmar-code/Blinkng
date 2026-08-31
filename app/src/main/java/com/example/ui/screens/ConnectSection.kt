package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.CampusPeer
import com.example.data.models.NigerianUniversities
import com.example.data.models.RoommateApplicant
import com.example.data.models.StudyCircle
import com.example.data.models.VerificationBadge
import com.example.ui.components.VerifiedMark
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private enum class RoommateTab { BROWSE, APPLY }

private val connectPageTitles = listOf("Discover", "Find a Roommate", "More")

// ─────────────────────────────────────────────────────────────────────────
// SMALL ANIMATION HELPERS
// ─────────────────────────────────────────────────────────────────────────

/** Adds a gentle press-down scale to any clickable surface for a "premium" tactile feel. */
private fun Modifier.pressScale(scaleDown: Float = 0.94f): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pressScale"
    )
    this
        .scale(scale)
        .clickable(interactionSource = interactionSource, indication = null) { }
}

// ─────────────────────────────────────────────────────────────────────────
// MAIN ENTRY POINT
// ─────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConnectSection(
    userAvatar: String,
    isDark: Boolean,
    onOpenMenu: () -> Unit,
    onOpenActivity: () -> Unit,
    onProfileClick: (String) -> Unit,
    onDirectMessage: (partner: String, partnerName: String?, partnerAvatar: String?) -> Unit,
    selectedTopTab: Int,
    onHomeClick: () -> Unit,
    onReelClick: () -> Unit,
    onConnectClick: () -> Unit,
    onGameClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBg = if (isDark) DarkSurface else LightSurface
    val cardBorder = if (isDark) DarkBorder else LightBorder

    val yourInterests = remember { listOf("Kotlin", "AI", "Chess", "Startups", "Music", "Football", "Design") }

    val samplePeers = remember { sampleCampusPeers() }
    val sampleCircles = remember { sampleStudyCircles() }
    val sampleApplicants = remember { mutableStateListOf(*sampleRoommateApplicants().toTypedArray()) }

    val connectedPeerIds = remember { mutableStateListOf<String>() }
    val interestedApplicantIds = remember { mutableStateListOf<String>() }

    val pagerState = rememberPagerState(pageCount = { connectPageTitles.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ConnectHeader(
            userAvatar = userAvatar,
            onMenuClick = onOpenMenu,
            onNotificationClick = onOpenActivity,
            onProfileClick = { onProfileClick("you") }
        )

        TopNavigationRow(
            selected = selectedTopTab,
            onHome = onHomeClick,
            onReel = onReelClick,
            onConnect = onConnectClick,
            onGame = onGameClick
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Swipeable section tabs (Discover / Find a Roommate / More)
        SwipeSectionTabs(
            titles = connectPageTitles,
            pagerState = pagerState,
            isDark = isDark,
            onTabClick = { index -> scope.launch { pagerState.animateScrollToPage(index) } }
        )

        Spacer(modifier = Modifier.height(6.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 0.dp
        ) { page ->
            when (page) {
                0 -> DiscoverPage(
                    peers = samplePeers,
                    yourInterests = yourInterests,
                    connectedPeerIds = connectedPeerIds,
                    isDark = isDark,
                    cardBg = cardBg,
                    cardBorder = cardBorder,
                    onProfileClick = onProfileClick,
                    onDirectMessage = onDirectMessage,
                    onToggleConnect = { id ->
                        if (connectedPeerIds.contains(id)) connectedPeerIds.remove(id) else connectedPeerIds.add(id)
                    }
                )

                1 -> RoommatePage(
                    applicants = sampleApplicants,
                    interestedIds = interestedApplicantIds,
                    isDark = isDark,
                    cardBg = cardBg,
                    cardBorder = cardBorder,
                    onProfileClick = onProfileClick,
                    onDirectMessage = onDirectMessage,
                    onToggleInterested = { id ->
                        if (interestedApplicantIds.contains(id)) interestedApplicantIds.remove(id)
                        else interestedApplicantIds.add(id)
                    },
                    onSubmitApplication = { applicant -> sampleApplicants.add(0, applicant) }
                )

                2 -> MorePage(
                    circles = sampleCircles,
                    isDark = isDark,
                    cardBg = cardBg,
                    cardBorder = cardBorder
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// HEADER + TOP NAV (unchanged behaviour, kept intact)
// ─────────────────────────────────────────────────────────────────────────

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
        IconButton(onClick = onMenuClick, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Default.MoreHoriz, contentDescription = "Menu", modifier = Modifier.size(27.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Connect",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.weight(1f))

        IconButton(onClick = onNotificationClick, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications", modifier = Modifier.size(25.dp))
        }

        Spacer(modifier = Modifier.width(2.dp))

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable { onProfileClick() }
        ) {
            AsyncImage(model = userAvatar, contentDescription = "Profile", modifier = Modifier.fillMaxSize())
        }

        Spacer(modifier = Modifier.width(4.dp))
    }
}

@Composable
private fun TopNavigationRow(
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
            .padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TopTabItem(text = "Home", selected = selected == 0, onClick = onHome)
        Spacer(modifier = Modifier.width(8.dp))
        TopTabItem(text = "Reel", selected = selected == 1, onClick = onReel)
        Spacer(modifier = Modifier.width(8.dp))
        TopTabItem(text = "Connect", selected = selected == 2, onClick = onConnect)
        Spacer(modifier = Modifier.width(8.dp))
        TopTabItem(text = "Game", selected = selected == 3, onClick = onGame)
    }
}

@Composable
private fun TopTabItem(text: String, selected: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(220), label = "tabBg"
    )
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(100.dp),
        color = bgColor
    ) {
        Text(
            text = text,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// SWIPEABLE SECTION TABS + PAGE INDICATOR
// ─────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeSectionTabs(
    titles: List<String>,
    pagerState: PagerState,
    isDark: Boolean,
    onTabClick: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            titles.forEachIndexed { index, title ->
                val isSelected = pagerState.currentPage == index ||
                        (pagerState.currentPageOffsetFraction > 0.5f && pagerState.currentPage + 1 == index) ||
                        (pagerState.currentPageOffsetFraction < -0.5f && pagerState.currentPage - 1 == index)

                val bg by animateColorAsState(
                    targetValue = if (isSelected) BlinkPink else Color.Transparent,
                    animationSpec = tween(200), label = "sectionTabBg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(200), label = "sectionTabText"
                )

                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = bg,
                    border = if (!isSelected) BorderStroke(1.dp, if (isDark) DarkBorder else LightBorder) else null,
                    modifier = Modifier
                        .weight(1f)
                        .pressScale()
                        .clickable { onTabClick(index) }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 9.dp)) {
                        Text(
                            text = title,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Dot indicator that eases between pages
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            titles.indices.forEach { index ->
                val distance = kotlin.math.abs((pagerState.currentPage + pagerState.currentPageOffsetFraction) - index)
                val isActive = distance < 0.5f
                val width by animateDpAsState(
                    targetValue = if (isActive) 18.dp else 6.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "dotWidth"
                )
                val color by animateColorAsState(
                    targetValue = if (isActive) BlinkPink else (if (isDark) DarkBorder else LightBorder),
                    animationSpec = tween(200), label = "dotColor"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .width(width)
                        .clip(RoundedCornerShape(100.dp))
                        .background(color)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// PAGE 1 — DISCOVER (search + spin-to-meet-someone + browsable list)
// ─────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiscoverPage(
    peers: List<CampusPeer>,
    yourInterests: List<String>,
    connectedPeerIds: SnapshotStateList<String>,
    isDark: Boolean,
    cardBg: Color,
    cardBorder: Color,
    onProfileClick: (String) -> Unit,
    onDirectMessage: (String, String?, String?) -> Unit,
    onToggleConnect: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFaculty by remember { mutableStateOf("All") }
    var selectedUniversity by remember { mutableStateOf("All Universities") }
    var selectedLevel by remember { mutableStateOf("All Levels") }

    var isSpinning by remember { mutableStateOf(false) }
    var spinResult by remember { mutableStateOf<CampusPeer?>(null) }
    val spinRotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val faculties = listOf("All", "Sciences", "Engineering", "Arts & Lit", "Social Sciences", "Law", "Medicine", "Management")
    val universities = remember { listOf("All Universities") + NigerianUniversities.all }
    val levels = listOf("All Levels", "100 Level", "200 Level", "300 Level", "400 Level", "500 Level")

    val filteredPeers = peers.filter { peer ->
        val matchesFaculty = selectedFaculty == "All" || peer.faculty.contains(selectedFaculty, ignoreCase = true)
        val matchesUniversity = selectedUniversity == "All Universities" || peer.university == selectedUniversity
        val matchesLevel = selectedLevel == "All Levels" || peer.level == selectedLevel
        val matchesSearch = searchQuery.isBlank() ||
                peer.name.contains(searchQuery, ignoreCase = true) ||
                peer.username.contains(searchQuery, ignoreCase = true) ||
                peer.department.contains(searchQuery, ignoreCase = true) ||
                peer.university.contains(searchQuery, ignoreCase = true) ||
                peer.interests.any { it.contains(searchQuery, ignoreCase = true) }
        matchesFaculty && matchesUniversity && matchesLevel && matchesSearch
    }

    fun triggerSpin() {
        if (isSpinning || peers.isEmpty()) return
        isSpinning = true
        spinResult = null
        scope.launch {
            val extraSpins = 1080f + Random.nextInt(0, 720)
            spinRotation.animateTo(
                targetValue = spinRotation.value + extraSpins,
                animationSpec = tween(durationMillis = 1900, easing = FastOutSlowInEasing)
            )
            spinResult = peers[Random.nextInt(peers.size)]
            isSpinning = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            SearchAndFilters(
                searchQuery = searchQuery,
                onQueryChange = { searchQuery = it },
                faculties = faculties,
                selectedFaculty = selectedFaculty,
                onFacultySelected = { selectedFaculty = it },
                universities = universities,
                selectedUniversity = selectedUniversity,
                onUniversitySelected = { selectedUniversity = it },
                levels = levels,
                selectedLevel = selectedLevel,
                onLevelSelected = { selectedLevel = it },
                cardBg = cardBg,
                cardBorder = cardBorder
            )
        }

        item { Spacer(modifier = Modifier.height(18.dp)) }

        // Spin-to-meet section
        item {
            SpinToMeetSection(
                isSpinning = isSpinning,
                rotationDegrees = spinRotation.value,
                onSpinClick = { triggerSpin() },
                isDark = isDark
            )
        }

        item {
            AnimatedVisibility(
                visible = spinResult != null,
                enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                spinResult?.let { peer ->
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SpinResultCard(
                            peer = peer,
                            yourInterests = yourInterests,
                            isConnected = connectedPeerIds.contains(peer.id),
                            isDark = isDark,
                            cardBorder = cardBorder,
                            onProfileClick = { onProfileClick(peer.username) },
                            onMessage = { onDirectMessage(peer.username, peer.name, peer.avatarUrl) },
                            onToggleConnect = { onToggleConnect(peer.id) },
                            onDismiss = { spinResult = null }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(22.dp)) }

        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "Browse Campus Peers",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Or search normally — no spinning required",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { Spacer(modifier = Modifier.height(10.dp)) }

        items(filteredPeers, key = { it.id }) { peer ->
            val isConnected = connectedPeerIds.contains(peer.id)
            Box(
                modifier = Modifier
                    .animateItem(placementSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                PeerListCard(
                    peer = peer,
                    yourInterests = yourInterests,
                    isConnected = isConnected,
                    isDark = isDark,
                    cardBg = cardBg,
                    cardBorder = cardBorder,
                    onProfileClick = { onProfileClick(peer.username) },
                    onMessage = { onDirectMessage(peer.username, peer.name, peer.avatarUrl) },
                    onToggleConnect = { onToggleConnect(peer.id) }
                )
            }
        }

        if (filteredPeers.isEmpty()) {
            item { EmptyState(title = "No classmates found", subtitle = "Try a different faculty, university, or keyword.") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchAndFilters(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    faculties: List<String>,
    selectedFaculty: String,
    onFacultySelected: (String) -> Unit,
    universities: List<String>,
    selectedUniversity: String,
    onUniversitySelected: (String) -> Unit,
    levels: List<String>,
    selectedLevel: String,
    onLevelSelected: (String) -> Unit,
    cardBg: Color,
    cardBorder: Color
) {
    var universityMenuExpanded by remember { mutableStateOf(false) }
    var levelMenuExpanded by remember { mutableStateOf(false) }

    Column {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            placeholder = { Text("Search by name, university, faculty, department...", fontSize = 13.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingIcon = {
                AnimatedVisibility(visible = searchQuery.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BlinkPink,
                unfocusedBorderColor = cardBorder,
                focusedContainerColor = cardBg,
                unfocusedContainerColor = cardBg
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // University picker
        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            ExposedDropdownMenuBox(
                expanded = universityMenuExpanded,
                onExpandedChange = { universityMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedUniversity,
                    onValueChange = {},
                    readOnly = true,
                    leadingIcon = { Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = universityMenuExpanded) },
                    shape = RoundedCornerShape(16.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BlinkPink,
                        unfocusedBorderColor = cardBorder,
                        focusedContainerColor = cardBg,
                        unfocusedContainerColor = cardBg
                    ),
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = universityMenuExpanded, onDismissRequest = { universityMenuExpanded = false }) {
                    universities.forEach { uni ->
                        DropdownMenuItem(
                            text = { Text(uni, fontSize = 13.sp) },
                            onClick = {
                                onUniversitySelected(uni)
                                universityMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Level picker
        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            ExposedDropdownMenuBox(
                expanded = levelMenuExpanded,
                onExpandedChange = { levelMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedLevel,
                    onValueChange = {},
                    readOnly = true,
                    leadingIcon = { Icon(Icons.Default.Stairs, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelMenuExpanded) },
                    shape = RoundedCornerShape(16.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BlinkPink,
                        unfocusedBorderColor = cardBorder,
                        focusedContainerColor = cardBg,
                        unfocusedContainerColor = cardBg
                    ),
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = levelMenuExpanded, onDismissRequest = { levelMenuExpanded = false }) {
                    levels.forEach { level ->
                        DropdownMenuItem(
                            text = { Text(level, fontSize = 13.sp) },
                            onClick = {
                                onLevelSelected(level)
                                levelMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(faculties) { faculty ->
                val isSelected = selectedFaculty == faculty
                val bg by animateColorAsState(if (isSelected) BlinkPink else cardBg, tween(200), label = "facultyChipBg")
                Surface(
                    modifier = Modifier.pressScale().clickable { onFacultySelected(faculty) },
                    shape = RoundedCornerShape(100.dp),
                    color = bg,
                    border = if (!isSelected) BorderStroke(1.dp, cardBorder) else null
                ) {
                    Text(
                        text = faculty,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SpinToMeetSection(
    isSpinning: Boolean,
    rotationDegrees: Float,
    onSpinClick: () -> Unit,
    isDark: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spinIdlePulse")
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "idlePulse"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Ambient glow ring
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .alpha(if (isSpinning) 0.7f else glowAlpha)
                    .clip(CircleShape)
                    .background(BlinkPink.copy(alpha = 0.18f))
            )

            Box(
                modifier = Modifier
                    .size(150.dp)
                    .scale(if (isSpinning) 1f else idlePulse)
                    .graphicsLayer { rotationZ = rotationDegrees }
                    .clip(CircleShape)
                    .background(
                        androidx.compose.ui.graphics.Brush.sweepGradient(
                            listOf(BlinkPink, Color(0xFFFF8FB1), BlinkPink, Color(0xFFFFC1D6), BlinkPink)
                        )
                    )
                    .border(3.dp, if (isDark) DarkSurface else LightSurface, CircleShape)
                    .pressScale(0.9f)
                    .clickable(enabled = !isSpinning) { onSpinClick() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(if (isDark) DarkSurface else LightSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Casino,
                            contentDescription = "Spin to meet someone new",
                            tint = BlinkPink,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isSpinning) "Spinning…" else "SPIN",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Spin the wheel to meet a random student on campus",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SpinResultCard(
    peer: CampusPeer,
    yourInterests: List<String>,
    isConnected: Boolean,
    isDark: Boolean,
    cardBorder: Color,
    onProfileClick: () -> Unit,
    onMessage: () -> Unit,
    onToggleConnect: () -> Unit,
    onDismiss: () -> Unit
) {
    val shared = peer.interests.filter { it in yourInterests }

    Surface(
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.5.dp, BlinkPink.copy(alpha = 0.55f)),
        color = if (isDark) DarkSurface else LightSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Surface(shape = RoundedCornerShape(100.dp), color = BlinkPink.copy(alpha = 0.15f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = BlinkPink, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("You landed on", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BlinkPink)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .border(2.dp, BlinkPink, CircleShape)
                        .clickable { onProfileClick() }
                ) {
                    AsyncImage(model = peer.avatarUrl, contentDescription = peer.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(peer.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(modifier = Modifier.width(4.dp))
                        VerifiedMark(badge = peer.badge)
                    }
                    Text("@${peer.username}", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${peer.university} • ${peer.faculty} • ${peer.level}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(peer.bio, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 17.sp)

            if (shared.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text("You both like", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    shared.forEach { tag ->
                        Surface(shape = RoundedCornerShape(100.dp), color = BlinkOnlineGreen.copy(alpha = 0.15f)) {
                            Text(
                                "✓ $tag",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = BlinkOnlineGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ConnectButton(isConnected = isConnected, onClick = onToggleConnect, modifier = Modifier.weight(1f))
                MessageButton(onClick = onMessage, cardBorder = cardBorder, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// SHARED PEER CARD (browse list)
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun PeerListCard(
    peer: CampusPeer,
    yourInterests: List<String>,
    isConnected: Boolean,
    isDark: Boolean,
    cardBg: Color,
    cardBorder: Color,
    onProfileClick: () -> Unit,
    onMessage: () -> Unit,
    onToggleConnect: () -> Unit
) {
    val shared = peer.interests.filter { it in yourInterests }
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onProfileClick() }
                ) {
                    AsyncImage(model = peer.avatarUrl, contentDescription = peer.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(peer.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(modifier = Modifier.width(4.dp))
                        VerifiedMark(badge = peer.badge)
                    }
                    if (isExpanded) {
                        Text(
                            "@${peer.username} • ${peer.university}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${peer.department} (${peer.level})",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        // When collapsed, just show a hint or leave it empty as requested by user.
                        // "all the users should just be avatar and name with verified badge"
                        Text(
                            "Tap to view profile",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(peer.bio, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 16.sp)

                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    peer.interests.forEach { tag ->
                        val isShared = tag in shared
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = if (isShared) BlinkOnlineGreen.copy(alpha = 0.15f)
                            else if (isDark) Color(0xFF222222) else Color(0xFFECECEC)
                        ) {
                            Text(
                                text = if (isShared) "✓ $tag" else "#$tag",
                                fontSize = 10.sp,
                                color = if (isShared) BlinkOnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ConnectButton(isConnected = isConnected, onClick = onToggleConnect, modifier = Modifier.weight(1f), tag = "connect_peer_${peer.username}")
                    MessageButton(onClick = onMessage, cardBorder = cardBorder, modifier = Modifier.weight(1f), tag = "message_peer_${peer.username}")
                }
            }
        }
    }
}

@Composable
private fun ConnectButton(isConnected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, tag: String? = null) {
    val bg by animateColorAsState(
        targetValue = if (isConnected) Color(0xFF2ECC71).copy(alpha = 0.15f) else BlinkPink,
        animationSpec = tween(220), label = "connectBg"
    )
    val fg by animateColorAsState(
        targetValue = if (isConnected) Color(0xFF2ECC71) else Color.White,
        animationSpec = tween(220), label = "connectFg"
    )
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(100.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        modifier = modifier
            .height(38.dp)
            .pressScale()
            .let { if (tag != null) it.testTag(tag) else it }
    ) {
        AnimatedContent(targetState = isConnected, label = "connectIcon") { connected ->
            Icon(
                imageVector = if (connected) Icons.Default.Check else Icons.Default.PersonAdd,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = if (isConnected) "Connected" else "Connect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MessageButton(onClick: () -> Unit, cardBorder: Color, modifier: Modifier = Modifier, tag: String? = null) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(100.dp),
        border = BorderStroke(1.dp, cardBorder),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = modifier
            .height(38.dp)
            .pressScale()
            .let { if (tag != null) it.testTag(tag) else it }
    ) {
        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Message", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ─────────────────────────────────────────────────────────────────────────
// PAGE 2 — FIND A ROOMMATE (apply + browse)
// ─────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoommatePage(
    applicants: SnapshotStateList<RoommateApplicant>,
    interestedIds: SnapshotStateList<String>,
    isDark: Boolean,
    cardBg: Color,
    cardBorder: Color,
    onProfileClick: (String) -> Unit,
    onDirectMessage: (String, String?, String?) -> Unit,
    onToggleInterested: (String) -> Unit,
    onSubmitApplication: (RoommateApplicant) -> Unit
) {
    var tab by remember { mutableStateOf(RoommateTab.BROWSE) }
    var genderFilter by remember { mutableStateOf("All") }
    var levelFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    val filtered = applicants.filter { a ->
        (genderFilter == "All" || a.gender == genderFilter) &&
                (levelFilter == "All" || a.level == levelFilter) &&
                (searchQuery.isBlank() ||
                        a.name.contains(searchQuery, ignoreCase = true) ||
                        a.university.contains(searchQuery, ignoreCase = true) ||
                        a.preferredLocation.contains(searchQuery, ignoreCase = true))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Sub-tab switch
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(100.dp))
                .background(if (isDark) Color(0xFF1A1A1A) else Color(0xFFF0F0F0))
                .padding(4.dp)
        ) {
            listOf(RoommateTab.BROWSE to "Browse", RoommateTab.APPLY to "Apply").forEach { (tabValue, label) ->
                val isSelected = tab == tabValue
                val bg by animateColorAsState(if (isSelected) BlinkPink else Color.Transparent, tween(200), label = "roommateTabBg")
                val fg by animateColorAsState(
                    if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    tween(200), label = "roommateTabFg"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(100.dp))
                        .background(bg)
                        .clickable { tab = tabValue }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (slideInHorizontally { w -> if (targetState == RoommateTab.APPLY) w else -w } + fadeIn()) togetherWith
                        (slideOutHorizontally { w -> if (targetState == RoommateTab.APPLY) -w else w } + fadeOut())
            },
            label = "roommateTabContent",
            modifier = Modifier.fillMaxSize()
        ) { currentTab ->
            when (currentTab) {
                RoommateTab.BROWSE -> {
                    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search by name, university, or location...", fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BlinkPink,
                                    unfocusedBorderColor = cardBorder,
                                    focusedContainerColor = cardBg,
                                    unfocusedContainerColor = cardBg
                                ),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            )
                        }

                        item { Spacer(modifier = Modifier.height(10.dp)) }

                        item {
                            FilterChipsRow(
                                title = "Gender",
                                options = listOf("All", "Male", "Female"),
                                selected = genderFilter,
                                onSelected = { genderFilter = it },
                                cardBg = cardBg,
                                cardBorder = cardBorder
                            )
                        }

                        item { Spacer(modifier = Modifier.height(8.dp)) }

                        item {
                            FilterChipsRow(
                                title = "Year",
                                options = listOf("All", "100 Level", "200 Level", "300 Level", "400 Level", "500 Level"),
                                selected = levelFilter,
                                onSelected = { levelFilter = it },
                                cardBg = cardBg,
                                cardBorder = cardBorder
                            )
                        }

                        item { Spacer(modifier = Modifier.height(16.dp)) }

                        item {
                            Text(
                                text = "${filtered.size} students looking for a roommate",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        item { Spacer(modifier = Modifier.height(10.dp)) }

                        items(filtered, key = { it.id }) { applicant ->
                            Box(
                                modifier = Modifier
                                    .animateItem(placementSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                RoommateApplicantCard(
                                    applicant = applicant,
                                    isInterested = interestedIds.contains(applicant.id),
                                    isDark = isDark,
                                    cardBg = cardBg,
                                    cardBorder = cardBorder,
                                    onProfileClick = { onProfileClick(applicant.username) },
                                    onMessage = { onDirectMessage(applicant.username, applicant.name, applicant.avatarUrl) },
                                    onToggleInterested = { onToggleInterested(applicant.id) }
                                )
                            }
                        }

                        if (filtered.isEmpty()) {
                            item { EmptyState(title = "No matches yet", subtitle = "Try adjusting the gender or year filter.") }
                        }
                    }
                }

                RoommateTab.APPLY -> {
                    RoommateApplyForm(
                        cardBg = cardBg,
                        cardBorder = cardBorder,
                        onSubmit = { applicant ->
                            onSubmitApplication(applicant)
                            tab = RoommateTab.BROWSE
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    title: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    cardBg: Color,
    cardBorder: Color
) {
    Column {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { option ->
                val isSelected = selected == option
                val bg by animateColorAsState(if (isSelected) BlinkPink else cardBg, tween(200), label = "filterChipBg")
                Surface(
                    modifier = Modifier.pressScale().clickable { onSelected(option) },
                    shape = RoundedCornerShape(100.dp),
                    color = bg,
                    border = if (!isSelected) BorderStroke(1.dp, cardBorder) else null
                ) {
                    Text(
                        text = option,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RoommateApplicantCard(
    applicant: RoommateApplicant,
    isInterested: Boolean,
    isDark: Boolean,
    cardBg: Color,
    cardBorder: Color,
    onProfileClick: () -> Unit,
    onMessage: () -> Unit,
    onToggleInterested: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onProfileClick() }
                ) {
                    AsyncImage(model = applicant.avatarUrl, contentDescription = applicant.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(applicant.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(modifier = Modifier.width(4.dp))
                        VerifiedMark(badge = applicant.badge)
                    }
                    Text(
                        "${applicant.university} • ${applicant.faculty} (${applicant.level})",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(shape = RoundedCornerShape(100.dp), color = if (isDark) Color(0xFF222222) else Color(0xFFECECEC)) {
                    Text(
                        applicant.gender,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(applicant.bio, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 16.sp)

            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                RoommateStat(icon = Icons.Default.Payments, label = applicant.budget, modifier = Modifier.weight(1f))
                RoommateStat(icon = Icons.Default.LocationOn, label = applicant.preferredLocation, modifier = Modifier.weight(1f))
                RoommateStat(icon = Icons.Default.CalendarMonth, label = applicant.moveInDate, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                val bg by animateColorAsState(
                    targetValue = if (isInterested) Color(0xFF2ECC71).copy(alpha = 0.15f) else BlinkPink,
                    animationSpec = tween(220), label = "interestedBg"
                )
                val fg by animateColorAsState(
                    targetValue = if (isInterested) Color(0xFF2ECC71) else Color.White,
                    animationSpec = tween(220), label = "interestedFg"
                )
                Button(
                    onClick = onToggleInterested,
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
                    modifier = Modifier.weight(1f).height(38.dp).pressScale()
                ) {
                    Icon(
                        imageVector = if (isInterested) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isInterested) "Interested" else "I'm Interested", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                MessageButton(onClick = onMessage, cardBorder = cardBorder, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RoommateStat(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = BlinkPink, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RoommateApplyForm(
    cardBg: Color,
    cardBorder: Color,
    onSubmit: (RoommateApplicant) -> Unit
) {
    var gender by remember { mutableStateOf("Male") }
    var level by remember { mutableStateOf("300 Level") }
    var budget by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var moveInDate by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }

    val levels = listOf("100 Level", "200 Level", "300 Level", "400 Level", "500 Level")
    val isValid = budget.isNotBlank() && location.isNotBlank() && bio.isNotBlank()

    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        item {
            Text("Apply as a roommate seeker", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "Fill in your details so other students can find and reach you.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            FormSectionLabel("Gender")
            FilterChipsRow(title = "", options = listOf("Male", "Female"), selected = gender, onSelected = { gender = it }, cardBg = cardBg, cardBorder = cardBorder)
            Spacer(modifier = Modifier.height(14.dp))
        }

        item {
            FormSectionLabel("Year / Level")
            FilterChipsRow(title = "", options = levels, selected = level, onSelected = { level = it }, cardBg = cardBg, cardBorder = cardBorder)
            Spacer(modifier = Modifier.height(14.dp))
        }

        item {
            FormTextField(label = "Monthly Budget (e.g. ₦40,000)", value = budget, onValueChange = { budget = it }, icon = Icons.Default.Payments, cardBg = cardBg, cardBorder = cardBorder)
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            FormTextField(label = "Preferred Location (e.g. Off-campus, Yaba)", value = location, onValueChange = { location = it }, icon = Icons.Default.LocationOn, cardBg = cardBg, cardBorder = cardBorder)
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            FormTextField(label = "Move-in Date (e.g. September 2026)", value = moveInDate, onValueChange = { moveInDate = it }, icon = Icons.Default.CalendarMonth, cardBg = cardBg, cardBorder = cardBorder)
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            FormTextField(
                label = "Tell future roommates about yourself",
                value = bio,
                onValueChange = { bio = it },
                icon = Icons.Default.EditNote,
                cardBg = cardBg,
                cardBorder = cardBorder,
                singleLine = false,
                minLines = 3
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        item {
            Button(
                onClick = {
                    if (isValid) {
                        onSubmit(
                            RoommateApplicant(
                                id = "applicant_${Random.nextInt(10000, 99999)}",
                                name = "You",
                                username = "you",
                                avatarUrl = "https://images.unsplash.com/photo-1633332755192-727a05c4013d?w=400&fit=crop",
                                university = "Your University",
                                faculty = "Your Faculty",
                                level = level,
                                gender = gender,
                                budget = budget,
                                preferredLocation = location,
                                moveInDate = moveInDate.ifBlank { "Flexible" },
                                bio = bio,
                                badge = VerificationBadge.NONE
                            )
                        )
                        showSuccess = true
                    }
                },
                enabled = isValid,
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BlinkPink, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(46.dp).pressScale()
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Submit Application", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(120.dp))
        }
    }

    LaunchedEffect(showSuccess) {
        if (showSuccess) {
            delay(1800)
            showSuccess = false
        }
    }

    AnimatedVisibility(
        visible = showSuccess,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF2ECC71),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Application submitted! Now visible under Browse.", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun FormSectionLabel(text: String) {
    Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun FormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    cardBg: Color,
    cardBorder: Color,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        singleLine = singleLine,
        minLines = minLines,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BlinkPink,
            unfocusedBorderColor = cardBorder,
            focusedContainerColor = cardBg,
            unfocusedContainerColor = cardBg
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

// ─────────────────────────────────────────────────────────────────────────
// PAGE 3 — MORE (study circles + extras)
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun MorePage(
    circles: List<StudyCircle>,
    isDark: Boolean,
    cardBg: Color,
    cardBorder: Color
) {
    val joinedCircleIds = remember { mutableStateListOf<String>() }

    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Campus Study Circles", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Text("${circles.size} Active", fontSize = 12.sp, color = BlinkPink, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(circles) { circle ->
                        val isJoined = joinedCircleIds.contains(circle.id)
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, cardBorder),
                            modifier = Modifier.width(220.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(shape = CircleShape, color = BlinkPink.copy(alpha = 0.15f), modifier = Modifier.size(36.dp)) {
                                        Icon(circle.icon, contentDescription = null, tint = BlinkPink, modifier = Modifier.padding(8.dp))
                                    }
                                    val bg by animateColorAsState(
                                        if (isJoined) BlinkOnlineGreen.copy(alpha = 0.15f) else BlinkPink, tween(200), label = "circleBg"
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(100.dp),
                                        color = bg,
                                        modifier = Modifier.pressScale().clickable {
                                            if (isJoined) joinedCircleIds.remove(circle.id) else joinedCircleIds.add(circle.id)
                                        }
                                    ) {
                                        Text(
                                            text = if (isJoined) "Joined ✓" else "Join Circle",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isJoined) BlinkOnlineGreen else Color.White,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(circle.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(circle.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("${circle.membersCount + if (isJoined) 1 else 0} members", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        item {
            Text(
                text = "More to explore",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        val extras = listOf(
            Triple("Campus Events", "Discover meetups, socials & workshops near you", Icons.Default.Event),
            Triple("Marketplace", "Buy, sell, or swap items with fellow students", Icons.Default.Storefront),
            Triple("Mentorship", "Get paired with a senior in your faculty", Icons.Default.EmojiPeople),
            Triple("Ride Share", "Split fares with students headed your way", Icons.Default.DirectionsCar)
        )

        items(extras) { (title, subtitle, icon) ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = cardBg,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .pressScale()
                    .clickable { }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
                    Surface(shape = CircleShape, color = BlinkPink.copy(alpha = 0.15f), modifier = Modifier.size(40.dp)) {
                        Icon(icon, contentDescription = null, tint = BlinkPink, modifier = Modifier.padding(9.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Text(subtitle, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// SHARED — EMPTY STATE
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.SearchOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ─────────────────────────────────────────────────────────────────────────
// SAMPLE DATA
// ─────────────────────────────────────────────────────────────────────────

private fun sampleCampusPeers(): List<CampusPeer> = listOf(
    CampusPeer(
        id = "peer_1", name = "Adebayo Tobi", username = "tobi_tech",
        avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400&fit=crop",
        university = "University of Lagos", faculty = "Sciences", department = "Computer Science", level = "400 Level",
        bio = "Android & Kotlin enthusiast. Working on smart campus utilities.",
        interests = listOf("Kotlin", "Mobile Dev", "AI", "Chess"), badge = VerificationBadge.GOLD, mutualFriends = 12
    ),
    CampusPeer(
        id = "peer_2", name = "Chioma Nwosu", username = "chioma_designs",
        avatarUrl = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=400&fit=crop",
        university = "Covenant University", faculty = "Arts & Lit", department = "Creative Arts", level = "300 Level",
        bio = "UI/UX & brand identity designer. Looking for tech project teams.",
        interests = listOf("Design", "Branding", "Photography", "Music"), badge = VerificationBadge.BLUE, mutualFriends = 8
    ),
    CampusPeer(
        id = "peer_3", name = "Ibrahim Aliyu", username = "ibrahim_eng",
        avatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400&fit=crop",
        university = "Ahmadu Bello University", faculty = "Engineering", department = "Mechanical Eng.", level = "500 Level",
        bio = "Robotics, CAD drafting, and campus renewable energy research.",
        interests = listOf("Robotics", "SolidWorks", "Startups", "Football"), badge = VerificationBadge.BLUE, mutualFriends = 5
    ),
    CampusPeer(
        id = "peer_4", name = "Zainab Bello", username = "zainab_law",
        avatarUrl = "https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=400&fit=crop",
        university = "University of Ibadan", faculty = "Law", department = "Commercial Law", level = "400 Level",
        bio = "Debate team lead, legal tech advocate, and moot court finalist.",
        interests = listOf("Debating", "Startups", "AI"), badge = VerificationBadge.GOLD, mutualFriends = 15
    ),
    CampusPeer(
        id = "peer_5", name = "Emeka Okafor", username = "emeka_med",
        avatarUrl = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=400&fit=crop",
        university = "University of Lagos", faculty = "Medicine", department = "Physiology", level = "300 Level",
        bio = "Health tech researcher, peer tutor, and blood donation coordinator.",
        interests = listOf("Music", "Chess", "Football"), badge = VerificationBadge.NONE, mutualFriends = 4
    ),
    CampusPeer(
        id = "peer_6", name = "Funmilayo Ade", username = "funmi_biz",
        avatarUrl = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=400&fit=crop",
        university = "Covenant University", faculty = "Management", department = "Business Admin", level = "200 Level",
        bio = "Campus entrepreneur running a small logistics side hustle.",
        interests = listOf("Startups", "Design", "Music"), badge = VerificationBadge.NONE, mutualFriends = 6
    )
)

private fun sampleRoommateApplicants(): List<RoommateApplicant> = listOf(
    RoommateApplicant(
        id = "rm_1", name = "David Okon", username = "david_okon",
        avatarUrl = "https://images.unsplash.com/photo-1603415526960-f7e0328c63b1?w=400&fit=crop",
        university = "University of Lagos", faculty = "Engineering", level = "300 Level",
        gender = "Male", budget = "₦45,000/mo", preferredLocation = "Off-campus, Akoka",
        moveInDate = "Sept 2026", bio = "Quiet, tidy, and studies late. Looking for a serious roommate.",
        badge = VerificationBadge.BLUE
    ),
    RoommateApplicant(
        id = "rm_2", name = "Amaka Eze", username = "amaka_eze",
        avatarUrl = "https://images.unsplash.com/photo-1487412720507-e7ab37603c6f?w=400&fit=crop",
        university = "Covenant University", faculty = "Arts & Lit", level = "200 Level",
        gender = "Female", budget = "₦60,000/mo", preferredLocation = "Off-campus, Ota",
        moveInDate = "Flexible", bio = "Easygoing and social, but respects quiet hours during exams.",
        badge = VerificationBadge.GOLD
    ),
    RoommateApplicant(
        id = "rm_3", name = "Tunde Bakare", username = "tunde_b",
        avatarUrl = "https://images.unsplash.com/photo-1519345182560-3f2917c472ef?w=400&fit=crop",
        university = "University of Ibadan", faculty = "Sciences", level = "400 Level",
        gender = "Male", budget = "₦35,000/mo", preferredLocation = "On-campus hostel",
        moveInDate = "Jan 2027", bio = "Football lover, early riser, non-smoker.",
        badge = VerificationBadge.NONE
    ),
    RoommateApplicant(
        id = "rm_4", name = "Blessing Umeh", username = "blessing_u",
        avatarUrl = "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=400&fit=crop",
        university = "Ahmadu Bello University", faculty = "Medicine", level = "500 Level",
        gender = "Female", budget = "₦50,000/mo", preferredLocation = "Off-campus, Samaru",
        moveInDate = "Sept 2026", bio = "Final year med student, needs a calm reading environment.",
        badge = VerificationBadge.BLUE
    )
)

private fun sampleStudyCircles(): List<StudyCircle> = listOf(
    StudyCircle(id = "circle_1", name = "CSC 301 & 401 Coding Circle", faculty = "Sciences", membersCount = 142, description = "Algorithms, database assignments, and peer code reviews.", icon = Icons.Default.Code),
    StudyCircle(id = "circle_2", name = "Engineering Design & CAD Hub", faculty = "Engineering", membersCount = 89, description = "Engineering drawing, modeling, and final year project help.", icon = Icons.Default.Build),
    StudyCircle(id = "circle_3", name = "Campus Tech & Startup Innovators", faculty = "Management", membersCount = 210, description = "Idea pitching, freelance opportunities, and hackathons.", icon = Icons.Default.RocketLaunch),
    StudyCircle(id = "circle_4", name = "Moot Court & Debate Forum", faculty = "Law", membersCount = 76, description = "Legal argumentation, public speaking, and case reviews.", icon = Icons.Default.Gavel)
)