package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.models.DiscoveryResult
import com.example.data.models.DiscoveryResultType
import com.example.data.models.FeedPost
import com.example.data.models.MarketItem
import com.example.data.repository.FollowStateStore
import kotlinx.coroutines.launch
import com.example.data.models.UserProfile
import com.example.viewmodel.SearchDiscoveryViewModel
import java.util.Locale

/**
 * Reference-first Search landing. Real server discovery is preferred when available;
 * the existing feed/profile collections remain available during backend rollout.
 * Advanced search stays accessible through the filter control.
 */
private enum class ReferenceCategory(val label: String, val discoveryType: DiscoveryResultType?) {
    ALL("All", null),
    PEOPLE("People", DiscoveryResultType.PROFILE),
    POSTS("Posts", DiscoveryResultType.POST),
    MARKET("Market", DiscoveryResultType.MARKET_ITEM),
    COMMUNITIES("Communities", DiscoveryResultType.COMMUNITY),
}

private const val REF_RECENTS_PREF = "blink_reference_search"
private const val REF_RECENTS_KEY = "queries"
private const val REF_RECENTS_SEPARATOR = "\u001F"

@Composable
internal fun BlinkReferenceSearchScreen(
    profiles: List<UserProfile>,
    posts: List<FeedPost>,
    serverProfiles: List<UserProfile>,
    serverPosts: List<FeedPost>,
    marketItems: List<MarketItem>,
    currentUsername: String,
    myProfile: UserProfile,
    onProfileClick: (String) -> Unit,
    onPostClick: (FeedPost) -> Unit,
    onMarketItemClick: (MarketItem) -> Unit,
    onGameClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    hasUnreadNotifications: Boolean,
    onOpenAdvanced: () -> Unit,
    onBackToHome: () -> Unit,
    isDark: Boolean,
) {
    val search: SearchDiscoveryViewModel = viewModel()
    val discovery by search.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val followScope = rememberCoroutineScope()
    val followingIds by FollowStateStore.followingIds.collectAsStateWithLifecycle()
    var busyFollowId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { FollowStateStore.refresh() }
    val accountKey = myProfile.id.ifBlank { currentUsername.trim().lowercase(Locale.ROOT) }
    val recentsKey = "$REF_RECENTS_KEY:$accountKey"
    val prefs = remember(context) { context.getSharedPreferences(REF_RECENTS_PREF, Context.MODE_PRIVATE) }
    var query by rememberSaveable(accountKey) { mutableStateOf("") }
    var category by rememberSaveable(accountKey) { mutableStateOf(ReferenceCategory.ALL) }
    var recents by remember(accountKey) {
        mutableStateOf(
            prefs.getString(recentsKey, "").orEmpty().split(REF_RECENTS_SEPARATOR)
                .map(String::trim).filter(String::isNotEmpty).distinct().take(10)
        )
    }
    var selectedDetail by remember { mutableStateOf<DiscoveryResult?>(null) }

    val bg = if (isDark) Color(0xFF0C0D10) else Color.White
    val fg = if (isDark) Color.White else Color(0xFF111111)
    val muted = if (isDark) Color(0xFFA2A2A2) else Color(0xFF858585)
    val subtle = if (isDark) Color(0xFF202124) else Color(0xFFF4F4F4)
    val line = if (isDark) Color(0xFF373737) else Color(0xFFEEEEEE)
    val accent = if (isDark) Color.White else Color.Black
    val accentText = if (isDark) Color.Black else Color.White

    fun rememberQuery(value: String) {
        val clean = value.trim().take(200)
        if (clean.isBlank() || discovery.privateHistory) return
        recents = (listOf(clean) + recents.filterNot { it.equals(clean, ignoreCase = true) }).take(10)
        prefs.edit().putString(recentsKey, recents.joinToString(REF_RECENTS_SEPARATOR)).apply()
    }
    fun toggleFollow(profileId: String) {
        if (profileId.isBlank() || busyFollowId != null) return
        busyFollowId = profileId
        val next = profileId !in followingIds
        followScope.launch {
            val okay = FollowStateStore.setFollowing(profileId, next)
            busyFollowId = null
            if (!okay) Toast.makeText(context, "Follow request failed. Try again.", Toast.LENGTH_SHORT).show()
        }
    }
    fun select(value: ReferenceCategory) {
        category = value
        if (discovery.backendAvailable) search.selectType(value.discoveryType)
    }

    BackHandler {
        when {
            query.isNotBlank() -> query = ""
            category != ReferenceCategory.ALL -> select(ReferenceCategory.ALL)
            else -> onBackToHome()
        }
    }

    LaunchedEffect(discovery.backendAvailable, query) {
        if (discovery.backendAvailable && query != discovery.query) search.setQuery(query)
    }
    LaunchedEffect(discovery.backendAvailable, category) {
        if (discovery.backendAvailable && discovery.selectedType != category.discoveryType) {
            search.selectType(category.discoveryType)
        }
    }

    val allProfiles = remember(profiles, serverProfiles) {
        (profiles + serverProfiles).filter { it.username.isNotBlank() }
            .distinctBy { it.id.ifBlank { it.username.lowercase(Locale.ROOT) } }
    }
    val allPosts = remember(posts, serverPosts) { (posts + serverPosts).distinctBy { it.id } }
    val actualMarket = remember(marketItems, query) {
        marketItems.filter { !it.isSold && (
            query.isBlank() || it.title.contains(query, true) ||
            it.category.contains(query, true) || it.university.contains(query, true) ||
            it.description.contains(query, true) || it.location.contains(query, true)
        ) }.take(30)
    }
    val marketById = remember(marketItems) { marketItems.associateBy { it.id } }
    val currentProfile = myProfile.takeIf { it.id.isNotBlank() } ?: allProfiles.firstOrNull { it.username.equals(currentUsername, true) }
    val localPeople = remember(allProfiles, query) {
        allProfiles.filter {
            query.isBlank() || it.fullName.contains(query, true) ||
                it.username.contains(query, true) || it.university.contains(query, true) ||
                it.department.contains(query, true)
        }.take(30)
    }
    val localPosts = remember(allPosts, query) {
        allPosts.filter {
            !it.isReel && (query.isBlank() || it.text.contains(query, true) ||
                it.author.contains(query, true) || it.tags.any { tag -> tag.contains(query, true) })
        }.take(30)
    }
    val live = if (discovery.backendAvailable && discovery.query == query && !discovery.isLoading) discovery.results else emptyList()
    val livePeople = live.filter { it.type == DiscoveryResultType.PROFILE }
    val livePosts = live.filter { it.type == DiscoveryResultType.POST }
    val liveMarket = live.filter { it.type == DiscoveryResultType.MARKET_ITEM }
    val liveCommunities = live.filter { it.type == DiscoveryResultType.COMMUNITY }
    val limit = if (category == ReferenceCategory.ALL) 2 else 20

    Column(Modifier.fillMaxSize().background(bg).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(42.dp).clickable { if (currentUsername.isNotBlank()) onProfileClick(currentUsername) }, contentAlignment = Alignment.Center) {
                ReferenceAvatar(currentProfile?.avatarUrl, 42)
                if (currentProfile?.onlineNow == true) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(11.dp).clip(CircleShape)
                            .background(Color(0xFF18C95C))
                    )
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("BLINK", fontSize = 21.sp, letterSpacing = 4.sp,
                    fontWeight = FontWeight.Black, color = fg, maxLines = 1)
            }
            IconButton(onClick = onGameClick, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.SportsEsports, "Game", modifier = Modifier.size(23.dp), tint = fg)
            }
            Box(Modifier.size(40.dp)) {
                IconButton(onClick = onNotificationsClick, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Rounded.NotificationsNone, "Notifications", modifier = Modifier.size(23.dp), tint = fg)
                }
                if (hasUnreadNotifications) {
                    Box(Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 5.dp).size(7.dp)
                        .clip(CircleShape).background(Color(0xFFFA2B33)))
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier.weight(1f).heightIn(min = 49.dp),
                shape = RoundedCornerShape(18.dp),
                color = subtle,
            ) {
                Row(
                    Modifier.padding(horizontal = 13.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, null, Modifier.size(21.dp), tint = fg)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text(
                            "Search people, posts, market, communities...",
                            fontSize = 12.sp, color = muted, maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        BasicTextField(
                            value = query, onValueChange = { query = it.take(200) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            textStyle = TextStyle(fontSize = 13.sp, color = fg),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                rememberQuery(query)
                                keyboard?.hide()
                            }),
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Rounded.Close, "Clear search", Modifier.size(16.dp), tint = muted)
                        }
                    }
                }
            }
            Surface(
                onClick = onOpenAdvanced,
                shape = RoundedCornerShape(16.dp),
                color = subtle,
                border = BorderStroke(1.dp, line),
            ) {
                Box(Modifier.size(49.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Tune, "Search filters", Modifier.size(22.dp), tint = fg)
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            items(ReferenceCategory.entries, key = { it.name }) { option ->
                val active = option == category
                Surface(
                    onClick = { select(option) },
                    shape = RoundedCornerShape(40.dp),
                    color = if (active) accent else subtle,
                ) {
                    Text(
                        option.label,
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) accentText else muted,
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (query.isBlank() && category == ReferenceCategory.ALL) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent searches", Modifier.weight(1f), color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        recents = emptyList()
                        prefs.edit().remove(recentsKey).apply()
                    }) { Text("Clear all", fontSize = 11.sp, color = muted) }
                }
                if (recents.isEmpty()) {
                    Text("Your searches will appear here", fontSize = 11.sp, color = muted,
                        modifier = Modifier.padding(vertical = 6.dp))
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(recents, key = { it.lowercase(Locale.ROOT) }) { term ->
                        Surface(onClick = { query = term }, shape = RoundedCornerShape(22.dp), color = subtle) {
                            Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.History, null, Modifier.size(16.dp), tint = fg)
                                Spacer(Modifier.width(6.dp))
                                Text(term, maxLines = 1, fontSize = 11.sp, color = fg)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (discovery.backendAvailable && discovery.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = fg)
                Spacer(Modifier.height(8.dp))
            }
            if (discovery.backendAvailable && discovery.errorMessage != null) {
                Text(discovery.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                TextButton(onClick = search::refresh) { Text("Retry") }
            }

            if (category == ReferenceCategory.ALL || category == ReferenceCategory.PEOPLE) {
                ReferenceTitle("People", fg, muted) { select(ReferenceCategory.PEOPLE) }
                if (livePeople.isNotEmpty()) livePeople.take(limit).forEach { result ->
                    val profileId = result.id
                    ReferencePersonRow(
                        title = result.title, subtitle = result.subtitle, avatar = result.avatarUrl,
                        verified = result.verified, fg = fg, muted = muted, line = line,
                        followable = profileId.isNotBlank() && profileId != currentProfile?.id,
                        following = profileId in followingIds, isBusy = busyFollowId == profileId,
                        onFollow = { toggleFollow(profileId) },
                        onClick = { if (result.username.isNotBlank()) onProfileClick(result.username) },
                    )
                } else localPeople.take(limit).forEach { profile ->
                    val profileId = profile.id
                    ReferencePersonRow(
                        title = profile.fullName.ifBlank { profile.username },
                        subtitle = listOf(profile.university, profile.department).filter(String::isNotBlank).joinToString(" · "),
                        avatar = profile.avatarUrl,
                        verified = profile.verificationBadge != com.example.data.models.VerificationBadge.NONE,
                        fg = fg, muted = muted, line = line,
                        followable = profileId.isNotBlank() && profileId != currentProfile?.id,
                        following = profileId in followingIds, isBusy = busyFollowId == profileId,
                        onFollow = { toggleFollow(profileId) },
                        onClick = { onProfileClick(profile.username) },
                    )
                }
                if (livePeople.isEmpty() && localPeople.isEmpty()) ReferenceNoResults("No people found", muted)
                Spacer(Modifier.height(12.dp))
            }

            if (category == ReferenceCategory.ALL || category == ReferenceCategory.POSTS) {
                ReferenceTitle("Posts", fg, muted) { select(ReferenceCategory.POSTS) }
                if (livePosts.isNotEmpty()) livePosts.take(if (category == ReferenceCategory.ALL) 1 else limit).forEach { item ->
                    ReferencePostRow(item.title, item.subtitle, item.body, item.avatarUrl, item.likeCount, item.commentCount, fg, muted, line) {
                        onPostClick(allPosts.firstOrNull { it.id == item.id } ?: item.asFeedPost())
                    }
                } else localPosts.take(if (category == ReferenceCategory.ALL) 1 else limit).forEach { item ->
                    ReferencePostRow(item.author, item.timeAgo, item.text, item.authorAvatar, item.likes, item.commentsCount, fg, muted, line) {
                        onPostClick(item)
                    }
                }
                if (livePosts.isEmpty() && localPosts.isEmpty()) ReferenceNoResults("No posts found", muted)
                Spacer(Modifier.height(12.dp))
            }

            if (category == ReferenceCategory.ALL || category == ReferenceCategory.MARKET) {
                ReferenceTitle("Market", fg, muted) { select(ReferenceCategory.MARKET) }
                if (liveMarket.isNotEmpty()) {
                    liveMarket.take(if (category == ReferenceCategory.ALL) 1 else limit).forEach { item ->
                        ReferenceMarketRow(
                            title = item.title, category = item.category, price = item.price,
                            location = item.location, image = item.imageUrl,
                            fg = fg, bg = bg, muted = muted, line = line,
                            onClick = {
                                val local = marketById[item.id]
                                if (local != null) onMarketItemClick(local) else selectedDetail = item
                            }
                        )
                    }
                } else if (actualMarket.isNotEmpty()) {
                    actualMarket.take(if (category == ReferenceCategory.ALL) 1 else limit).forEach { item ->
                        ReferenceMarketRow(
                            title = item.title, category = item.category, price = item.price,
                            location = item.university.ifBlank { item.location }, image = item.images.firstOrNull(),
                            fg = fg, bg = bg, muted = muted, line = line,
                            onClick = { onMarketItemClick(item) }
                        )
                    }
                } else ReferenceNoResults("No market listings found", muted)
                Spacer(Modifier.height(12.dp))
            }

            if (category == ReferenceCategory.ALL || category == ReferenceCategory.COMMUNITIES) {
                ReferenceTitle("Communities", fg, muted) { select(ReferenceCategory.COMMUNITIES) }
                if (liveCommunities.isEmpty()) ReferenceNoResults(
                    if (discovery.backendAvailable) "No communities found" else "Community discovery is not available yet",
                    muted
                )
                liveCommunities.take(if (category == ReferenceCategory.ALL) 1 else limit).forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ReferenceThumbnail(item.imageUrl, 68)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("${item.memberCount} members · ${item.subtitle}", color = muted, fontSize = 10.sp, maxLines = 2)
                        }
                        Surface(onClick = { search.toggleEntity(item) }, shape = RoundedCornerShape(12.dp), color = accent) {
                            Text(if (item.following) "Joined" else "Join",
                                Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
                                color = accentText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = line)
                }
            }
            Spacer(Modifier.height(22.dp))
        }
    }

    selectedDetail?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedDetail = null },
            title = { Text(item.title) },
            text = {
                Column {
                    item.price?.let { Text("₦" + String.format(Locale.US, "%,d", it)) }
                    Text(item.body.ifBlank { item.subtitle })
                    if (item.location.isNotBlank()) Text(item.location)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val seller = item.username
                    selectedDetail = null
                    if (seller.isNotBlank()) onProfileClick(seller)
                }) { Text(if (item.username.isNotBlank()) "View seller" else "Close") }
            },
        )
    }
}

