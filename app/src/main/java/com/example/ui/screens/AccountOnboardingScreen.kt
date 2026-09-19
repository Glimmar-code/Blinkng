package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Interests
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.BlinkOnboardingCatalog
import com.example.data.models.NigerianUniversities
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.data.repository.FollowStateStore
import com.example.data.repository.ProfileRepository
import com.example.ui.components.BlinkMark
import com.example.ui.components.VerifiedMark
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PINNED_BLINK_CREATOR = "futa_no1_blogger"
private const val REQUIRED_ONBOARDING_FOLLOWS = 5

@Composable
fun AccountOnboardingScreen(
    profile: UserProfile,
    candidates: List<UserProfile>,
    onCheckUsername: (String, (Boolean, String?) -> Unit) -> Unit,
    onSaveUsername: (String, (Boolean, String?) -> Unit) -> Unit,
    onSaveBasics: (
        university: String,
        department: String,
        level: String,
        gender: String,
        birthDate: String,
        avatarUrl: String,
        onResult: (Boolean, String?) -> Unit
    ) -> Unit,
    onSaveInterests: (List<String>, (Boolean, String?) -> Unit) -> Unit,
    onFinish: ((Boolean, String?) -> Unit) -> Unit,
    onRefreshSuggestions: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileRepository = remember { ProfileRepository() }

    val startingStep = profile.onboardingStep.coerceIn(0, 3)
    var step by rememberSaveable(profile.id) { mutableIntStateOf(startingStep) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val initialUsername = profile.username
        .takeUnless { it.startsWith("blink_", ignoreCase = true) }
        .orEmpty()
    var username by rememberSaveable(profile.id) { mutableStateOf(initialUsername) }
    var usernameChecking by remember { mutableStateOf(false) }
    var usernameAvailable by remember { mutableStateOf<Boolean?>(null) }
    var usernameMessage by remember { mutableStateOf<String?>(null) }

    var university by rememberSaveable(profile.id) { mutableStateOf(profile.university) }
    var department by rememberSaveable(profile.id) { mutableStateOf(profile.department) }
    var level by rememberSaveable(profile.id) { mutableStateOf(profile.academicLevel) }
    var gender by rememberSaveable(profile.id) { mutableStateOf(profile.gender) }
    var birthDate by rememberSaveable(profile.id) { mutableStateOf(profile.birthDate) }
    var avatarUrl by rememberSaveable(profile.id) { mutableStateOf(profile.avatarUrl) }
    var pendingAvatarUri by remember { mutableStateOf<Uri?>(null) }

    val selectedInterests = remember(profile.id) {
        mutableStateListOf<String>().apply {
            addAll(profile.interests.ifEmpty { profile.hobbies })
        }
    }

    var universityPicker by remember { mutableStateOf(false) }
    var departmentPicker by remember { mutableStateOf(false) }
    var levelPicker by remember { mutableStateOf(false) }
    var genderPicker by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) pendingAvatarUri = uri
    }

    val followingIds by FollowStateStore.followingIds.collectAsState()

    LaunchedEffect(username, step) {
        if (step != 0) return@LaunchedEffect
        val clean = username.trim().lowercase().removePrefix("@")
        if (clean.length < 3) {
            usernameAvailable = null
            usernameMessage = if (clean.isBlank()) null else "Use at least 3 characters."
            return@LaunchedEffect
        }

        usernameChecking = true
        usernameAvailable = null
        delay(350)
        onCheckUsername(clean) { available, message ->
            usernameChecking = false
            usernameAvailable = available
            usernameMessage = message
        }
    }

    LaunchedEffect(step) {
        if (step == 3) {
            FollowStateStore.refresh()
            onRefreshSuggestions()
        }
    }

    val orderedCandidates = remember(
        candidates,
        university,
        department,
        level,
        selectedInterests.toList(),
        profile.id
    ) {
        candidates
            .asSequence()
            .filter { it.id.isNotBlank() && it.id != profile.id }
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<UserProfile> {
                    it.username.equals(PINNED_BLINK_CREATOR, ignoreCase = true)
                }.thenByDescending {
                    var score = 0
                    if (university.isNotBlank() && it.university.equals(university, ignoreCase = true)) score += 8
                    if (department.isNotBlank() && it.department.equals(department, ignoreCase = true)) score += 6
                    if (level.isNotBlank() && it.academicLevel.equals(level, ignoreCase = true)) score += 2
                    val otherInterests = it.interests.ifEmpty { it.hobbies }
                    score += selectedInterests.count { chosen ->
                        otherInterests.any { it.equals(chosen, ignoreCase = true) }
                    } * 3
                    score + (it.followerCount.coerceAtMost(1000) / 100)
                }
            )
            .take(20)
            .toList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BlinkMark(size = 34.dp, showText = true)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "STEP ${step + 1}/4",
                        color = BlinkPink,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { (step + 1) / 4f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = BlinkPink,
                    trackColor = DarkBorder
                )
            }

            if (error != null) {
                item {
                    AuthMessageCard(
                        message = error.orEmpty(),
                        success = false,
                        onDismiss = { error = null }
                    )
                }
            }

            when (step) {
                0 -> {
                    item {
                        StepHeader(
                            title = "Choose your username",
                            subtitle = "This is your unique BLINK identity for mentions, search and shared profile links.",
                            icon = Icons.Default.AlternateEmail
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                    .lowercase()
                                    .filter { char ->
                                        char.isLetterOrDigit() || char == '_' || char == '.'
                                    }
                                    .take(25)
                                error = null
                            },
                            label = { Text("Username") },
                            prefix = { Text("@") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_username")
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when {
                                usernameChecking -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(7.dp))
                                    Text("Checking availability...", color = DarkTextSecondary, fontSize = 11.sp)
                                }

                                usernameAvailable == true -> {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF51D88A),
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(Modifier.width(7.dp))
                                    Text(
                                        usernameMessage ?: "Username available",
                                        color = Color(0xFF51D88A),
                                        fontSize = 11.sp
                                    )
                                }

                                usernameAvailable == false -> {
                                    Text(
                                        usernameMessage ?: "Username is unavailable.",
                                        color = Color(0xFFFF7A7A),
                                        fontSize = 11.sp
                                    )
                                }

                                !usernameMessage.isNullOrBlank() -> {
                                    Text(usernameMessage.orEmpty(), color = DarkTextSecondary, fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        PrimaryOnboardingButton(
                            text = "Continue",
                            enabled = usernameAvailable == true && !saving
                        ) {
                            saving = true
                            onSaveUsername(username) { ok, message ->
                                saving = false
                                if (ok) {
                                    step = 1
                                    error = null
                                } else {
                                    error = message ?: "Unable to save username."
                                }
                            }
                        }
                    }
                }

                1 -> {
                    item {
                        StepHeader(
                            title = "Build your basic profile",
                            subtitle = "University, department and gender are required. Your picture, level and birthday can be skipped.",
                            icon = Icons.Default.Person
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val avatarModel = pendingAvatarUri?.toString() ?: avatarUrl
                            Surface(
                                shape = CircleShape,
                                color = DarkSurface,
                                border = BorderStroke(1.dp, DarkBorder),
                                modifier = Modifier.size(82.dp)
                            ) {
                                if (avatarModel.isNotBlank()) {
                                    AsyncImage(
                                        model = avatarModel,
                                        contentDescription = "Profile picture",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = .70f),
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.width(14.dp))

                            Column {
                                OutlinedButton(
                                    onClick = { avatarPicker.launch("image/*") }
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                                    Spacer(Modifier.width(7.dp))
                                    Text("Add profile picture")
                                }
                                Text(
                                    "Optional — you can skip this.",
                                    color = DarkTextSecondary,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(top = 5.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        PickerField(
                            label = "University",
                            value = university,
                            icon = Icons.Default.AccountBalance,
                            placeholder = "Select university",
                            onClick = { universityPicker = true }
                        )

                        Spacer(Modifier.height(10.dp))

                        PickerField(
                            label = "Department",
                            value = department,
                            icon = Icons.Default.MenuBook,
                            placeholder = "Select department",
                            onClick = { departmentPicker = true }
                        )

                        Spacer(Modifier.height(10.dp))

                        PickerField(
                            label = "Level",
                            value = level,
                            icon = Icons.Default.School,
                            placeholder = "Optional — select or skip",
                            onClick = { levelPicker = true }
                        )

                        Spacer(Modifier.height(10.dp))

                        PickerField(
                            label = "Gender",
                            value = gender,
                            icon = Icons.Default.Person,
                            placeholder = "Select gender",
                            onClick = { genderPicker = true }
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = birthDate,
                            onValueChange = { value ->
                                birthDate = value.filter { it.isDigit() || it == '-' }.take(10)
                                error = null
                            },
                            label = { Text("Birthday (optional)") },
                            placeholder = { Text("YYYY-MM-DD") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(18.dp))

                        val birthdayValid = birthDate.isBlank() ||
                            Regex("""\d{4}-\d{2}-\d{2}""").matches(birthDate)

                        PrimaryOnboardingButton(
                            text = if (saving) "Saving..." else "Continue",
                            enabled = university.isNotBlank() &&
                                department.isNotBlank() &&
                                gender.isNotBlank() &&
                                birthdayValid &&
                                !saving
                        ) {
                            scope.launch {
                                saving = true
                                error = null

                                val finalAvatar = if (pendingAvatarUri != null) {
                                    profileRepository.uploadAvatar(
                                        context = context,
                                        uri = pendingAvatarUri!!,
                                        userId = profile.id
                                    ) ?: run {
                                        saving = false
                                        error = "Profile picture upload failed. You can remove it and continue."
                                        return@launch
                                    }
                                } else {
                                    avatarUrl
                                }

                                onSaveBasics(
                                    university,
                                    department,
                                    level,
                                    gender,
                                    birthDate,
                                    finalAvatar
                                ) { ok, message ->
                                    saving = false
                                    if (ok) {
                                        avatarUrl = finalAvatar
                                        pendingAvatarUri = null
                                        step = 2
                                    } else {
                                        error = message ?: "Unable to save profile."
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    item {
                        StepHeader(
                            title = "Choose your interests",
                            subtitle = "Pick what you care about. BLINK uses these signals for discovery, Connect and recommendation personalization.",
                            icon = Icons.Default.Interests
                        )
                    }

                    BlinkOnboardingCatalog.interestGroups.forEach { (group, interests) ->
                        item(key = "interest_group:$group") {
                            Text(
                                text = group,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(7.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                items(interests, key = { it }) { interest ->
                                    val selected = interest in selectedInterests
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            if (selected) selectedInterests.remove(interest)
                                            else selectedInterests.add(interest)
                                        },
                                        label = { Text(interest) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BlinkPink.copy(alpha = .22f),
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "${selectedInterests.size} selected",
                            color = DarkTextSecondary,
                            fontSize = 11.sp
                        )

                        Spacer(Modifier.height(12.dp))

                        PrimaryOnboardingButton(
                            text = "Continue",
                            enabled = selectedInterests.isNotEmpty() && !saving
                        ) {
                            saving = true
                            onSaveInterests(selectedInterests.toList()) { ok, message ->
                                saving = false
                                if (ok) {
                                    step = 3
                                    onRefreshSuggestions()
                                } else {
                                    error = message ?: "Unable to save interests."
                                }
                            }
                        }
                    }
                }

                else -> {
                    item {
                        StepHeader(
                            title = "Find your people",
                            subtitle = "Follow at least 5 accounts to continue. Suggestions use your university, department, level and interests.",
                            icon = Icons.Default.GroupAdd
                        )

                        Spacer(Modifier.height(8.dp))

                        Surface(
                            color = BlinkPink.copy(alpha = .10f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, BlinkPink.copy(alpha = .25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${followingIds.size.coerceAtMost(REQUIRED_ONBOARDING_FOLLOWS)} / $REQUIRED_ONBOARDING_FOLLOWS followed",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }

                    if (orderedCandidates.isEmpty()) {
                        item {
                            Surface(
                                color = DarkSurface,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "Finding people for you...",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Check your connection and refresh if suggestions do not appear.",
                                        color = DarkTextSecondary,
                                        fontSize = 11.sp
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    OutlinedButton(onClick = onRefreshSuggestions) {
                                        Text("Refresh suggestions")
                                    }
                                }
                            }
                        }
                    } else {
                        items(
                            items = orderedCandidates,
                            key = { it.id }
                        ) { candidate ->
                            SuggestedUserRow(
                                profile = candidate,
                                followed = candidate.id in followingIds,
                                featured = candidate.username.equals(
                                    PINNED_BLINK_CREATOR,
                                    ignoreCase = true
                                ),
                                onToggle = {
                                    scope.launch {
                                        FollowStateStore.setFollowing(
                                            candidate.id,
                                            candidate.id !in followingIds
                                        )
                                    }
                                }
                            )
                        }
                    }

                    item {
                        Spacer(Modifier.height(6.dp))

                        PrimaryOnboardingButton(
                            text = if (followingIds.size >= REQUIRED_ONBOARDING_FOLLOWS)
                                "Enter BLINK"
                            else
                                "Follow ${REQUIRED_ONBOARDING_FOLLOWS - followingIds.size.coerceAtMost(REQUIRED_ONBOARDING_FOLLOWS)} more",
                            enabled = followingIds.size >= REQUIRED_ONBOARDING_FOLLOWS && !saving
                        ) {
                            saving = true
                            onFinish { ok, message ->
                                saving = false
                                if (!ok) error = message ?: "Unable to finish onboarding."
                            }
                        }
                    }
                }
            }
        }
    }

    if (universityPicker) {
        SearchablePickerDialog(
            title = "Select university",
            items = NigerianUniversities.all,
            selected = university,
            onDismiss = { universityPicker = false },
            onSelected = {
                university = it
                universityPicker = false
            }
        )
    }

    if (departmentPicker) {
        SearchablePickerDialog(
            title = "Select department",
            items = BlinkOnboardingCatalog.departments,
            selected = department,
            onDismiss = { departmentPicker = false },
            onSelected = {
                department = it
                departmentPicker = false
            }
        )
    }

    if (levelPicker) {
        SearchablePickerDialog(
            title = "Select level",
            items = BlinkOnboardingCatalog.levels,
            selected = level,
            onDismiss = { levelPicker = false },
            onSelected = {
                level = it
                levelPicker = false
            },
            allowClear = true
        )
    }

    if (genderPicker) {
        SearchablePickerDialog(
            title = "Select gender",
            items = BlinkOnboardingCatalog.genders,
            selected = gender,
            onDismiss = { genderPicker = false },
            onSelected = {
                gender = it
                genderPicker = false
            }
        )
    }
}

@Composable
private fun StepHeader(
    title: String,
    subtitle: String,
    icon: ImageVector
) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = CircleShape,
            color = BlinkPink.copy(alpha = .13f),
            modifier = Modifier.size(42.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = BlinkPink)
            }
        }

        Spacer(Modifier.width(12.dp))

        Column {
            Text(
                title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                subtitle,
                color = DarkTextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun PickerField(
    label: String,
    value: String,
    icon: ImageVector,
    placeholder: String,
    onClick: () -> Unit
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = BlinkPink)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = DarkTextSecondary, fontSize = 9.sp)
                Text(
                    value.ifBlank { placeholder },
                    color = if (value.isBlank()) DarkTextSecondary else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(Icons.Default.ExpandMore, contentDescription = null, tint = DarkTextSecondary)
        }
    }
}

@Composable
private fun SearchablePickerDialog(
    title: String,
    items: List<String>,
    selected: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
    allowClear: Boolean = false
) {
    var query by rememberSaveable(title) { mutableStateOf("") }
    val filtered = remember(query, items) {
        if (query.isBlank()) items
        else items.filter { it.contains(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    placeholder = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(330.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (allowClear) {
                    item {
                        TextButton(
                            onClick = { onSelected("") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Skip / not specified")
                        }
                    }
                }

                items(filtered, key = { it }) { value ->
                    Surface(
                        color = if (value == selected)
                            BlinkPink.copy(alpha = .10f)
                        else
                            Color.Transparent,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(value) }
                    ) {
                        Row(
                            modifier = Modifier.padding(11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (value == selected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = BlinkPink,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(value, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun SuggestedUserRow(
    profile: UserProfile,
    followed: Boolean,
    featured: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (featured) BlinkPink.copy(alpha = .45f) else DarkBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black,
                modifier = Modifier.size(50.dp)
            ) {
                if (profile.avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = profile.avatarUrl,
                        contentDescription = profile.fullName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.width(11.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile.fullName.ifBlank { profile.username },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    if (profile.verificationBadge != VerificationBadge.NONE) {
                        Spacer(Modifier.width(4.dp))
                        VerifiedMark(
                            badge = profile.verificationBadge,
                            size = 12.dp
                        )
                    }
                }

                Text(
                    "@${profile.username}",
                    color = DarkTextSecondary,
                    fontSize = 10.5.sp
                )

                val detail = listOf(profile.university, profile.department)
                    .filter { it.isNotBlank() }
                    .joinToString(" • ")
                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        color = DarkTextSecondary,
                        fontSize = 9.5.sp,
                        maxLines = 1
                    )
                }

                if (featured) {
                    Text(
                        "Featured BLINK creator",
                        color = BlinkPink,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }

            Button(
                onClick = onToggle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (followed) Color.White.copy(alpha = .12f) else BlinkPink,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(100.dp),
                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 8.dp)
            ) {
                Text(if (followed) "Following" else "Follow", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PrimaryOnboardingButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = BlinkPink,
            contentColor = Color.White,
            disabledContainerColor = DarkSurface,
            disabledContentColor = DarkTextSecondary
        ),
        shape = RoundedCornerShape(100.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}
