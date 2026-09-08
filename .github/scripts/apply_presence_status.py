from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def replace_at_least(text: str, old: str, new: str, minimum: int, label: str) -> str:
    count = text.count(old)
    if count < minimum:
        raise RuntimeError(f"{label}: expected at least {minimum} matches, found {count}")
    return text.replace(old, new)


# ---------------------------------------------------------------------------
# Android: one shared last-seen formatter, real message/profile presence,
# and status dots on presence-aware avatar surfaces.
# ---------------------------------------------------------------------------
time_path = Path("app/src/main/java/com/example/util/TimeFormatters.kt")
time = time_path.read_text()
time = replace_once(
    time,
    '''    private val absoluteFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a", Locale.getDefault())
''',
    '''    private val absoluteFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a", Locale.getDefault())
    private val presenceDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
''',
    "presence date formatter",
)
time = replace_once(
    time,
    '''    private fun parseInstant(rawTimestamp: String?): Instant? {
''',
    '''    fun presenceStatus(
        isOnline: Boolean,
        rawTimestamp: String?,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (isOnline) return "Active now"
        val instant = parseInstant(rawTimestamp) ?: return "Last seen recently"
        val seconds = max(0L, now.epochSecond - instant.epochSecond)
        val minutes = seconds / 60L
        val hours = seconds / 3_600L
        val days = seconds / 86_400L

        return when {
            seconds < 60L -> "Last seen just now"
            minutes == 1L -> "Last seen 1 min ago"
            minutes < 60L -> "Last seen $minutes mins ago"
            hours == 1L -> "Last seen 1 hr ago"
            hours < 24L -> "Last seen $hours hrs ago"
            days == 1L -> "Last seen 1 day ago"
            else -> "Last seen ${presenceDateFormatter.format(instant.atZone(zoneId))}"
        }
    }

    private fun parseInstant(rawTimestamp: String?): Instant? {
''',
    "presence status formatter",
)
time_path.write_text(time)

chat_path = Path("app/src/main/java/com/example/data/repository/ChatRepository.kt")
chat = chat_path.read_text()
chat = replace_once(
    chat,
    '''                        lastSeen = o.optString("partner_last_seen")
                            .takeIf { it.isNotBlank() && !it.equals("null", true) }
                            ?.let(TimeFormatters::relativeOrDate)
                            ?: "recently",
''',
    '''                        lastSeen = o.optString("partner_last_seen")
                            .takeIf { it.isNotBlank() && !it.equals("null", true) }
                            ?.let { TimeFormatters.presenceStatus(false, it) }
                            ?: "Last seen recently",
''',
    "chat last-seen formatting",
)
chat_path.write_text(chat)

profile_path = Path("app/src/main/java/com/example/ui/screens/ProfileScreen.kt")
profile = profile_path.read_text()
profile = replace_once(
    profile,
    '''import com.example.sharing.ShareLinkManager
import kotlinx.coroutines.delay
''',
    '''import com.example.sharing.ShareLinkManager
import com.example.util.TimeFormatters
import kotlinx.coroutines.delay
''',
    "ProfileScreen TimeFormatters import",
)
profile = replace_once(
    profile,
    '''                                    if (profile.onlineNow) {
                                        Box(
                                            modifier = Modifier
                                                .size(19.dp)
                                                .align(Alignment.BottomEnd)
                                                .background(Color(0xFF22C55E), CircleShape)
                                                .border(3.dp, bgColor, CircleShape)
                                        )
                                    }
''',
    '''                                    Box(
                                        modifier = Modifier
                                            .size(19.dp)
                                            .align(Alignment.BottomEnd)
                                            .background(
                                                if (profile.onlineNow) Color(0xFF22C55E) else Color(0xFF8B5A2B),
                                                CircleShape
                                            )
                                            .border(3.dp, bgColor, CircleShape)
                                    )
''',
    "profile green/brown presence dot",
)
profile = replace_once(
    profile,
    '''                        Text(
                            text = "@${profile.username}",
                            fontSize = 13.sp,
                            color = BlinkPink,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (profile.professionalHeadline.isNotBlank()) {
''',
    '''                        Text(
                            text = "@${profile.username}",
                            fontSize = 13.sp,
                            color = BlinkPink,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .background(
                                        if (profile.onlineNow) Color(0xFF22C55E) else Color(0xFF8B5A2B),
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = TimeFormatters.presenceStatus(profile.onlineNow, profile.lastSeenAt),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (profile.onlineNow) Color(0xFF22C55E) else textSecondary
                            )
                        }

                        if (profile.professionalHeadline.isNotBlank()) {
''',
    "profile presence label",
)
profile_path.write_text(profile)

