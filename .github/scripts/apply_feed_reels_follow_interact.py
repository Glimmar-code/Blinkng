from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one source block, found {count}")
    return text.replace(old, new, 1)


# -----------------------------------------------------------------------------
# Feed card: render saved textStyle and add Follow -> Interact beside author.
# -----------------------------------------------------------------------------
post_path = Path("app/src/main/java/com/example/ui/components/PostCard.kt")
post = post_path.read_text(encoding="utf-8")

post = replace_once(
    post,
    '''    hasActiveStory: Boolean = false,
    modifier: Modifier = Modifier
) {''',
    '''    hasActiveStory: Boolean = false,
    authorProfileId: String = "",
    isFollowingAuthor: Boolean = false,
    onFollowAuthor: (String) -> Unit = {},
    onUnfollowAuthor: (String) -> Unit = {},
    onMessageAuthor: () -> Unit = {},
    onGiftCoinsAuthor: (String) -> Unit = {},
    onChallengeAuthor: (String) -> Unit = {},
    onMentorRequestAuthor: (String) -> Unit = {},
    onFriendRequestAuthor: (String) -> Unit = {},
    onRoommateRequestAuthor: (String) -> Unit = {},
    onStudyMateRequestAuthor: (String) -> Unit = {},
    onConnectHubAuthor: () -> Unit = {},
    modifier: Modifier = Modifier
) {''',
    "PostCard interaction parameters",
)

follow_anchor = '''                        if (resolvedAuthorUsername.isNotBlank()) {
'''
follow_block = '''                        if (!isAuthor && authorProfileId.isNotBlank()) {
                            Spacer(Modifier.width(6.dp))
                            ProfileFollowInteractButton(
                                isFollowing = isFollowingAuthor,
                                onFollow = { onFollowAuthor(authorProfileId) },
                                onUnfollow = { onUnfollowAuthor(authorProfileId) },
                                onMessage = onMessageAuthor,
                                onGiftCoins = { onGiftCoinsAuthor(authorProfileId) },
                                onGameChallenge = { onChallengeAuthor(authorProfileId) },
                                onMentorRequest = { onMentorRequestAuthor(authorProfileId) },
                                onFriendRequest = { onFriendRequestAuthor(authorProfileId) },
                                onRoommateRequest = { onRoommateRequestAuthor(authorProfileId) },
                                onStudyMateRequest = { onStudyMateRequestAuthor(authorProfileId) },
                                onViewProfile = { onProfileClick(profileTarget) },
                                onOpenConnectHub = onConnectHubAuthor
                            )
                        }
                        if (resolvedAuthorUsername.isNotBlank()) {
'''
post = replace_once(post, follow_anchor, follow_block, "feed follow/interact control")

text_start = post.index('            if (post.text.isNotBlank()) {')
text_end = post.index('            post.poll?.let { poll ->', text_start)
new_text = '''            if (post.text.isNotBlank()) {
                val hasSavedTextStyle = post.textStyle?.isNotBlank() == true &&
                    displayImages.isEmpty() && post.poll == null
                if (hasSavedTextStyle) {
                    val textStyle = resolveTextPostStyle(post.textStyle, post.id)
                    val textSize = when {
                        post.text.length > 320 -> 22.sp
                        post.text.length > 170 -> 26.sp
                        else -> 31.sp
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 280.dp)
                            .background(textStyle.brush())
                            .padding(horizontal = 26.dp, vertical = 34.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SelectionContainer {
                            Text(
                                text = post.text,
                                color = textStyle.textColor,
                                fontSize = textSize,
                                lineHeight = textSize * 1.18f,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = if (expandedText) Int.MAX_VALUE else 14,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    SelectionContainer {
                        Text(
                            text = post.text,
                            color = primaryText,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                            maxLines = if (expandedText) Int.MAX_VALUE else 7,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (post.text.length > 320) {
                    Text(
                        text = if (expandedText) "Show less" else "See more",
                        color = socialBlue,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(start = 14.dp, top = 6.dp, bottom = 8.dp)
                            .clickable { expandedText = !expandedText }
                    )
                }
            }

'''
post = post[:text_start] + new_text + post[text_end:]
post_path.write_text(post, encoding="utf-8")


# -----------------------------------------------------------------------------
# Feed screen: one shared follow state + real interaction callbacks.
# -----------------------------------------------------------------------------
feed_path = Path("app/src/main/java/com/example/ui/screens/FeedScreen.kt")
feed = feed_path.read_text(encoding="utf-8")

