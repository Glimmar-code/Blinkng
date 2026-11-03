from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]

# 1) Make the Kotlin feed parser trust a video only when the row is explicitly a reel.
service = ROOT / "app/src/main/java/com/example/data/supabase/SupabaseService.kt"
s = service.read_text()
old = '''        val isVerified = obj.optBoolean("is_verified", false) || obj.optString("verification_badge", "").equals("BLUE", ignoreCase = true) || obj.optString("verification_badge", "").equals("GOLD", ignoreCase = true)'''
new = '''        val parsedVideoUrl = obj.optString("video_url", "").takeIf { it.isNotBlank() }\n        val parsedType = obj.optString("type", "").lowercase(Locale.US)\n        val parsedIsReel = parsedType == "reel" && !parsedVideoUrl.isNullOrBlank() && obj.optBoolean("is_reel", true)\n\n        val isVerified = obj.optBoolean("is_verified", false) || obj.optString("verification_badge", "").equals("BLUE", ignoreCase = true) || obj.optString("verification_badge", "").equals("GOLD", ignoreCase = true)'''
if old in s and "val parsedVideoUrl" not in s:
    s = s.replace(old, new, 1)
s = s.replace('''            isReel = obj.optBoolean("is_reel", false),\n            videoDuration = obj.optString("video_duration", "0:00"),\n            videoUrl = obj.optString("video_url", null),''', '''            isReel = parsedIsReel,\n            videoDuration = obj.optString("video_duration", "0:00"),\n            videoUrl = parsedVideoUrl,''', 1)
service.write_text(s)

# 2) Wire chat calls to a deterministic Jitsi room.
messages = ROOT / "app/src/main/java/com/example/ui/screens/MessagesScreen.kt"
m = messages.read_text()
if 'import android.content.Intent' not in m:
    m = 'import android.content.Intent\nimport android.net.Uri\n' + m
needle = '''private fun ChatTopBar(\n    convo: ChatConversation,'''
if needle in m and 'val callContext = LocalContext.current' not in m:
    marker = ''') {\n\n    TopAppBar('''
    replacement = ''') {\n\n    val callContext = LocalContext.current\n    val callRoom = "https://meet.jit.si/Blink-${convo.id}"\n\n    TopAppBar('''
    pos = m.find(needle)
    end = m.find(marker, pos)
    if end != -1:
        m = m[:end] + replacement + m[end + len(marker):]
        m = m.replace('''IconButton(\n                onClick = {}\n            ) {\n\n                Icon(\n                    Icons.Default.Phone,''', '''IconButton(\n                onClick = {\n                    callContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$callRoom#config.startWithVideoMuted=true")))\n                }\n            ) {\n\n                Icon(\n                    Icons.Default.Phone,''', 1)
        m = m.replace('''IconButton(\n                onClick = {}\n            ) {\n\n                Icon(\n                    Icons.Default.Videocam,''', '''IconButton(\n                onClick = {\n                    callContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(callRoom)))\n                }\n            ) {\n\n                Icon(\n                    Icons.Default.Videocam,''', 1)
messages.write_text(m)

# 3) Surface Google OAuth errors instead of silently returning.
callback = ROOT / "app/src/main/java/com/example/auth/GoogleAuthCallbackActivity.kt"
c = callback.read_text()
old_error = '''        val accessToken = params["access_token"]\n        val refreshToken = params["refresh_token"]\n        if (!accessToken.isNullOrBlank()) SupabaseService.saveSession(accessToken, refreshToken)'''
new_error = '''        val accessToken = params["access_token"]\n        val refreshToken = params["refresh_token"]\n        val errorDescription = params["error_description"] ?: params["error"]\n        if (!errorDescription.isNullOrBlank()) {\n            android.util.Log.e("GoogleAuthCallback", "OAuth failed: $errorDescription")\n            returnToMain()\n            return\n        }\n        if (!accessToken.isNullOrBlank()) SupabaseService.saveSession(accessToken, refreshToken)'''
if old_error in c:
    c = c.replace(old_error, new_error, 1)
callback.write_text(c)