messages_path = Path("app/src/main/java/com/example/ui/screens/PremiumMessagesScreen.kt")
messages = messages_path.read_text()
messages = replace_once(
    messages,
    '''    online: Boolean = false,
    emphasizeRing: Boolean = true
) {
''',
    '''    online: Boolean? = null,
    emphasizeRing: Boolean = true
) {
''',
    "message avatar nullable presence",
)
messages = replace_once(
    messages,
    '''        if (online) {
            Box(
                modifier = Modifier
                    .size(size * .25f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(palette.online)
                    .border(2.dp, palette.glass, CircleShape)
            )
        }
''',
    '''        online?.let { active ->
            Box(
                modifier = Modifier
                    .size(size * .25f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(if (active) palette.online else Color(0xFF8B5A2B))
                    .border(2.dp, palette.glass, CircleShape)
            )
        }
''',
    "message green/brown avatar dot",
)
messages_path.write_text(messages)

search_path = Path("app/src/main/java/com/example/ui/screens/ProfessionalSearchScreen.kt")
search = search_path.read_text()
search = replace_once(
    search,
    '''        if (person.onlineNow) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .background(BlinkOnlineGreen, CircleShape)
            )
        }
''',
    '''        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(14.dp)
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                .background(if (person.onlineNow) BlinkOnlineGreen else Color(0xFF8B5A2B), CircleShape)
        )
''',
    "search person green/brown dot",
)
search_path.write_text(search)

post_card_path = Path("app/src/main/java/com/example/ui/components/PostCard.kt")
post_card = post_card_path.read_text()
post_card = replace_once(
    post_card,
    '''    onConnectHubAuthor: () -> Unit = {},
    modifier: Modifier = Modifier
) {
''',
    '''    onConnectHubAuthor: () -> Unit = {},
    authorOnline: Boolean? = null,
    modifier: Modifier = Modifier
) {
''',
    "PostCard optional presence parameter",
)
post_card = replace_once(
    post_card,
    '''                    AsyncImage(
                        model = post.authorAvatar,
                        error = painterResource(R.drawable.ic_default_profile),
                        fallback = painterResource(R.drawable.ic_default_profile),
                        contentDescription = "$resolvedAuthorName profile picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                }

                Spacer(Modifier.width(10.dp))
''',
    '''                    AsyncImage(
                        model = post.authorAvatar,
                        error = painterResource(R.drawable.ic_default_profile),
                        fallback = painterResource(R.drawable.ic_default_profile),
                        contentDescription = "$resolvedAuthorName profile picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                    authorOnline?.let { active ->
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .align(Alignment.BottomEnd)
                                .background(if (active) Color(0xFF22C55E) else Color(0xFF8B5A2B), CircleShape)
                                .border(2.dp, surfaceColor, CircleShape)
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))
''',
    "PostCard presence dot",
)
post_card_path.write_text(post_card)

