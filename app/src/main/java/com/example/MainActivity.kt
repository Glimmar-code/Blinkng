package com.example

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.BlinkTheme
import com.example.viewmodel.AppDestination
import com.example.viewmodel.BlinkViewModel
import com.example.viewmodel.MainTab
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private val viewModel: BlinkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }

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
                                            viewModel.setDestination(AppDestination.ONBOARDING)
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
                                    SignInScreen(
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
                                        onSuccess = { name, user, email, fac ->
                                            viewModel.signUp(name, user, email, fac)
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
                                            viewModel.completeProfileOnboarding(uni, level, bio, skills)
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
    }
}

@Composable
fun MainAppContent(
    uiState: com.example.viewmodel.BlinkUiState,
    viewModel: BlinkViewModel
) {
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
                uiState.isActivityOpen
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
                        userAvatar = uiState.myProfile.avatarUrl,
                        currentSubTab = uiState.feedSubTab,
                        onSubTabChanged = { viewModel.setFeedSubTab(it) },
                        isDark = uiState.isDarkMode,
                        onLikePost = { viewModel.togglePostLike(it) },
                        onCommentPost = { viewModel.openCommentsForPost(it) },
                        onBookmarkPost = { viewModel.toggleBookmark(it) },
                        onSharePost = { viewModel.sharePost(it) },
                        onOptionsClick = { viewModel.openPostOptions(it) },
                        onProfileClick = { viewModel.openProfile(it) },
                        onAddStoryClick = { viewModel.openCreatePost(true) },
                        onStoryClick = { story -> viewModel.showToast("Viewing story by @${story.username}") },
                        onOpenCreatePost = { viewModel.openCreatePost(true) },
                        onOpenActivity = { viewModel.openActivity(true) },
                        onOpenMenu = { viewModel.openMenu(true) },
                        onToggleTheme = { viewModel.toggleDarkMode() },
                        isServerConnected = uiState.isLiveSupabaseConnected,
                        onViewedPost = { viewModel.recordPostView(it) },
                        onVotePoll = { postId, optId -> viewModel.votePoll(postId, optId) },
                        onDirectMessage = { partner, partnerName, partnerAvatar ->
                            viewModel.openChatWithUser(partner, partnerName, partnerAvatar)
                        }
                    )
                }

                MainTab.SEARCH -> {
                    SearchScreen(
                        posts = uiState.posts,
                        onProfileClick = { viewModel.openProfile(it) },
                        onPostClick = { viewModel.openCommentsForPost(it.id) },
                        isDark = uiState.isDarkMode
                    )
                }

                MainTab.LEADERBOARD -> {
                    LeaderboardScreen(
                        users = uiState.leaderboardUsers,
                        onProfileClick = { viewModel.openProfile(it) },
                        isDark = uiState.isDarkMode
                    )
                }

                MainTab.MARKET -> {
                    MarketScreen(
                        items = uiState.marketItems,
                        isSellerActive = uiState.myProfile.isSellerActive,
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
                        isDark = uiState.isDarkMode
                    )
                }
            }
        }

        // Floating Bottom Nav (Visible on all main tabs & sub-tabs, hidden only on modal sheets/fullscreen details)
        val shouldShowBottomBar = uiState.viewingProduct == null &&
                uiState.viewingProfile == null &&
                !uiState.isPostItemOpen &&
                !uiState.isBecomeSellerOpen &&
                !uiState.isEditProfileOpen &&
                !uiState.isConversationFullScreen &&
                !uiState.isActivityOpen

        androidx.compose.animation.AnimatedVisibility(
            visible = shouldShowBottomBar,
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            FloatingBottomBar(
                currentTab = uiState.selectedTab,
                onTabSelected = { viewModel.setTab(it) },
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
                val isMyProfile = viewModel.isMe(profile.username) || viewModel.isMe(profile.fullName) || viewModel.isMe(profile.id) || profile.id == "user_me"
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
                isAuthor = post.author == uiState.myProfile.username || post.author == "efe.design" || post.author == "you",
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
                    onProfileClick = { 
                        viewModel.closeConversation()
                        viewModel.openProfile(it) 
                    },
                    isDark = uiState.isDarkMode
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
                isDark = uiState.isDarkMode
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

        // Modals: Create Post Sheet
        if (uiState.isCreatePostOpen) {
            CreatePostSheet(
                profile = uiState.myProfile,
                onDismiss = { viewModel.openCreatePost(false) },
                onSubmitPost = { text, faculty, imageUri, videoUri, tags, mentions, poll, isReel ->
                    viewModel.addPost(text, faculty, imageUri, videoUri, tags, mentions, poll, isReel)
                },
                isDark = uiState.isDarkMode
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
    }
}
