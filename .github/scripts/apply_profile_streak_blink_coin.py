from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


# -----------------------------------------------------------------------------
# SupabaseService: daily streak + private Blink Coin reads
# -----------------------------------------------------------------------------
service_path = Path("app/src/main/java/com/example/data/supabase/SupabaseService.kt")
service = service_path.read_text()
service_anchor = """    private val anonKey =
        SupabaseConfig.anonKey

    // ============================================================
    // HTTP
"""
service_insert = """    private val anonKey =
        SupabaseConfig.anonKey

    /**
     * Records today's authenticated app activity and returns the canonical public
     * streak. The RPC is idempotent for the same UTC day and resets after a missed day.
     */
    suspend fun touchDailyStreak(): Int? = withContext(Dispatchers.IO) {
        try {
            val request = newRequestBuilder("/rest/v1/rpc/touch_daily_streak")
                .post("{}".toRequestBody(jsonMediaType))
                .build()

            executeRequest(request).use { response ->
                val body = response.body?.string().orEmpty().trim()
                if (!response.isSuccessful) {
                    Log.w(TAG, "DAILY_STREAK failed status=${response.code} body=$body")
                    return@withContext null
                }
                body.trim('\\"').toIntOrNull()
            }
        } catch (e: Exception) {
            Log.w(TAG, "DAILY_STREAK exception", e)
            null
        }
    }

    /** Owner-only balance. RLS prevents another authenticated user from reading it. */
    suspend fun fetchMyBlinkCoinBalance(): Long = withContext(Dispatchers.IO) {
        try {
            val request = newRequestBuilder("/rest/v1/rpc/get_my_blink_coin_balance")
                .post("{}".toRequestBody(jsonMediaType))
                .build()

            executeRequest(request).use { response ->
                val body = response.body?.string().orEmpty().trim()
                if (!response.isSuccessful) {
                    Log.w(TAG, "BLINK_COIN_BALANCE failed status=${response.code} body=$body")
                    return@withContext 0L
                }
                body.trim('\\"').toBigDecimalOrNull()?.toLong() ?: 0L
            }
        } catch (e: Exception) {
            Log.w(TAG, "BLINK_COIN_BALANCE exception", e)
            0L
        }
    }

    // ============================================================
    // HTTP
"""
service = replace_once(service, service_anchor, service_insert, "SupabaseService RPC insertion")
service_path.write_text(service)


# -----------------------------------------------------------------------------
# BlinkViewModel: private balance state + once-per-day streak refresh
# -----------------------------------------------------------------------------
vm_path = Path("app/src/main/java/com/example/viewmodel/BlinkViewModel.kt")
vm = vm_path.read_text()
vm = replace_once(
    vm,
    """    val pendingMessageCount: Int = 0,
    val discoverProfiles: List<UserProfile> = emptyList(),
""",
    """    val pendingMessageCount: Int = 0,
    val blinkCoinBalance: Long = 0L,
    val discoverProfiles: List<UserProfile> = emptyList(),
""",
    "BlinkUiState coin field",
)

reward_methods = """
    fun refreshProfileRewards() {
        val before = _uiState.value
        if (!before.isOnline || before.myProfile.id.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val streak = runCatching { supabaseService.touchDailyStreak() }
                .onFailure { Log.w(TAG, "Daily streak refresh failed", it) }
                .getOrNull()
            val balance = runCatching { supabaseService.fetchMyBlinkCoinBalance() }
                .onFailure { Log.w(TAG, "Blink Coin balance refresh failed", it) }
                .getOrDefault(before.blinkCoinBalance)

            withContext(Dispatchers.Main) {
                val latest = _uiState.value
                val currentMe = latest.myProfile
                if (currentMe.id.isBlank()) return@withContext

                val updatedMe = streak?.let { currentMe.copy(dailyStreak = it) } ?: currentMe
                val updatedProfiles = latest.profiles.map { candidate ->
                    if (
                        candidate.id == updatedMe.id ||
                        candidate.username.equals(updatedMe.username, ignoreCase = true)
                    ) updatedMe else candidate
                }
                val updatedViewing = latest.viewingProfile?.let { candidate ->
                    if (
                        candidate.id == updatedMe.id ||
                        candidate.username.equals(updatedMe.username, ignoreCase = true)
                    ) updatedMe else candidate
                }

                _uiState.value = latest.copy(
                    myProfile = updatedMe,
                    profiles = updatedProfiles,
                    viewingProfile = updatedViewing,
                    blinkCoinBalance = balance
                )
                saveLocalProfile(updatedMe)
                persistProfile(updatedMe)
                persistExtendedCache()
            }
        }
    }

    fun watchAdForBlinkCoins() {
        showToast("Rewarded ads need an ad provider configured before coins can be granted.")
    }

    fun buyBlinkCoins() {
        showToast("Blink Coin purchases need Google Play Billing products configured first.")
    }

"""
vm_anchor = """    fun refreshIfStale(maxAgeMillis: Long = 60_000L) {
"""
if vm.count(vm_anchor) != 1:
    raise RuntimeError(f"ViewModel rewards insertion: expected 1 anchor, found {vm.count(vm_anchor)}")