feed_path = Path("app/src/main/java/com/example/ui/screens/PremiumFeedScreen.kt")
feed = feed_path.read_text()
feed = replace_once(
    feed,
    '''        0 -> PremiumHomeFeed(
            posts = posts,
            reels = reels,
            currentUsername = currentUsername,
''',
    '''        0 -> PremiumHomeFeed(
            posts = posts,
            reels = reels,
            profiles = profiles,
            currentUsername = currentUsername,
''',
    "PremiumHomeFeed presence data call",
)
feed = replace_once(
    feed,
    '''private fun PremiumHomeFeed(
    posts: List<FeedPost>,
    reels: List<FeedPost>,
    currentUsername: String,
''',
    '''private fun PremiumHomeFeed(
    posts: List<FeedPost>,
    reels: List<FeedPost>,
    profiles: List<UserProfile>,
    currentUsername: String,
''',
    "PremiumHomeFeed presence data signature",
)
feed = replace_once(
    feed,
    '''    val context = LocalContext.current
    val resumePrefs = remember(context) {
''',
    '''    val context = LocalContext.current
    val authorPresenceByKey = remember(profiles) {
        buildMap<String, Boolean> {
            profiles.forEach { profile ->
                profile.username.trim().removePrefix("@").lowercase().takeIf(String::isNotBlank)?.let { put(it, profile.onlineNow) }
                profile.fullName.trim().lowercase().takeIf(String::isNotBlank)?.let { put(it, profile.onlineNow) }
            }
        }
    }
    val resumePrefs = remember(context) {
''',
    "PremiumHomeFeed author presence map",
)
feed = replace_once(
    feed,
    '''                                                    onVotePoll = onVotePoll,
                                                    isAuthor = post.author.equals(currentUsername.removePrefix("@"), ignoreCase = true) ||
''',
    '''                                                    onVotePoll = onVotePoll,
                                                    authorOnline = authorPresenceByKey[
                                                        post.authorUsername.trim().removePrefix("@").lowercase()
                                                    ] ?: authorPresenceByKey[post.author.trim().removePrefix("@").lowercase()],
                                                    isAuthor = post.author.equals(currentUsername.removePrefix("@"), ignoreCase = true) ||
''',
    "PremiumHomeFeed PostCard presence",
)
feed_path.write_text(feed)

# Formatter unit coverage.
test_path = Path("app/src/test/java/com/example/util/TimeFormattersTest.kt")
test_path.parent.mkdir(parents=True, exist_ok=True)
if test_path.exists():
    raise RuntimeError("TimeFormattersTest.kt already exists; refusing to overwrite")
test_path.write_text('''package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class TimeFormattersTest {
    private val now = Instant.parse("2026-09-08T20:00:00Z")

    @Test
    fun presenceStatus_followsMinuteHourDayThenDateRules() {
        assertEquals("Active now", TimeFormatters.presenceStatus(true, "2026-09-08T19:00:00Z", now, ZoneOffset.UTC))
        assertEquals("Last seen just now", TimeFormatters.presenceStatus(false, "2026-09-08T19:59:30Z", now, ZoneOffset.UTC))
        assertEquals("Last seen 1 min ago", TimeFormatters.presenceStatus(false, "2026-09-08T19:59:00Z", now, ZoneOffset.UTC))
        assertEquals("Last seen 59 mins ago", TimeFormatters.presenceStatus(false, "2026-09-08T19:01:00Z", now, ZoneOffset.UTC))
        assertEquals("Last seen 1 hr ago", TimeFormatters.presenceStatus(false, "2026-09-08T19:00:00Z", now, ZoneOffset.UTC))
        assertEquals("Last seen 2 hrs ago", TimeFormatters.presenceStatus(false, "2026-09-08T18:00:00Z", now, ZoneOffset.UTC))
        assertEquals("Last seen 1 day ago", TimeFormatters.presenceStatus(false, "2026-09-07T20:00:00Z", now, ZoneOffset.UTC))
        assertEquals("Last seen Sep 6, 2026", TimeFormatters.presenceStatus(false, "2026-09-06T20:00:00Z", now, ZoneOffset.UTC))
        assertEquals("Last seen recently", TimeFormatters.presenceStatus(false, null, now, ZoneOffset.UTC))
    }
}
''')

