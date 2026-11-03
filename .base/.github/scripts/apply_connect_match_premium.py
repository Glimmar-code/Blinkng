from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        if new in text:
            return text
        raise RuntimeError(f"{label}: expected source block not found")
    if text.count(old) != 1:
        raise RuntimeError(f"{label}: expected source block exactly once, found {text.count(old)}")
    return text.replace(old, new, 1)


def replace_between(text: str, start: str, end: str, replacement: str, label: str) -> str:
    start_index = text.find(start)
    if start_index < 0:
        if replacement.strip() in text:
            return text
        raise RuntimeError(f"{label}: start marker not found")
    end_index = text.find(end, start_index)
    if end_index < 0:
        raise RuntimeError(f"{label}: end marker not found")
    return text[:start_index] + replacement + "\n\n" + text[end_index:]


# 1) Add connection intent to the preference model.
model_path = Path("app/src/main/java/com/example/data/models/ConnectHubModels.kt")
model = model_path.read_text()
model = replace_once(
    model,
    '''    val academicLevel: String? = null,\n    val relationshipStatus: String? = null,\n    val typePrompt: String = "",\n    val onlineOnly: Boolean = false\n''',
    '''    val academicLevel: String? = null,\n    val relationshipStatus: String? = null,\n    val connectionIntent: String? = null,\n    val typePrompt: String = "",\n    val onlineOnly: Boolean = false\n''',
    "MatchSpinPreferences connection intent",
)
model_path.write_text(model)


# 2) Fold connection intent into the existing semantic prompt without changing the server RPC signature.
repo_path = Path("app/src/main/java/com/example/data/repository/ConnectHubRepository.kt")
repo = repo_path.read_text()
repo = replace_once(
    repo,
    '''            val body = JSONObject()\n                .put("p_university", clean(preferences.university))\n                .put("p_faculty", clean(preferences.faculty))\n                .put("p_department", clean(preferences.department))\n                .put("p_academic_level", clean(preferences.academicLevel))\n                .put("p_relationship_status", clean(preferences.relationshipStatus))\n                .put("p_type_prompt", clean(preferences.typePrompt.take(200)))\n                .put("p_online_only", preferences.onlineOnly)\n''',
    '''            val semanticPrompt = listOfNotNull(\n                preferences.connectionIntent\n                    ?.trim()\n                    ?.takeIf { it.isNotBlank() && !it.equals("Any", ignoreCase = true) }\n                    ?.let { "Connection intent: $it" },\n                preferences.typePrompt.trim().takeIf { it.isNotBlank() }\n            ).joinToString(". ").take(200)\n\n            val body = JSONObject()\n                .put("p_university", clean(preferences.university))\n                .put("p_faculty", clean(preferences.faculty))\n                .put("p_department", clean(preferences.department))\n                .put("p_academic_level", clean(preferences.academicLevel))\n                .put("p_relationship_status", clean(preferences.relationshipStatus))\n                .put("p_type_prompt", clean(semanticPrompt))\n                .put("p_online_only", preferences.onlineOnly)\n''',
    "spinMatch semantic intent",
)
repo_path.write_text(repo)


ui_path = Path("app/src/main/java/com/example/ui/screens/ConnectHubPremiumPanel.kt")
ui = ui_path.read_text()

# 3) Session-persistent premium state: saved preferences and recent results.
ui = replace_once(
    ui,
    '''    var matchRemainingCoins by remember { mutableStateOf<Long?>(null) }\n    var matchReasons by remember { mutableStateOf<List<String>>(emptyList()) }\n    var matchError by remember { mutableStateOf<String?>(null) }\n    var challengeTarget by remember { mutableStateOf<UserProfile?>(null) }\n''',
    '''    var matchRemainingCoins by remember { mutableStateOf<Long?>(null) }\n    var matchReasons by remember { mutableStateOf<List<String>>(emptyList()) }\n    var matchError by remember { mutableStateOf<String?>(null) }\n    var savedMatchPreferences by remember { mutableStateOf<MatchSpinPreferences?>(null) }\n    var recentMatches by remember { mutableStateOf<List<Pair<UserProfile, Int>>>(emptyList()) }\n    var challengeTarget by remember { mutableStateOf<UserProfile?>(null) }\n''',
    "premium match state",
)