feed = replace_once(
    feed,
    'import com.example.data.models.ConnectHubSnapshot\n',
    'import com.example.data.models.ChallengeGameType\nimport com.example.data.models.ConnectHubSnapshot\n',
    "ChallengeGameType import",
)
feed = replace_once(
    feed,
    'import com.example.data.models.VerificationBadge\n',
    'import com.example.data.models.VerificationBadge\nimport com.example.data.repository.FollowStateStore\nimport com.example.data.repository.UserInteractionRepository\n',
    "interaction repository imports",
)
if 'import androidx.compose.runtime.collectAsState\n' not in feed:
    feed = replace_once(
        feed,
        'import androidx.compose.runtime.Composable\n',
        'import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.collectAsState\n',
        "collectAsState import",
    )
if 'import androidx.compose.runtime.rememberCoroutineScope\n' not in feed:
    feed = replace_once(
        feed,
        'import androidx.compose.runtime.remember\n',
        'import androidx.compose.runtime.remember\nimport androidx.compose.runtime.rememberCoroutineScope\n',
        "rememberCoroutineScope import",
    )
if 'import kotlinx.coroutines.launch\n' not in feed:
    feed = replace_once(
        feed,
        'import kotlinx.coroutines.delay\n',
        'import kotlinx.coroutines.delay\nimport kotlinx.coroutines.launch\n',
        "launch import",
    )

feed = replace_once(
    feed,
    '''    val refreshFeed by rememberUpdatedState(onRefresh)
    val postIds = remember(posts) { posts.mapTo(linkedSetOf()) { it.id } }
''',
    '''    val refreshFeed by rememberUpdatedState(onRefresh)
    val interactionRepository = remember { UserInteractionRepository() }
    val interactionScope = rememberCoroutineScope()
    val followingIds by FollowStateStore.followingIds.collectAsState()
    LaunchedEffect(currentUsername) {
        FollowStateStore.refresh()
    }
    val postIds = remember(posts) { posts.mapTo(linkedSetOf()) { it.id } }
''',
    "feed shared interaction state",
)

feed = replace_once(
    feed,
    '''                reels = reels,
                currentUsername = currentUsername,
                isDark = isDark,
''',
    '''                reels = reels,
                currentUsername = currentUsername,
                profiles = profiles,
                connectHub = connectHub,
                connectHubActions = connectHubActions,
                onDirectMessage = onDirectMessage,
                isDark = isDark,
''',
    "pass interaction data into Reels",
)

feed = replace_once(
    feed,
    '''                                val hasActiveStory = resolvedUsername.lowercase() in activeStoryUsernames

                                Column {
''',
    '''                                val hasActiveStory = resolvedUsername.lowercase() in activeStoryUsernames
                                val authorId = authorProfile?.id.orEmpty()
                                val mentorListingId = connectHub.mentors.firstOrNull { it.userId == authorId }?.id
                                val roommateListingId = connectHub.roommates.firstOrNull { it.userId == authorId }?.id
                                val readingListingId = connectHub.readingMates.firstOrNull { it.userId == authorId }?.id

                                Column {
''',
    "resolve feed author interaction targets",
)

feed = replace_once(
    feed,
    '''                                        authorVerificationBadge = resolvedBadge,
                                        hasActiveStory = hasActiveStory
                                    )
''',
    '''                                        authorVerificationBadge = resolvedBadge,
                                        hasActiveStory = hasActiveStory,
                                        authorProfileId = authorId,
                                        isFollowingAuthor = authorId.isNotBlank() && authorId in followingIds,
                                        onFollowAuthor = { id ->
                                            interactionScope.launch { FollowStateStore.setFollowing(id, true) }
                                        },
                                        onUnfollowAuthor = { id ->
                                            interactionScope.launch { FollowStateStore.setFollowing(id, false) }
                                        },
                                        onMessageAuthor = {
                                            onDirectMessage(
                                                resolvedUsername,
                                                resolvedName,
                                                authorProfile?.avatarUrl?.takeIf { it.isNotBlank() } ?: post.authorAvatar
                                            )
                                        },
                                        onGiftCoinsAuthor = { id ->
                                            interactionScope.launch { interactionRepository.giftCoins(id, 10) }
                                        },
                                        onChallengeAuthor = { id ->
                                            connectHubActions.challengeUser(id, ChallengeGameType.GENERAL_KNOWLEDGE.apiName)
                                        },
                                        onMentorRequestAuthor = {
                                            if (mentorListingId != null) connectHubActions.requestMentor(mentorListingId)
                                            else navigate(2)
                                        },
                                        onFriendRequestAuthor = { id ->
                                            interactionScope.launch { interactionRepository.sendFriendRequest(id) }
                                        },
                                        onRoommateRequestAuthor = {
                                            if (roommateListingId != null) connectHubActions.applyRoommate(roommateListingId)
                                            else navigate(2)
                                        },
                                        onStudyMateRequestAuthor = {
                                            if (readingListingId != null) connectHubActions.requestReadingMate(readingListingId)
                                            else navigate(2)
                                        },
                                        onConnectHubAuthor = { navigate(2) }
                                    )
''',
    "wire feed author interactions",
)
feed_path.write_text(feed, encoding="utf-8")