# ---------------------------------------------------------------------------
# Windows parity: profile/search/feed identity and direct messages consume the
# same server presence values and render the same green/brown status language.
# ---------------------------------------------------------------------------
models_path = Path("desktopApp/src/main/kotlin/com/blinkng/desktop/data/DesktopModels.kt")
models = models_path.read_text()
models = replace_once(
    models,
    '''    val coinBalance: Long,
    val isOnline: Boolean,
)
''',
    '''    val coinBalance: Long,
    val isOnline: Boolean,
    val lastSeenAt: String?,
)
''',
    "DesktopProfile last seen",
)
models = replace_once(
    models,
    '''    val authorVerificationTier: String,
    val text: String?,
''',
    '''    val authorVerificationTier: String,
    val authorOnline: Boolean,
    val text: String?,
''',
    "DesktopFeedPost author presence",
)
models = replace_once(
    models,
    '''data class DesktopConversation(
    val id: String,
    val title: String,
    val avatarUrl: String?,
    val isGroup: Boolean,
    val lastMessageAt: String?,
)
''',
    '''data class DesktopConversation(
    val id: String,
    val title: String,
    val avatarUrl: String?,
    val isGroup: Boolean,
    val lastMessageAt: String?,
    val isOnline: Boolean = false,
    val lastSeenAt: String? = null,
)
''',
    "DesktopConversation presence",
)
models_path.write_text(models)

client_path = Path("desktopApp/src/main/kotlin/com/blinkng/desktop/data/DesktopSupabaseClient.kt")
client = client_path.read_text()
client = replace_at_least(
    client,
    "current_wallet_balance,is_online",
    "current_wallet_balance,online_now,is_online,last_seen_at",
    2,
    "desktop profile presence selects",
)
client = replace_once(
    client,
    '''        coinBalance = row.optLong("current_wallet_balance"),
        isOnline = row.optBoolean("is_online"),
    )
''',
    '''        coinBalance = row.optLong("current_wallet_balance"),
        isOnline = row.optBoolean("online_now", row.optBoolean("is_online")),
        lastSeenAt = row.optNullableString("last_seen_at"),
    )
''',
    "desktop profile presence parse",
)
client = replace_once(
    client,
    '''        authorVerified = profile?.isVerified == true,
        authorVerificationTier = profile?.verificationTier.orEmpty(),
        text = row.optNullableString("text"),
''',
    '''        authorVerified = profile?.isVerified == true,
        authorVerificationTier = profile?.verificationTier.orEmpty(),
        authorOnline = profile?.isOnline == true,
        text = row.optNullableString("text"),
''',
    "desktop feed presence parse",
)
start = client.find("    suspend fun fetchConversations(): List<DesktopConversation> = withContext(Dispatchers.IO) {")
end = client.find("\n    suspend fun fetchMessages(conversationId: String): List<DesktopMessage>", start)
if start < 0 or end < 0:
    raise RuntimeError("desktop fetchConversations boundaries not found")
new_fetch_conversations = '''    suspend fun fetchConversations(): List<DesktopConversation> = withContext(Dispatchers.IO) {
        val summaryResponse = runCatching {
            postObject(
                "/rest/v1/rpc/get_conversation_summaries_page",
                JSONObject()
                    .put("p_limit", 100)
                    .put("p_before", JSONObject.NULL)
                    .put("p_before_id", JSONObject.NULL),
            )
        }.getOrNull()
        val summaryRows = summaryResponse as? JSONArray
        if (summaryRows != null) {
            return@withContext (0 until summaryRows.length()).mapNotNull { i ->
                summaryRows.optJSONObject(i)?.let { row ->
                    DesktopConversation(
                        id = row.optString("conversation_id"),
                        title = row.optString("partner_name").ifBlank {
                            row.optString("partner_username").ifBlank { "Conversation" }
                        },
                        avatarUrl = row.optNullableString("partner_avatar"),
                        isGroup = row.optBoolean("is_group", false),
                        lastMessageAt = row.optNullableString("last_message_at"),
                        isOnline = row.optBoolean("partner_online", false),
                        lastSeenAt = row.optNullableString("partner_last_seen"),
                    )
                }
            }
        }

        // Graceful compatibility fallback for deployments where the summary RPC is unavailable.
        val rows = getArray(
            "/rest/v1/conversations?select=id,title,avatar_url,is_group,last_message_at&order=last_message_at.desc.nullslast&limit=100",
        )
        (0 until rows.length()).mapNotNull { i ->
            rows.optJSONObject(i)?.let { row ->
                DesktopConversation(
                    id = row.optString("id"),
                    title = row.optString("title").ifBlank { if (row.optBoolean("is_group")) "Group chat" else "Conversation" },
                    avatarUrl = row.optNullableString("avatar_url"),
                    isGroup = row.optBoolean("is_group"),
                    lastMessageAt = row.optNullableString("last_message_at"),
                )
            }
        }
    }
'''
client = client[:start] + new_fetch_conversations + client[end:]
client_path.write_text(client)