# 4) Pass premium state and routing into the hero.
ui = replace_once(
    ui,
    '''            remainingCoins = matchRemainingCoins,\n            matchReasons = matchReasons,\n            errorMessage = matchError,\n            onSpin = {\n                if (!isMatching && current != null) showMatchSpinDialog = true\n            },\n''',
    '''            remainingCoins = matchRemainingCoins,\n            matchReasons = matchReasons,\n            errorMessage = matchError,\n            recentMatches = recentMatches,\n            hasSavedPreferences = savedMatchPreferences != null,\n            onSpin = {\n                if (!isMatching && current != null) showMatchSpinDialog = true\n            },\n''',
    "SmartMatchHero premium args",
)

# 5) Give the preference dialog a reusable saved preset.
ui = replace_once(
    ui,
    '''        MatchSpinDialog(\n            profiles = profiles,\n            isSubmitting = isMatching,\n            onDismiss = { showMatchSpinDialog = false },\n            onSpin = { preferences ->\n''',
    '''        MatchSpinDialog(\n            profiles = profiles,\n            isSubmitting = isMatching,\n            initialPreferences = savedMatchPreferences,\n            onSavePreferences = { savedMatchPreferences = it },\n            onDismiss = { showMatchSpinDialog = false },\n            onSpin = { preferences ->\n''',
    "MatchSpinDialog saved preset args",
)

# 6) Remember successful results, intent and reasons, while retaining existing routing.
old_success = '''                        .onSuccess { result ->\n                            val candidate = result.candidate\n                            match = UserProfile(\n                                id = candidate.userId,\n                                fullName = candidate.fullName,\n                                username = candidate.username,\n                                avatarUrl = candidate.avatarUrl,\n                                university = candidate.university,\n                                faculty = candidate.faculty,\n                                department = candidate.department,\n                                academicLevel = candidate.academicLevel,\n                                relationshipStatus = candidate.relationshipStatus,\n                                onlineNow = candidate.onlineNow,\n                                lastSeenAt = candidate.lastSeenAt,\n                                coreSkills = candidate.commonSkills.toMutableList(),\n                                hobbies = candidate.commonHobbies\n                            ) to candidate.compatibilityScore\n                            matchRemainingCoins = result.remainingCoins\n                            matchReasons = result.matchReasons\n                        }\n'''
new_success = '''                        .onSuccess { result ->\n                            val candidate = result.candidate\n                            val scoredMatch = UserProfile(\n                                id = candidate.userId,\n                                fullName = candidate.fullName,\n                                username = candidate.username,\n                                avatarUrl = candidate.avatarUrl,\n                                university = candidate.university,\n                                faculty = candidate.faculty,\n                                department = candidate.department,\n                                academicLevel = candidate.academicLevel,\n                                relationshipStatus = candidate.relationshipStatus,\n                                onlineNow = candidate.onlineNow,\n                                lastSeenAt = candidate.lastSeenAt,\n                                coreSkills = candidate.commonSkills.toMutableList(),\n                                hobbies = candidate.commonHobbies\n                            ) to candidate.compatibilityScore\n                            match = scoredMatch\n                            recentMatches = (listOf(scoredMatch) + recentMatches.filterNot { it.first.id == candidate.userId })\n                                .take(5)\n                            savedMatchPreferences = preferences\n                            matchRemainingCoins = result.remainingCoins\n                            val intentReason = preferences.connectionIntent\n                                ?.takeIf { it.isNotBlank() && !it.equals("Any", ignoreCase = true) }\n                                ?.let { "Good fit for $it" }\n                            matchReasons = (result.matchReasons + listOfNotNull(intentReason)).distinct().take(6)\n                        }\n'''
ui = replace_once(ui, old_success, new_success, "successful match premium handling")

