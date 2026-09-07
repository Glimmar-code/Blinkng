from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


# -----------------------------------------------------------------------------
# Models: server-backed spin preferences/result
# -----------------------------------------------------------------------------
models_path = Path("app/src/main/java/com/example/data/models/ConnectHubModels.kt")
models = models_path.read_text()
models = replace_once(
    models,
    """data class ConnectRequestItem(
""",
    """data class MatchSpinPreferences(
    val university: String? = null,
    val faculty: String? = null,
    val department: String? = null,
    val academicLevel: String? = null,
    val relationshipStatus: String? = null,
    val typePrompt: String = "",
    val onlineOnly: Boolean = false
)

data class MatchSpinResult(
    val candidate: SmartMatchCandidate,
    val remainingCoins: Long,
    val coinsSpent: Int = 10,
    val matchReasons: List<String> = emptyList()
)

data class ConnectRequestItem(
""",
    "Connect Hub match-spin models",
)
models_path.write_text(models)


# -----------------------------------------------------------------------------
# Repository: authenticated RPC for one atomic 10-Blink-Coin spin
# -----------------------------------------------------------------------------
repo_path = Path("app/src/main/java/com/example/data/repository/ConnectHubRepository.kt")
repo = repo_path.read_text()
repo = replace_once(
    repo,
    """import com.example.data.models.SmartMatchCandidate
""",
    """import com.example.data.models.SmartMatchCandidate
import com.example.data.models.MatchSpinPreferences
import com.example.data.models.MatchSpinResult
""",
    "Connect Hub repository match imports",
)
repo = replace_once(
    repo,
    """    suspend fun recordGameSession(gameType: String, score: Int): Boolean =
""",
    """    suspend fun spinMatch(preferences: MatchSpinPreferences): MatchSpinResult =
        withContext(Dispatchers.IO) {
            fun clean(value: String?): Any = value
                ?.trim()
                ?.takeIf { it.isNotBlank() && !it.equals("all", ignoreCase = true) }
                ?: JSONObject.NULL

            val body = JSONObject()
                .put("p_university", clean(preferences.university))
                .put("p_faculty", clean(preferences.faculty))
                .put("p_department", clean(preferences.department))
                .put("p_academic_level", clean(preferences.academicLevel))
                .put("p_relationship_status", clean(preferences.relationshipStatus))
                .put("p_type_prompt", clean(preferences.typePrompt.take(200)))
                .put("p_online_only", preferences.onlineOnly)

            val payload = JSONObject(rpc("spin_connect_match", body))
            val candidateJson = payload.optJSONObject("candidate")
                ?: throw IllegalStateException("No match was returned. Try widening your preferences.")

            MatchSpinResult(
                candidate = parseSmartMatch(candidateJson),
                remainingCoins = payload.optLong("remaining_coins", 0L),
                coinsSpent = payload.optInt("coins_spent", 10),
                matchReasons = payload.optStringList("match_reasons")
            )
        }

    suspend fun recordGameSession(gameType: String, score: Int): Boolean =
""",
    "Connect Hub spin RPC",
)
repo_path.write_text(repo)


# -----------------------------------------------------------------------------
# Profile relationship status options: align UI with backend enum values
# -----------------------------------------------------------------------------
edit_path = Path("app/src/main/java/com/example/ui/screens/EditProfileScreen.kt")
edit = edit_path.read_text()
edit = replace_once(
    edit,
    'listOf("Single", "Taken", "Private").forEach { status ->',
    'listOf("Single", "Taken", "Married", "It\'s complicated", "Prefer not to say", "Private").forEach { status ->',
    "Edit Profile relationship statuses",
)
edit_path.write_text(edit)


# -----------------------------------------------------------------------------
# Connect UI: preference dialog + server-authoritative match spin
# -----------------------------------------------------------------------------
panel_path = Path("app/src/main/java/com/example/ui/screens/ConnectHubPremiumPanel.kt")
panel = panel_path.read_text()