# 4) Keep the Android Google OAuth flow on the existing implicit-token callback.
auth = ROOT / "app/src/main/java/com/example/data/repository/AuthRepository.kt"
a = auth.read_text()
a = a.replace('val url = "$baseUrl/auth/v1/authorize?provider=google&redirect_to=$redirect"', 'val url = "$baseUrl/auth/v1/authorize?provider=google&redirect_to=$redirect&flow_type=implicit"', 1)
auth.write_text(a)

# 5) Keep the Home/Reel/Connect/Game switcher visible in fullscreen Reels.
reels = ROOT / "app/src/main/java/com/example/ui/screens/VideoReelsScreen.kt"
r = reels.read_text()
reel_old = '''        VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->\n            ReelPage(reels[index], index == pager.currentPage, onLike, onComment, onBookmark, onShare, onProfileClick)\n        }\n        Text("Reels", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 45.dp))'''
reel_new = '''        VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->\n            ReelPage(reels[index], index == pager.currentPage, onLike, onComment, onBookmark, onShare, onProfileClick)\n        }\n        ReelsTopNavigation(onPosts = onBackToPosts, onConnect = onConnectClick, onGame = onGameClick)\n        Text("Reels", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 45.dp))'''
if reel_old in r:
    r = r.replace(reel_old, reel_new, 1)
if 'private fun ReelsTopNavigation(' not in r and 'Text("For You"' not in r:
    r += '''\n\n@Composable\nprivate fun ReelsTopNavigation(onPosts: () -> Unit, onConnect: () -> Unit, onGame: () -> Unit) {\n    Row(Modifier.fillMaxWidth().padding(top = 82.dp, start = 18.dp, end = 18.dp).align(Alignment.TopCenter), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {\n        ReelNavButton("Post", false, onPosts)\n        Spacer(Modifier.width(6.dp))\n        ReelNavButton("Reel", true) {}\n        Spacer(Modifier.width(6.dp))\n        ReelNavButton("Connect", false, onConnect)\n        Spacer(Modifier.width(6.dp))\n        ReelNavButton("Game", false, onGame)\n    }\n}\n\n@Composable\nprivate fun ReelNavButton(label: String, selected: Boolean, onClick: () -> Unit) {\n    Surface(Modifier.clickable { onClick() }, shape = CircleShape, color = if (selected) Color.White else Color.White.copy(alpha = 0.14f)) {\n        Text(label, color = if (selected) Color.Black else Color.White, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp))\n    }\n}\n'''
reels.write_text(r)

# 6) Add explicit video media to chat messages.
model = ROOT / "app/src/main/java/com/example/data/models/PostModel.kt"
p = model.read_text()
p = p.replace('    val attachedImageUrl: String? = null\n', '    val attachedImageUrl: String? = null,\n    val attachedVideoUrl: String? = null\n', 1)
model.write_text(p)