@Composable
private fun ReferenceTitle(title: String, fg: Color, muted: Color, onSeeAll: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        TextButton(onClick = onSeeAll, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
            Text("See all  ›", fontSize = 11.sp, color = muted)
        }
    }
}

@Composable
private fun ReferencePersonRow(
    title: String, subtitle: String, avatar: String?, verified: Boolean,
    fg: Color, muted: Color, line: Color,
    followable: Boolean, following: Boolean, isBusy: Boolean,
    onFollow: () -> Unit, onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clickable(onClick = onClick)) { ReferenceAvatar(avatar, 49) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clickable(onClick = onClick)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (verified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.Verified, "Verified", Modifier.size(15.dp), tint = Color(0xFF159CF5))
                }
            }
            Text(subtitle, color = muted, fontSize = 10.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Surface(
            onClick = if (followable) onFollow else onClick,
            enabled = !isBusy,
            shape = RoundedCornerShape(12.dp),
            color = if (followable && following) Color.Transparent else fg,
            border = if (followable && following) BorderStroke(1.dp, line) else null,
        ) {
            Text(
                when {
                    isBusy -> "..."
                    !followable -> "View"
                    following -> "Following"
                    else -> "Follow"
                },
                Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                color = if (followable && following) fg else if (fg == Color.White) Color.Black else Color.White,
                fontWeight = FontWeight.Bold, fontSize = 11.sp,
            )
        }
    }
    HorizontalDivider(color = line)
}