panel = replace_once(
    panel,
    """import androidx.compose.foundation.horizontalScroll
""",
    """import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
""",
    "Connect panel vertical scroll import",
)
panel = replace_once(
    panel,
    """import androidx.compose.foundation.layout.height
""",
    """import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
""",
    "Connect panel heightIn import",
)
panel = replace_once(
    panel,
    """import androidx.compose.material3.Button
""",
    """import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
""",
    "Connect panel dropdown imports",
)
panel = replace_once(
    panel,
    """import androidx.compose.material3.Surface
""",
    """import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
""",
    "Connect panel switch import",
)
panel = replace_once(
    panel,
    """import com.example.data.models.GameChallenge
import com.example.data.models.UserProfile
""",
    """import com.example.data.models.GameChallenge
import com.example.data.models.MatchSpinPreferences
import com.example.data.models.NigerianUniversities
import com.example.data.models.UserProfile
import com.example.data.repository.ConnectHubRepository
""",
    "Connect panel match imports",
)

panel = replace_once(
    panel,
    """    var hubQuery by rememberSaveable { mutableStateOf("") }
    var isMatching by remember { mutableStateOf(false) }
    var challengeTarget by remember { mutableStateOf<UserProfile?>(null) }
    var followingIds by remember { mutableStateOf(setOf<String>()) }
    val coroutineScope = rememberCoroutineScope()
""",
    """    var hubQuery by rememberSaveable { mutableStateOf("") }
    var isMatching by remember { mutableStateOf(false) }
    var showMatchSpinDialog by rememberSaveable { mutableStateOf(false) }
    var matchRemainingCoins by remember { mutableStateOf<Long?>(null) }
    var matchReasons by remember { mutableStateOf<List<String>>(emptyList()) }
    var matchError by remember { mutableStateOf<String?>(null) }
    var challengeTarget by remember { mutableStateOf<UserProfile?>(null) }
    var followingIds by remember { mutableStateOf(setOf<String>()) }
    val coroutineScope = rememberCoroutineScope()
    val matchRepository = remember { ConnectHubRepository() }
""",
    "Connect panel match state",
)

old_hero = """        SmartMatchHero(
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
"""
new_hero = """        SmartMatchHero(
            candidates = candidates,
            current = current,
            match = match,
            isMatching = isMatching,
            remainingCoins = matchRemainingCoins,
            matchReasons = matchReasons,
            errorMessage = matchError,
            onSpin = {
                if (!isMatching && current != null) showMatchSpinDialog = true
            },
            onProfileClick = onProfileClick,
            onMessage = { p -> onMessageUser(p.username, p.fullName, p.avatarUrl) },
            onChallenge = { p -> challengeTarget = p }
        )
"""
panel = replace_once(panel, old_hero, new_hero, "Connect panel server-backed spin")

challenge_anchor = """    challengeTarget?.let { target ->
"""
spin_dialog = """    if (showMatchSpinDialog) {
        MatchSpinDialog(
            profiles = profiles,
            isSubmitting = isMatching,
            onDismiss = { showMatchSpinDialog = false },
            onSpin = { preferences ->
                showMatchSpinDialog = false
                isMatching = true
                match = null
                matchError = null
                matchReasons = emptyList()
                coroutineScope.launch {
                    runCatching { matchRepository.spinMatch(preferences) }
                        .onSuccess { result ->
                            val candidate = result.candidate
                            match = UserProfile(
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
                            matchRemainingCoins = result.remainingCoins
                            matchReasons = result.matchReasons
                        }
                        .onFailure { error ->
                            matchError = error.message ?: "Unable to find a match right now."
                        }
                    isMatching = false
                }
            }
        )
    }

    challengeTarget?.let { target ->
"""
panel = replace_once(panel, challenge_anchor, spin_dialog, "Connect panel match dialog insertion")

hero_signature_old = """private fun SmartMatchHero(
    candidates: List<Pair<UserProfile, Int>>,
    current: UserProfile?,
    match: Pair<UserProfile, Int>?,
    isMatching: Boolean,
    onSpin: () -> Unit,
"""
hero_signature_new = """private fun SmartMatchHero(
    candidates: List<Pair<UserProfile, Int>>,
    current: UserProfile?,
    match: Pair<UserProfile, Int>?,
    isMatching: Boolean,
    remainingCoins: Long?,
    matchReasons: List<String>,
    errorMessage: String?,
    onSpin: () -> Unit,
"""
panel = replace_once(panel, hero_signature_old, hero_signature_new, "Smart Match hero signature")