media = ROOT / "app/src/main/java/com/example/data/supabase/MessageMediaService.kt"
if not media.exists():
    media.write_text(r'''package com.example.data.supabase

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.data.models.ChatConversation
import com.example.data.models.ChatMessage
import com.example.data.models.MessageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit

object MessageMediaService {
    private const val TAG = "MessageMediaService"
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private fun uid(): String? = SupabaseService.accessToken()?.let { token -> runCatching { val part = token.split('.').getOrNull(1) ?: return@runCatching null; JSONObject(String(Base64.decode(part, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), StandardCharsets.UTF_8)).optString("sub").takeIf { it.isNotBlank() } }.getOrNull() }
    private fun builder(path: String): Request.Builder { val token = SupabaseService.accessToken() ?: throw IllegalStateException("Please sign in again."); return Request.Builder().url("${SupabaseConfig.url.trimEnd('/')}$path").addHeader("apikey", SupabaseConfig.anonKey).addHeader("Authorization", "Bearer $token").addHeader("Accept", "application/json") }
    private fun error(body: String, fallback: String): String = runCatching { JSONObject(body).optString("message").ifBlank { JSONObject(body).optString("hint") } }.getOrNull().orEmpty().ifBlank { fallback }
    suspend fun sendVideoMessage(context: Context, receiverUsername: String, uri: Uri): Result<ChatMessage> = withContext(Dispatchers.IO) {
        try {
            val senderId = uid() ?: return@withContext Result.failure(Exception("Please sign in again.")); val receiver = receiverUsername.trim(); if (receiver.isBlank()) return@withContext Result.failure(Exception("Recipient is required."))
            val target = client.newCall(builder("/rest/v1/profiles?username=eq.${java.net.URLEncoder.encode(receiver, "UTF-8")}&select=id,username&limit=1").get().build()).execute().use { r -> val body = r.body?.string().orEmpty(); if (!r.isSuccessful) throw IllegalStateException(error(body, "Recipient lookup failed.")); JSONArray(body.ifBlank { "[]" }).optJSONObject(0) } ?: return@withContext Result.failure(Exception("Recipient not found."))
            val targetId = target.optString("id"); if (targetId == senderId) return@withContext Result.failure(Exception("Cannot message yourself."))
            val mine = client.newCall(builder("/rest/v1/conversation_participants?user_id=eq.${java.net.URLEncoder.encode(senderId, "UTF-8")}&select=conversation_id").get().build()).execute().use { r -> JSONArray(r.body?.string().orEmpty().ifBlank { "[]" }) }
            var conversationId = ""
            for (i in 0 until mine.length()) { val cid = mine.optJSONObject(i)?.optString("conversation_id").orEmpty(); if (cid.isBlank()) continue; val exists = client.newCall(builder("/rest/v1/conversation_participants?conversation_id=eq.${java.net.URLEncoder.encode(cid, "UTF-8")}&user_id=eq.${java.net.URLEncoder.encode(targetId, "UTF-8")}&select=user_id&limit=1").get().build()).execute().use { r -> r.isSuccessful && r.body?.string().orEmpty() != "[]" }; if (exists) { conversationId = cid; break } }
            if (conversationId.isBlank()) { val cBody = JSONObject().put("created_by", senderId).put("is_group", false); val cRaw = client.newCall(builder("/rest/v1/conversations").addHeader("Prefer", "return=representation").post(cBody.toString().toRequestBody(jsonType)).build()).execute().use { r -> val body = r.body?.string().orEmpty(); if (!r.isSuccessful) throw IllegalStateException(error(body, "Conversation creation failed.")); body }; conversationId = JSONArray(cRaw).getJSONObject(0).optString("id"); listOf(senderId, targetId).forEach { member -> val body = JSONObject().put("conversation_id", conversationId).put("user_id", member); client.newCall(builder("/rest/v1/conversation_participants").post(body.toString().toRequestBody(jsonType)).build()).execute().use { r -> if (!r.isSuccessful) throw IllegalStateException(error(r.body?.string().orEmpty(), "Conversation membership failed.")) } } }
            val mime = context.contentResolver.getType(uri) ?: "video/mp4"; val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext Result.failure(Exception("Could not read video.")); if (bytes.isEmpty()) return@withContext Result.failure(Exception("Video is empty.")); val ext = when { mime.contains("webm", true) -> "webm"; mime.contains("quicktime", true) -> "mov"; else -> "mp4" }; val path = "users/$senderId/messages/videos/${UUID.randomUUID()}.$ext"
            client.newCall(builder("/storage/v1/object/post-media/$path").addHeader("Content-Type", mime).addHeader("x-upsert", "true").post(bytes.toRequestBody(mime.toMediaType())).build()).execute().use { r -> val body = r.body?.string().orEmpty(); if (!r.isSuccessful) throw IllegalStateException(error(body, "Video upload failed.")) }
            val publicUrl = "${SupabaseConfig.url.trimEnd('/')}/storage/v1/object/public/post-media/$path"; val body = JSONObject().put("conversation_id", conversationId).put("sender_id", senderId).put("content", "Video").put("media_url", publicUrl).put("is_read", false).put("message_type", "video"); val raw = client.newCall(builder("/rest/v1/messages").addHeader("Prefer", "return=representation").post(body.toString().toRequestBody(jsonType)).build()).execute().use { r -> val text = r.body?.string().orEmpty(); if (!r.isSuccessful) throw IllegalStateException(error(text, "Video message failed.")); text }; val o = JSONArray(raw).getJSONObject(0)
            Result.success(ChatMessage(id = o.optString("id"), senderId = senderId, text = "Video", timestamp = "Just now", isFromMe = true, conversationId = conversationId, rawTimestamp = o.optString("created_at"), isRead = false, status = MessageStatus.SENT, attachedVideoUrl = publicUrl))
        } catch (e: Exception) { Log.e(TAG, "sendVideoMessage failed", e); Result.failure(e) }
    }
    suspend fun hydrateVideos(conversations: List<ChatConversation>): List<ChatConversation> = withContext(Dispatchers.IO) {
        if (conversations.isEmpty() || SupabaseService.accessToken().isNullOrBlank()) return@withContext conversations
        try { val raw = client.newCall(builder("/rest/v1/messages?select=id,media_url,message_type&message_type=eq.video&limit=300").get().build()).execute().use { r -> if (!r.isSuccessful) return@withContext conversations else r.body?.string().orEmpty() }; val arr = JSONArray(raw.ifBlank { "[]" }); val mediaById = mutableMapOf<String, String>(); for (i in 0 until arr.length()) { val o = arr.optJSONObject(i) ?: continue; val id = o.optString("id"); val url = o.optString("media_url"); if (id.isNotBlank() && url.isNotBlank()) mediaById[id] = url }; conversations.map { convo -> convo.copy(messages = convo.messages.map { msg -> msg.copy(attachedVideoUrl = mediaById[msg.id]) }.toMutableList()) }
        } catch (e: Exception) { Log.w(TAG, "hydrateVideos failed", e); conversations }
    }
}
''')