@Composable
private fun ReferenceMarketRow(
    title: String, category: String, price: Long?,
    location: String, image: String?, fg: Color, bg: Color, muted: Color, line: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(14.dp),
        color = bg, border = BorderStroke(1.dp, line),
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ReferenceThumbnail(image, 96)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1)
                Text(category, fontSize = 11.sp, color = muted, maxLines = 1)
                price?.let {
                    Text("₦" + String.format(Locale.US, "%,d", it), fontSize = 16.sp,
                        fontWeight = FontWeight.Bold, color = fg)
                }
                Text(location, fontSize = 10.sp, color = muted, maxLines = 1)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = muted)
        }
    }
}

@Composable
private fun ReferencePostRow(
    author: String, time: String, body: String, avatar: String?,
    likes: Int, comments: Int, fg: Color, muted: Color, line: Color,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = Color.Transparent, border = BorderStroke(1.dp, line)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReferenceAvatar(avatar, 37)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(author, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(time, color = muted, fontSize = 10.sp, maxLines = 1)
                }
                Icon(Icons.Rounded.MoreVert, null, tint = fg)
            }
            Spacer(Modifier.height(8.dp))
            Text(body, color = fg, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(19.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FavoriteBorder, null, Modifier.size(16.dp), tint = fg)
                Text(likes.toString(), fontSize = 10.sp, color = muted)
                Icon(Icons.Rounded.ChatBubbleOutline, null, Modifier.size(16.dp), tint = fg)
                Text(comments.toString(), fontSize = 10.sp, color = muted)
            }
        }
    }
}

@Composable
private fun ReferenceAvatar(url: String?, size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color(0xFFE8E8E8)), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Rounded.Person, null, tint = Color.DarkGray)
    }
}

@Composable
private fun ReferenceThumbnail(url: String?, size: Int) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEFEFEF)), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Rounded.Image, null, tint = Color.Gray)
    }
}

@Composable
private fun ReferenceNoResults(message: String, muted: Color) {
    Text(message, fontSize = 11.sp, color = muted, modifier = Modifier.padding(vertical = 10.dp))
}