panel = replace_once(
    panel,
    """                    Text(
                        "Find someone based on what you have most in common.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
""",
    """                    Text(
                        "Set your preferences, then search profiles and public interests. Each successful spin costs 10 Blink Coins.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
""",
    "Smart Match hero subtitle",
)
panel = replace_once(
    panel,
    """                    enabled = candidates.isNotEmpty() && !isMatching,
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(if (isMatching) "Matching…" else "Spin")
""",
    """                    enabled = current != null && !isMatching,
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(if (isMatching) "Matching…" else "Spin · 10")
""",
    "Smart Match paid spin button",
)
panel = replace_once(
    panel,
    """                        MatchResultCard(
                            current = current,
                            person = person,
                            compatibilityOverride = serverScore,
                            onProfileClick = { onProfileClick(person.username) },
                            onMessage = { onMessage(person) },
                            onChallenge = { onChallenge(person) }
                        )
""",
    """                        MatchResultCard(
                            current = current,
                            person = person,
                            compatibilityOverride = serverScore,
                            onProfileClick = { onProfileClick(person.username) },
                            onMessage = { onMessage(person) },
                            onChallenge = { onChallenge(person) }
                        )
                        if (matchReasons.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Why this match: ${matchReasons.joinToString(" • ")}",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        remainingCoins?.let { balance ->
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "$balance Blink Coins remaining",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
""",
    "Smart Match result metadata",
)
panel = replace_once(
    panel,
    """                } else if (isMatching) {
                    Text(
                        "Finding your strongest campus match…",
                        modifier = Modifier.padding(top = 14.dp),
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
""",
    """                } else if (isMatching) {
                    Text(
                        "Searching profiles, campus details and public interests…",
                        modifier = Modifier.padding(top = 14.dp),
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    errorMessage,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
""",
    "Smart Match error state",
)

match_dialog_code = r'''

@Composable
private fun MatchSpinDialog(
    profiles: List<UserProfile>,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSpin: (MatchSpinPreferences) -> Unit
) {
    var universitySearch by rememberSaveable { mutableStateOf("") }
    var selectedUniversity by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedFaculty by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDepartment by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedLevel by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRelationship by rememberSaveable { mutableStateOf<String?>(null) }
    var typePrompt by rememberSaveable { mutableStateOf("") }
    var onlineOnly by rememberSaveable { mutableStateOf(false) }

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

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Column {
                Text("Match Spin", fontWeight = FontWeight.Black)
                Text(
                    "Choose who you want to meet",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "A successful spin costs 10 Blink Coins. If no eligible match is found, you are not charged.",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

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
                    label = { Text("Type your type (optional)") },
                    placeholder = { Text("e.g. likes coding, cooking, social activities or football") },
                    supportingText = {
                        Text("Matches against public profile details and Everyone posts — never private messages.")
                    },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp)
                )

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
                                "Optional — useful when you want someone available now.",
                                fontSize = 9.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = onlineOnly, onCheckedChange = { onlineOnly = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSpin(
                        MatchSpinPreferences(
                            university = selectedUniversity,
                            faculty = selectedFaculty,
                            department = selectedDepartment,
                            academicLevel = selectedLevel,
                            relationshipStatus = selectedRelationship,
                            typePrompt = typePrompt.trim(),
                            onlineOnly = onlineOnly
                        )
                    )
                },
                enabled = !isSubmitting
            ) {
                Text("Spin for 10 coins")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Cancel") }
        }
    )
}

@Composable
private fun SearchableUniversityMatchField(
    query: String,
    selected: String?,
    options: List<String>,
    onQueryChange: (String) -> Unit,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                onQueryChange(it)
                expanded = true
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("University") },
            placeholder = { Text("All universities") },
            leadingIcon = { Icon(Icons.Default.School, contentDescription = null) },
            supportingText = {
                Text(if (selected == null) "All universities" else "Selected university")
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("All universities") },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            options.forEach { university ->
                DropdownMenuItem(
                    text = { Text(university, maxLines = 2) },
                    onClick = {
                        onSelect(university)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun MatchChoiceDropdown(
    label: String,
    value: String?,
    options: List<String>,
    allLabel: String,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value ?: allLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(allLabel) },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
'''

hero_anchor = """@Composable
private fun SmartMatchHero(
"""
panel = replace_once(panel, hero_anchor, match_dialog_code + "\n@Composable\nprivate fun SmartMatchHero(\n", "Match Spin dialog composables")

panel_path.write_text(panel)