presence_ui_path = Path("desktopApp/src/main/kotlin/com/blinkng/desktop/ui/PresenceUi.kt")
if presence_ui_path.exists():
    raise RuntimeError("PresenceUi.kt already exists; refusing to overwrite")
presence_ui_path.write_text('''package com.blinkng.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

private val desktopPresenceDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
private val onlineGreen = Color(0xFF22C55E)
private val offlineBrown = Color(0xFF8B5A2B)

internal fun desktopPresenceStatus(
    isOnline: Boolean,
    rawTimestamp: String?,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    if (isOnline) return "Active now"
    val raw = rawTimestamp?.trim().orEmpty()
    val instant = if (raw.isBlank() || raw.equals("null", true)) null else {
        runCatching { Instant.parse(raw) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
    } ?: return "Last seen recently"
    val seconds = max(0L, now.epochSecond - instant.epochSecond)
    val minutes = seconds / 60L
    val hours = seconds / 3_600L
    val days = seconds / 86_400L
    return when {
        seconds < 60L -> "Last seen just now"
        minutes == 1L -> "Last seen 1 min ago"
        minutes < 60L -> "Last seen $minutes mins ago"
        hours == 1L -> "Last seen 1 hr ago"
        hours < 24L -> "Last seen $hours hrs ago"
        days == 1L -> "Last seen 1 day ago"
        else -> "Last seen ${desktopPresenceDateFormatter.format(instant.atZone(zoneId))}"
    }
}

@Composable
internal fun PresenceAvatar(
    name: String,
    isOnline: Boolean?,
    size: Dp = 38.dp,
) {
    Box(modifier = Modifier.size(size)) {
        Box(
            modifier = Modifier.fillMaxSize().clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.trim().firstOrNull()?.uppercase() ?: "B",
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        isOnline?.let { active ->
            Box(
                modifier = Modifier
                    .size((size.value * .28f).dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(if (active) onlineGreen else offlineBrown)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
    }
}
''')

windows_messages_path = Path("desktopApp/src/main/kotlin/com/blinkng/desktop/ui/MessagesScreen.kt")
windows_messages = windows_messages_path.read_text()
windows_messages = replace_once(
    windows_messages,
    '''                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(conversation.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                if (conversation.isGroup) "Group conversation" else "Direct conversation",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
''',
    '''                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PresenceAvatar(
                                name = conversation.title,
                                isOnline = if (conversation.isGroup) null else conversation.isOnline,
                                size = 42.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(conversation.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(
                                    if (conversation.isGroup) "Group conversation" else desktopPresenceStatus(conversation.isOnline, conversation.lastSeenAt),
                                    fontSize = 11.sp,
                                    color = if (!conversation.isGroup && conversation.isOnline) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
''',
    "desktop conversation list presence",
)
windows_messages = replace_once(
    windows_messages,
    '''            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(active.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    if (active.isGroup) "Group chat" else "Blink conversation",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
''',
    '''            Spacer(Modifier.width(4.dp))
            PresenceAvatar(
                name = active.title,
                isOnline = if (active.isGroup) null else active.isOnline,
                size = 40.dp,
            )
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(active.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    if (active.isGroup) "Group chat" else desktopPresenceStatus(active.isOnline, active.lastSeenAt),
                    fontSize = 11.sp,
                    color = if (!active.isGroup && active.isOnline) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
''',
    "desktop active chat presence",
)
windows_messages = replace_once(
    windows_messages,
    '''import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
''',
    '''import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
''',
    "desktop Messages Color import",
)
windows_messages_path.write_text(windows_messages)

