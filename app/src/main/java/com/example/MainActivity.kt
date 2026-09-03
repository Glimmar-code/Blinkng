package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.*
import com.example.auth.AccountSessionStore
import com.example.notification.BlinkNotificationHelper
import com.example.ui.screens.*
import com.example.ui.theme.BlinkTheme
import com.example.viewmodel.AppDestination
import com.example.viewmodel.BlinkViewModel
import com.example.viewmodel.MainTab
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private val viewModel: BlinkViewModel by viewModels()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val action = intent?.getStringExtra(BlinkNotificationHelper.EXTRA_ACTION) ?: return

        when (action) {
            BlinkNotificationHelper.ACTION_OPEN_CHAT -> {
                val username = intent.getStringExtra(BlinkNotificationHelper.EXTRA_PARTNER_USERNAME).orEmpty()
                val name = intent.getStringExtra(BlinkNotificationHelper.EXTRA_PARTNER_NAME)
                if (username.isNotBlank()) {
                    viewModel.setTab(MainTab.MESSAGES)
                    viewModel.openChatWithUser(username, name, null)
                }
            }

            BlinkNotificationHelper.ACTION_OPEN_POST -> {
                val postId = intent.getStringExtra(BlinkNotificationHelper.EXTRA_POST_ID)
                viewModel.setTab(MainTab.HOME)
                viewModel.setFeedSubTab(0)
                if (!postId.isNullOrBlank()) {
                    viewModel.openCommentsForPost(postId)
                }
            }

            BlinkNotificationHelper.ACTION_OPEN_MARKET -> {
                viewModel.setTab(MainTab.MARKET)
            }

            BlinkNotificationHelper.ACTION_OPEN_SOCIAL -> {
                viewModel.openActivity(true)
            }
        }

        intent.removeExtra(BlinkNotificationHelper.EXTRA_ACTION)
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.uiState.value.destination == AppDestination.MAIN) {
            viewModel.fetchSupabaseData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }

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
                }
            }

            // Listen for snackbar events
            LaunchedEffect(Unit) {
                viewModel.snackBarMessages.collectLatest { msg ->
                    snackbarHostState.showSnackbar(msg)
                }
            }

            BlinkTheme(darkTheme = uiState.isDarkMode) {
                Scaffold(
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
                                fadeIn(animationSpec = tween(300)) togetherWith
                                        fadeOut(animationSpec = tween(300))
                            },
                            label = "AppNavigation"
                        ) { destination ->
                            when (destination) {
                                AppDestination.SPLASH -> {
                                    SplashScreen(
                                        onTimeout = {
                                            if (com.example.data.supabase.SupabaseService.accessToken().isNullOrBlank() &&
                                                com.example.data.supabase.SupabaseService.refreshToken().isNullOrBlank()) {
                                                viewModel.setDestination(AppDestination.ONBOARDING)
                                            }
                                        }
                                    )
                                }

                                AppDestination.ONBOARDING -> {
                                    OnboardingScreen(
                                        onSignInClick = { viewModel.setDestination(AppDestination.SIGN_IN) },
                                        onSignUpClick = { viewModel.setDestination(AppDestination.SIGN_UP) },
                                        onGoogleSignIn = { email -> viewModel.loginWithGoogle(email) }
                                    )
                                }

                                AppDestination.SIGN_IN -> {
                                    val recent = remember { AccountSessionStore.list(this@MainActivity).firstOrNull() }
                                    SignInScreen(
                                        initialIdentifier = recent?.email?.takeIf { it.isNotBlank() } ?: recent?.username.orEmpty(),
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
                                    SignUpScreen(
                                        onBack = { viewModel.setDestination(AppDestination.ONBOARDING) },
                                        onSuccess = { name, user, email, pass, fac ->
                                            viewModel.signUp(name, user, email, pass, fac)
                                        },
                                        onGoogleSignUp = { email -> viewModel.loginWithGoogle(email) },
                                        onSwitchToSignIn = { viewModel.setDestination(AppDestination.SIGN_IN) }
                                    )
                                }

                                AppDestination.PROFILE_SETUP -> {
                                    ProfileSetupOnboardingScreen(
                                        studentName = uiState.myProfile.fullName,
                                        studentUsername = uiState.myProfile.username,
                                        onComplete = { uni, dept, level, bio, skills ->
                                            viewModel.completeProfileOnboarding(uni, dept, level, bio, skills)
                                        }
                                    )
                                }

                                AppDestination.MAIN -> {
                                    MainAppContent(
                                        uiState = uiState,
                                        viewModel = viewModel
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        handleNotificationIntent(intent)
    }
}

@Composable
fun MainAppContent(
    uiState: com.example.viewmodel.BlinkUiState,
    viewModel: BlinkViewModel
) {
    // Auto-hide bottom bar on scroll down and reappear on scroll up
    var isBottomBarVisibleByScroll by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(uiState.selectedTab) {
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
                uiState.showSellerCongratulationsDialog
    ) {
        when {
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
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main Tab Content
        androidx.compose.animation.AnimatedContent(
            targetState = uiState.selectedTab,
            label = "TabAnimatedContent",
            transitionSpec = {
                androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { 50 }) togetherWith 
                androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { -50 })
            }
        ) { tab ->
            when (tab) {
                MainTab.HOME -> {
                    FeedScreen(
                        posts = uiState.posts,
                        reels = uiState.reels,
                        stories = uiState.stories,
                        profiles = uiState.profiles,
                        leaderboardUsers = uiState.leaderboardUsers,
                        connectHub = uiState.connectHub,
                        isConnectHubLoading = uiState.isConnectHubLoading,
                        connectHubActions = ConnectHubActions(
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
                            challengeUser = { userId, gameType ->
                                viewModel.challengeUser(userId, gameType)
                            },
                            respondChallenge = { challengeId, accept ->
                                viewModel.respondToGameChallenge(challengeId, accept)
                            },
                            respondRequest = { kind, requestId, accept ->
                                viewModel.respondToConnectRequest(kind, requestId, accept)
                            },
                            submitChallengeScore = { challengeId, score ->
                                viewModel.submitChallengeScore(challengeId, score)
                            },
                            recordGameResult = { gameType, score ->
                                viewModel.recordGameResult(gameType, score)
                            },
                            claimDailySpin = { viewModel.claimDailyGameSpin() }
                        ),
                        currentUsername = uiState.myProfile.username,
                        userAvatar = uiState.myProfile.avatarUrl,
                        currentSubTab = uiState.feedSubTab,
                        onSubTabChanged = { viewModel.setFeedSubTab(it) },
                        isDark = uiState.isDarkMode,
                        onLikePost = { viewModel.togglePostLike(it) },
                        onCommentPost = { viewModel.openCommentsForPost(it) },
                        onBookmarkPost = { viewModel.toggleBookmark(it) },
                        onSharePost = { viewModel.sharePost(it) },
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
                        isLoading = uiState.isFeedLoading,
                        isRefreshing = uiState.isRefreshingContent,
                        errorMessage = uiState.feedErrorMessage,
                        onRefresh = { viewModel.refreshContent() },
                        onRetry = { viewModel.refreshContent() },
                        onViewedPost = { viewModel.recordPostView(it) },
                        onVotePoll = { postId, optId -> viewModel.votePoll(postId, optId) },
                        onDirectMessage = { partner, partnerName, partnerAvatar ->
                            viewModel.openChatWithUser(partner, partnerName, partnerAvatar)
                        },
                        hasMorePosts = uiState.hasMorePosts,
                        hasMoreReels = uiState.hasMoreReels,
                        isLoadingMorePosts = uiState.isLoadingMorePosts,
                        isLoadingMoreReels = uiState.isLoadingMoreReels,
                        onLoadMorePosts = { viewModel.loadMoreFeed(false) },
                        onLoadMoreReels = { viewModel.loadMoreFeed(true) },
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
                        onPostClick = { viewModel.openCommentsForPost(it.id) },
                        onLikePost = { viewModel.togglePostLike(it) },
                        onCommentPost = { viewModel.openCommentsForPost(it) },
                        onBookmarkPost = { viewModel.toggleBookmark(it) },
                        onSharePost = { viewModel.sharePost(it) },
                        onOptionsClick = { viewModel.openPostOptions(it) },
                        onDeletePost = { viewModel.deletePost(it) },
                        isDark = uiState.isDarkMode
                    )
                }

                MainTab.LEADERBOARD -> {
                    LeaderboardScreen(
                        users = uiState.leaderboardUsers,
                        userProfile = uiState.myProfile,
                        onProfileClick = { viewModel.openProfile(it) },
                        isDark = uiState.isDarkMode,
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
                        isDark = uiState.isDarkMode
                    )
                }

                MainTab.MESSAGES -> {
                    MessagesScreen(
                        conversations = uiState.conversations,
                        activePartner = uiState.activeConversationPartner,
                        onOpenConversation = { partner ->
                            viewModel.openChatWithUser(partner)
                        },
                        onCloseConversation = { viewModel.closeConversation() },
                        onSendMessage = { partner, text -> viewModel.sendMessage(partner, text) },
                        onProfileClick = { viewModel.openProfile(it) },
                        isDark = uiState.isDarkMode,
                        isConnected = uiState.isOnline
                    )
                }
            }
        }

        OfflineConnectionBanner(
            visible = !uiState.isOnline,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(30f)
        )

        // Floating Bottom Nav (Visible on all main tabs, auto-hides on feed scroll down, re-appears on scroll up)
        val shouldShowBottomBar = uiState.viewingProduct == null &&
                uiState.viewingProfile == null &&
                !uiState.isPostItemOpen &&
                !uiState.isBecomeSellerOpen &&
                !uiState.isEditProfileOpen &&
                !uiState.isConversationFullScreen &&
                !uiState.isActivityOpen &&
                !uiState.isGetVerifiedOpen &&
                !uiState.isCreatePostOpen &&
                !uiState.isCreateStoryOpen &&
                uiState.activeViewingStory == null &&
                !uiState.showSellerCongratulationsDialog &&
                uiState.activePostOptionsPost == null &&
                uiState.activeCommentsPostId == null &&
                !uiState.isMenuOpen &&
                isBottomBarVisibleByScroll

        androidx.compose.animation.AnimatedVisibility(
            visible = shouldShowBottomBar,
            enter = androidx.compose.animation.slideInVertically(
                initialOffsetY = { it },
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                )
            ) + androidx.compose.animation.fadeIn(
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 220)
            ),
            exit = androidx.compose.animation.slideOutVertically(
                targetOffsetY = { it },
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 240,
                    easing = androidx.compose.animation.core.FastOutLinearInEasing
                )
            ) + androidx.compose.animation.fadeOut(
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 180)
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            FloatingBottomBar(
                currentTab = uiState.selectedTab,
                onTabSelected = {
                    isBottomBarVisibleByScroll = true
                    viewModel.setTab(it)
                },
                isDark = uiState.isDarkMode
            )
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
                    isDark = uiState.isDarkMode
                )
            }
        }

        // Sub-screen Overlays: User Profile with dynamic tabs (Posts, Liked, Saved, Skills, About) & messaging
        AnimatedVisibility(
            visible = uiState.viewingProfile != null,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            uiState.viewingProfile?.let { profile ->
                val isMyProfile = viewModel.isMe(profile.username)
                val currentProfileToDisplay = if (isMyProfile) uiState.myProfile else profile

                val profilePosts = if (isMyProfile) {
                    (uiState.posts + uiState.reels).distinctBy { it.id }.filter { viewModel.isMe(it.author) }
                } else {
                    (uiState.posts + uiState.reels).distinctBy { it.id }.filter { it.author.equals(profile.username, ignoreCase = true) || it.author.equals(profile.fullName, ignoreCase = true) }
                }
                val profileLikedPosts = (uiState.posts + uiState.reels).filter { it.isLiked }
                val profileSavedPosts = (uiState.posts + uiState.reels).filter { it.isBookmarked }

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
                    likedPosts = profileLikedPosts,
                    savedPosts = profileSavedPosts,
                    userMarketItems = userMarketItems,
                    onBack = { viewModel.closeProfile() },
                    onEditProfileClick = { viewModel.openEditProfile(true) },
                    onDirectMessage = { partner -> viewModel.openChatWithUser(partner) },
                    onEndorseSkill = { skill -> viewModel.endorseSkill(skill) },
                    onLikePost = { viewModel.togglePostLike(it) },
                    onCommentPost = { viewModel.openCommentsForPost(it) },
                    onBookmarkPost = { viewModel.toggleBookmark(it) },
                    onSharePost = { viewModel.sharePost(it) },
                    onOptionsClick = { viewModel.openPostOptions(it) },
                    onDeletePost = { viewModel.deletePost(it) },
                    onProfileClick = { viewModel.openProfile(it) },
                    onMarketItemClick = { viewModel.openProductDetail(it) },
                    onOpenGetVerified = { viewModel.openGetVerified(true) },
                    isDark = uiState.isDarkMode
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
                onSubmit = { title, price, category, condition, description, imageUrl ->
                    viewModel.addMarketItem(title, price, category, condition, description, imageUrl)
                },
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
                isDark = uiState.isDarkMode,
                onDismiss = { viewModel.openCommentsForPost(null) },
                onSendComment = { text, replyToUser ->
                    uiState.activeCommentsPostId?.let { postId ->
                        viewModel.addComment(postId, text, replyToUser)
                    }
                },
                onToggleCommentLike = { commentId ->
                    viewModel.toggleCommentLike(commentId)
                },
                onProfileClick = { username ->
                    viewModel.openCommentsForPost(null)
                    viewModel.openProfile(username)
                }
            )
        }

        // Modals: Post Options Menu Sheet (Save, Share, Delete, Report, Mute)
        if (uiState.activePostOptionsPost != null) {
            val post = uiState.activePostOptionsPost!!
            PostOptionsMenuSheet(
                post = post,
                isAuthor = post.author.equals(uiState.myProfile.username, ignoreCase = true),
                isDark = uiState.isDarkMode,
                onDismiss = { viewModel.openPostOptions(null) },
                onToggleSave = { viewModel.toggleBookmark(post.id) },
                onShare = { viewModel.sharePost(post.id) },
                onDelete = { viewModel.deletePost(post.id) },
                onReport = { reason -> viewModel.reportPost(post.id, reason) },
                onMuteUser = { username -> viewModel.muteUser(username) }
            )
        }

        // Sub-screen Overlays: Chat Conversation
        AnimatedVisibility(
            visible = uiState.isConversationFullScreen && uiState.activeConversationPartner != null,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
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
                },
                isDark = uiState.isDarkMode,
                isConnected = uiState.isLiveSupabaseConnected,
                isLoading = uiState.activitiesLoading,
                errorMessage = uiState.activitiesError,
                onRefresh = { viewModel.fetchSupabaseData() },
                onMarkAllRead = { viewModel.markAllActivitiesRead() }
            )
        }

        // Modals: Get Verified Sheet (Blue ₦800, Gold 1k followers + ₦2,000)
        if (uiState.isGetVerifiedOpen) {
            GetVerifiedSheet(
                profile = uiState.myProfile,
                isDark = uiState.isDarkMode,
                onDismiss = { viewModel.openGetVerified(false) },
                onUpgrade = { tier ->
                    viewModel.applyVerification(tier)
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
                onSubmitPost = { text, faculty, imageUri, videoUri, tags, mentions, poll, isReel, audience, category, location, linkUrl, allowComments, hideLikes, isPinned, isDisappearing, audioTitle, altText ->
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
                        altText = altText
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
                onSimulateNotification = { viewModel.simulateBackgroundNotification(context) }
            )
        }

        // Modals: Interactive Fullscreen Story Viewer
        if (uiState.activeViewingStory != null) {
            StoryViewerDialog(
                stories = uiState.stories,
                initialStory = uiState.activeViewingStory!!,
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