# 7) Replace the dialog with intent, quick interests, selected-filter summary and saved preferences.
match_dialog = r'''@Composable
private fun MatchSpinDialog(
    profiles: List<UserProfile>,
    isSubmitting: Boolean,
    initialPreferences: MatchSpinPreferences?,
    onSavePreferences: (MatchSpinPreferences) -> Unit,
    onDismiss: () -> Unit,
    onSpin: (MatchSpinPreferences) -> Unit
) {
    var universitySearch by rememberSaveable(initialPreferences?.university) {
        mutableStateOf(initialPreferences?.university.orEmpty())
    }
    var selectedUniversity by remember(initialPreferences?.university) { mutableStateOf(initialPreferences?.university) }
    var selectedFaculty by remember(initialPreferences?.faculty) { mutableStateOf(initialPreferences?.faculty) }
    var selectedDepartment by remember(initialPreferences?.department) { mutableStateOf(initialPreferences?.department) }
    var selectedLevel by remember(initialPreferences?.academicLevel) { mutableStateOf(initialPreferences?.academicLevel) }
    var selectedRelationship by remember(initialPreferences?.relationshipStatus) {
        mutableStateOf(initialPreferences?.relationshipStatus)
    }
    var connectionIntent by rememberSaveable(initialPreferences?.connectionIntent) {
        mutableStateOf(initialPreferences?.connectionIntent ?: "Any")
    }
    var typePrompt by rememberSaveable(initialPreferences?.typePrompt) {
        mutableStateOf(initialPreferences?.typePrompt.orEmpty())
    }
    var onlineOnly by rememberSaveable(initialPreferences?.onlineOnly) {
        mutableStateOf(initialPreferences?.onlineOnly ?: false)
    }

    val universityOptions = remember(universitySearch) {
        val q = universitySearch.trim()
        NigerianUniversities.all
            .asSequence()
            .filter { q.isBlank() || it.contains(q, ignoreCase = true) }
            .take(24)
            .toList()
    }
    val campusProfiles = remember(profiles, selectedUniversity) {
        profiles.filter { profile ->
            selectedUniversity == null || profile.university.equals(selectedUniversity, ignoreCase = true)
        }
    }
    val facultyOptions = remember(campusProfiles) {
        campusProfiles.map { it.faculty.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val departmentOptions = remember(campusProfiles, selectedFaculty) {
        campusProfiles
            .filter { selectedFaculty == null || it.faculty.equals(selectedFaculty, ignoreCase = true) }
            .map { it.department.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    val levelOptions = remember(profiles) {
        (
            listOf("100 Level", "200 Level", "300 Level", "400 Level", "500 Level", "600 Level", "Postgraduate") +
                profiles.map { it.academicLevel.trim() }.filter { it.isNotBlank() }
        ).distinct()
    }
    val intentOptions = remember {
        listOf("Any", "Friends", "Study partner", "Project teammate", "Networking", "Mentor", "Gaming buddy")
    }
    val interestSuggestions = remember {
        listOf("Coding", "Music", "Football", "Business", "Gaming", "Reading", "Design", "Cooking", "Photography", "Entrepreneurship")
    }

    fun currentPreferences(): MatchSpinPreferences = MatchSpinPreferences(
        university = selectedUniversity,
        faculty = selectedFaculty,
        department = selectedDepartment,
        academicLevel = selectedLevel,
        relationshipStatus = selectedRelationship,
        connectionIntent = connectionIntent,
        typePrompt = typePrompt.trim(),
        onlineOnly = onlineOnly
    )

    val activeFilters = listOfNotNull(
        selectedUniversity,
        selectedFaculty,
        selectedDepartment,
        selectedLevel,
        connectionIntent.takeUnless { it == "Any" },
        selectedRelationship,
        "Online".takeIf { onlineOnly }
    )

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Column {
                Text("Smart Match", fontWeight = FontWeight.Black)
                Text(
                    "Build a precise discovery profile",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .38f)
                ) {
                    Text(
                        "A successful spin costs 10 Blink Coins. No eligible result means no charge.",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Text("I'm looking for", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    intentOptions.forEach { intent ->
                        PremiumChoicePill(
                            label = intent,
                            selected = connectionIntent == intent,
                            onClick = { connectionIntent = intent }
                        )
                    }
                }

                SearchableUniversityMatchField(
                    query = universitySearch,
                    selected = selectedUniversity,
                    options = universityOptions,
                    onQueryChange = {
                        universitySearch = it
                        if (selectedUniversity != null && !selectedUniversity.equals(it, ignoreCase = true)) {
                            selectedUniversity = null
                            selectedFaculty = null
                            selectedDepartment = null
                        }
                    },
                    onSelect = { university ->
                        selectedUniversity = university
                        universitySearch = university.orEmpty()
                        selectedFaculty = null
                        selectedDepartment = null
                    }
                )

                MatchChoiceDropdown(
                    label = "Faculty",
                    value = selectedFaculty,
                    options = facultyOptions,
                    allLabel = "All faculties",
                    onSelect = {
                        selectedFaculty = it
                        selectedDepartment = null
                    }
                )
                MatchChoiceDropdown(
                    label = "Department",
                    value = selectedDepartment,
                    options = departmentOptions,
                    allLabel = "All departments",
                    onSelect = { selectedDepartment = it }
                )
                MatchChoiceDropdown(
                    label = "Level",
                    value = selectedLevel,
                    options = levelOptions,
                    allLabel = "All levels",
                    onSelect = { selectedLevel = it }
                )
                MatchChoiceDropdown(
                    label = "Connection status",
                    value = selectedRelationship,
                    options = listOf("Single", "Taken", "Married", "It's complicated", "Prefer not to say", "Private"),
                    allLabel = "All statuses",
                    onSelect = { selectedRelationship = it }
                )

                OutlinedTextField(
                    value = typePrompt,
                    onValueChange = { typePrompt = it.take(180) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Describe your ideal connection (optional)") },
                    placeholder = { Text("e.g. likes coding, cooking, social activities or football") },
                    supportingText = {
                        Text("Uses public profile details and Everyone posts — never private messages.")
                    },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp)
                )

                Text("Quick interests", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    interestSuggestions.forEach { interest ->
                        val selected = typePrompt.contains(interest, ignoreCase = true)
                        PremiumChoicePill(
                            label = interest,
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    typePrompt = listOf(typePrompt.trim().trimEnd(','), interest)
                                        .filter { it.isNotBlank() }
                                        .joinToString(", ")
                                        .take(180)
                                }
                            }
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onlineOnly = !onlineOnly }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Online now only", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Prioritize people currently available in Blink.",
                                fontSize = 9.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = onlineOnly, onCheckedChange = { onlineOnly = it })
                    }
                }

                if (activeFilters.isNotEmpty()) {
                    Text("Active preferences", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        activeFilters.take(8).forEach { filter ->
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)
                            ) {
                                Text(
                                    filter,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSpin(currentPreferences()) },
                enabled = !isSubmitting,
                shape = RoundedCornerShape(100.dp)
            ) {
                Text("Find match · 10")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(
                    onClick = { onSavePreferences(currentPreferences()) },
                    enabled = !isSubmitting
                ) { Text("Save") }
                TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun PremiumChoicePill(label: String, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else .97f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "premiumChoiceScale"
    )
    val background by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f),
        animationSpec = tween(180),
        label = "premiumChoiceColor"
    )
    Surface(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(100.dp),
        color = background
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}'''
ui = replace_between(
    ui,
    "@Composable\nprivate fun MatchSpinDialog(",
    "@Composable\nprivate fun SearchableUniversityMatchField(",
    match_dialog,
    "premium MatchSpinDialog",
)