vm = ROOT / "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"
v = vm.read_text()
if 'import com.example.data.supabase.MessageMediaService' not in v:
    v = v.replace('import com.example.data.supabase.SupabaseService\n', 'import com.example.data.supabase.SupabaseService\nimport com.example.data.supabase.MessageMediaService\n', 1)
v = v.replace('val conversations = supabaseService.fetchMessages()', 'val conversations = MessageMediaService.hydrateVideos(supabaseService.fetchMessages())', 1)
if 'fun sendVideoMessage(partnerUsername: String, uri: Uri)' not in v:
    marker = '    fun retrySendMessage(partnerUsername: String, failedMessage: ChatMessage) {'
    method = '''    fun sendVideoMessage(partnerUsername: String, uri: Uri) {\n        val cleanPartner = partnerUsername.trim()\n        if (cleanPartner.isBlank()) return\n        val tempId = "temp_video_${UUID.randomUUID()}"\n        val uid = supabaseService.getCurrentUserId() ?: "local_user"\n        appendMessageToState(cleanPartner, ChatMessage(id = tempId, senderId = uid, receiverUsername = cleanPartner, text = "Video", timestamp = "Sending...", isFromMe = true, isRead = false, status = MessageStatus.SENDING))\n        viewModelScope.launch {\n            MessageMediaService.sendVideoMessage(appContext, cleanPartner, uri).fold({ serverMsg ->\n                replaceMessageInState(cleanPartner, tempId, serverMsg.copy(status = MessageStatus.SENT))\n                fetchSupabaseData()\n            }, {\n                updateMessageStatusInState(cleanPartner, tempId, MessageStatus.FAILED)\n                showToast("Failed to send video. Tap the message to retry.")\n            })\n        }\n    }\n\n'''
    if marker not in v: raise SystemExit('retrySendMessage marker missing')
    v = v.replace(marker, method + marker, 1)
vm.write_text(v)

# 7) Chat video picker and playable video-message bubble.
if 'import androidx.activity.result.contract.ActivityResultContracts' not in m:
    m = m.replace('import android.net.Uri\n', 'import android.net.Uri\nimport androidx.activity.result.contract.ActivityResultContracts\nimport androidx.activity.compose.rememberLauncherForActivityResult\n', 1)
sig_old = '''    onSendMessage: (String) -> Unit,\n    onProfileClick: (String) -> Unit,'''
sig_new = '''    onSendMessage: (String) -> Unit,\n    onSendVideo: (Uri) -> Unit = {},\n    onProfileClick: (String) -> Unit,'''
if sig_old in m: m = m.replace(sig_old, sig_new, 1)
anchor = '''    val clipboard =\n        LocalClipboardManager.current\n'''
if anchor in m and 'val videoPicker = rememberLauncherForActivityResult' not in m:
    m = m.replace(anchor, anchor + '''\n    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->\n        if (uri != null) onSendVideo(uri)\n    }\n''', 1)
