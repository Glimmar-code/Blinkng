package com.blinkng.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.desktop.DesktopAppState
import com.blinkng.desktop.data.DesktopProfile
import com.blinkng.shared.BlinkOnboardingPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val OnboardingAccent = Color(0xFF7A5CFF)
private val OnboardingSurface = Color(0xFF101014)
private val OnboardingBorder = Color.White.copy(alpha = 0.12f)
private val OnboardingMuted = Color(0xFFA6A6AD)

@Composable
fun BlinkDesktopOnboardingScreen(state: DesktopAppState) {
    val profile = state.profile ?: return
    val step = profile.onboardingStep.coerceIn(0, BlinkOnboardingPolicy.TOTAL_STEPS - 1)
    val scope = rememberCoroutineScope()

    var username by remember(profile.id) {
        mutableStateOf(
            profile.username
                .takeUnless { it.startsWith("blink_", ignoreCase = true) }
                .orEmpty(),
        )
    }
    var usernameAvailable by remember { mutableStateOf<Boolean?>(null) }
    var usernameStatus by remember { mutableStateOf<String?>(null) }

    var university by remember(profile.id) { mutableStateOf(profile.university.orEmpty()) }
    var department by remember(profile.id) { mutableStateOf(profile.department.orEmpty()) }
    var level by remember(profile.id) { mutableStateOf(profile.academicLevel.orEmpty()) }
    var gender by remember(profile.id) { mutableStateOf(profile.gender.orEmpty()) }
    var birthDate by remember(profile.id) { mutableStateOf(profile.birthDate.orEmpty()) }
    val selectedInterests = remember(profile.id) {
        mutableStateListOf<String>().apply { addAll(profile.interests) }
    }

    var suggestions by remember { mutableStateOf<List<DesktopProfile>>(emptyList()) }
    var followingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var suggestionsLoading by remember { mutableStateOf(false) }
    var localMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(username, step) {
        if (step != 0) return@LaunchedEffect
        val clean = BlinkOnboardingPolicy.normalizeUsername(username)
        val validation = BlinkOnboardingPolicy.usernameValidationMessage(clean)
        if (validation != null) {
            usernameAvailable = false
            usernameStatus = validation
            return@LaunchedEffect
        }

        usernameAvailable = null
        usernameStatus = "Checking availability..."
        delay(350)
        val (available, message) = state.checkOnboardingUsername(clean)
        usernameAvailable = available
        usernameStatus = message
    }

    LaunchedEffect(step) {
        if (step == 3) {
            suggestionsLoading = true
            runCatching {
                suggestions = state.onboardingSuggestions()
                followingIds = state.onboardingFollowingIds()
            }.onFailure {
                localMessage = it.message ?: "Unable to load suggestions."
            }
            suggestionsLoading = false
        }
    }

    val rankedSuggestions = remember(
        suggestions,
        profile.university,
        profile.department,
        profile.academicLevel,
        profile.interests,
    ) {
        suggestions.sortedWith(
            compareByDescending<DesktopProfile> {
                it.username.equals(
                    BlinkOnboardingPolicy.PINNED_CREATOR_USERNAME,
                    ignoreCase = true,
                )
            }.thenByDescending {
                var score = 0
                if (
                    !profile.university.isNullOrBlank() &&
                    it.university.equals(profile.university, ignoreCase = true)
                ) score += 8
                if (
                    !profile.department.isNullOrBlank() &&
                    it.department.equals(profile.department, ignoreCase = true)
                ) score += 6
                if (
                    !profile.academicLevel.isNullOrBlank() &&
                    it.academicLevel.equals(profile.academicLevel, ignoreCase = true)
                ) score += 2
                score += profile.interests.count { chosen ->
                    it.interests.any { other -> other.equals(chosen, ignoreCase = true) }
                } * 3
                score + (it.followerCount.coerceAtMost(1000) / 100)
            },
        ).take(20)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .padding(24.dp),
            color = Color(0xFF080808),
            border = BorderStroke(1.dp, OnboardingBorder),
            shape = RoundedCornerShape(28.dp),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(30.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BlinkDesktopLogo(size = 44.dp, showText = false)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Set up your BLINK account",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                "Step ${step + 1} of ${BlinkOnboardingPolicy.TOTAL_STEPS}",
                                color = OnboardingMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = (step + 1) / BlinkOnboardingPolicy.TOTAL_STEPS.toFloat(),
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = OnboardingAccent,
                        trackColor = Color.White.copy(alpha = 0.10f),
                    )
                }

                state.errorMessage?.let { message ->
                    item {
                        OnboardingMessage(message, error = true)
                    }
                }
                localMessage?.let { message ->
                    item {
                        OnboardingMessage(message, error = true)
                    }
                }

                when (step) {
                    0 -> {
                        item {
                            OnboardingHeader(
                                title = "Choose your username",
                                subtitle = "Your unique BLINK name for search, mentions and profile links.",
                                icon = Icons.Rounded.Person,
                            )
                            OutlinedTextField(
                                value = username,
                                onValueChange = {
                                    username = BlinkOnboardingPolicy.normalizeUsername(it)
                                    usernameAvailable = null
                                    localMessage = null
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                singleLine = true,
                                label = { Text("Username") },
                                prefix = { Text("@") },
                            )
                            usernameStatus?.let { status ->
                                Text(
                                    status,
                                    color = when (usernameAvailable) {
                                        true -> Color(0xFF56D88A)
                                        false -> Color(0xFFFF7A7A)
                                        null -> OnboardingMuted
                                    },
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            OnboardingPrimaryButton(
                                text = if (state.busy) "Saving..." else "Continue",
                                enabled = usernameAvailable == true && !state.busy,
                            ) {
                                scope.launch {
                                    runCatching {
                                        state.saveOnboardingUsername(username)
                                    }.onFailure {
                                        localMessage = it.message
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        item {
                            OnboardingHeader(
                                title = "Build your basic profile",
                                subtitle = "University, department and gender are required. Level and birthday are optional.",
                                icon = Icons.Rounded.School,
                            )

                            Text(
                                "Profile picture is optional and can be added from Profile after setup.",
                                color = OnboardingMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                            )

                            OutlinedTextField(
                                value = university,
                                onValueChange = { university = it.take(120) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Search / enter university") },
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = department,
                                onValueChange = { department = it.take(100) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Search / enter department") },
                            )

                            Spacer(Modifier.height(12.dp))
                            Text("Level — optional", color = Color.White, fontWeight = FontWeight.SemiBold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                items(BlinkOnboardingPolicy.levels, key = { it }) { option ->
                                    FilterChip(
                                        selected = level == option,
                                        onClick = {
                                            level = if (level == option) "" else option
                                        },
                                        label = { Text(option) },
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            Text("Gender", color = Color.White, fontWeight = FontWeight.SemiBold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                items(BlinkOnboardingPolicy.genders, key = { it }) { option ->
                                    FilterChip(
                                        selected = gender == option,
                                        onClick = { gender = option },
                                        label = { Text(option) },
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = birthDate,
                                onValueChange = {
                                    birthDate = it.filter { char -> char.isDigit() || char == '-' }.take(10)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Birthday — optional") },
                                placeholder = { Text("YYYY-MM-DD") },
                            )

                            Spacer(Modifier.height(16.dp))
                            OnboardingPrimaryButton(
                                text = if (state.busy) "Saving..." else "Continue",
                                enabled = university.isNotBlank() &&
                                    department.isNotBlank() &&
                                    gender.isNotBlank() &&
                                    !state.busy,
                            ) {
                                scope.launch {
                                    runCatching {
                                        state.saveOnboardingBasics(
                                            university = university,
                                            department = department,
                                            level = level,
                                            gender = gender,
                                            birthDate = birthDate,
                                        )
                                    }.onFailure {
                                        localMessage = it.message
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        item {
                            OnboardingHeader(
                                title = "Choose your interests",
                                subtitle = "These help personalize Feed, Reels, Connect and people suggestions.",
                                icon = Icons.Rounded.Interests,
                            )
                        }

                        BlinkOnboardingPolicy.interestGroups.forEach { (group, interests) ->
                            item(key = "interest-$group") {
                                Text(
                                    group,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                )
                                Spacer(Modifier.height(6.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    items(interests, key = { it }) { interest ->
                                        val selected = interest in selectedInterests
                                        FilterChip(
                                            selected = selected,
                                            onClick = {
                                                if (selected) selectedInterests.remove(interest)
                                                else selectedInterests.add(interest)
                                            },
                                            label = { Text(interest) },
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                "${selectedInterests.size} selected",
                                color = OnboardingMuted,
                                fontSize = 11.sp,
                            )
                            Spacer(Modifier.height(10.dp))
                            OnboardingPrimaryButton(
                                text = if (state.busy) "Saving..." else "Continue",
                                enabled = selectedInterests.isNotEmpty() && !state.busy,
                            ) {
                                scope.launch {
                                    runCatching {
                                        state.saveOnboardingInterests(selectedInterests.toList())
                                    }.onFailure {
                                        localMessage = it.message
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        item {
                            OnboardingHeader(
                                title = "Find your people",
                                subtitle = "Follow at least ${BlinkOnboardingPolicy.REQUIRED_FOLLOWS} accounts to continue.",
                                icon = Icons.Rounded.Groups,
                            )
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                color = OnboardingAccent.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, OnboardingAccent.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text(
                                    "${followingIds.size.coerceAtMost(BlinkOnboardingPolicy.REQUIRED_FOLLOWS)} / ${BlinkOnboardingPolicy.REQUIRED_FOLLOWS} followed",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(14.dp),
                                )
                            }
                        }

                        if (suggestionsLoading) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        } else if (rankedSuggestions.isEmpty()) {
                            item {
                                OnboardingMessage(
                                    "No suggestions loaded. Check your connection and try again.",
                                    error = true,
                                )
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            suggestionsLoading = true
                                            runCatching {
                                                suggestions = state.onboardingSuggestions()
                                                followingIds = state.onboardingFollowingIds()
                                            }
                                            suggestionsLoading = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Refresh suggestions")
                                }
                            }
                        } else {
                            items(rankedSuggestions, key = { it.id }) { candidate ->
                                DesktopSuggestedUser(
                                    profile = candidate,
                                    followed = candidate.id in followingIds,
                                    featured = candidate.username.equals(
                                        BlinkOnboardingPolicy.PINNED_CREATOR_USERNAME,
                                        ignoreCase = true,
                                    ),
                                    onToggle = {
                                        scope.launch {
                                            runCatching {
                                                followingIds = state.setOnboardingFollowing(
                                                    profileId = candidate.id,
                                                    shouldFollow = candidate.id !in followingIds,
                                                )
                                            }.onFailure {
                                                localMessage = it.message
                                            }
                                        }
                                    },
                                )
                            }
                        }

                        item {
                            OnboardingPrimaryButton(
                                text = if (followingIds.size >= BlinkOnboardingPolicy.REQUIRED_FOLLOWS)
                                    "Enter BLINK"
                                else
                                    "Follow ${BlinkOnboardingPolicy.REQUIRED_FOLLOWS - followingIds.size.coerceAtMost(BlinkOnboardingPolicy.REQUIRED_FOLLOWS)} more",
                                enabled = followingIds.size >= BlinkOnboardingPolicy.REQUIRED_FOLLOWS && !state.busy,
                            ) {
                                scope.launch {
                                    runCatching { state.finishOnboarding() }
                                        .onFailure { localMessage = it.message }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingHeader(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = OnboardingAccent.copy(alpha = 0.16f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(
                    icon,
                    contentDescription = null,
                    tint = OnboardingAccent,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                subtitle,
                color = OnboardingMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun OnboardingMessage(message: String, error: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (error) Color(0xFFFF5A5A).copy(alpha = 0.10f)
        else Color(0xFF56D88A).copy(alpha = 0.10f),
        border = BorderStroke(
            1.dp,
            if (error) Color(0xFFFF7A7A) else Color(0xFF56D88A),
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(
            message,
            color = if (error) Color(0xFFFFA0A0) else Color(0xFF8EE6AF),
            fontSize = 12.sp,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun DesktopSuggestedUser(
    profile: DesktopProfile,
    followed: Boolean,
    featured: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = OnboardingSurface,
        border = BorderStroke(
            1.dp,
            if (featured) OnboardingAccent.copy(alpha = 0.55f) else OnboardingBorder,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.08f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        profile.fullName.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile.fullName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    if (profile.isVerified) {
                        Spacer(Modifier.width(5.dp))
                        androidx.compose.material3.Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = "Verified",
                            tint = Color(0xFF64A8FF),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                Text(
                    "@${profile.username}",
                    color = OnboardingMuted,
                    fontSize = 11.sp,
                )
                val details = listOfNotNull(profile.university, profile.department)
                    .filter { it.isNotBlank() }
                    .joinToString(" • ")
                if (details.isNotBlank()) {
                    Text(details, color = OnboardingMuted, fontSize = 10.sp)
                }
                if (featured) {
                    Text(
                        "Featured BLINK creator",
                        color = OnboardingAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Button(
                onClick = onToggle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (followed) Color.White.copy(alpha = 0.12f)
                    else OnboardingAccent,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(100.dp),
            ) {
                Text(if (followed) "Following" else "Follow")
            }
        }
    }
}

@Composable
private fun OnboardingPrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = OnboardingAccent,
            contentColor = Color.White,
            disabledContainerColor = Color.White.copy(alpha = 0.08f),
            disabledContentColor = OnboardingMuted,
        ),
        shape = RoundedCornerShape(100.dp),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}