# 8) Replace the result hero with premium staged animation, reasons, spin-again and recent routed matches.
smart_hero = r'''@Composable
private fun SmartMatchHero(
    candidates: List<Pair<UserProfile, Int>>,
    current: UserProfile?,
    match: Pair<UserProfile, Int>?,
    isMatching: Boolean,
    remainingCoins: Long?,
    matchReasons: List<String>,
    errorMessage: String?,
    recentMatches: List<Pair<UserProfile, Int>>,
    hasSavedPreferences: Boolean,
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
    val stages = remember {
        listOf(
            "Reading your preferences",
            "Comparing public interests",
            "Ranking compatible profiles",
            "Selecting the strongest fit"
        )
    }
    var searchStage by remember(isMatching) { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(isMatching) {
        if (isMatching) {
            while (true) {
                delay(620)
                searchStage = (searchStage + 1) % stages.size
            }
        } else {
            searchStage = 0
        }
    }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Smart Match", fontSize = 17.sp, fontWeight = FontWeight.Black)
                        if (hasSavedPreferences) {
                            Spacer(Modifier.width(7.dp))
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)
                            ) {
                                Text(
                                    "Saved",
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Text(
                        "Preference-based discovery using public profile and activity signals.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onSpin,
                    enabled = current != null && !isMatching,
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(if (isMatching) "Matching…" else "Spin · 10")
                }
            }

            AnimatedContent(
                targetState = Triple(match, isMatching, errorMessage),
                transitionSpec = {
                    (fadeIn(tween(300)) + scaleIn(initialScale = .95f) + slideInVertically { it / 8 }) togetherWith
                        (fadeOut(tween(180)) + scaleOut(targetScale = .97f))
                },
                label = "premiumSmartMatchState"
            ) { state ->
                val result = state.first
                val matching = state.second
                val error = state.third
                when {
                    result != null -> {
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
                            if (matchReasons.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                Text("Why this match", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                MatchReasonPills(matchReasons)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                remainingCoins?.let { balance ->
                                    Text(
                                        "$balance Blink Coins remaining",
                                        modifier = Modifier.weight(1f),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } ?: Spacer(Modifier.weight(1f))
                                TextButton(onClick = onSpin, enabled = !isMatching) {
                                    Text("Spin again · 10")
                                }
                            }
                        }
                    }
                    matching -> PremiumMatchSearchProgress(stages = stages, stageIndex = searchStage)
                    !error.isNullOrBlank() -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .55f)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    error,
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                                TextButton(onClick = onSpin) { Text("Adjust preferences") }
                            }
                        }
                    }
                    else -> {
                        Text(
                            "Choose your intent, campus preferences and interests to get a more useful match.",
                            modifier = Modifier.padding(top = 12.dp),
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (recentMatches.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f))
                Spacer(Modifier.height(10.dp))
                Text("Recent matches", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recentMatches.forEach { (person, score) ->
                        Surface(
                            modifier = Modifier.clickable { onProfileClick(person.username) },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = .72f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = person.avatarUrl,
                                    error = painterResource(R.drawable.ic_default_profile),
                                    fallback = painterResource(R.drawable.ic_default_profile),
                                    contentDescription = person.fullName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(28.dp).clip(CircleShape)
                                )
                                Spacer(Modifier.width(7.dp))
                                Column {
                                    Text(
                                        person.fullName.ifBlank { person.username },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text("$score% match", fontSize = 8.5.sp, color = BlinkPink, fontWeight = FontWeight.Bold)
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
private fun PremiumMatchSearchProgress(stages: List<String>, stageIndex: Int) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        AnimatedContent(
            targetState = stageIndex,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically { it / 3 }) togetherWith fadeOut(tween(140))
            },
            label = "matchSearchStage"
        ) { index ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(stages[index], fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Matching securely against eligible public signals…",
                        fontSize = 9.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            stages.indices.forEach { index ->
                val width by animateDpAsState(
                    targetValue = if (index == stageIndex) 24.dp else 7.dp,
                    animationSpec = tween(220),
                    label = "matchStageWidth"
                )
                Box(
                    Modifier
                        .width(width)
                        .height(7.dp)
                        .background(
                            if (index == stageIndex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(100.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun MatchReasonPills(reasons: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        reasons.take(6).forEachIndexed { index, reason ->
            var visible by remember(reason) { mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(reason) {
                delay(index * 55L)
                visible = true
            }
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(220)) + scaleIn(initialScale = .90f)
            ) {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)
                ) {
                    Text(
                        reason,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}'''
ui = replace_between(
    ui,
    "@Composable\nprivate fun SmartMatchHero(",
    "@Composable\nprivate fun CategoryTabBar(",
    smart_hero,
    "premium SmartMatchHero",
)

ui_path.write_text(ui)
print("Applied premium Connect match discovery UI, transitions, presets, intents and recent-profile routing.")