# -----------------------------------------------------------------------------
# Reels: move avatar beside name and reuse the same Follow -> Interact menu.
# -----------------------------------------------------------------------------
reels_path = Path("app/src/main/java/com/example/ui/screens/VideoReelsScreen.kt")
reels = reels_path.read_text(encoding="utf-8")

reels = replace_once(
    reels,
    'import com.example.data.models.FeedPost\n',
    'import com.example.data.models.ChallengeGameType\nimport com.example.data.models.ConnectHubSnapshot\nimport com.example.data.models.FeedPost\nimport com.example.data.models.UserProfile\nimport com.example.data.repository.FollowStateStore\nimport com.example.data.repository.UserInteractionRepository\n',
    "Reels interaction imports",
)
reels = replace_once(
    reels,
    'import com.example.ui.components.PremiumPullRefreshIndicator\n',
    'import com.example.ui.components.PremiumPullRefreshIndicator\nimport com.example.ui.components.ProfileFollowInteractButton\n',
    "Reels interaction component import",
)

reels = replace_once(
    reels,
    '''    onProfileClick: (String) -> Unit,
    onBackToPosts: () -> Unit,
    isLoading: Boolean = false,
''',
    '''    onProfileClick: (String) -> Unit,
    onBackToPosts: () -> Unit,
    profiles: List<UserProfile> = emptyList(),
    connectHub: ConnectHubSnapshot = ConnectHubSnapshot(),
    connectHubActions: ConnectHubActions = ConnectHubActions(),
    onDirectMessage: (partner: String, partnerName: String?, partnerAvatar: String?) -> Unit = { _, _, _ -> },
    isLoading: Boolean = false,
''',
    "VideoReelsScreen interaction parameters",
)

reels = replace_once(
    reels,
    '''                    onProfileClick = onProfileClick,
                    onBackToPosts = onBackToPosts,
                    hasMore = hasMore,
''',
    '''                    onProfileClick = onProfileClick,
                    onBackToPosts = onBackToPosts,
                    profiles = profiles,
                    connectHub = connectHub,
                    connectHubActions = connectHubActions,
                    onDirectMessage = onDirectMessage,
                    onOpenConnectHub = onConnectClick,
                    hasMore = hasMore,
''',
    "ReelsContent arguments",
)

reels = replace_once(
    reels,
    '''    onProfileClick: (String) -> Unit,
    onBackToPosts: () -> Unit,
    hasMore: Boolean,
''',
    '''    onProfileClick: (String) -> Unit,
    onBackToPosts: () -> Unit,
    profiles: List<UserProfile>,
    connectHub: ConnectHubSnapshot,
    connectHubActions: ConnectHubActions,
    onDirectMessage: (partner: String, partnerName: String?, partnerAvatar: String?) -> Unit,
    onOpenConnectHub: () -> Unit,
    hasMore: Boolean,
''',
    "ReelsContent interaction parameters",
)

reels = replace_once(
    reels,
    '''    var selectedTab by remember { mutableStateOf("For You") }

    LaunchedEffect(pager, reels, resumeUserKey) {
''',
    '''    var selectedTab by remember { mutableStateOf("For You") }

    LaunchedEffect(currentUsername) {
        FollowStateStore.refresh()
    }

    LaunchedEffect(pager, reels, resumeUserKey) {
''',
    "refresh Reels follow state",
)

reels = replace_once(
    reels,
    '''                isAuthor = reel.author.equals(currentUsername, ignoreCase = true),
                onLike = onLike,
''',
    '''                isAuthor = reel.author.equals(currentUsername.removePrefix("@"), ignoreCase = true) ||
                    reel.authorUsername.removePrefix("@").equals(currentUsername.removePrefix("@"), ignoreCase = true),
                onLike = onLike,
''',
    "Reels own-author detection",
)

reels = replace_once(
    reels,
    '''                onDelete = onDelete,
                onProfileClick = onProfileClick,
                onSwipeToHome = onBackToPosts,
''',
    '''                onDelete = onDelete,
                onProfileClick = onProfileClick,
                profiles = profiles,
                connectHub = connectHub,
                connectHubActions = connectHubActions,
                onDirectMessage = onDirectMessage,
                onOpenConnectHub = onOpenConnectHub,
                onSwipeToHome = onBackToPosts,
''',
    "ReelPage interaction arguments",
)

