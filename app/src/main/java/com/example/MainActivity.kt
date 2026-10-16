package com.example

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.ui.components.*
import com.example.ads.BlinkAdConsentManager
import com.example.ads.BlinkAdsRuntime
import com.example.ads.BlinkRewardedAdManager
import com.example.auth.AccountSessionStore
import com.example.notification.BlinkNotificationHelper
import com.example.notification.BlinkFirebaseMessagingService
import com.example.notification.BlinkInAppNotification
import com.example.notification.BlinkInAppNotificationDestination
import com.example.sharing.DeepLinkRouter
import com.example.sharing.ShareContentType
import com.example.sharing.ShareLinkManager
import com.example.ui.screens.*
import com.example.ui.theme.BlinkTheme
import com.example.update.PlayInAppUpdateCoordinator
import com.example.util.startActivitySafely
import com.example.viewmodel.AppDestination
import com.example.viewmodel.BlinkViewModel
import com.example.viewmodel.MainTab
import com.blinkng.shared.BlinkStoreAvailability
import com.blinkng.shared.BlinkSwipeNavigation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: BlinkViewModel by viewModels()
    private lateinit var rewardedAdManager: BlinkRewardedAdManager
    private lateinit var adConsentManager: BlinkAdConsentManager
    private var presenceHeartbeatJob: Job? = null
    private var rewardedAdShowPending = false
    private var itemsOpenSignal by mutableIntStateOf(0)
    private var boostOpenSignal by mutableIntStateOf(0)
    private var showBugReportSheet by mutableStateOf(false)
    private var shakeSensorListener: android.hardware.SensorEventListener? = null
    private var lastShakeTriggerAt: Long = 0L
    private var lastShakeImpulseAt: Long = 0L
    private var shakeImpulses: Int = 0

    private fun startBugShakeListener() {
        if (shakeSensorListener != null) return
        val manager = getSystemService(android.content.Context.SENSOR_SERVICE) as? android.hardware.SensorManager ?: return
        val sensor = manager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER) ?: return
        val listener = object : android.hardware.SensorEventListener {
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                if (viewModel.uiState.value.destination != AppDestination.MAIN ||
                    !getSharedPreferences(com.example.ui.components.BlinkBugPrefs.FILE, MODE_PRIVATE)
                        .getBoolean(com.example.ui.components.BlinkBugPrefs.SHAKE_ENABLED, true) ||
                    showBugReportSheet) return
                val g = android.hardware.SensorManager.GRAVITY_EARTH
                val x = event.values[0] / g
                val y = event.values[1] / g
                val z = event.values[2] / g
                val strength = kotlin.math.sqrt(x * x + y * y + z * z)
                val now = android.os.SystemClock.elapsedRealtime()
                if (strength < 2.6f || now - lastShakeImpulseAt < 170L ||
                    now - lastShakeTriggerAt < 5000L) return
                shakeImpulses = if (now - lastShakeImpulseAt <= 1200L) shakeImpulses + 1 else 1
                lastShakeImpulseAt = now
                if (shakeImpulses >= 2) {
                    shakeImpulses = 0
                    lastShakeTriggerAt = now
                    showBugReportSheet = true
                }
            }
        }
        if (manager.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_UI)) {
            shakeSensorListener = listener
        }
    }

    private fun stopBugShakeListener() {
        val listener = shakeSensorListener ?: return
        (getSystemService(android.content.Context.SENSOR_SERVICE) as? android.hardware.SensorManager)
            ?.unregisterListener(listener)
        shakeSensorListener = null
    }

    private val inAppUpdateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { }

    private val playInAppUpdateCoordinator by lazy {
        PlayInAppUpdateCoordinator(this, inAppUpdateLauncher)
    }

    private fun showRewardedAdForCoins() {
        if (!BlinkAdsRuntime.canRequestAds.value) {
            // UMP may still be refreshing after a cold start or account change.
            // Re-check consent on a deliberate tap; never request an ad before UMP permits it.
            adConsentManager.gatherConsent {
                if (BlinkAdsRuntime.canRequestAds.value && !isFinishing && !isDestroyed) {
                    showRewardedAdForCoins()
                } else {
                    viewModel.rewardedAdUnavailable(
                        "Ads are unavailable until privacy choices are complete. Check Ad privacy in BLINK settings."
                    )
                }
            }
            return
        }
        if (rewardedAdShowPending) {
            viewModel.rewardedAdUnavailable("Your rewarded ad is already being prepared.")
            return
        }

        rewardedAdShowPending = true

        fun beginClaimAndShow() {
            lifecycleScope.launch {
                val claimId = viewModel.beginRewardedAdClaim()
                if (claimId == null) {
                    rewardedAdShowPending = false
                    return@launch
                }

                val userId = viewModel.uiState.value.myProfile.id
                rewardedAdManager.show(
                    userId = userId,
                    claimId = claimId,
                    onRewardEarned = { viewModel.completeRewardedAdClaim(claimId) },
                    onUnavailable = { message ->
                        rewardedAdShowPending = false
                        viewModel.rewardedAdUnavailable(message)
                    },
                    onClosed = {
                        rewardedAdShowPending = false
                    }
                )
            }
        }

        if (rewardedAdManager.isReady) {
            beginClaimAndShow()
        } else {
            viewModel.rewardedAdUnavailable("Preparing your rewarded ad…")
            rewardedAdManager.load(
                onReady = ::beginClaimAndShow,
                onUnavailable = { message ->
                    rewardedAdShowPending = false
                    viewModel.rewardedAdUnavailable(message)
                }
            )
        }
    }

    private fun showAdPrivacyOptions() {
        adConsentManager.showPrivacyOptions { error ->
            if (error != null) {
                viewModel.showToast("Unable to open ad privacy choices. Please try again.")
            }
            if (BlinkAdsRuntime.canRequestAds.value) {
                rewardedAdManager.load()
            }
        }
    }

    private fun startPresenceHeartbeat() {
        presenceHeartbeatJob?.cancel()
        if (viewModel.uiState.value.destination != AppDestination.MAIN) return
        presenceHeartbeatJob = lifecycleScope.launch {
            while (isActive && viewModel.uiState.value.destination == AppDestination.MAIN) {
                viewModel.updatePresence(true)
                delay(60_000)
            }
        }
    }

    private fun stopPresenceHeartbeat(markOffline: Boolean) {
        presenceHeartbeatJob?.cancel()
        presenceHeartbeatJob = null
        if (markOffline && viewModel.uiState.value.destination == AppDestination.MAIN) {
            viewModel.updatePresence(false)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        handleNotificationIntent(intent)
        val data = intent?.data
        if (data?.scheme == "blink" && data.host == "payment") {
            viewModel.verifyPendingPaystackCheckout()
            intent.data = null
            return
        }
        if (viewModel.handleAuthDeepLink(intent?.data)) {
            intent?.data = null
            return
        }
        DeepLinkRouter.parse(intent?.data)?.let { deepLink ->
            viewModel.handleDeepLink(deepLink)
            intent?.data = null
        }
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val action = intent?.getStringExtra(BlinkNotificationHelper.EXTRA_ACTION) ?: return

        when (action) {
            BlinkNotificationHelper.ACTION_OPEN_CHAT -> {
                val username = intent.getStringExtra(BlinkNotificationHelper.EXTRA_PARTNER_USERNAME).orEmpty()
                val name = intent.getStringExtra(BlinkNotificationHelper.EXTRA_PARTNER_NAME)
                val avatar = intent.getStringExtra(BlinkNotificationHelper.EXTRA_PARTNER_AVATAR)
                if (username.isNotBlank()) {
                    viewModel.setTab(MainTab.MESSAGES)
                    viewModel.openChatWithUser(username, name, avatar)
                }
            }

            BlinkNotificationHelper.ACTION_OPEN_POST,
            BlinkNotificationHelper.ACTION_OPEN_REEL -> {
                val postId = intent.getStringExtra(BlinkNotificationHelper.EXTRA_POST_ID)
                val isReel = action == BlinkNotificationHelper.ACTION_OPEN_REEL
                viewModel.setTab(MainTab.HOME)
                viewModel.setFeedSubTab(if (isReel) 1 else 0)
                if (!postId.isNullOrBlank()) {
                    viewModel.handleDeepLink(
                        com.example.sharing.AppDeepLink(
                            type = if (isReel) ShareContentType.REEL else ShareContentType.POST,
                            id = postId
                        )
                    )
                }
            }

            BlinkNotificationHelper.ACTION_OPEN_PROFILE -> {
                val identifier = intent
                    .getStringExtra(BlinkNotificationHelper.EXTRA_PROFILE_IDENTIFIER)
                    .orEmpty()
                if (identifier.isNotBlank()) {
                    viewModel.openProfile(identifier)
                } else {
                    viewModel.openActivity(true)
                }
            }

            BlinkNotificationHelper.ACTION_OPEN_MARKET -> {
                val marketId = intent.getStringExtra(BlinkNotificationHelper.EXTRA_MARKET_ID)
                viewModel.openMarketFromNotification(marketId)
            }

            BlinkNotificationHelper.ACTION_OPEN_SOCIAL -> {
                viewModel.openActivity(true)
            }

            BlinkNotificationHelper.ACTION_OPEN_ITEMS -> {
                viewModel.setTab(MainTab.HOME)
                viewModel.setFeedSubTab(0)
                itemsOpenSignal += 1
            }

            BlinkNotificationHelper.ACTION_OPEN_BOOST -> {
                viewModel.setTab(MainTab.HOME)
                viewModel.setFeedSubTab(0)
                boostOpenSignal += 1
            }
        }

        intent.removeExtra(BlinkNotificationHelper.EXTRA_ACTION)
    }

    private fun handleInAppNotification(event: BlinkInAppNotification) {
        event.activity?.let { activity ->
            viewModel.handleNotificationClick(activity)
            return
        }

        when (event.destination) {
            BlinkInAppNotificationDestination.CHAT -> {
                if (event.senderUsername.isNotBlank()) {
                    viewModel.setTab(MainTab.MESSAGES)
                    viewModel.openChatWithUser(
                        event.senderUsername,
                        event.senderName.takeIf { it.isNotBlank() },
                        event.senderAvatar.takeIf { it.isNotBlank() }
                    )
                } else {
                    viewModel.openActivity(true)
                }
            }

            BlinkInAppNotificationDestination.POST -> {
                val postId = event.postId ?: event.targetId
                if (!postId.isNullOrBlank()) {
                    val state = viewModel.uiState.value
                    val isReel = event.targetType.equals("reel", ignoreCase = true) ||
                        state.reels.any { it.id == postId }
                    viewModel.setTab(MainTab.HOME)
                    viewModel.setFeedSubTab(if (isReel) 1 else 0)
                    viewModel.handleDeepLink(
                        com.example.sharing.AppDeepLink(
                            type = if (isReel) ShareContentType.REEL else ShareContentType.POST,
                            id = postId
                        )
                    )
                } else {
                    viewModel.openActivity(true)
                }
            }

            BlinkInAppNotificationDestination.PROFILE -> {
                val identifier = event.senderUsername.ifBlank { event.senderId }
                if (identifier.isNotBlank()) viewModel.openProfile(identifier)
                else viewModel.openActivity(true)
            }

            BlinkInAppNotificationDestination.MARKET -> {
                viewModel.openMarketFromNotification(event.marketId ?: event.targetId)
            }

            BlinkInAppNotificationDestination.DROPS -> {
                startActivity(
                    Intent(this, BlinkDropsActivity::class.java).apply {
                        putExtra(BlinkDropsActivity.EXTRA_DROP_ID, event.targetId)
                    }
                )
            }

            BlinkInAppNotificationDestination.BOOST -> {
                viewModel.setTab(MainTab.HOME)
                viewModel.setFeedSubTab(0)
                boostOpenSignal += 1
            }

            BlinkInAppNotificationDestination.ITEMS -> {
                viewModel.setTab(MainTab.HOME)
                viewModel.setFeedSubTab(0)
                itemsOpenSignal += 1
            }

            BlinkInAppNotificationDestination.NOTIFICATIONS -> {
                viewModel.openActivity(true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        playInAppUpdateCoordinator.onResume()
        startBugShakeListener()
        if (viewModel.uiState.value.destination == AppDestination.MAIN) {
            viewModel.refreshIfStale()
            viewModel.verifyPendingPaystackCheckout()
            viewModel.retryPendingRewardedAdClaim()
            if (BlinkAdsRuntime.canRequestAds.value && !rewardedAdManager.isReady) {
                rewardedAdManager.load()
            }
            startPresenceHeartbeat()
        }
    }

    override fun onPause() {
        stopBugShakeListener()
        super.onPause()
    }

    override fun onDestroy() {
        if (::rewardedAdManager.isInitialized) rewardedAdManager.close()
        stopBugShakeListener()
        playInAppUpdateCoordinator.close()
        super.onDestroy()
    }

    override fun onStop() {
        // Save the exact navigation surface before Android backgrounds or kills the task.
        viewModel.persistResumePoint()
        stopPresenceHeartbeat(markOffline = true)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Removing BLINK from Android Recents can leave the process alive. A new
        // task must still start at the top of For You; configuration recreation
        // (savedInstanceState != null) keeps the current in-task position.
        if (savedInstanceState == null) FeedLaneProcessVisit.resetForNewTask()
        rewardedAdManager = BlinkRewardedAdManager(
            activity = this,
            adUnitId = BuildConfig.ADMOB_REWARDED_AD_UNIT_ID
        )
        adConsentManager = BlinkAdConsentManager(this)
        // Install our own Compose host. The Activity setContent convenience API
        // assumes android.R.id.content already exists in the decor hierarchy.
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        setContentView(composeView)
        runCatching { enableEdgeToEdge() }
            .onFailure { error ->
                android.util.Log.w("MainActivity", "Edge-to-edge setup unavailable.", error)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        composeView.setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val adPrivacyOptionsRequired by BlinkAdsRuntime.privacyOptionsRequired.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }
            val updateReadyToInstall by playInAppUpdateCoordinator.updateReadyToInstall.collectAsStateWithLifecycle()

            LaunchedEffect(uiState.destination) {
                if (uiState.destination == AppDestination.MAIN) {
                    startPresenceHeartbeat()
                } else {
                    stopPresenceHeartbeat(markOffline = false)
                }
            }
            // Persist the account that has actually reached the authenticated main app.
            // Keying by destination + user id prevents repeated writes during recomposition.
            LaunchedEffect(uiState.destination, uiState.myProfile.id) {
                if (uiState.destination == AppDestination.MAIN && uiState.myProfile.id.isNotBlank()) {
                    AccountSessionStore.recordCurrentSession(
                        context = this@MainActivity,
                        userId = uiState.myProfile.id,
                        username = uiState.myProfile.username,
                        fullName = uiState.myProfile.fullName,
                        email = uiState.myProfile.email.value,
                        avatarUrl = uiState.myProfile.avatarUrl
                    )
                    BlinkFirebaseMessagingService.syncCurrentToken(this@MainActivity)
                    viewModel.refreshProfileRewards()
                }
            }

            LaunchedEffect(updateReadyToInstall) {
                if (updateReadyToInstall) {
                    val result = snackbarHostState.showSnackbar(
                        message = "BLINK update downloaded",
                        actionLabel = "Restart",
                        withDismissAction = true,
                        duration = SnackbarDuration.Indefinite
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        playInAppUpdateCoordinator.completeUpdate()
                    }
                }
            }

            // Listen for snackbar events
            LaunchedEffect(Unit) {
                viewModel.snackBarMessages.collectLatest { msg ->
                    snackbarHostState.showSnackbar(msg)
                }
            }

            BlinkTheme(darkTheme = uiState.isDarkMode) {
                // Keep system icons readable when the selected app theme differs from Android's.
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !uiState.isDarkMode
                        isAppearanceLightNavigationBars = !uiState.isDarkMode
                    }
                }
                val homeManagesSystemInsets =
                    uiState.destination == AppDestination.MAIN &&
                    uiState.selectedTab == MainTab.HOME &&
                    uiState.feedSubTab in 0..2
                // Messages and Search already apply statusBarsPadding in their
                // own top chrome. Consuming a second status inset in Scaffold
                // creates the same black/empty band fixed on Home. Preserve
                // the bottom and side gesture/cutout insets for these routes.
                val routeManagesTopInset =
                    uiState.destination == AppDestination.MAIN &&
                    uiState.selectedTab in setOf(MainTab.MESSAGES, MainTab.SEARCH)
                Scaffold(
                    contentWindowInsets = when {
                        homeManagesSystemInsets -> WindowInsets(0, 0, 0, 0)
                        routeManagesTopInset -> WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                        )
                        else -> ScaffoldDefaults.contentWindowInsets
                    },
                    containerColor = if (homeManagesSystemInsets) {
                        MaterialTheme.colorScheme.background
                    } else {
                        MaterialTheme.colorScheme.background
                    },
                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.testTag("app_snackbar")
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AnimatedContent(
                            targetState = uiState.destination,
                            transitionSpec = {
                                if (initialState == AppDestination.SPLASH || targetState == AppDestination.SPLASH) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    fadeIn(animationSpec = tween(160)) togetherWith
                                            fadeOut(animationSpec = tween(120))
                                }
                            },
                            label = "AppNavigation"
                        ) { destination ->
                            when (destination) {
                                AppDestination.SPLASH -> {
                                    SplashScreen(
                                        onTimeout = { viewModel.completeSplash() }
                                    )
                                }

                                AppDestination.ONBOARDING -> {
                                    BlinkWelcomeScreen(
                                        onCreateAccount = { viewModel.setDestination(AppDestination.SIGN_UP) },
                                        onLogin = { viewModel.setDestination(AppDestination.SIGN_IN) },
                                        onGoogle = { email -> viewModel.loginWithGoogle(email) }
                                    )
                                }

                                AppDestination.SIGN_IN -> {
                                    val initialIdentifier = remember {
                                        AccountSessionStore.lastIdentifier(this@MainActivity).ifBlank {
                                            val recent = AccountSessionStore.list(this@MainActivity).firstOrNull()
                                            recent?.email?.takeIf { it.isNotBlank() } ?: recent?.username.orEmpty()
                                        }
                                    }
                                    SignInScreen(
                                        initialIdentifier = initialIdentifier,
                                        onBack = { viewModel.setDestination(AppDestination.ONBOARDING) },
                                        onSignInWithCredentials = { emailOrUser, password, onResult ->
                                            viewModel.signInWithCredentials(emailOrUser, password, onResult)
                                        },
                                        onGoogleSignIn = { email -> viewModel.loginWithGoogle(email) },
                                        onForgotPassword = { email, onResult ->
                                            viewModel.sendPasswordReset(email, onResult)
                                        },
                                        onSwitchToSignUp = { viewModel.setDestination(AppDestination.SIGN_UP) }
                                    )
                                }

                                AppDestination.SIGN_UP -> {
                                    CreateAccountScreen(
                                        onBack = { viewModel.setDestination(AppDestination.ONBOARDING) },
                                        onCreateAccount = { name, email, password ->
                                            viewModel.signUp(name, email, password)
                                        },
                                        onGoogle = { email -> viewModel.loginWithGoogle(email) },
                                        onLogin = { viewModel.setDestination(AppDestination.SIGN_IN) }
                                    )
                                }

                                AppDestination.RESET_PASSWORD -> {
                                    ResetPasswordScreen(
                                        onSubmit = { password, onResult ->
                                            viewModel.updateRecoveredPassword(password, onResult)
                                        },
                                        onCancel = { viewModel.cancelPasswordRecovery() }
                                    )
                                }

                                AppDestination.PROFILE_SETUP -> {
                                    AccountOnboardingScreen(
                                        profile = uiState.myProfile,
                                        candidates = (uiState.profiles + uiState.discoverProfiles)
                                            .distinctBy { it.id },
                                        onCheckUsername = { username, onResult ->
                                            viewModel.checkOnboardingUsername(username, onResult)
                                        },
                                        onSaveUsername = { username, onResult ->
                                            viewModel.saveOnboardingUsername(username, onResult)
                                        },
                                        onSaveBasics = { university, department, level, gender, birthDate, avatarUrl, onResult ->
                                            viewModel.saveOnboardingBasics(
                                                university = university,
                                                department = department,
                                                level = level,
                                                gender = gender,
                                                birthDate = birthDate,
                                                avatarUrl = avatarUrl,
                                                onResult = onResult
                                            )
                                        },
                                        onSaveInterests = { interests, onResult ->
                                            viewModel.saveOnboardingInterests(interests, onResult)
                                        },
                                        onFinish = { onResult ->
                                            viewModel.finishAccountOnboarding(onResult)
                                        },
                                        onRefreshSuggestions = {
                                            viewModel.refreshOnboardingSuggestions()
                                        }
                                    )
                                }

                                AppDestination.MAIN -> {
                                    MainAppContent(
                                        uiState = uiState,
                                        viewModel = viewModel,
                                        onWatchAdForCoins = ::showRewardedAdForCoins,
                                        itemsOpenSignal = itemsOpenSignal,
                                        boostOpenSignal = boostOpenSignal,
                                        isAdPrivacyOptionsRequired = adPrivacyOptionsRequired,
                                        onAdPrivacyOptions = ::showAdPrivacyOptions,
                                        onReportBug = { showBugReportSheet = true }
                                    )
                                }
                            }
                        }

                        if (uiState.destination == AppDestination.MAIN) {
                            BlinkInAppNotificationHost(
                                onOpen = ::handleInAppNotification,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .zIndex(100f)
                            )
                        }
                    }
                }
                if (showBugReportSheet && uiState.destination == AppDestination.MAIN) {
                    BlinkBugReportSheet(onDismiss = { showBugReportSheet = false })
                }
            }
        }

        adConsentManager.gatherConsent {
            if (BlinkAdsRuntime.canRequestAds.value) rewardedAdManager.load()
        }
        handleIncomingIntent(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// MESSAGING_RELIABILITY_AUDIT_V3
fun MainAppContent(
    uiState: com.example.viewmodel.BlinkUiState,
    viewModel: BlinkViewModel,
    onWatchAdForCoins: () -> Unit,
    itemsOpenSignal: Int,
    boostOpenSignal: Int,
    isAdPrivacyOptionsRequired: Boolean,
    onAdPrivacyOptions: () -> Unit,
    onReportBug: () -> Unit
) {
    // Auto-hide bottom bar on scroll down and reappear on scroll up
    var isBottomBarVisibleByScroll by rememberSaveable { mutableStateOf(true) }
    var homeReselectSignal by rememberSaveable { mutableIntStateOf(0) }
    var feedUtilitySheet by rememberSaveable { mutableStateOf<String?>(null) }
    var showStoreComingSoon by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(feedUtilitySheet) {
        // Restored states and old navigation callers cannot reopen the paused Store.
        if (BlinkStoreAvailability.isPaused && feedUtilitySheet == "store") {
            feedUtilitySheet = null
            showStoreComingSoon = true
        }
    }
    LaunchedEffect(itemsOpenSignal) {
        if (itemsOpenSignal > 0) feedUtilitySheet = "items"
    }
    LaunchedEffect(boostOpenSignal) {
        if (boostOpenSignal > 0) feedUtilitySheet = "boost"
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val connectHubActions = remember(viewModel) {
        ConnectHubActions(
            refresh = { viewModel.refreshConnectHub() },
            publishRoommate = { title, description, location, minBudget, maxBudget ->
                viewModel.publishRoommateProfile(title, description, location, minBudget, maxBudget)
            },
            applyRoommate = { viewModel.applyForRoommate(it) },
            publishMentor = { subjects, headline, description, mode ->
                viewModel.publishMentorProfile(subjects, headline, description, mode)
            },
            requestMentor = { viewModel.requestMentor(it) },
            publishReadingMate = { courses, style, times, location, description ->
                viewModel.publishReadingMateProfile(courses, style, times, location, description)
            },
            requestReadingMate = { viewModel.requestReadingMate(it) },
            applyHousingAgent = { businessName, serviceAreas, bio ->
                viewModel.applyAsHousingAgent(businessName, serviceAreas, bio)
            },
            publishHousingRequest = { title, location, minBudget, maxBudget, description ->
                viewModel.publishHousingRequest(title, location, minBudget, maxBudget, description)
            },
            applyToHousingRequest = { requestId, message ->
                viewModel.applyToHousingRequest(requestId, message)
            },
            challengeUser = { userId, gameType -> viewModel.challengeUser(userId, gameType) },
            respondChallenge = { challengeId, accept -> viewModel.respondToGameChallenge(challengeId, accept) },
            respondRequest = { kind, requestId, accept -> viewModel.respondToConnectRequest(kind, requestId, accept) },
            submitChallengeScore = { challengeId, score -> viewModel.submitChallengeScore(challengeId, score) },
            recordGameResult = { gameType, score -> viewModel.recordGameResult(gameType, score) }
        )
    }

    fun sharePostOrReel(postId: String) {
        val post = (
            uiState.posts +
                uiState.followingPosts +
                uiState.reels +
                uiState.discoverPosts +
                listOfNotNull(uiState.deepLinkedPost, uiState.activePostOptionsPost)
            ).firstOrNull { it.id == postId }
        if (post == null) {
            viewModel.showToast("This content is unavailable.")
            return
        }
        viewModel.sharePost(postId)
        ShareLinkManager.share(
            context = context,
            type = if (post.isReel) ShareContentType.REEL else ShareContentType.POST,
            id = post.id,
            title = if (post.isReel) "Share reel" else "Share post",
            message = post.text.take(180),
            previewImageUrl = post.images.firstOrNull() ?: post.authorAvatar.takeIf { it.isNotBlank() }
        )
    }

    LaunchedEffect(uiState.selectedTab, uiState.feedSubTab) {
        isBottomBarVisibleByScroll = true
    }

    // Handle back button presses for sub-views
    BackHandler(
        enabled = uiState.viewingProduct != null ||
                uiState.viewingProfile != null ||
                uiState.isPostItemOpen ||
                uiState.isBecomeSellerOpen ||
                uiState.isEditProfileOpen ||
                uiState.isMenuOpen ||
                uiState.activeConversationPartner != null ||
                uiState.activePostOptionsPost != null ||
                uiState.isActivityOpen ||
                uiState.isGetVerifiedOpen ||
                uiState.isCreatePostOpen ||
                uiState.isCreateStoryOpen ||
                uiState.activeViewingStory != null ||
                uiState.showSellerCongratulationsDialog ||
                uiState.deepLinkedPost != null ||
                (uiState.selectedTab == MainTab.HOME && uiState.feedSubTab == 1)
    ) {
        when {
            uiState.deepLinkedPost != null -> viewModel.closeDeepLinkedPost()
            uiState.activePostOptionsPost != null -> viewModel.openPostOptions(null)
            uiState.isMenuOpen -> viewModel.openMenu(false)
            uiState.isEditProfileOpen -> viewModel.openEditProfile(false)
            uiState.isBecomeSellerOpen -> viewModel.openBecomeSeller(false)
            uiState.isPostItemOpen -> viewModel.openPostItem(false)
            uiState.isActivityOpen -> viewModel.openActivity(false)
            uiState.activeConversationPartner != null -> viewModel.closeConversation()
            uiState.viewingProfile != null -> viewModel.closeProfile()
            uiState.viewingProduct != null -> viewModel.closeProductDetail()
            uiState.isGetVerifiedOpen -> viewModel.openGetVerified(false)
            uiState.isCreatePostOpen -> viewModel.openCreatePost(false)
            uiState.isCreateStoryOpen -> viewModel.openCreateStory(false)
            uiState.activeViewingStory != null -> viewModel.closeStory()
            uiState.showSellerCongratulationsDialog -> viewModel.dismissSellerCongratulations()
            uiState.selectedTab == MainTab.HOME && uiState.feedSubTab == 1 -> {
                isBottomBarVisibleByScroll = true
                viewModel.setFeedSubTab(0)
            }
        }
    }

    // Follow the canonical five-page sequence. Extra routes are direct-access only.
    val selectedPage = when (uiState.selectedTab) {
        MainTab.HOME -> when (uiState.feedSubTab) { 1 -> 1; 2 -> 3; 3 -> 7; else -> 0 }
        MainTab.MARKET -> 2
        MainTab.MESSAGES -> 4
        MainTab.SEARCH -> 5
        MainTab.LEADERBOARD -> 6
    }
    val pagerState = rememberPagerState(
        initialPage = selectedPage,
        pageCount = { if (selectedPage in 0..4) 5 else 8 }
    )
    var requestedPage by remember { mutableIntStateOf(selectedPage) }
    val latestSelectedPage by rememberUpdatedState(selectedPage)
    val reducePageMotion = com.example.performance.rememberBlinkReduceMotion()

    LaunchedEffect(selectedPage) {
        requestedPage = selectedPage
        if (pagerState.currentPage != selectedPage) {
            if (pagerState.currentPage in 0..4 && selectedPage in 0..4 &&
                kotlin.math.abs(pagerState.currentPage - selectedPage) == 1
            ) {
                pagerState.animateScrollToPage(selectedPage)
            } else {
                pagerState.scrollToPage(selectedPage)
            }
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collectLatest { page ->
            if (page == requestedPage || page !in 0..4 ||
                requestedPage != latestSelectedPage
            ) return@collectLatest
            requestedPage = page
            when (page) {
                0 -> { viewModel.setTab(MainTab.HOME); viewModel.setFeedSubTab(0) }
                1 -> { viewModel.setTab(MainTab.HOME); viewModel.setFeedSubTab(1) }
                2 -> viewModel.setTab(MainTab.MARKET)
                3 -> { viewModel.setTab(MainTab.HOME); viewModel.setFeedSubTab(2) }
                4 -> viewModel.setTab(MainTab.MESSAGES)
            }
        }
    }

    val mainPageSwipeEnabled = feedUtilitySheet == null &&
        uiState.viewingProduct == null && uiState.viewingProfile == null &&
        uiState.activeConversationPartner == null && uiState.activeCommentsPostId == null &&
        uiState.activePostOptionsPost == null && !uiState.isMenuOpen &&
        !uiState.isGetVerifiedOpen && !uiState.isBecomeSellerOpen &&
        !uiState.isConversationFullScreen && !uiState.showSellerCongratulationsDialog &&
        !uiState.isCreatePostOpen && !uiState.isCreateStoryOpen &&
        !uiState.isPostItemOpen && !uiState.isEditProfileOpen &&
        !uiState.isActivityOpen && uiState.deepLinkedPost == null &&
        uiState.activeViewingStory == null &&
        !(uiState.selectedTab == MainTab.HOME && uiState.feedSubTab == 3)

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = mainPageSwipeEnabled && pagerState.currentPage in 0..4
        ) { page ->
            val tab = when (page) {
                2 -> MainTab.MARKET
                4 -> MainTab.MESSAGES
                5 -> MainTab.SEARCH
                6 -> MainTab.LEADERBOARD
                else -> MainTab.HOME
            }
            val pageDistance = (pagerState.currentPage - page) +
                pagerState.currentPageOffsetFraction
            Box(modifier = Modifier.fillMaxSize().graphicsLayer {
                val scale = if (reducePageMotion) 1f else BlinkSwipeNavigation.scale(pageDistance)
                scaleX = scale
                scaleY = scale
                alpha = if (reducePageMotion) 1f else BlinkSwipeNavigation.alpha(pageDistance)
            }) {
            when (tab) {
                MainTab.HOME -> {
                    PremiumFeedScreen(
                        posts = uiState.posts,
                        followingPosts = uiState.followingPosts,
                        hiddenFeedPostIds = uiState.hiddenFeedPostIds,
                        mutedFeedAuthors = uiState.mutedUsers,
                        reels = uiState.reels,
                        stories = uiState.stories,
                        profiles = uiState.profiles,
                        leaderboardUsers = uiState.gameLeaderboardUsers,
                        connectHub = uiState.connectHub,
                        isConnectHubLoading = uiState.isConnectHubLoading,
                        connectHubActions = connectHubActions,
                        currentUsername = uiState.myProfile.username,
                        userAvatar = uiState.myProfile.avatarUrl,
                        currentSubTab = when (page) { 1 -> 1; 3 -> 2; 7 -> 3; else -> 0 },
                        routedReelId = uiState.routedReelId,
                        onSubTabChanged = { viewModel.setFeedSubTab(it) },
                        isDark = uiState.isDarkMode,
                        onLikePost = { viewModel.togglePostLike(it) },
                        onCommentPost = { viewModel.openCommentsForPost(it) },
                        onBookmarkPost = { viewModel.toggleBookmark(it) },
                        onRepostPost = { viewModel.toggleRepost(it) },
                        onSharePost = { sharePostOrReel(it) },
                        onOptionsClick = { viewModel.openPostOptions(it) },
                        onDeletePost = { viewModel.deletePost(it) },
                        onProfileClick = { viewModel.openProfile(it) },
                        onAddStoryClick = { viewModel.openCreateStory(true) },
                        onStoryClick = { story -> viewModel.openStory(story) },
                        onOpenCreatePost = { viewModel.openCreatePost(true) },
                        onOpenActivity = { viewModel.openActivity(true) },
                        onOpenMenu = { viewModel.openMenu(true) },
                        onToggleTheme = { viewModel.toggleDarkMode() },
                        isServerConnected = uiState.isLiveSupabaseConnected,
                        activityPulsePolicy = uiState.activityPulsePolicy,
                        onActivityPulseEvent = { surface, eventType, realCount, displayedValue, metadata ->
                            viewModel.recordActivityPulseEvent(
                                surface = surface,
                                eventType = eventType,
                                realCount = realCount,
                                displayedValue = displayedValue,
                                metadata = metadata,
                            )
                        },
                        isLoading = uiState.isFeedLoading,
                        isRefreshing = uiState.isRefreshingHome || uiState.isRefreshingContent,
                        isRefreshingFollowing = uiState.isRefreshingFollowing || uiState.isRefreshingContent,
                        postsRefreshRevision = uiState.postsRefreshRevision,
                        followingRefreshRevision = uiState.followingRefreshRevision,
                        isRefreshingReels = uiState.isRefreshingReels || uiState.isRefreshingContent,
                        errorMessage = uiState.feedErrorMessage,
                        followingErrorMessage = uiState.followingFeedErrorMessage,
                        onRefresh = { viewModel.refreshFeedLane() },
                        onRefreshFollowing = { viewModel.refreshFeedLane(following = true) },
                        onRefreshReels = { viewModel.refreshFeedLane(isReel = true) },
                        onRetry = { viewModel.refreshFeedLane() },
                        onViewedPost = { viewModel.recordPostView(it) },
                        onVotePoll = { postId, optId -> viewModel.votePoll(postId, optId) },
                        onDirectMessage = { partner, partnerName, partnerAvatar ->
                            viewModel.openChatWithUser(partner, partnerName, partnerAvatar)
                        },
                        onSearchClick = { viewModel.setTab(MainTab.SEARCH) },
                        onItemsClick = { feedUtilitySheet = "items" },
                        onLeaderboardClick = { feedUtilitySheet = "leaderboard" },
                        onStoreClick = {
                            if (BlinkStoreAvailability.isPaused) showStoreComingSoon = true
                            else feedUtilitySheet = "store"
                        },
                        onBoostClick = { feedUtilitySheet = "boost" },
                        onDropsClick = { feedUtilitySheet = "drops" },
                        onGameClick = { feedUtilitySheet = "game" },
                        onMarketClick = { viewModel.setTab(MainTab.MARKET) },
                        onMessageClick = { viewModel.setTab(MainTab.MESSAGES) },
                        hasUnreadNotifications = uiState.activities.any { it.isUnread },
                        unreadNotificationCount = uiState.activities.count { it.isUnread },
                        isInteractionOverlayOpen =
                            uiState.activeCommentsPostId != null || uiState.activePostOptionsPost != null,
                        hasMorePosts = uiState.hasMorePosts,
                        hasMoreFollowingPosts = uiState.hasMoreFollowingPosts,
                        hasMoreReels = uiState.hasMoreReels,
                        isLoadingMorePosts = uiState.isLoadingMorePosts,
                        isLoadingMoreFollowingPosts = uiState.isLoadingMoreFollowingPosts,
                        isLoadingMoreReels = uiState.isLoadingMoreReels,
                        onLoadMorePosts = { viewModel.loadMoreFeed(false) },
                        onLoadMoreFollowingPosts = { viewModel.loadMoreFollowingFeed() },
                        onLoadMoreReels = { viewModel.loadMoreFeed(true) },
                        homeReselectSignal = homeReselectSignal,
                        onBottomBarVisibilityChange = { isVisible ->
                            isBottomBarVisibleByScroll = isVisible
                        }
                    )
                }

                MainTab.SEARCH -> {
                    SearchScreen(
                        profiles = uiState.profiles,
                        posts = (uiState.posts + uiState.reels).distinctBy { it.id },
                        currentUsername = uiState.myProfile.username,
                        serverProfiles = uiState.discoverProfiles,
                        serverPosts = uiState.discoverPosts,
                        isSearching = uiState.isDiscoverSearching,
                        onSearchQueryChange = { viewModel.searchDiscover(it) },
                        onProfileClick = { viewModel.openProfile(it) },
                        onPostClick = { viewModel.openSearchPost(it) },
                        onLikePost = { viewModel.togglePostLike(it) },
                        onCommentPost = { viewModel.openCommentsForPost(it) },
                        onBookmarkPost = { viewModel.toggleBookmark(it) },
                        onVotePoll = { postId, optionId -> viewModel.votePoll(postId, optionId) },
                        onSharePost = { sharePostOrReel(it) },
                        onOptionsClick = { viewModel.openPostOptions(it) },
                        onDeletePost = { viewModel.deletePost(it) },
                        onBackToHome = {
                            viewModel.setTab(MainTab.HOME)
                            viewModel.setFeedSubTab(0)
                        },
                        isDark = uiState.isDarkMode
                    )
                }

                MainTab.LEADERBOARD -> {
                    LeaderboardScreen(
                        users = uiState.leaderboardUsers,
                        userProfile = uiState.myProfile,
                        onProfileClick = { viewModel.openProfile(it) },
                        isDark = uiState.isDarkMode,
                        isLoading = uiState.isFeedLoading && uiState.leaderboardUsers.isEmpty(),
                            isLiveDataAvailable = uiState.isOnline && uiState.isLiveSupabaseConnected,
                        activityPulsePolicy = uiState.activityPulsePolicy,
                        onActivityPulseEvent = { surface, eventType, realCount, displayedValue, metadata ->
                            viewModel.recordActivityPulseEvent(
                                surface = surface,
                                eventType = eventType,
                                realCount = realCount,
                                displayedValue = displayedValue,
                                metadata = metadata,
                            )
                        },
                        onRefresh = { viewModel.refreshLeaderboard() }
                    )
                }

                MainTab.MARKET -> {
                    MarketScreen(
                        items = uiState.marketItems,
                        isSellerActive = uiState.myProfile.isSellerActive,
                        sellerStoreName = uiState.myProfile.sellerStoreName,
                        verificationBadge = uiState.myProfile.verificationBadge,
                        onItemClick = { viewModel.openProductDetail(it) },
                        onOpenPostItem = { viewModel.openPostItem(true) },
                        onOpenBecomeSeller = { viewModel.openBecomeSeller(true) },
                        onOpenGetVerified = { viewModel.openGetVerified(true) },
                        onToggleSave = { viewModel.toggleMarketSaved(it) },
                        onSearch = { query, category, sort, minPrice, maxPrice, savedOnly, verifiedOnly ->
                            viewModel.searchMarketItems(query, category, sort, minPrice, maxPrice, savedOnly, verifiedOnly)
                        },
                        onRefresh = { viewModel.refreshMarketItems() },
                        onLoadMore = { viewModel.loadMoreMarketItems() },
                        isLoading = uiState.isMarketLoading,
                        isLoadingMore = uiState.isLoadingMoreMarket,
                        hasMore = uiState.marketHasMore,
                        errorMessage = uiState.marketErrorMessage,
                        isDark = uiState.isDarkMode
                    )
                }

                MainTab.MESSAGES -> {
                    PremiumMessagesScreen(
                        conversations = uiState.conversations,
                        stories = uiState.stories,
                        activities = uiState.activities,
                        chatPrivacySettings = uiState.chatPrivacySettings,
                        myAvatar = uiState.myProfile.avatarUrl,
                        myName = uiState.myProfile.fullName.ifBlank { uiState.myProfile.username },
                        activePartner = uiState.activeConversationPartner,
                        isConversationFullScreen = uiState.isConversationFullScreen,
                        onConversationFullScreenChange = { viewModel.setConversationFullScreen(it) },
                        onOpenConversation = { partner ->
                            viewModel.openChatWithUser(partner)
                        },
                        onCloseConversation = { viewModel.closeConversation() },
                        onSendMessage = { partner, text, replyTo ->
                            viewModel.sendMessage(partner, text, replyToMessageId = replyTo)
                        },
                        onForwardMessage = { target, message ->
                            viewModel.forwardChatMessage(target, message)
                        },
                        interactionActions = ChatInteractionActions(
                            onReact = { partner, message, emoji -> viewModel.toggleMessageReaction(partner, message, emoji) },
                            onEdit = { partner, message, content -> viewModel.editChatMessage(partner, message, content) },
                            onDeleteForMe = { partner, message -> viewModel.deleteChatMessageForMe(partner, message) },
                            onDeleteForEveryone = { partner, message -> viewModel.deleteChatMessageForEveryone(partner, message) },
                            onToggleStar = { partner, message -> viewModel.toggleChatMessageStar(partner, message) },
                            onTogglePin = { partner, message -> viewModel.toggleChatMessagePin(partner, message) },
                            onReportMessage = { message, reason -> viewModel.reportChatMessage(message, reason) },
                            onClearConversation = { conversation -> viewModel.clearConversationForMe(conversation) },
                            onMuteConversation = { conversation, muted -> viewModel.setConversationMuted(conversation, muted) },
                            onMuteConversationFor = { conversation, duration -> viewModel.muteConversationFor(conversation, duration) },
                            onNotificationSettings = { conversation, mode, muteUntil ->
                                viewModel.setChatNotificationSettings(conversation, mode, muteUntil)
                            },
                            onArchiveConversation = { conversation, archived -> viewModel.setConversationArchived(conversation, archived) },
                            onPinConversation = { conversation, pinned -> viewModel.setConversationPinned(conversation, pinned) },
                            onMarkConversationUnread = { conversation, unread -> viewModel.setConversationMarkedUnread(conversation, unread) },
                            onRespondMessageRequest = { conversation, accept -> viewModel.respondToMessageRequest(conversation, accept) },
                            onBlockConversation = { conversation -> viewModel.blockChatUser(conversation) },
                            onReportConversation = { conversation, reason -> viewModel.reportConversation(conversation, reason) }
                        ),
                        onSendAttachment = { partner, uri, kind ->
                            viewModel.sendAttachmentMessage(partner, uri, kind)
                        },
                        onPresenceChange = { partner, state ->
                            viewModel.updateChatPresence(partner, state)
                        },
                        onSendVideo = { partner, uri -> viewModel.sendVideoMessage(partner, uri) },
                        onRetryMessage = { partner, message ->
                            viewModel.retrySendMessage(partner, message)
                        },
                        hasMoreMessages = { conversationId ->
                            uiState.messageHistoryHasMore[conversationId] ?: true
                        },
                        isLoadingOlder = { conversationId ->
                            uiState.loadingOlderConversationId == conversationId
                        },
                        onLoadOlder = { partner -> viewModel.loadOlderMessages(partner) },
                        isLoadingMessages = { conversationId ->
                            uiState.loadingInitialConversationId == conversationId
                        },
                        onProfileClick = { viewModel.openProfileFromChat(it) },
                        onStoryClick = { story -> viewModel.openStory(story) },
                        onAddStoryClick = { viewModel.openCreateStory(true) },
                        onOpenActivity = { viewModel.openActivity(true) },
                        onUpdateChatPrivacy = { settings ->
                            viewModel.updateChatPrivacySettings(settings)
                        },
                        onComposeMessage = { viewModel.setTab(MainTab.SEARCH) },
                        isDark = uiState.isDarkMode,
                        isConnected = uiState.isOnline,
                        isLoading = uiState.isConversationsLoading
                    )
                }
            }
        }
        }

        feedUtilitySheet?.let { utility ->
            var intentionalLongSwipe by remember(utility) { mutableStateOf(false) }
            var gameRoundActive by remember(utility) { mutableStateOf(false) }
            var gameExitConfirmVisible by remember(utility) { mutableStateOf(false) }
            val utilitySheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
                confirmValueChange = { target ->
                    target != SheetValue.Hidden || (intentionalLongSwipe && !gameRoundActive)
                }
            )
            val utilityScope = rememberCoroutineScope()
            val utilityWindowHeightPx = with(LocalDensity.current) {
                LocalConfiguration.current.screenHeightDp.dp.toPx()
            }
            // Track the sheet's window position: local pointer coordinates alone
            // undercount movement while Material3 animates the sheet with the finger.
            var utilitySheetCoordinates by remember(utility) {
                mutableStateOf<LayoutCoordinates?>(null)
            }

            fun dismissUtility(afterDismiss: () -> Unit = {}) {
                if (utility == "game" && gameRoundActive) {
                    gameExitConfirmVisible = true
                    return
                }
                utilityScope.launch {
                    // Explicit navigation and close actions are never swipe-gated.
                    intentionalLongSwipe = true
                    runCatching { utilitySheetState.hide() }
                    feedUtilitySheet = null
                    afterDismiss()
                }
            }

            if (gameExitConfirmVisible) {
                AlertDialog(
                    onDismissRequest = { gameExitConfirmVisible = false },
                    title = { Text("Leave this game?") },
                    text = { Text("Your unfinished round will be lost.") },
                    confirmButton = {
                        TextButton(onClick = {
                            gameRoundActive = false
                            gameExitConfirmVisible = false
                            dismissUtility()
                        }) { Text("Leave game") }
                    },
                    dismissButton = {
                        TextButton(onClick = { gameExitConfirmVisible = false }) {
                            Text("Keep playing")
                        }
                    }
                )
            }

            ModalBottomSheet(
                onDismissRequest = {
                    if (utility == "game" && gameRoundActive) {
                        gameExitConfirmVisible = true
                    } else {
                        feedUtilitySheet = null
                    }
                },
                sheetState = utilitySheetState,
                dragHandle = {
                    Box(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        BottomSheetDefaults.DragHandle(modifier = Modifier.align(Alignment.Center))
                        IconButton(
                            onClick = { dismissUtility() },
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close ${utility} page")
                        }
                    }
                },
                // Listen without consuming motion. Inner LazyColumns and games still
                // scroll normally; a small pull can no longer dismiss the whole page.
                modifier = Modifier
                    .testTag("feedUtilitySheet")
                    .onGloballyPositioned { utilitySheetCoordinates = it }
                    .pointerInput(utility, utilityWindowHeightPx) {
                        awaitEachGesture {
                            intentionalLongSwipe = false
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startY = utilitySheetCoordinates
                                ?.takeIf { it.isAttached }
                                ?.localToWindow(down.position)?.y ?: down.position.y
                            var peakDownwardTravelPx = 0f
                            var wasReleased = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Final)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                peakDownwardTravelPx = maxOf(
                                    peakDownwardTravelPx,
                                    ((utilitySheetCoordinates
                                        ?.takeIf { it.isAttached }
                                        ?.localToWindow(change.position)?.y
                                        ?: change.position.y) - startY).coerceAtLeast(0f)
                                )
                                intentionalLongSwipe = UtilitySheetDismissPolicy.allowsDownwardDismiss(
                                    peakDownwardTravelPx,
                                    utilityWindowHeightPx
                                )
                                if (!change.pressed) {
                                    wasReleased = true
                                    break
                                }
                            }
                            // Inner scrollables may absorb long pulls so Material3 cannot
                            // complete the sheet gesture; explicitly close after release.
                            if (UtilitySheetDismissPolicy.allowsCompletedDownwardDismiss(
                                    peakDownwardTravelPx,
                                    utilityWindowHeightPx,
                                    wasReleased
                                )
                            ) {
                                dismissUtility()
                            }
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.96f)
                ) {
                    when (utility) {
                        "leaderboard" -> LeaderboardScreen(
                            users = uiState.leaderboardUsers,
                            userProfile = uiState.myProfile,
                            onProfileClick = { username ->
                                dismissUtility { viewModel.openProfile(username) }
                            },
                            isDark = uiState.isDarkMode,
                            isLoading = uiState.isFeedLoading && uiState.leaderboardUsers.isEmpty(),
                            isLiveDataAvailable = uiState.isOnline && uiState.isLiveSupabaseConnected,
                            activityPulsePolicy = uiState.activityPulsePolicy,
                            onActivityPulseEvent = { surface, eventType, realCount, displayedValue, metadata ->
                                viewModel.recordActivityPulseEvent(
                                    surface = surface,
                                    eventType = eventType,
                                    realCount = realCount,
                                    displayedValue = displayedValue,
                                    metadata = metadata,
                                )
                            },
                            onRefresh = { viewModel.refreshLeaderboard() }
                        )

                        "game" -> GameSection(
                            userAvatar = uiState.myProfile.avatarUrl,
                            onRoundActiveChange = { gameRoundActive = it },
                            leaderboardUsers = uiState.gameLeaderboardUsers,
                            connectHub = uiState.connectHub,
                            connectHubActions = connectHubActions,
                            isDark = uiState.isDarkMode,
                            onOpenMenu = { viewModel.openMenu(true) },
                            onOpenActivity = { viewModel.openActivity(true) },
                            onProfileClick = { username ->
                                dismissUtility { viewModel.openProfile(username) }
                            },
                            selectedTopTab = 3,
                            onHomeClick = {
                                dismissUtility {
                                    viewModel.setTab(MainTab.HOME)
                                    viewModel.setFeedSubTab(0)
                                }
                            },
                            onReelClick = {
                                dismissUtility {
                                    viewModel.setTab(MainTab.HOME)
                                    viewModel.setFeedSubTab(1)
                                }
                            },
                            onConnectClick = {
                                dismissUtility {
                                    viewModel.setTab(MainTab.HOME)
                                    viewModel.setFeedSubTab(2)
                                }
                            },
                            onGameClick = {},
                            modifier = Modifier.fillMaxSize()
                        )

                        "store" -> BlinkStoreRoute(onClose = { dismissUtility() })

                        "boost" -> BlinkBoostGrowthRoute(
                            posts = uiState.posts,
                            reels = uiState.reels,
                            marketItems = uiState.marketItems,
                            myProfile = uiState.myProfile,
                            isDark = uiState.isDarkMode,
                            onLikePost = { viewModel.togglePostLike(it) },
                            onCommentPost = { viewModel.openCommentsForPost(it) },
                            onBookmarkPost = { viewModel.toggleBookmark(it) },
                            onVotePoll = { postId, optionId -> viewModel.votePoll(postId, optionId) },
                            onOpenPost = { post -> dismissUtility { viewModel.openSearchPost(post) } },
                            onProfileClick = { username ->
                                dismissUtility { viewModel.openProfile(username) }
                            },
                            onListingClick = { listing ->
                                dismissUtility {
                                    viewModel.setTab(MainTab.MARKET)
                                    viewModel.openProductDetail(listing)
                                }
                            },
                            onGetCoins = {
                                if (BlinkStoreAvailability.isPaused) showStoreComingSoon = true
                                else feedUtilitySheet = "store"
                            },
                            onCreateContent = {
                                dismissUtility { viewModel.openCreatePost(true) }
                            },
                            onClose = { dismissUtility() },
                        )

                        "drops" -> BlinkDropsRoute(
                            onGetCoins = {
                                if (BlinkStoreAvailability.isPaused) showStoreComingSoon = true
                                else feedUtilitySheet = "store"
                            },
                            onClose = { dismissUtility() }
                        )

                        "items" -> BlinkItemsScreen(
                            profiles = uiState.profiles,
                            currentUserId = uiState.myProfile.id,
                            currentUsername = uiState.myProfile.username,
                            onClose = { dismissUtility() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        if (showStoreComingSoon && BlinkStoreAvailability.isPaused) {
            BlinkStoreComingSoonDialog(onDismiss = { showStoreComingSoon = false })
        }

        OfflineConnectionBanner(
            visible = !uiState.isOnline,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(30f)
        )

        // Floating bottom navigation follows the feed chrome state. Its transition is
        // deliberately non-bouncy so a restored bar glides in instead of springing back.
        val shouldShowBottomBar = feedUtilitySheet == null &&
                uiState.viewingProduct == null &&
                uiState.viewingProfile == null &&
                !uiState.isPostItemOpen &&
                !uiState.isBecomeSellerOpen &&
                !uiState.isEditProfileOpen &&
                uiState.activeConversationPartner == null &&
                !uiState.isConversationFullScreen &&
                !uiState.isActivityOpen &&
                !uiState.isGetVerifiedOpen &&
                !uiState.isCreatePostOpen &&
                !uiState.isCreateStoryOpen &&
                uiState.activeViewingStory == null &&
                !uiState.showSellerCongratulationsDialog &&
                uiState.activePostOptionsPost == null &&
                uiState.activeCommentsPostId == null &&
                uiState.deepLinkedPost == null &&
                !uiState.isMenuOpen

        androidx.compose.animation.AnimatedVisibility(
            visible = shouldShowBottomBar,
            enter = androidx.compose.animation.slideInVertically(
                initialOffsetY = { it },
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 200,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            ) + androidx.compose.animation.fadeIn(
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 170)
            ),
            exit = androidx.compose.animation.slideOutVertically(
                targetOffsetY = { it },
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 180,
                    easing = androidx.compose.animation.core.FastOutLinearInEasing
                )
            ) + androidx.compose.animation.fadeOut(
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 150)
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            FeedBottomBar(
                currentTab = uiState.selectedTab,
                feedSubTab = uiState.feedSubTab,
                onHomeClick = {
                    isBottomBarVisibleByScroll = true

                    // A Home reselect is only valid when the user is actually looking at
                    // the main Home feed. Profiles, comments, menus, chats, reels/game,
                    // and every other surface must navigate Home without refreshing it.
                    val isHomeFeedSurface =
                        uiState.selectedTab == MainTab.HOME &&
                            uiState.feedSubTab == 0 &&
                            uiState.viewingProduct == null &&
                            uiState.viewingProfile == null &&
                            !uiState.isPostItemOpen &&
                            !uiState.isBecomeSellerOpen &&
                            !uiState.isEditProfileOpen &&
                            !uiState.isMenuOpen &&
                            uiState.activeConversationPartner == null &&
                            !uiState.isConversationFullScreen &&
                            uiState.activePostOptionsPost == null &&
                            uiState.activeCommentsPostId == null &&
                            !uiState.isActivityOpen &&
                            !uiState.isGetVerifiedOpen &&
                            !uiState.isCreatePostOpen &&
                            !uiState.isCreateStoryOpen &&
                            uiState.activeViewingStory == null &&
                            !uiState.showSellerCongratulationsDialog &&
                            uiState.deepLinkedPost == null

                    if (isHomeFeedSurface) {
                        homeReselectSignal++
                    } else {
                        // Bottom-nav Home behaves as navigation, not as a hidden refresh.
                        // Close any transient surface that can sit above a main tab first.
                        if (uiState.deepLinkedPost != null) viewModel.closeDeepLinkedPost()
                        if (uiState.activePostOptionsPost != null) viewModel.openPostOptions(null)
                        if (uiState.activeCommentsPostId != null) viewModel.openCommentsForPost(null)
                        if (uiState.isMenuOpen) viewModel.openMenu(false)
                        if (uiState.isEditProfileOpen) viewModel.openEditProfile(false)
                        if (uiState.isBecomeSellerOpen) viewModel.openBecomeSeller(false)
                        if (uiState.isPostItemOpen) viewModel.openPostItem(false)
                        if (uiState.isActivityOpen) viewModel.openActivity(false)
                        if (uiState.activeConversationPartner != null) viewModel.closeConversation()
                        if (uiState.viewingProfile != null) viewModel.closeProfile()
                        if (uiState.viewingProduct != null) viewModel.closeProductDetail()
                        if (uiState.isGetVerifiedOpen) viewModel.openGetVerified(false)
                        if (uiState.isCreatePostOpen) viewModel.openCreatePost(false)
                        if (uiState.isCreateStoryOpen) viewModel.openCreateStory(false)
                        if (uiState.activeViewingStory != null) viewModel.closeStory()
                        if (uiState.showSellerCongratulationsDialog) viewModel.dismissSellerCongratulations()

                        viewModel.setTab(MainTab.HOME)
                        viewModel.setFeedSubTab(0)
                    }
                },
                onReelsClick = {
                    isBottomBarVisibleByScroll = true
                    viewModel.setTab(MainTab.HOME)
                    viewModel.setFeedSubTab(1)
                },
                onMarketClick = {
                    isBottomBarVisibleByScroll = true
                    viewModel.setTab(MainTab.MARKET)
                },
                onConnectClick = {
                    isBottomBarVisibleByScroll = true
                    viewModel.setTab(MainTab.HOME)
                    viewModel.setFeedSubTab(2)
                },
                onMessageClick = {
                    isBottomBarVisibleByScroll = true
                    viewModel.setTab(MainTab.MESSAGES)
                },
                isDark = uiState.isDarkMode,
                onMenuClick = {
                    isBottomBarVisibleByScroll = true
                    viewModel.openMenu(true)
                },
                isMenuOpen = uiState.isMenuOpen
            )
        }

        BackgroundPostPublishIndicator(
            isCreatingPost = uiState.isCreatingPost,
            messages = viewModel.snackBarMessages,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (shouldShowBottomBar) 92.dp else 18.dp)
                .zIndex(50f)
        )

        AnimatedVisibility(
            visible = uiState.deepLinkedPost != null,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            uiState.deepLinkedPost?.let { post ->
                SharedPostScreen(
                    post = post,
                    currentUsername = uiState.myProfile.username,
                    isDark = uiState.isDarkMode,
                    onBack = { viewModel.closeDeepLinkedPost() },
                    onLike = { viewModel.togglePostLike(post.id) },
                    onComment = { viewModel.openCommentsForPost(post.id) },
                    onBookmark = { viewModel.toggleBookmark(post.id) },
                    onShare = { sharePostOrReel(post.id) },
                    onOptions = { viewModel.openPostOptions(post) },
                    onDelete = { viewModel.deletePost(post.id) },
                    onProfileClick = { viewModel.openProfile(it) },
                    onVotePoll = { postId, optionId -> viewModel.votePoll(postId, optionId) }
                )
            }
        }

        // Sub-screen Overlays: Product Detail
        AnimatedVisibility(
            visible = uiState.viewingProduct != null,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            uiState.viewingProduct?.let { product ->
                ProductDetailScreen(
                    item = product,
                    onBack = { viewModel.closeProductDetail() },
                    onDirectMessage = { partner, sellerName, sellerAvatar ->
                        viewModel.openChatWithUser(partner, sellerName, sellerAvatar)
                    },
                    onSellerProfileClick = { viewModel.openProfile(it) },
                    onToggleSave = { viewModel.toggleMarketSaved(it) },
                    onRequestOrder = { listing, quantity ->
                        viewModel.requestMarketplaceOrder(listing, quantity)
                    },
                    onReport = { listing, reason, details ->
                        viewModel.reportMarketItem(listing, reason, details)
                    },
                    isOwner = product.sellerId.isNotBlank() &&
                        product.sellerId == uiState.myProfile.id,
                    onUpdateListingStatus = { listing, status ->
                        viewModel.updateMarketListingStatus(listing, status)
                    },
                    onDeleteListing = { viewModel.deleteMarketListing(it) },
                    isSubmittingOrder = uiState.isRequestingMarketOrder,
                    isDark = uiState.isDarkMode
                )
            }
        }

        // Sub-screen Overlays: User Profile with dynamic tabs (Posts, Liked, Saved, Skills, About) & messaging
        AnimatedVisibility(
            visible = uiState.viewingProfile != null,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 280,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            ) + fadeIn(androidx.compose.animation.core.tween(180)),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 230,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            ) + fadeOut(androidx.compose.animation.core.tween(160))
        ) {
            uiState.viewingProfile?.let { profile ->
                val isMyProfile = viewModel.isMe(profile.username)
                val currentProfileToDisplay = if (isMyProfile) uiState.myProfile else profile

                LaunchedEffect(currentProfileToDisplay.id, currentProfileToDisplay.username, isMyProfile) {
                    viewModel.loadProfileSurfaceData(currentProfileToDisplay, force = true)
                    if (!isMyProfile && currentProfileToDisplay.username.isNotBlank()) {
                        com.example.notification.ProfileViewActivityTracker.recordViewedProfile(
                            currentProfileToDisplay.username
                        )
                    }
                }

                val profileKey = currentProfileToDisplay.id.ifBlank {
                    currentProfileToDisplay.username.trim().removePrefix("@").lowercase()
                }
                val profilePosts = uiState.profilePostsByUserId[profileKey].orEmpty()
                val isProfilePostsLoading = profilePosts.isEmpty() &&
                    (profileKey in uiState.profileSurfaceLoadingIds ||
                     (profileKey !in uiState.profileSurfaceLoadedIds &&
                      profileKey !in uiState.profileSurfaceFailedIds))
                val profilePostsLoadFailed = profilePosts.isEmpty() &&
                    profileKey in uiState.profileSurfaceFailedIds
                val profileLikedPosts = if (isMyProfile) uiState.myLikedPosts.filter { it.isLiked } else emptyList()
                val profileSavedPosts = if (isMyProfile) uiState.mySavedPosts.filter { it.isBookmarked } else emptyList()

                val userMarketItems = if (isMyProfile) {
                    uiState.marketItems.filter {
                        viewModel.isMe(it.sellerUsername) || viewModel.isMe(it.sellerName)
                    }
                } else {
                    uiState.marketItems.filter { it.sellerUsername.equals(profile.username, ignoreCase = true) }
                }

                ProfileScreen(
                    profile = currentProfileToDisplay,
                    isMe = isMyProfile,
                    userPosts = profilePosts,
                    isPostsLoading = isProfilePostsLoading,
                    hasPostsLoadError = profilePostsLoadFailed,
                    likedPosts = profileLikedPosts,
                    savedPosts = profileSavedPosts,
                    userMarketItems = userMarketItems,
                    onBack = { viewModel.closeProfile() },
                    onEditProfileClick = { viewModel.openEditProfile(true) },
                    onDirectMessage = { partner ->
                        viewModel.closeProfile()
                        viewModel.setTab(MainTab.MESSAGES)
                        viewModel.openChatWithUser(partner, profile.fullName, profile.avatarUrl)
                    },
                    onEndorseSkill = { skill -> viewModel.endorseSkill(skill) },
                    onLikePost = { viewModel.togglePostLike(it) },
                    onCommentPost = { viewModel.openCommentsForPost(it) },
                    onBookmarkPost = { viewModel.toggleBookmark(it) },
                    onSharePost = { sharePostOrReel(it) },
                    onOptionsClick = { viewModel.openPostOptions(it) },
                    onDeletePost = { viewModel.deletePost(it) },
                    onProfileClick = { viewModel.openProfile(it) },
                    onMarketItemClick = { viewModel.openProductDetail(it) },
                    onOpenGetVerified = { viewModel.openGetVerified(true) },
                    blinkCoinBalance = if (isMyProfile) uiState.blinkCoinBalance else 0L,
                    economyPolicy = uiState.economyPolicy,
                    rewardedAdsToday = if (isMyProfile) uiState.rewardedAdsToday else 0,
                    onWatchAdForCoins = onWatchAdForCoins,
                    onBuyBlinkCoins = { viewModel.buyBlinkCoins() },
                    isDark = uiState.isDarkMode,
                    onRefreshProfile = {
                        viewModel.refreshProgressState()
                        viewModel.loadProfileSurfaceData(currentProfileToDisplay, force = true)
                    }
                )
            }
        }

        // Sub-screen Overlays: Post Item Screen
        AnimatedVisibility(
            visible = uiState.isPostItemOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            PostItemScreen(
                onBack = { viewModel.openPostItem(false) },
                onSubmit = { title, price, category, condition, description, images, quantity, negotiable, deliveryMethod, pickupLocation ->
                    viewModel.addMarketItem(
                        title = title,
                        price = price,
                        category = category,
                        condition = condition,
                        description = description,
                        imageSources = images,
                        quantity = quantity,
                        negotiable = negotiable,
                        deliveryMethod = deliveryMethod,
                        pickupLocation = pickupLocation
                    )
                },
                isPublishing = uiState.isPublishingMarketItem,
                isDark = uiState.isDarkMode
            )
        }

        // Sub-screen Overlays: Become Seller Screen
        AnimatedVisibility(
            visible = uiState.isBecomeSellerOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            BecomeSellerScreen(
                onBack = { viewModel.openBecomeSeller(false) },
                onSuccess = { storeName, phone, whatsapp, state, city ->
                    viewModel.activateSellerAccount(storeName, phone, whatsapp, state, city)
                },
                initialStoreName = uiState.myProfile.sellerStoreName,
                initialPhone = uiState.myProfile.phone.value,
                initialWhatsapp = uiState.myProfile.whatsapp.value,
                cashCheckoutEnabled = uiState.economyPolicy.cashCheckoutEnabled,
                isSubmitting = uiState.isSellerActivationLoading,
                isDark = uiState.isDarkMode
            )
        }

        // Sub-screen Overlays: Edit Profile Screen
        AnimatedVisibility(
            visible = uiState.isEditProfileOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            EditProfileScreen(
                profile = uiState.myProfile,
                onBack = { viewModel.openEditProfile(false) },
                onSave = { updated ->
                    viewModel.updateMyProfile(updated)
                },
                isDark = uiState.isDarkMode
            )
        }

        // Modals: Comments Modal Sheet with reply & like interactions
        if (uiState.activeCommentsPostId != null) {
            CommentSheet(
                comments = uiState.comments,
                isLoading = uiState.isCommentsLoading,
                isPosting = uiState.isPostingComment,
                currentUserId = uiState.myProfile.id,
                draftKey = uiState.activeCommentsPostId.orEmpty(),
                mentionCandidates = (listOf(uiState.myProfile) + uiState.profiles)
                    .filter { it.username.isNotBlank() }
                    .distinctBy { it.username.trim().removePrefix("@").lowercase() },
                isDark = uiState.isDarkMode,
                onDismiss = { viewModel.openCommentsForPost(null) },
                onSendComment = { text, parentCommentId ->
                    uiState.activeCommentsPostId?.let { postId ->
                        viewModel.addComment(postId, text, parentCommentId)
                    }
                },
                onToggleCommentLike = { commentId ->
                    viewModel.toggleCommentLike(commentId)
                },
                onReportComment = { commentId, reason ->
                    viewModel.reportComment(commentId, reason)
                },
                onProfileClick = { username ->
                    viewModel.openCommentsForPost(null)
                    viewModel.openProfile(username)
                }
            )
        }

        // Modals: Post Options Menu Sheet (Save, Share, Delete, Report, Mute)
        uiState.activePostOptionsPost?.let { post ->
            PostOptionsMenuSheet(
                post = post,
                isAuthor = post.author.equals(uiState.myProfile.username, ignoreCase = true),
                isDark = uiState.isDarkMode,
                onDismiss = { viewModel.openPostOptions(null) },
                onToggleSave = { viewModel.toggleBookmark(post.id) },
                onShare = { sharePostOrReel(post.id) },
                onDelete = { viewModel.deletePost(post.id) },
                onReport = { reason -> viewModel.reportPost(post.id, reason) },
                onMuteUser = { username -> viewModel.muteUser(username) },
                onNotInterested = { viewModel.markPostNotInterested(post.id) }
            )
        }

        // Sub-screen Overlays: Chat Conversation
        AnimatedVisibility(
            visible = uiState.isConversationFullScreen && uiState.activeConversationPartner != null && uiState.selectedTab != MainTab.MESSAGES,
            enter = fadeIn(tween(180)),
            exit = slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(390)) + fadeOut(tween(220))
        ) {
            val convo = uiState.conversations.find { it.partnerUsername == uiState.activeConversationPartner }
            if (convo != null) {
                com.example.ui.screens.ChatConversationView(
                    convo = convo,
                    onBack = { viewModel.closeConversation() },
                    onSendMessage = { text -> viewModel.sendMessage(convo.partnerUsername, text) },
                    onSendVideo = { uri -> viewModel.sendVideoMessage(convo.partnerUsername, uri) },
                    onProfileClick = { username ->
                        viewModel.openProfileFromChat(username)
                    },
                    isDark = uiState.isDarkMode,
                    isConnected = uiState.isLiveSupabaseConnected,
                    onRetryMessage = { msg ->
                        viewModel.retrySendMessage(convo.partnerUsername, msg)
                    },
                    hasMoreMessages = uiState.messageHistoryHasMore[convo.id] ?: true,
                    isLoadingOlder = uiState.loadingOlderConversationId == convo.id,
                    onLoadOlder = { viewModel.loadOlderMessages(convo.partnerUsername) }
                )
            }
        }

        // Sub-screen Overlays: Notification Center (Activity)
        AnimatedVisibility(
            visible = uiState.isActivityOpen,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            ActivityScreen(
                activities = uiState.activities,
                onBack = { viewModel.openActivity(false) },
                onProfileClick = { username ->
                    viewModel.openActivity(false)
                    viewModel.openProfile(username)
                },
                onNotificationClick = { activity ->
                    viewModel.handleNotificationClick(activity)
                    activity.targetPostId?.let { postId ->
                        val type = if (uiState.reels.any { it.id == postId }) {
                            ShareContentType.REEL
                        } else {
                            ShareContentType.POST
                        }
                        viewModel.handleDeepLink(
                            com.example.sharing.AppDeepLink(type = type, id = postId)
                        )
                    }
                },
                isDark = uiState.isDarkMode,
                isConnected = uiState.isLiveSupabaseConnected,
                isLoading = uiState.activitiesLoading,
                errorMessage = uiState.activitiesError,
                onRefresh = { viewModel.fetchSupabaseData() },
                onMarkAllRead = { viewModel.markAllActivitiesRead() },
                onToggleRead = { activity -> viewModel.toggleActivityReadState(activity) }
            )
        }

        // Modals: Get Verified Sheet (Blue ₦800, Gold 1k followers + ₦2,000)
        if (uiState.isGetVerifiedOpen) {
            GetVerifiedSheet(
                profile = uiState.myProfile,
                isDark = uiState.isDarkMode,
                blinkCoinBalance = uiState.blinkCoinBalance,
                economyPolicy = uiState.economyPolicy,
                rewardedAdsToday = uiState.rewardedAdsToday,
                dailyMissions = uiState.dailyMissions,
                isDailyMissionsLoading = uiState.isDailyMissionsLoading,
                onDismiss = { viewModel.openGetVerified(false) },
                onWatchAdForCoins = onWatchAdForCoins,
                onBuyBlinkCoins = { viewModel.buyBlinkCoins() },
                onRefreshDailyMissions = { viewModel.refreshProfileRewards() },
                onClaimDailyMission = { viewModel.claimDailyMission(it) },
                onVerifyWithCoins = { viewModel.verifyBlueWithCoins() },
                onUpgrade = { tier ->
                    if (tier == com.example.data.models.VerificationBadge.BLUE) {
                        viewModel.startPaystackVerificationCheckout()
                    } else {
                        viewModel.applyVerification(tier)
                    }
                }
            )
        }

        if (uiState.isCoinPackPickerOpen) {
            AlertDialog(
                onDismissRequest = { viewModel.closeCoinPackPicker() },
                title = { Text("Choose a Blink Coin pack") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Paystack will open a secure hosted checkout. Coins are delivered only after server verification.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        uiState.economyPolicy.coinPacks.forEach { pack ->
                            OutlinedButton(
                                onClick = { viewModel.startPaystackCoinCheckout(pack.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text("₦${pack.priceNgn} → ${pack.coins} coins")
                                    if (pack.bonusCoins > 0) {
                                        Text(
                                            "+${pack.bonusCoins} bonus coins",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { viewModel.closeCoinPackPicker() }) { Text("Cancel") }
                }
            )
        }

        AnimatedVisibility(
            visible = uiState.isCreateStoryOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            CreateStoryScreen(
                profile = uiState.myProfile,
                isUploading = uiState.isCreatingStory,
                onBack = { viewModel.openCreateStory(false) },
                onPublish = { uri, caption, isVideo ->
                    viewModel.publishStory(uri, caption, isVideo)
                }
            )
        }

        // Modals: Create Post Sheet
        if (uiState.isCreatePostOpen) {
            CreatePostSheet(
                profile = uiState.myProfile,
                savedDrafts = uiState.savedDrafts,
                scheduledPosts = uiState.scheduledPosts,
                onDismiss = { viewModel.openCreatePost(false) },
                onSubmitPost = { text, faculty, imageUri, videoUri, tags, mentions, poll, isReel, audience, category, location, linkUrl, allowComments, hideLikes, isPinned, isDisappearing, audioTitle, altText, textStyle, clientRequestId ->
                    // Keep the composer alive until Supabase confirms the save. The ViewModel
                    // closes it only after success, so a failed publish keeps the user's draft.
                    viewModel.addPost(
                        text = text,
                        faculty = faculty,
                        imageUri = imageUri,
                        videoUri = videoUri,
                        tags = tags,
                        mentions = mentions,
                        poll = poll,
                        isReel = isReel,
                        audience = audience,
                        category = category,
                        location = location,
                        linkUrl = linkUrl,
                        allowComments = allowComments,
                        hideLikes = hideLikes,
                        isPinned = isPinned,
                        isDisappearing = isDisappearing,
                        audioTitle = audioTitle,
                        altText = altText,
                        textStyle = textStyle,
                        clientRequestId = clientRequestId
                    )
                },
                onSaveDraft = { draft ->
                    viewModel.saveDraft(draft)
                },
                onDeleteDraft = { draftId ->
                    viewModel.deleteDraft(draftId)
                },
                onSchedulePost = { post, timeMillis, timeFormatted ->
                    viewModel.schedulePost(post, timeMillis, timeFormatted)
                },
                onPublishScheduledPostNow = { scheduleId ->
                    viewModel.publishScheduledPostNow(scheduleId)
                },
                onCancelScheduledPost = { scheduleId ->
                    viewModel.deleteScheduledPost(scheduleId)
                },
                isDark = uiState.isDarkMode,
                isSubmitting = uiState.isCreatingPost
            )
        }

        // Modals: 3-Dot App Menu Sheet
        if (uiState.isMenuOpen) {
            val context = androidx.compose.ui.platform.LocalContext.current
            AppMenuSheet(
                profile = uiState.myProfile,
                isDark = uiState.isDarkMode,
                onDismiss = { viewModel.openMenu(false) },
                onViewProfile = { viewModel.openProfile("you") },
                onEditProfile = { viewModel.openEditProfile(true) },
                onOpenMarket = { viewModel.setTab(MainTab.MARKET) },
                onOpenPostItem = { viewModel.openPostItem(true) },
                onOpenBecomeSeller = { viewModel.openBecomeSeller(true) },
                onOpenLeaderboard = { viewModel.setTab(MainTab.LEADERBOARD) },
                onOpenActivity = { viewModel.openActivity(true) },
                onToggleTheme = { viewModel.toggleDarkMode() },
                onLogout = { viewModel.logout() },
                onShowToast = { viewModel.showToast(it) },
                showAdPrivacyOptions = isAdPrivacyOptionsRequired,
                onAdPrivacyOptions = onAdPrivacyOptions,
                onSimulateNotification = { viewModel.simulateBackgroundNotification(context) },
                onReportBug = onReportBug
            )
        }

        // Modals: Interactive Fullscreen Story Viewer
        uiState.activeViewingStory?.let { activeStory ->
            StoryViewerDialog(
                stories = uiState.stories,
                initialStory = activeStory,
                currentUserId = "you",
                onDismiss = { viewModel.closeStory() },
                onStoryViewed = { storyId -> viewModel.markStoryViewed(storyId) },
                onLikeStory = { storyId -> viewModel.toggleStoryLike(storyId) },
                onReactStory = { storyId, emoji -> viewModel.reactToStory(storyId, emoji) },
                onReplyStory = { username, text -> viewModel.replyToStory(username, text) },
                onProfileClick = { username -> viewModel.openProfile(username) }
            )
        }

        // Modals: Seller Congratulations Dialog
        if (uiState.showSellerCongratulationsDialog) {
            SellerCongratulationsDialog(
                storeName = uiState.myProfile.sellerStoreName,
                onDismiss = { viewModel.dismissSellerCongratulations() },
                onCreatePost = {
                    viewModel.dismissSellerCongratulations()
                    viewModel.openPostItem(true)
                },
                isDark = uiState.isDarkMode
            )
        }
    }
}