content_path = Path("desktopApp/src/main/kotlin/com/blinkng/desktop/ui/ContentScreens.kt")
content = content_path.read_text()
content = replace_once(
    content,
    '''                        AvatarInitial(profile.fullName)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            VerifiedName(profile.fullName, profile.isVerified)
                            Text("@${profile.username} • ${profile.university ?: "Blinkng"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
''',
    '''                        PresenceAvatar(profile.fullName, profile.isOnline)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            VerifiedName(profile.fullName, profile.isVerified)
                            Text("@${profile.username} • ${profile.university ?: "Blinkng"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                desktopPresenceStatus(profile.isOnline, profile.lastSeenAt),
                                fontSize = 10.sp,
                                color = if (profile.isOnline) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
''',
    "desktop search profile presence",
)
content = replace_once(
    content,
    '''                            AvatarInitial(profile.fullName, 64.dp)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                VerifiedName(profile.fullName, profile.isVerified, 21.sp)
                                Text("@${profile.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(listOfNotNull(profile.university, profile.faculty, profile.department).joinToString(" • "), fontSize = 12.sp)
                            }
''',
    '''                            PresenceAvatar(profile.fullName, profile.isOnline, 64.dp)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                VerifiedName(profile.fullName, profile.isVerified, 21.sp)
                                Text("@${profile.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    desktopPresenceStatus(profile.isOnline, profile.lastSeenAt),
                                    fontSize = 11.sp,
                                    color = if (profile.isOnline) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(listOfNotNull(profile.university, profile.faculty, profile.department).joinToString(" • "), fontSize = 12.sp)
                            }
''',
    "desktop profile presence",
)
content = replace_once(
    content,
    '''                AvatarInitial(post.authorName)
                Spacer(Modifier.width(10.dp))
''',
    '''                PresenceAvatar(post.authorName, post.authorOnline)
                Spacer(Modifier.width(10.dp))
''',
    "desktop feed avatar presence",
)
content_path.write_text(content)

parity_path = Path("platform-parity/changes.md")
parity = parity_path.read_text()
row = "| 2026-09-08 | Presence and last-seen identity | Profiles, people search, direct messages and feed avatars use server-backed presence; known avatars show green when active and brown when offline, while profile/chat status reads Active now then minute/hour/day/date last seen | Desktop profile, people search, feed identity and direct messages use the same green/brown presence treatment and minute/hour/day/date status from Supabase presence fields | Existing set_my_presence heartbeat and 2-minute server presence lease remain authoritative; no Supabase schema or production migration change |\n"
parity = replace_once(
    parity,
    "|---|---|---|---|---|\n",
    "|---|---|---|---|---|\n" + row,
    "presence parity ledger row",
)
parity_path.write_text(parity)

# Structural safeguards before expensive builds.
checks = {
    "android presence formatter": "fun presenceStatus(" in time,
    "android profile offline dot": "Color(0xFF8B5A2B)" in profile,
    "android message nullable presence": "online: Boolean? = null" in messages,
    "android search offline dot": "else Color(0xFF8B5A2B)" in search,
    "android feed presence": "authorOnline = authorPresenceByKey" in feed,
    "desktop presence UI": presence_ui_path.exists(),
    "desktop message last seen": "desktopPresenceStatus(active.isOnline" in windows_messages,
    "desktop profile last seen": "desktopPresenceStatus(profile.isOnline" in content,
    "parity ledger": "Presence and last-seen identity" in parity,
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise RuntimeError("presence patch structural checks failed: " + ", ".join(failed))