reels = replace_once(
    reels,
    '''    onDelete: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    onSwipeToHome: () -> Unit,
''',
    '''    onDelete: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    profiles: List<UserProfile>,
    connectHub: ConnectHubSnapshot,
    connectHubActions: ConnectHubActions,
    onDirectMessage: (partner: String, partnerName: String?, partnerAvatar: String?) -> Unit,
    onOpenConnectHub: () -> Unit,
    onSwipeToHome: () -> Unit,
''',
    "ReelPage interaction parameters",
)

reels = replace_once(
    reels,
    '''    val haptic = LocalHapticFeedback.current
    val displayedViewsCount = rememberDelayedContentViewCount(reel.id, reel.viewsCount)

    var burstTrigger by remember(reel.id) { mutableStateOf(0) }
''',
    '''    val haptic = LocalHapticFeedback.current
    val displayedViewsCount = rememberDelayedContentViewCount(reel.id, reel.viewsCount)
    val reelUsername = reel.authorUsername.trim().removePrefix("@").ifBlank {
        reel.author.trim().removePrefix("@")
    }
    val authorProfile = remember(reel.id, profiles) {
        profiles.firstOrNull { it.username.trim().removePrefix("@").equals(reelUsername, true) }
            ?: profiles.firstOrNull { it.fullName.trim().equals(reel.author.trim(), true) }
    }
    val authorId = authorProfile?.id.orEmpty()
    val authorName = authorProfile?.fullName?.takeIf { it.isNotBlank() } ?: reel.author
    val authorAvatar = authorProfile?.avatarUrl?.takeIf { it.isNotBlank() } ?: reel.authorAvatar
    val followingIds by FollowStateStore.followingIds.collectAsState()
    val interactionRepository = remember { UserInteractionRepository() }
    val interactionScope = rememberCoroutineScope()
    val mentorListingId = connectHub.mentors.firstOrNull { it.userId == authorId }?.id
    val roommateListingId = connectHub.roommates.firstOrNull { it.userId == authorId }?.id
    val readingListingId = connectHub.readingMates.firstOrNull { it.userId == authorId }?.id

    var burstTrigger by remember(reel.id) { mutableStateOf(0) }
''',
    "resolve Reel author interactions",
)

old_rail_avatar = '''            AsyncImage(
                model = reel.authorAvatar,
                error = painterResource(R.drawable.ic_default_profile),
                fallback = painterResource(R.drawable.ic_default_profile),
                contentDescription = reel.author,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .clickable { onProfileClick(reel.author) }
            )
            Spacer(Modifier.height(16.dp))
'''
reels = replace_once(reels, old_rail_avatar, '', "remove upper Reel avatar")

old_author_text = '''            Text(
                "@${reel.author}",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                modifier = Modifier.clickable { onProfileClick(reel.author) }
            )
'''
new_author_row = '''            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AsyncImage(
                    model = authorAvatar,
                    error = painterResource(R.drawable.ic_default_profile),
                    fallback = painterResource(R.drawable.ic_default_profile),
                    contentDescription = "$authorName profile picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable { onProfileClick(reelUsername) }
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "@$reelUsername",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier.clickable { onProfileClick(reelUsername) }
                )
                if (!isAuthor && authorId.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    ProfileFollowInteractButton(
                        isFollowing = authorId in followingIds,
                        onFollow = {
                            interactionScope.launch { FollowStateStore.setFollowing(authorId, true) }
                        },
                        onUnfollow = {
                            interactionScope.launch { FollowStateStore.setFollowing(authorId, false) }
                        },
                        onMessage = { onDirectMessage(reelUsername, authorName, authorAvatar) },
                        onGiftCoins = {
                            interactionScope.launch { interactionRepository.giftCoins(authorId, 10) }
                        },
                        onGameChallenge = {
                            connectHubActions.challengeUser(authorId, ChallengeGameType.GENERAL_KNOWLEDGE.apiName)
                        },
                        onMentorRequest = {
                            if (mentorListingId != null) connectHubActions.requestMentor(mentorListingId)
                            else onOpenConnectHub()
                        },
                        onFriendRequest = {
                            interactionScope.launch { interactionRepository.sendFriendRequest(authorId) }
                        },
                        onRoommateRequest = {
                            if (roommateListingId != null) connectHubActions.applyRoommate(roommateListingId)
                            else onOpenConnectHub()
                        },
                        onStudyMateRequest = {
                            if (readingListingId != null) connectHubActions.requestReadingMate(readingListingId)
                            else onOpenConnectHub()
                        },
                        onViewProfile = { onProfileClick(reelUsername) },
                        onOpenConnectHub = onOpenConnectHub,
                        darkSurface = true
                    )
                }
            }
'''
reels = replace_once(reels, old_author_text, new_author_row, "Reel avatar-name-follow row")
reels_path.write_text(reels, encoding="utf-8")

print("Applied styled text + shared feed/Reels follow-interact UI")