vm = vm.replace(vm_anchor, reward_methods + vm_anchor, 1)
vm_path.write_text(vm)


# -----------------------------------------------------------------------------
# MainActivity: refresh private balance/streak after authenticated profile exists
# -----------------------------------------------------------------------------
main_path = Path("app/src/main/java/com/example/MainActivity.kt")
main = main_path.read_text()
main = replace_once(
    main,
    """                    BlinkFirebaseMessagingService.syncCurrentToken(this@MainActivity)
                    if (
""",
    """                    BlinkFirebaseMessagingService.syncCurrentToken(this@MainActivity)
                    viewModel.refreshProfileRewards()
                    if (
""",
    "MainActivity reward refresh",
)
main = replace_once(
    main,
    """                    onOpenGetVerified = { viewModel.openGetVerified(true) },
                    isDark = uiState.isDarkMode
""",
    """                    onOpenGetVerified = { viewModel.openGetVerified(true) },
                    blinkCoinBalance = if (isMyProfile) uiState.blinkCoinBalance else 0L,
                    onWatchAdForCoins = { viewModel.watchAdForBlinkCoins() },
                    onBuyBlinkCoins = { viewModel.buyBlinkCoins() },
                    isDark = uiState.isDarkMode
""",
    "ProfileScreen coin callbacks",
)
main_path.write_text(main)


# -----------------------------------------------------------------------------
# ProfileScreen UI
# -----------------------------------------------------------------------------
profile_path = Path("app/src/main/java/com/example/ui/screens/ProfileScreen.kt")
profile = profile_path.read_text()

profile = replace_once(
    profile,
    """    onMarketItemClick: (MarketItem) -> Unit,
    onOpenGetVerified: () -> Unit = {},
    isDark: Boolean,
""",
    """    onMarketItemClick: (MarketItem) -> Unit,
    onOpenGetVerified: () -> Unit = {},
    blinkCoinBalance: Long = 0L,
    onWatchAdForCoins: () -> Unit = {},
    onBuyBlinkCoins: () -> Unit = {},
    isDark: Boolean,
""",
    "ProfileScreen signature",
)

profile = replace_once(
    profile,
    """    var showAvatarViewer by rememberSaveable { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
""",
    """    var showAvatarViewer by rememberSaveable { mutableStateOf(false) }
    var showEarnCoinDialog by rememberSaveable { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
""",
    "Earn coin dialog state",
)

profile = replace_once(
    profile,
    """                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                CircleToolbarButton(
                                    icon = Icons.Default.Share,
                                    contentDescription = "Share profile",
                                    onClick = { showShareSheet = true }
                                )
                                CircleToolbarButton(
                                    icon = Icons.Default.MoreVert,
                                    contentDescription = "More profile options",
                                    onClick = { showMoreSheet = true }
                                )
                            }
""",
    """                            CircleToolbarButton(
                                icon = Icons.Default.MoreVert,
                                contentDescription = "More profile options",
                                onClick = { showMoreSheet = true }
                            )
""",
    "Move top share action",
)

old_name_row = """                        EntranceItem(visible = contentVisible, delayMillis = 60) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = profile.fullName,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = textPrimary
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                                AnimatedVisibility(
                                    visible = profile.verificationBadge != VerificationBadge.NONE,
                                    enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn()
                                ) {
                                    VerifiedMark(badge = profile.verificationBadge, size = 20.dp)
                                }
                            }
                        }
"""
new_name_row = """                        EntranceItem(visible = contentVisible, delayMillis = 60) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = profile.fullName,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = textPrimary
                                )
                                if (profile.verificationBadge != VerificationBadge.NONE) {
                                    Spacer(modifier = Modifier.width(7.dp))
                                    VerifiedMark(badge = profile.verificationBadge, size = 20.dp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = BlinkPink.copy(alpha = 0.10f),
                                    border = BorderStroke(1.dp, BlinkPink.copy(alpha = 0.28f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("🔥", fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "${profile.dailyStreak}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = BlinkPink
                                        )
                                    }
                                }
                            }
                        }
"""
profile = replace_once(profile, old_name_row, new_name_row, "Public daily streak chip")