old_sheet = '''        AttachmentSheet(\n            onDismiss = {\n                showAttachmentSheet = false\n            }\n        )'''
new_sheet = '''        AttachmentSheet(\n            onDismiss = {\n                showAttachmentSheet = false\n            },\n            onVideo = {\n                showAttachmentSheet = false\n                videoPicker.launch("video/*")\n            }\n        )'''
if old_sheet in m: m = m.replace(old_sheet, new_sheet, 1)
sheet_sig = '''private fun AttachmentSheet(\n    onDismiss: () -> Unit\n) {'''
if sheet_sig in m: m = m.replace(sheet_sig, '''private fun AttachmentSheet(\n    onDismiss: () -> Unit,\n    onVideo: () -> Unit = {}\n) {''', 1)
photo_anchor = '''            AttachmentGridItem(\n                icon =\n                    Icons.Default.PhotoLibrary,'''
if photo_anchor in m and 'title = "Video"' not in m:
    m = m.replace(photo_anchor, '''            AttachmentGridItem(\n                icon = Icons.Default.VideoLibrary,\n                title = "Video",\n                subtitle = "Choose a video from your gallery",\n                tint = BlinkPink,\n                onClick = onVideo\n            )\n\n''' + photo_anchor, 1)
item_sig = '''private fun AttachmentGridItem(\n    icon: androidx.compose.ui.graphics.vector.ImageVector,\n    title: String,\n    subtitle: String,\n    tint: Color\n) {'''
if item_sig in m: m = m.replace(item_sig, '''private fun AttachmentGridItem(\n    icon: androidx.compose.ui.graphics.vector.ImageVector,\n    title: String,\n    subtitle: String,\n    tint: Color,\n    onClick: () -> Unit = {}\n) {''', 1)
m = m.replace('            .clickable {}\n                .padding(', '            .clickable { onClick() }\n                .padding(', 1)
row_marker = '''            Column(\n                modifier =\n                    Modifier\n                        .widthIn(\n                            max = 305.dp\n                        )\n                        .padding(\n                            horizontal = 13.dp,\n                            vertical = 9.dp\n                        )\n            ) {\n'''
if row_marker in m and 'message.attachedVideoUrl' not in m:
    media_block = '''            Column(\n                modifier =\n                    Modifier\n                        .widthIn(\n                            max = 305.dp\n                        )\n                        .padding(\n                            horizontal = 13.dp,\n                            vertical = 9.dp\n                        )\n            ) {\n                if (!message.attachedVideoUrl.isNullOrBlank()) {\n                    val context = LocalContext.current\n                    Surface(shape = RoundedCornerShape(12.dp), color = Color.Black.copy(alpha = 0.18f), modifier = Modifier.fillMaxWidth().clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(message.attachedVideoUrl))) } }) {\n                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {\n                            Icon(Icons.Default.PlayCircle, contentDescription = "Play video", tint = if (message.isFromMe) Color.White else BlinkPink, modifier = Modifier.size(30.dp))\n                            Spacer(Modifier.width(9.dp))\n                            Column {\n                                Text("Video message", fontWeight = FontWeight.Bold, color = if (message.isFromMe) Color.White else MaterialTheme.colorScheme.onSurface)\n                                Text("Tap to play", fontSize = 10.sp, color = if (message.isFromMe) Color.White.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant)\n                            }\n                        }\n                    }\n                    Spacer(Modifier.height(5.dp))\n                }\n'''
    m = m.replace(row_marker, media_block, 1)
    close_marker = '''                Spacer(\n                    modifier =\n                        Modifier.height(\n                            4.dp\n                        )\n                )'''
    m = m.replace(close_marker, '''                }\n\n                Spacer(\n                    modifier =\n                        Modifier.height(\n                            4.dp\n                        )\n                )''', 1)
messages.write_text(m)

# 8) Wire video selection into the existing fullscreen chat overlay.
main = ROOT / "app/src/main/java/com/example/MainActivity.kt"
ma = main.read_text()
ma = ma.replace('''                    onSendMessage = { text -> viewModel.sendMessage(convo.partnerUsername, text) },\n                    onProfileClick = {''', '''                    onSendMessage = { text -> viewModel.sendMessage(convo.partnerUsername, text) },\n                    onSendVideo = { uri -> viewModel.sendVideoMessage(convo.partnerUsername, uri) },\n                    onProfileClick = {''', 1)
main.write_text(ma)