# Remove old quick-actions + large completion card so they can live below the stats/rank boxes.
quick_start = profile.index("                        // QUICK ACTIONS")
stats_start = profile.index("                        // STATS & RANKS", quick_start)
profile = profile[:quick_start] + profile[stats_start:]

rank_start_marker = "                        // Daily Streak, World Rank & Campus Rank Showcase Card\n"
rank_end_marker = "                    }\n                }\n\n                // ============================================================\n                // PROFILE TRUST"
rank_start = profile.index(rank_start_marker)
rank_end = profile.index(rank_end_marker, rank_start)
new_rank_and_actions = """                        // Campus + world rank showcase. The daily streak is now public beside the name.
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🏛️", fontSize = 15.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("#${profile.campusRank}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = BlinkGold)
                                    }
                                    Text("Campus Rank", fontSize = 9.5.sp, color = textSecondary)
                                }

                                DividerMetric()

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🌐", fontSize = 15.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("#${profile.worldRank}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = BlinkBlue)
                                    }
                                    Text("World Rank", fontSize = 9.5.sp, color = textSecondary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Requested profile actions live directly below the Posts/Campus boxes.
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            item {
                                OutlinePill(
                                    icon = Icons.Default.Link,
                                    text = "Share profile",
                                    onClick = { showShareSheet = true }
                                )
                            }
                            item {
                                OutlinePill(
                                    icon = Icons.Default.ContentCopy,
                                    text = "Copy username",
                                    onClick = {
                                        clipboard.setText(AnnotatedString("@${profile.username}"))
                                        Toast.makeText(context, "Username copied", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                            if (isMe) {
                                item {
                                    OutlinePill(
                                        icon = if (profileCompletion >= 100) Icons.Default.CheckCircle else Icons.Default.AutoAwesome,
                                        text = if (profileCompletion >= 100) "Profile complete" else "Complete profile ${profileCompletion}%",
                                        onClick = onEditProfileClick
                                    )
                                }
                            }
                            if (profile.links.website.isNotBlank()) {
                                item {
                                    OutlinePill(
                                        icon = Icons.Default.Language,
                                        text = "Website",
                                        onClick = { openExternalUrl(context, profile.links.website) }
                                    )
                                }
                            }
                        }

                        if (isMe) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                color = cardBg,
                                border = BorderStroke(1.dp, BlinkGold.copy(alpha = 0.42f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(shape = CircleShape, color = BlinkGold.copy(alpha = 0.14f)) {
                                        Text("🪙", fontSize = 22.sp, modifier = Modifier.padding(9.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Blink Coin", fontSize = 13.sp, fontWeight = FontWeight.Black, color = textPrimary)
                                        Text(
                                            "$blinkCoinBalance coins • Private",
                                            fontSize = 10.sp,
                                            color = textSecondary
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = { showEarnCoinDialog = true },
                                        shape = RoundedCornerShape(100.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                                    ) {
                                        Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text("Earn coin", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
"""
profile = profile[:rank_start] + new_rank_and_actions + profile[rank_end:]

# Insert Earn Coin chooser before the existing share sheet.
earn_dialog_anchor = """    // ================================================================
    // SHARE SHEET
    // ================================================================
"""
earn_dialog = """    // ================================================================
    // EARN BLINK COIN — private owner action
    // ================================================================
    if (showEarnCoinDialog && isMe) {
        AlertDialog(
            onDismissRequest = { showEarnCoinDialog = false },
            title = { Text("Earn Blink Coin", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showEarnCoinDialog = false
                                onWatchAdForCoins()
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                    ) {
                        Row(
                            modifier = Modifier.padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = BlinkPink)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Watch ad", fontWeight = FontWeight.Bold)
                                Text("Watch a rewarded ad to earn coins", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showEarnCoinDialog = false
                                onBuyBlinkCoins()
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                    ) {
                        Row(
                            modifier = Modifier.padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = BlinkGold)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Buy", fontWeight = FontWeight.Bold)
                                Text("Get Blink Coin with Google Play", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showEarnCoinDialog = false }) { Text("Close") }
            }
        )
    }

""" + earn_dialog_anchor
profile = replace_once(profile, earn_dialog_anchor, earn_dialog, "Earn coin dialog")
profile_path.write_text(profile)

print("Profile streak/Blink Coin patch applied")
